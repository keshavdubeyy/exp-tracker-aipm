package com.yourteam.expensetracker.notifications

import com.yourteam.expensetracker.BuildConfig
import android.accessibilityservice.AccessibilityService
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.yourteam.expensetracker.data.AppDatabase
import com.yourteam.expensetracker.data.PendingPayment
import com.yourteam.expensetracker.data.ScreenPayment
import com.yourteam.expensetracker.parser.GPayChatParser
import com.yourteam.expensetracker.parser.GPayScreenParser
import com.yourteam.expensetracker.parser.UpiPinScreenParser
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Reads GPay's chat screens ("Payment to X ₹N Paid …") so small payments (no bank SMS, no GPay
 * notification) are still captured. The config limits events to GPay's package.
 * Debug builds log counts and masked words only; never names, IDs or amounts.
 */
class GPayScreenReader : AccessibilityService() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val dao by lazy { AppDatabase.get(this).screenPaymentDao() }
    private val pendingDao by lazy { AppDatabase.get(this).pendingPaymentDao() }

    // One attempt = one PIN screen session. While we wait for it to close, the PIN screen's
    // many events are ignored. A PIN screen after that is a new attempt, unless an unanswered
    // attempt to the same payee and amount exists (wrong PIN, or the user went back to it).
    private var attempt: Job? = null

    // Chat screens fire many events; skip lines already sent to the database this session.
    private val savedKeys = HashSet<String>()

    // Pop-up: once the PIN screen is gone for any reason (paid, Back, Home, GPay swiped away
    // from recents), wait 5 s, then ask. We poll instead of waiting for GPay events, because
    // after Home or a swipe-away GPay sends no more events.
    private val overlay by lazy { PaymentOverlay(this) }

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        if (event.packageName != GPAY_PACKAGE) return

        // Read every GPay window, not just the "active" one: the success screen may be an overlay.
        val roots = gpayRoots()
        val texts = mutableListOf<String>()
        val counts = IntArray(2) // [0] = nodes seen, [1] = nodes hidden as "data sensitive"
        roots.forEach { collectTexts(it, texts, counts, depth = 0) }

        if (isDebuggable) {
            // Counts and yes/no only. Never log screen words (AGENTS.md rule 3 spirit).
            Log.d(
                TAG,
                "event=${AccessibilityEvent.eventTypeToString(event.eventType)} windows=${roots.size} " +
                    "nodes=${counts[0]} sensitive=${counts[1]} " + GPayScreenParser.diagnose(texts),
            )
            if (texts.any { '₹' in it }) Log.d(TAG, "masked: " + GPayScreenParser.masked(texts))
        }

        // A payment is starting: the UPI PIN screen shows amount and payee.
        UpiPinScreenParser.parse(texts)?.let { pin ->
            onPinScreen(pin.amountText, pin.payee)
            return
        }


        // GPay's success screen is an animation with no text. The chat it returns to lists
        // each payment as "Payment to X ₹N Paid …", so read those lines instead.
        val payments = GPayChatParser.parse(texts).filter { it.dedupeKey !in savedKeys }
        if (payments.isEmpty()) return
        savedKeys += payments.map { it.dedupeKey }

        val entries = payments.map {
            ScreenPayment(
                dedupeKey = it.dedupeKey,
                appName = "GPay",
                amountText = it.amountText,
                payee = it.payee,
                upiRef = null,
                capturedAt = it.occurredAt,
            )
        }
        scope.launch {
            entries.forEach { dao.insert(it) }
            // A new chat line may be the record of a started payment: match it right away.
            pendingDao.pendingSince(System.currentTimeMillis() - 35 * 60_000)
                .forEach { PaymentCheck.tryMatch(this@GPayScreenReader, it) }
        }
    }

    /** Checks every 0.5 s (for up to 5 min) until the PIN screen is no longer in front. */
    private suspend fun waitForPinScreenToCloseThenAsk(id: Long) {
        var missesInARow = 0
        repeat(600) {
            delay(500)
            missesInARow = if (isPinScreenShowing()) 0 else missesInARow + 1
            if (missesInARow >= 2) { // two checks in a row, so a momentary glitch doesn't count
                delay(POPUP_DELAY_MS)
                showPopup(id)
                return
            }
        }
    }

    /** Runs on IO; shows the pop-up on the main thread. Never over the PIN screen. */
    private suspend fun showPopup(id: Long) {
        repeat(6) { // the user may have gone back to the PIN screen; wait up to ~30 s more
            if (!isPinScreenShowing()) return@repeat
            delay(5_000)
        }
        if (isPinScreenShowing()) return // the notification fallback will ask later

        var pending = pendingDao.get(id) ?: return
        val source = when (pending.status) {
            PendingPayment.STATUS_PENDING -> PaymentCheck.tryMatch(this, pending)
            PendingPayment.STATUS_MATCHED -> "records"
            else -> return // already answered
        }
        pending = pendingDao.get(id) ?: return
        val content = if (source != null) {
            OverlayContent.Recorded(pending.amountText, pending.payee, source)
        } else {
            OverlayContent.Ask(pending.amountText, pending.payee)
        }
        withContext(Dispatchers.Main) {
            overlay.show(
                id,
                content,
                onYes = { answer(id, PendingPayment.STATUS_USER_CONFIRMED) },
                onNo = { answer(id, PendingPayment.STATUS_DISMISSED) },
                onLater = { PaymentCheck.scheduleReminder(this@GPayScreenReader, id) },
            )
        }
        if (isDebuggable) Log.d(TAG, "popup shown recorded=${source != null}")
        if (source != null) return

        // We asked early. If the bank SMS or GPay chat line turns up while the question is on
        // screen (up to 60 s), switch it to "Payment saved" so the user needn't answer.
        // Its own job, so a new PIN screen meanwhile still counts as a new attempt.
        scope.launch {
            repeat(30) {
                delay(2_000)
                val now = pendingDao.get(id) ?: return@launch
                if (now.status != PendingPayment.STATUS_PENDING) return@launch // answered, or matched
                val found = PaymentCheck.tryMatch(this@GPayScreenReader, now) ?: return@repeat
                withContext(Dispatchers.Main) {
                    overlay.showRecordedIfAsking(id, OverlayContent.Recorded(now.amountText, now.payee, found))
                }
                if (isDebuggable) Log.d(TAG, "popup switched to recorded")
                return@launch
            }
        }
    }

    private fun answer(id: Long, status: String) {
        scope.launch {
            pendingDao.setStatus(id, status)
            PaymentCheck.cancelNotification(this@GPayScreenReader, id)
        }
    }

    /** True if any on-screen GPay window is the PIN screen. Same window lookup as events. */
    private suspend fun isPinScreenShowing(): Boolean = withContext(Dispatchers.Main) {
        gpayRoots().any { root ->
            val texts = mutableListOf<String>()
            collectTexts(root, texts, IntArray(2), depth = 0)
            UpiPinScreenParser.parse(texts) != null
        }
    }

    private fun gpayRoots(): List<AccessibilityNodeInfo> =
        windows.mapNotNull { it.root }.filter { it.packageName == GPAY_PACKAGE }
            .ifEmpty { listOfNotNull(rootInActiveWindow?.takeIf { it.packageName == GPAY_PACKAGE }) }

    private fun onPinScreen(amountText: String, payee: String) {
        if (attempt?.isActive == true) return // same PIN session: still waiting for it to close
        attempt = scope.launch {
            val now = System.currentTimeMillis()
            // An unanswered attempt to the same payee and amount in the last 10 min is the same
            // payment (wrong PIN, or back to the PIN screen). Anything answered is a new attempt.
            val again = pendingDao.pendingSince(now - 10 * 60_000)
                .filter { it.amountText == amountText && it.payee.equals(payee, ignoreCase = true) }
                .maxByOrNull { it.detectedAt }
            val id = again?.id ?: pendingDao.insert(
                PendingPayment(
                    dedupeKey = "pin|$amountText|${payee.lowercase()}|$now", // unique per attempt
                    appName = "GPay",
                    amountText = amountText,
                    payee = payee,
                    detectedAt = now,
                ),
            ).takeIf { it > 0 } ?: return@launch
            if (isDebuggable) Log.d(TAG, "pinScreen=true -> ${if (again != null) "same attempt" else "pending saved"}")
            if (again == null) PaymentCheck.schedule(this@GPayScreenReader, id) // fallback notification
            withContext(Dispatchers.Main) { overlay.removeNow() } // back on the PIN screen: no stale pop-up
            waitForPinScreenToCloseThenAsk(id)
        }
    }

    private fun collectTexts(
        node: AccessibilityNodeInfo?,
        out: MutableList<String>,
        counts: IntArray,
        depth: Int,
    ) {
        if (node == null || depth > 40 || out.size > 300) return
        counts[0]++
        if (node.isPassword) return // the UPI PIN field: never read, stored or logged (ONBOARDING.md ob6)
        if (Build.VERSION.SDK_INT >= 34 && node.isAccessibilityDataSensitive) counts[1]++
        node.text?.toString()?.let(out::add)
        node.contentDescription?.toString()?.let { if (it != node.text?.toString()) out.add(it) }
        for (i in 0 until node.childCount) collectTexts(node.getChild(i), out, counts, depth + 1)
    }

    private val isDebuggable by lazy {
        BuildConfig.TEST_BUILD
    }

    override fun onInterrupt() = Unit

    override fun onDestroy() {
        overlay.removeNow()
        scope.cancel()
        super.onDestroy()
    }

    companion object {
        const val GPAY_PACKAGE = "com.google.android.apps.nbu.paisa.user"
        private const val TAG = "GPayScan"
        private const val POPUP_DELAY_MS = 2_000L // after the PIN screen closes; GPay's success animation is ~2 s

        fun isOn(context: Context): Boolean {
            val enabled = Settings.Secure.getString(
                context.contentResolver,
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
            ) ?: return false
            val me = ComponentName(context, GPayScreenReader::class.java)
            return enabled.split(':').any { ComponentName.unflattenFromString(it) == me }
        }

        /** Opens Accessibility settings with our entry highlighted where supported. */
        fun settingsIntent(context: Context): Intent {
            val key = ComponentName(context, GPayScreenReader::class.java).flattenToString()
            return Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
                .putExtra(":settings:fragment_args_key", key)
                .putExtra(":settings:show_fragment_args", Bundle().apply { putString(":settings:fragment_args_key", key) })
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
    }
}
