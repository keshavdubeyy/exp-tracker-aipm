package com.yourteam.expensetracker.data

import android.content.Context
import com.yourteam.expensetracker.categoriser.Categories
import com.yourteam.expensetracker.insights.BankCredit
import com.yourteam.expensetracker.insights.BudgetStatus
import com.yourteam.expensetracker.insights.Budgets
import com.yourteam.expensetracker.insights.CategoryLimit
import com.yourteam.expensetracker.insights.MonthBudget
import com.yourteam.expensetracker.insights.MonthSpend
import com.yourteam.expensetracker.insights.Payment
import com.yourteam.expensetracker.insights.Threshold
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate
import java.time.YearMonth

/** How a threshold was recorded (Category detail, bd7). */
enum class AlertWay { APP, NOTIFIED, SILENT }

/** One threshold that fired this month for a category (or the overall budget). */
data class FiredAlert(val threshold: Threshold, val on: LocalDate, val way: AlertWay)

/**
 * Feature 3 storage, on the phone only: one budget per month and the alerts already sent.
 * Past months are kept for history and never edited. Amounts only; no payment details.
 * With Sample data on, everything here is in memory and seeded so every state can be shown.
 */
class BudgetStore private constructor(context: Context) {

    private val repo = PaymentRepository.get(context)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val prefs = context.getSharedPreferences("budgets", Context.MODE_PRIVATE)
    private val alertPrefs = context.getSharedPreferences("budget_alerts", Context.MODE_PRIVATE)

    private val real = MutableStateFlow(loadBudgets())
    private val realAlerts = MutableStateFlow(loadAlerts())
    private val sample = MutableStateFlow(sampleBudgets(LocalDate.now()))
    private val sampleAlerts = MutableStateFlow(sampleAlertsFor(LocalDate.now()))

    private val sampleOn get() = repo.sampleMode.value
    private val budgetFlow get() = if (sampleOn) sample else real
    private val alertFlow get() = if (sampleOn) sampleAlerts else realAlerts

    @OptIn(ExperimentalCoroutinesApi::class)
    val budgets: StateFlow<Map<YearMonth, MonthBudget>> =
        repo.sampleMode.flatMapLatest { if (it) sample else real }.stateIn(scope, SharingStarted.Eagerly, budgetFlow.value)

    /** Key "2026-11|Shopping" (or "2026-11|__overall") → thresholds fired. */
    @OptIn(ExperimentalCoroutinesApi::class)
    val alerts: StateFlow<Map<String, List<FiredAlert>>> =
        repo.sampleMode.flatMapLatest { if (it) sampleAlerts else realAlerts }.stateIn(scope, SharingStarted.Eagerly, alertFlow.value)

    fun budgetFor(month: YearMonth): MonthBudget? = budgetFlow.value[month]

    /** Real budgets and alerts (never Sample), for the daily tester upload (MVP M24). */
    fun realBudgets(): Map<YearMonth, MonthBudget> = real.value
    fun realAlerts(): Map<String, List<FiredAlert>> = realAlerts.value

    fun firedFor(month: YearMonth, key: String): List<FiredAlert> = alertFlow.value[alertKey(month, key)].orEmpty()

    /** Past months with a budget, newest first (bd12). */
    fun pastMonths(today: LocalDate): List<YearMonth> =
        budgetFlow.value.keys.filter { it.isBefore(YearMonth.from(today)) }.sortedDescending()

    /**
     * New month (bd11): if this month has no budget yet, copy the latest earlier one's amounts,
     * unconfirmed until the user says Keep or Edit. No carry-forward of unused money.
     */
    fun ensureMonth(today: LocalDate) {
        val month = YearMonth.from(today)
        val all = budgetFlow.value
        if (month in all) return
        val last = all.keys.filter { it.isBefore(month) }.maxOrNull()?.let { all[it] } ?: return
        val copy = last.copy(month = month, confirmed = false)
        write(copy)
        markPassedSilently(copy, today)
    }

    /** Saves a whole month (setup / edit). Returns an undo that puts back what was there. */
    fun save(budget: MonthBudget, today: LocalDate = LocalDate.now()): () -> Unit {
        val before = budgetFlow.value[budget.month]
        val alertsBefore = alertFlow.value
        write(budget)
        rearmAll(budget, today)
        return {
            if (before == null) remove(budget.month) else write(before)
            setAlerts(alertsBefore)
        }
    }

    /** "Keep the same" on the new-month sheet. */
    fun confirm(month: YearMonth) {
        budgetFlow.value[month]?.let { write(it.copy(confirmed = true)) }
    }

    /** bd8: a new limit for one category (null removes its budget). Returns an undo. */
    fun setCategoryLimit(month: YearMonth, category: String, limitPaise: Long?, today: LocalDate = LocalDate.now()): () -> Unit {
        val b = budgetFlow.value[month] ?: return {}
        val cats = if (limitPaise == null) b.categories.filterNot { it.category == category }
        else if (b.categories.any { it.category == category }) b.categories.map { if (it.category == category) it.copy(limitPaise = limitPaise) else it }
        else b.categories + CategoryLimit(category, limitPaise)
        return save(b.copy(categories = cats, confirmed = true), today)
    }

    /** Records thresholds as fired (alerts posted or shown in the app). */
    fun recordFired(month: YearMonth, key: String, thresholds: Set<Threshold>, way: AlertWay, on: LocalDate) {
        if (thresholds.isEmpty()) return
        val k = alertKey(month, key)
        val now = alertFlow.value
        val have = now[k].orEmpty()
        val added = thresholds.filter { t -> have.none { it.threshold == t } }.map { FiredAlert(it, on, way) }
        if (added.isEmpty()) return
        setAlerts(now + (k to (have + added).sortedBy { it.threshold }))
    }

    // ---- Status from the payment snapshot ----

    /** The budget month worked out from [snap] (same counting as Insights). */
    fun statusFor(snap: PaymentSnapshot, budget: MonthBudget, today: LocalDate): BudgetStatus =
        Budgets.status(
            budget = budget,
            payments = snap.payments,
            received = snap.received,
            today = today,
            topCategoryOf = { topCategoryOf(snap, it) },
            refundCategoryOf = { refundCategoryOf(snap, it) },
        )

    fun monthSpend(snap: PaymentSnapshot, month: YearMonth): MonthSpend =
        Budgets.monthSpend(snap.payments, month, { topCategoryOf(snap, it) })

    // ---- private ----

    /** After any limit change: thresholds now below re-arm; ones the new limit already passes are recorded quietly. */
    private fun rearmAll(budget: MonthBudget, today: LocalDate) {
        val snap = repo.snapshot.value ?: return
        val status = statusFor(snap, budget, today)
        val now = alertFlow.value.toMutableMap()
        val lines = listOf(Budgets.OVERALL to status.overall) + status.categories.map { it.category!! to it }
        lines.forEach { (key, line) ->
            val k = alertKey(budget.month, key)
            val have = now[k].orEmpty()
            val (kept, passed) = Budgets.rearm(line, have.map { it.threshold }.toSet())
            now[k] = (have.filter { it.threshold in kept } + passed.map { FiredAlert(it, today, AlertWay.SILENT) }).sortedBy { it.threshold }
        }
        // Removed categories lose their alert history for this month.
        val keep = lines.map { alertKey(budget.month, it.first) }.toSet()
        now.keys.filter { it.startsWith("${budget.month}|") && it !in keep }.forEach { now.remove(it) }
        setAlerts(now)
    }

    private fun markPassedSilently(budget: MonthBudget, today: LocalDate) = rearmAll(budget, today)

    private fun write(b: MonthBudget) {
        budgetFlow.update { it + (b.month to b) }
        if (!sampleOn) prefs.edit().putString(b.month.toString(), toJson(b).toString()).apply()
    }

    private fun remove(month: YearMonth) {
        budgetFlow.update { it - month }
        if (!sampleOn) prefs.edit().remove(month.toString()).apply()
    }

    private fun setAlerts(map: Map<String, List<FiredAlert>>) {
        alertFlow.value = map
        if (sampleOn) return
        alertPrefs.edit().clear().apply {
            map.forEach { (k, list) ->
                putString(k, JSONArray(list.map { JSONObject().put("t", it.threshold.name).put("d", it.on.toString()).put("w", it.way.name) }).toString())
            }
        }.apply()
    }

    private fun loadBudgets(): Map<YearMonth, MonthBudget> = prefs.all.mapNotNull { (k, v) ->
        runCatching { fromJson(YearMonth.parse(k), JSONObject(v as String)) }.getOrNull()?.let { it.month to it }
    }.toMap()

    private fun loadAlerts(): Map<String, List<FiredAlert>> = alertPrefs.all.mapNotNull { (k, v) ->
        runCatching {
            val arr = JSONArray(v as String)
            k to (0 until arr.length()).map { i ->
                arr.getJSONObject(i).let { FiredAlert(Threshold.valueOf(it.getString("t")), LocalDate.parse(it.getString("d")), AlertWay.valueOf(it.getString("w"))) }
            }
        }.getOrNull()
    }.toMap()

    private fun toJson(b: MonthBudget) = JSONObject()
        .put("overall", b.overallPaise)
        .put("income", b.incomePaise ?: JSONObject.NULL)
        .put("confirmed", b.confirmed)
        .put("cats", JSONArray(b.categories.map { JSONObject().put("c", it.category).put("l", it.limitPaise) }))

    private fun fromJson(month: YearMonth, o: JSONObject): MonthBudget {
        val cats = o.getJSONArray("cats")
        return MonthBudget(
            month = month,
            overallPaise = o.getLong("overall"),
            categories = (0 until cats.length()).map { i -> cats.getJSONObject(i).let { CategoryLimit(it.getString("c"), it.getLong("l")) } },
            incomePaise = if (o.isNull("income")) null else o.getLong("income"),
            confirmed = o.optBoolean("confirmed", true),
        )
    }

    companion object {
        @Volatile private var instance: BudgetStore? = null

        fun get(context: Context): BudgetStore =
            instance ?: synchronized(this) { instance ?: BudgetStore(context.applicationContext).also { instance = it } }

        fun alertKey(month: YearMonth, key: String) = "$month|$key"

        /** A payment's top-level category: custom ones rolled into their parent; null = not sorted yet. */
        fun topCategoryOf(snap: PaymentSnapshot, p: Payment): String? =
            snap.categoryOf(p)?.let { Categories.parentOf(it, snap.customCategories) }

        /** A refund counts against the category of the latest earlier payment to the same name; else overall only. */
        fun refundCategoryOf(snap: PaymentSnapshot, c: BankCredit): String? {
            val from = c.from?.trim()?.lowercase()?.takeIf { it.isNotEmpty() } ?: return null
            return snap.payments.filter { it.at <= c.at && it.payee?.trim()?.lowercase() == from }
                .maxByOrNull { it.at }?.let { topCategoryOf(snap, it) }
        }

        // ---- Sample data: invented amounts so every Budgets state can be shown ----

        private fun sampleBudgets(today: LocalDate): Map<YearMonth, MonthBudget> {
            val now = YearMonth.from(today)
            fun r(rupees: Long) = rupees * 100
            val current = MonthBudget(
                month = now,
                overallPaise = r(35_000),
                incomePaise = r(40_000),
                categories = listOf(
                    CategoryLimit(Categories.FOOD, r(5_000)),
                    CategoryLimit(Categories.SHOPPING, r(6_500)),
                    CategoryLimit(Categories.TRAVEL, r(4_000)),
                    CategoryLimit(Categories.GROCERIES, r(3_000)),
                    CategoryLimit(Categories.ENTERTAINMENT, r(500)),
                ),
            )
            val last = MonthBudget(
                month = now.minusMonths(1),
                overallPaise = r(30_000),
                categories = listOf(
                    CategoryLimit(Categories.FOOD, r(4_000)),
                    CategoryLimit(Categories.SHOPPING, r(5_000)),
                    CategoryLimit(Categories.TRAVEL, r(1_000)),
                    CategoryLimit(Categories.GROCERIES, r(4_000)),
                    CategoryLimit(Categories.ENTERTAINMENT, r(500)),
                ),
            )
            return mapOf(now to current, last.month to last)
        }

        private fun sampleAlertsFor(today: LocalDate): Map<String, List<FiredAlert>> {
            val now = YearMonth.from(today)
            fun day(d: Int) = today.withDayOfMonth(d.coerceAtMost(today.dayOfMonth))
            return mapOf(
                alertKey(now, Categories.SHOPPING) to listOf(
                    FiredAlert(Threshold.P75, day(4), AlertWay.APP),
                    FiredAlert(Threshold.P90, day(7), AlertWay.NOTIFIED),
                ),
            )
        }
    }
}
