package com.yourteam.expensetracker.metrics

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.yourteam.expensetracker.BuildConfig
import com.yourteam.expensetracker.data.AlertWay
import com.yourteam.expensetracker.data.BudgetStore
import com.yourteam.expensetracker.data.PaymentInputs
import com.yourteam.expensetracker.data.PaymentRepository
import com.yourteam.expensetracker.insights.BudgetLevel
import com.yourteam.expensetracker.insights.Spending
import com.yourteam.expensetracker.ui.screens.onboarding.Onboarding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.first
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.time.LocalDate
import java.time.YearMonth
import java.util.concurrent.TimeUnit

/**
 * MVP M24: once a day, a tester phone sends counts to our server's POST /metrics, which the team
 * reads on /dashboard. Test builds only, only after the tester agreed, never Sample data.
 * The field names match `server/metrics.py` (the server rejects anything else).
 */
object MetricsUpload {
    private const val WORK = "daily-metrics-upload"
    private const val WORK_NOW = "metrics-upload-now"

    /** Upload is possible in this build: a test build with a server address. */
    val available: Boolean get() = BuildConfig.TEST_BUILD && BuildConfig.METRICS_BASE_URL.isNotBlank()

    /** Call on app start and after consent changes: keeps the daily job on or off to match. */
    fun schedule(context: Context) {
        val wm = WorkManager.getInstance(context)
        if (!available || UsageLog.consent(context) != true) { wm.cancelUniqueWork(WORK); return }
        val request = PeriodicWorkRequestBuilder<MetricsWorker>(24, TimeUnit.HOURS)
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .build()
        wm.enqueueUniquePeriodicWork(WORK, ExistingPeriodicWorkPolicy.KEEP, request)
    }

    /** Uploads on opening the app too (not only the daily job), so the dashboard is at most an hour behind each tester. */
    private const val OPEN_GAP_MS = 60 * 60_000L

    /** Call whenever the app comes to the front. */
    fun onOpen(context: Context) {
        if (System.currentTimeMillis() - UsageLog.lastUpload(context) >= OPEN_GAP_MS) sendNow(context)
    }

    /** Developer screen "Send now" (and right after the tester agrees). Waits for a connection. */
    fun sendNow(context: Context) {
        if (!available || UsageLog.consent(context) != true) return
        WorkManager.getInstance(context).enqueueUniqueWork(
            WORK_NOW, ExistingWorkPolicy.REPLACE,
            OneTimeWorkRequestBuilder<MetricsWorker>()
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .build(),
        )
    }

    /** "₹120", "Rs. 120", "INR 120". */
    private val AMOUNT = Regex("""(₹|\brs\.?|\binr)\s*[0-9]""", RegexOption.IGNORE_CASE)
    private val PAY_WORD = Regex("""\b(debited|credited|sent|paid|received|withdrawn|spent|transferred|deducted)\b""", RegexOption.IGNORE_CASE)

    /** A payment word and an amount: offers ("get ₹50 cashback"), statements and OTPs mostly don't have both. */
    private val BARE_AMOUNT = Regex("""\b(debited|credited|sent|paid|transferred|deducted)\s+(by|with|of|for)\s+[0-9]""", RegexOption.IGNORE_CASE) // SBI: "debited by 20.0"
    private fun looksLikePayment(text: String) =
        (PAY_WORD.containsMatchIn(text) && AMOUNT.containsMatchIn(text)) || BARE_AMOUNT.containsMatchIn(text)

    /** Today's counts. Fields we can't know yet (no SMS read, Sample data on) are left out, not sent as 0. */
    fun buildCounts(context: Context): Map<String, Long> {
        val c = mutableMapOf<String, Long>()
        fun put(k: String, v: Number?) { if (v != null) c[k] = v.toLong() }
        fun flag(b: Boolean) = if (b) 1L else 0L

        // Onboarding (M1)
        put("onboarding_started", flag(Onboarding.isStarted(context) || Onboarding.isComplete(context)))
        put("onboarding_done", flag(Onboarding.isComplete(context)))
        put("sms_allowed", flag(ContextCompat.checkSelfPermission(context, Manifest.permission.READ_SMS) == PackageManager.PERMISSION_GRANTED))
        put("onboarding_seconds", UsageLog.onboardingSeconds(context))
        // Live capture switched on: new bank SMS wake the app, and payment-app alerts reach it.
        put("sms_live", flag(ContextCompat.checkSelfPermission(context, Manifest.permission.RECEIVE_SMS) == PackageManager.PERMISSION_GRANTED))
        put("alerts_on", flag(com.yourteam.expensetracker.notifications.UpiApps.isNotificationAccessOn(context)))

        // Screens (M14–M16)
        put("active_days_7d", UsageLog.openDaysIn(context, 7))
        put("insights_days_7d", UsageLog.insightsDaysIn(context, 7))

        // Insights (Feature 2): cards first shown in the last 30 days, and what happened to them.
        put("ins_days_30d", UsageLog.insightsDaysIn(context, 30))
        put("ins_card_days_30d", InsightLog.cardDaysIn(context, 30))
        InsightLog.byKind(context).values.let { k ->
            put("ins_shown_30d", k.sumOf { it.shown })
            put("ins_opened_30d", k.sumOf { it.opened })
            put("ins_rated_30d", k.sumOf { it.rated })
            put("ins_helpful_30d", k.sumOf { it.helpful })
            put("ins_negative_30d", k.sumOf { it.notRelevant + it.dismissed })
            // Feature 2: the same for cards shown in AI words, to compare with the app's own wording.
            put("ins_ai_shown_30d", k.sumOf { it.aiShown })
            put("ins_ai_rated_30d", k.sumOf { it.aiRated })
            put("ins_ai_helpful_30d", k.sumOf { it.aiHelpful })
            put("ins_ai_negative_30d", k.sumOf { it.aiNegative })
        }

        // Categorise (M8–M11)
        val repo = PaymentRepository.get(context)
        repo.categoryCounts().forEach { (k, v) -> put(k, v) }

        // Payments, capture, did-it-go-through: only from real data that's loaded.
        // Test SMS (Developer › Test an SMS) are never counted.
        val snap = repo.realSnapshotNow()?.let { s ->
            s.copy(
                bankSms = s.bankSms.filterNot { com.yourteam.expensetracker.data.TestSmsStore.isTest(it.id) },
                payments = s.payments.filterNot { com.yourteam.expensetracker.data.TestSmsStore.isTest(it.sms?.smsId) },
            )
        }
        val now = System.currentTimeMillis()
        if (snap != null) {
            put("sms_bank_kept", snap.bankSms.size)
            put("sms_read", snap.bankSms.count { PaymentInputs.debit(it) != null || PaymentInputs.credit(it) != null })
            val started = snap.payments.filter { it.started != null && !it.started.manual }
            put("started_total", started.size)
            put("started_answered", started.count { !it.needsAnswer })
            put("started_pending_24h", started.count { it.needsAnswer && it.at < now - 24 * 3_600_000L })
            // Feature 1 "Tracked so far": payments counted, and their total only if the tester agreed to share it.
            put("pay_all", snap.payments.count(Spending::counts))
            if (UsageLog.totalAllowed(context)) put("paid_rupees_all", Spending.trackedRupees(snap.payments))
            // "Sorted so far": of those, how many have a category now (a count, never money).
            put("pay_sorted_all", snap.payments.count { Spending.counts(it) && snap.categoryOf(it) != null })
            // Feature 1 health checks: SMS and alerts that look like a payment (a payment word and an amount), and
            // how many of those we read. Should stay near 100%; a drop means a bank or app changed its wording.
            val payLikeSms = snap.bankSms.filter { looksLikePayment(it.body) }
            put("sms_paylike", payLikeSms.size)
            put("sms_paylike_read", payLikeSms.count { PaymentInputs.debit(it) != null || PaymentInputs.credit(it) != null })
            val payLikeAlerts = snap.notifications.filter { looksLikePayment(it.title + " " + it.text) }
            put("alert_paylike", payLikeAlerts.size)
            put("alert_paylike_read", payLikeAlerts.count { PaymentInputs.paid(it) != null || PaymentInputs.credit(it) != null })

            // Feature 1 capture: payments in the last 30 days, how each got in, and how well it was read.
            val month = snap.payments.filter { it.at >= now - 30 * 86_400_000L && !it.needsAnswer }
            fun via(p: com.yourteam.expensetracker.insights.Payment) = p.started?.let { CaptureLog.via(context, it.id) }
            val found = month.filter { it.sms != null || it.chat != null || via(it) == CaptureLog.Via.SCAN }
            val added = month - found.toSet()
            put("pay_30d", month.size)
            put("pay_auto_30d", found.size)
            put("pay_added_notif_30d", added.count { it.started?.manual != true && via(it).let { v -> v == CaptureLog.Via.NOTIFICATION || v == CaptureLog.Via.POPUP } })
            put("pay_added_app_30d", added.count { it.started?.manual == true || via(it) == CaptureLog.Via.APP })
            put("pay_typed_30d", added.count { it.started?.manual == true })
            put("pay_named_30d", month.count(com.yourteam.expensetracker.insights.CaptureChecks::payeeNamed))
            put("pay_double_30d", com.yourteam.expensetracker.insights.CaptureChecks.possibleDoubles(month))
            val recent = snap.payments.filter { Spending.counts(it) && it.at >= now - 30 * 86_400_000L }
            put("spend_count_30d", recent.size)
            // Dashboard "Last 30 days": the same total as paid_rupees_all, for these 30 days only. Same consent.
            if (UsageLog.totalAllowed(context)) put("paid_rupees_30d", Spending.trackedRupees(recent))
            put("spend_uncategorised_30d", recent.count { snap.categoryOf(it) == null })
            // Feature 1: the history found at install (the inbox before that day), and how much of it has a category now.
            val installedAt = runCatching { context.packageManager.getPackageInfo(context.packageName, 0).firstInstallTime }.getOrNull()
            if (installedAt != null) {
                com.yourteam.expensetracker.categoriser.CategoryMetrics.pastCounts(
                    snap.payments.filter(Spending::counts).map { it.at to snap.guessFor(it) }, installedAt,
                ).forEach { (k, v) -> put(k, v) }
            }
        }

        // Feature 1: bank SMS that showed up in the app within a minute of arriving.
        val delays = CaptureLog.smsDelays(context)
        put("capture_timed", delays.size)
        put("capture_1m", delays.count { it <= CaptureLog.ONE_MINUTE_MS })

        // Budgets (M25)
        val store = BudgetStore.get(context)
        val budgets = store.realBudgets()
        val month = YearMonth.now()
        val thisMonth = budgets[month]
        put("budget_set", flag(thisMonth != null))
        put("budget_months", budgets.size)
        put("budget_categories", thisMonth?.categories?.size ?: 0)
        if (thisMonth != null && snap != null) {
            put("budget_over", store.statusFor(snap, thisMonth, LocalDate.now()).categories.count { it.level == BudgetLevel.OVER })
        }
        put("budget_alerts_sent", store.realAlerts().filterKeys { it.startsWith("$month|") }.values.sumOf { l -> l.count { it.way == AlertWay.NOTIFIED } })
        return c
    }

    /** Insights cards per kind (counts only), or null when none were shown in the last 30 days. */
    fun insightKindsJson(context: Context): JSONObject? = with(InsightLog) {
        val by = byKind(context).takeIf { it.isNotEmpty() } ?: return null
        JSONObject().apply {
            by.forEach { (type, k) ->
                put(type.key, JSONObject().put("shown", k.shown).put("opened", k.opened).put("helpful", k.helpful)
                    .put("not_relevant", k.notRelevant).put("dismissed", k.dismissed))
            }
        }
    }

    /** Real AI cases per golden-dataset scenario: counts only, never payee names. Null until payments are read. */
    fun goldenJson(context: Context): JSONObject? {
        val repo = PaymentRepository.get(context)
        val snap = repo.realSnapshotNow()?.let { s ->
            s.copy(payments = s.payments.filterNot { com.yourteam.expensetracker.data.TestSmsStore.isTest(it.sms?.smsId) })
        } ?: return null
        val result = GoldenCoverage.compute(snap, repo::categoryRecord)
        return JSONObject().apply {
            result.byScenario.forEach { (scenario, c) ->
                put(scenario.key, JSONObject().put("asked", c.asked).put("picked", c.picked).put("top3", c.top3).put("first", c.first))
            }
        }
    }

    /** Sends today's report. Returns true when the server accepted it. */
    suspend fun upload(context: Context): Boolean = withContext(Dispatchers.IO) {
        if (!available || UsageLog.consent(context) != true) return@withContext false
        val repo = PaymentRepository.get(context)
        repo.ensureLoaded() // a background start has no SMS read yet; wait briefly so payment counts are included
        kotlinx.coroutines.withTimeoutOrNull(20_000) { repo.snapshot.first { it != null } }
        val body = JSONObject()
            .put("install_id", UsageLog.installId(context))
            .apply { UsageLog.oldId(context)?.let { put("replaces", it) } } // merges this phone's earlier random ID
            .put("app_version", "${BuildConfig.VERSION_NAME}-${BuildConfig.VERSION_CODE}") // build number: who hasn't updated
            .put("day", LocalDate.now().toString())
            .put("counts", JSONObject(buildCounts(context) as Map<*, *>).apply {
                goldenJson(context)?.let { put("golden", it) }
                insightKindsJson(context)?.let { put("ins_kinds", it) }
            })
        try {
            val conn = (URL(BuildConfig.METRICS_BASE_URL.trimEnd('/') + "/metrics").openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                connectTimeout = 15_000
                readTimeout = 15_000
                doOutput = true
                setRequestProperty("Content-Type", "application/json")
                if (BuildConfig.AI_TESTER_KEY.isNotBlank()) setRequestProperty("X-Tester-Key", BuildConfig.AI_TESTER_KEY)
            }
            conn.outputStream.use { it.write(body.toString().toByteArray()) }
            val code = conn.responseCode
            conn.disconnect()
            val ok = code in 200..299
            if (ok && body.has("replaces")) UsageLog.forgetOldId(context)
            UsageLog.recordUpload(context, ok, if (ok) "Sent" else "Server said $code")
            ok
        } catch (e: Exception) {
            UsageLog.recordUpload(context, false, "Couldn't reach the server") // no details: they could hold the URL only
            false
        }
    }
}

/** WorkManager job for [MetricsUpload]; retries later when offline or the server fails. */
class MetricsWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result =
        if (MetricsUpload.upload(applicationContext)) Result.success() else if (runAttemptCount < 5) Result.retry() else Result.failure()
}
