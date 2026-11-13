package com.yourteam.expensetracker.notifications

import android.Manifest
import android.app.AppOpsManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Process
import android.provider.Settings
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.yourteam.expensetracker.MainActivity
import com.yourteam.expensetracker.R
import com.yourteam.expensetracker.data.PaymentRepository
import com.yourteam.expensetracker.data.ScanPayState

/**
 * Knows a UPI payment was *attempted* without accessibility (no screen reading at all).
 *
 * Every UPI app (GPay, PhonePe, Navi, Paytm, …) asks for the PIN on the same NPCI screen,
 * [PIN_ACTIVITY]. With Usage Access, Android tells us only "that screen opened in GPay at 1:05:10
 * and closed at 1:05:24": no screen content, no taps, no amount, no PIN.
 *
 * When it closes we wait [GRACE_MS] for a bank SMS, app alert or Scan & Pay record. If one comes,
 * nothing is asked. If not, one question: a notification, and the same modal in our app.
 */
object PinScreenWatcher {
    const val PIN_ACTIVITY = "org.npci.upi.security.pinactivitycomponent.GetCredential"
    const val ACTION_ASK = "com.yourteam.expensetracker.PIN_ASK"
    private const val ACTION_NO = "com.yourteam.expensetracker.PIN_NO"
    private const val EXTRA_OPENED = "opened"

    /** Ask this soon after the PIN screen closes. If an SMS or alert comes later, the question is replaced. */
    const val GRACE_MS = 2_000L
    /** Ignore a PIN screen shown for less than this (opened and closed by mistake). */
    private const val MIN_OPEN_MS = 1_500L
    /** A payment recorded this long before the PIN screen opened still counts as this one. */
    private const val MATCH_BEFORE_MS = 2 * 60_000L
    /** Unanswered attempts older than this are dropped. */
    private const val KEEP_MS = 6 * 3_600_000L

    private const val PREFS = "pin_watch"
    private const val CHANNEL_ID = "pin_check"

    enum class State { WAITING, ASKED, DONE }

    data class Attempt(val pkg: String, val app: String, val openedAt: Long, val closedAt: Long, val state: State) {
        fun encode() = listOf(pkg, app, openedAt, closedAt, state.name).joinToString("|")
        companion object {
            fun decode(s: String): Attempt? = s.split('|').takeIf { it.size == 5 }?.let { p ->
                Attempt(p[0], p[1], p[2].toLongOrNull() ?: return null, p[3].toLongOrNull() ?: return null,
                    runCatching { State.valueOf(p[4]) }.getOrDefault(State.DONE))
            }
        }
    }

    // ---- Permission ----

    fun hasAccess(context: Context): Boolean {
        val ops = context.getSystemService(AppOpsManager::class.java)
        @Suppress("DEPRECATION")
        val mode = if (Build.VERSION.SDK_INT >= 29) {
            ops.unsafeCheckOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), context.packageName)
        } else {
            ops.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), context.packageName)
        }
        return mode == AppOpsManager.MODE_ALLOWED
    }

    fun settingsIntent(): Intent = Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    // ---- Watching ----

    /**
     * Read new screen events, then ask about any attempt nothing else recorded. Cheap: call often.
     * Returns true while a UPI app is in front (or its PIN screen is open): then call again within a second.
     */
    @Synchronized
    fun tick(context: Context): Boolean {
        if (!hasAccess(context)) return false
        readEvents(context)
        askAboutDue(context)
        val p = prefs(context)
        return p.getString("open_pkg", null) != null || p.getString("fg_pkg", null) in UpiApps.byPackage
    }

    private fun readEvents(context: Context) {
        val prefs = prefs(context)
        val now = System.currentTimeMillis()
        val since = prefs.getLong("last", now - 60_000L).coerceAtLeast(now - 10 * 60_000L)
        val usm = context.getSystemService(UsageStatsManager::class.java) ?: return
        val events = runCatching { usm.queryEvents(since, now) }.getOrNull() ?: return

        var fgPkg = prefs.getString("fg_pkg", null)
        var openPkg = prefs.getString("open_pkg", null)
        var openAt = prefs.getLong("open_at", 0L)
        val closed = mutableListOf<Pair<String, Pair<Long, Long>>>()
        val e = UsageEvents.Event()
        while (events.hasNextEvent()) {
            events.getNextEvent(e)
            if (e.eventType == UsageEvents.Event.ACTIVITY_RESUMED) fgPkg = e.packageName // app in front: poll faster
            if (e.className != PIN_ACTIVITY) continue
            when (e.eventType) {
                UsageEvents.Event.ACTIVITY_RESUMED -> if (openPkg == null) { openPkg = e.packageName; openAt = e.timeStamp }
                UsageEvents.Event.ACTIVITY_PAUSED, UsageEvents.Event.ACTIVITY_STOPPED -> {
                    val pkg = openPkg
                    if (pkg != null && pkg == e.packageName) {
                        if (e.timeStamp - openAt >= MIN_OPEN_MS) closed += pkg to (openAt to e.timeStamp)
                        openPkg = null
                    }
                }
            }
        }
        // A PIN screen left open for over 10 minutes was abandoned.
        if (openPkg != null && now - openAt > 10 * 60_000L) openPkg = null

        val attempts = attempts(context).toMutableList()
        for ((pkg, times) in closed) {
            // PIN re-entered (wrong PIN, retry) within 60 s in the same app = the same payment.
            val i = attempts.indexOfLast { it.pkg == pkg && times.first - it.closedAt < 60_000L }
            if (i >= 0) attempts[i] = attempts[i].copy(closedAt = times.second)
            else attempts += Attempt(pkg, appName(context, pkg), times.first, times.second,
                // Scan & Pay asks in its own modal: don't ask twice.
                if (ScanPayState.handingOff(context)) State.DONE else State.WAITING)
        }
        prefs.edit()
            .putLong("last", now).putString("fg_pkg", fgPkg)
            .putString("open_pkg", openPkg).putLong("open_at", openAt)
            .apply()
        save(context, attempts)
    }

    /** True if a bank SMS, app alert, Scan & Pay or pop-up already recorded a payment around [a]. */
    private fun recorded(context: Context, a: Attempt): Boolean {
        val payments = PaymentRepository.get(context).snapshot.value?.payments ?: return false
        val from = a.openedAt - MATCH_BEFORE_MS
        val to = a.closedAt + 30 * 60_000L
        return payments.any { it.at in from..to }
    }

    private fun askAboutDue(context: Context) {
        val now = System.currentTimeMillis()
        val list = attempts(context).filter { now - it.closedAt < KEEP_MS }.map { a ->
            when {
                a.state == State.DONE -> a
                recorded(context, a) -> { cancel(context, a); a.copy(state = State.DONE) }
                a.state == State.WAITING && now - a.closedAt >= GRACE_MS -> { notify(context, a); a.copy(state = State.ASKED) }
                else -> a
            }
        }
        save(context, list)
    }

    /** The attempt to ask about in our app right now, if any (newest first). */
    fun unanswered(context: Context): Attempt? {
        tick(context)
        return attempts(context).lastOrNull { it.state != State.DONE && !recorded(context, it) }
    }

    /** The user answered (in the modal or the notification). */
    @Synchronized
    fun resolve(context: Context, openedAt: Long) {
        val list = attempts(context).map { if (it.openedAt == openedAt) it.copy(state = State.DONE) else it }
        list.firstOrNull { it.openedAt == openedAt }?.let { cancel(context, it) }
        save(context, list)
    }

    // ---- Notification ----

    private fun notify(context: Context, a: Attempt) {
        if (!PaymentCheck.canNotify(context)) return // the modal still asks when the app is opened
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "Payments to confirm", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "Asks once when a UPI app showed the PIN screen but no bank SMS or app alert followed."
            },
        )
        val open = PendingIntent.getActivity(
            context, code(a, 0),
            Intent(context, MainActivity::class.java).setAction(ACTION_ASK).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val no = PendingIntent.getBroadcast(
            context, code(a, 1),
            Intent(context, PinCheckReceiver::class.java).setAction(ACTION_NO).putExtra(EXTRA_OPENED, a.openedAt),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val n = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_payment)
            .setContentTitle("Did your ${a.app} payment go through?")
            .setContentText("Tap to add the amount. If your bank texts you, we'll fill it in.")
            .setContentIntent(open)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .addAction(0, "Yes, add amount", open)
            .addAction(0, "No", no)
            .build()
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED ||
            Build.VERSION.SDK_INT < 33
        ) {
            @Suppress("MissingPermission")
            NotificationManagerCompat.from(context).notify(id(a), n)
        }
    }

    private fun cancel(context: Context, a: Attempt) = NotificationManagerCompat.from(context).cancel(id(a))

    // ---- Helpers ----

    fun time(at: Long): String = java.time.Instant.ofEpochMilli(at).atZone(java.time.ZoneId.systemDefault())
        .format(java.time.format.DateTimeFormatter.ofPattern("h:mm a"))

    private fun appName(context: Context, pkg: String): String = UpiApps.byPackage[pkg] ?: runCatching {
        context.packageManager.getApplicationLabel(context.packageManager.getApplicationInfo(pkg, 0)).toString()
    }.getOrDefault("your UPI app")

    /** For the Developer screen: recent attempts, newest first. No amounts or names exist to show. */
    fun recent(context: Context): List<Attempt> = attempts(context).reversed()

    private fun attempts(context: Context): List<Attempt> =
        prefs(context).getString("attempts", "").orEmpty().split('\n').mapNotNull { Attempt.decode(it) }

    private fun save(context: Context, list: List<Attempt>) =
        prefs(context).edit().putString("attempts", list.takeLast(20).joinToString("\n") { it.encode() }).apply()

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    private fun id(a: Attempt) = 30_000 + (a.openedAt / 1000 % 10_000).toInt()
    private fun code(a: Attempt, slot: Int) = id(a) * 2 + slot

    internal fun onNo(context: Context, intent: Intent) = resolve(context, intent.getLongExtra(EXTRA_OPENED, -1))
}

/** "No, I didn't" on the notification. */
class PinCheckReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) = PinScreenWatcher.onNo(context, intent)
}
