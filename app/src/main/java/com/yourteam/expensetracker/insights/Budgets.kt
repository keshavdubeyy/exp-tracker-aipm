// ALL money math lives here, plain Kotlin only (AGENTS.md rule 1).
// Feature 3 (Budgets). No AI anywhere in this feature: plain sums over the same payments
// Insights counts (completed only, pending never, each payment once). Monthly cycles only.
package com.yourteam.expensetracker.insights

import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId

/** One category's monthly limit. [category] is always a top-level (predefined) category. */
data class CategoryLimit(val category: String, val limitPaise: Long)

/**
 * The budget for one month. [incomePaise] is optional and never changes any budget number
 * (it only shows what's left for savings). [confirmed] is false when the month was copied from
 * the last one and the user hasn't answered "Keep the same / Edit amounts" yet (bd11).
 */
data class MonthBudget(
    val month: YearMonth,
    val overallPaise: Long,
    val categories: List<CategoryLimit> = emptyList(),
    val incomePaise: Long? = null,
    val confirmed: Boolean = true,
) {
    val allocatedPaise: Long get() = categories.sumOf { it.limitPaise }
    fun limitFor(category: String): Long? = categories.firstOrNull { it.category == category }?.limitPaise
}

/** Colour band (design: under 90% blue · 90–99% amber · 100%+ red, always with words). */
enum class BudgetLevel { UNDER, NEAR, REACHED, OVER }

/** One bar: the overall budget ([category] null) or one category. */
data class BudgetLine(
    val category: String?,
    val limitPaise: Long,
    val spentPaise: Long,
    /** Payments counted in this line, newest first. */
    val paymentKeys: List<String> = emptyList(),
) {
    /** Negative when over. */
    val leftPaise: Long get() = limitPaise - spentPaise
    val overPaise: Long get() = maxOf(0L, spentPaise - limitPaise)
    val level: BudgetLevel get() = Budgets.level(spentPaise, limitPaise)
    val percent: Int get() = Budgets.percent(spentPaise, limitPaise)
    /** 0..1 for the progress bar. */
    val fraction: Float get() = if (limitPaise <= 0) (if (spentPaise > 0) 1f else 0f) else (spentPaise.toFloat() / limitPaise).coerceIn(0f, 1f)
}

/** Everything the Budgets screen draws for one month. All amounts in paise. */
data class BudgetStatus(
    val budget: MonthBudget,
    val overall: BudgetLine,
    /** In the order the user set them. */
    val categories: List<BudgetLine>,
    /** Payments with no category yet: in the overall, in no category (no category alert until sorted). */
    val unsortedPaise: Long,
    val unsortedCount: Int,
    /** Spend in categories without a budget: counted in the overall only. */
    val noBudgetPaise: Long,
    val noBudgetCategories: List<String>,
    /** Days left in the month including today; 0 once the month is over. */
    val daysLeft: Int,
    /** Left ÷ days left, rounded down to a whole rupee. Null when over budget or the month is over. */
    val dailyPaise: Long?,
) {
    val month: YearMonth get() = budget.month
    val lastDay: Boolean get() = daysLeft == 1
    fun line(category: String): BudgetLine? = categories.firstOrNull { it.category == category }
}

/** Last month in short, for "Start from October" and the hints on step 3. */
data class MonthSpend(val totalPaise: Long, val byCategory: List<Pair<String, Long>>) {
    fun of(category: String): Long? = byCategory.firstOrNull { it.first == category }?.second
}

/** Category limits added up against the overall budget (bd4 / bd5). */
data class SplitCheck(val allocatedPaise: Long, val overallPaise: Long) {
    val unallocatedPaise: Long get() = maxOf(0L, overallPaise - allocatedPaise)
    val overByPaise: Long get() = maxOf(0L, allocatedPaise - overallPaise)
}

/** Alert thresholds. 75% is shown in the app only; the rest send a notification. */
enum class Threshold(val label: String) { P75("75%"), P90("90%"), P100("100%"), OVER("Over") }

/** One alert that should go out now: the highest threshold newly crossed for [key]. */
data class AlertDue(val key: String, val threshold: Threshold, val line: BudgetLine, val crossed: Set<Threshold>)

object Budgets {

    /** Key for the overall budget in alert records (categories use their own name). */
    const val OVERALL = "__overall"

    fun level(spent: Long, limit: Long): BudgetLevel = when {
        limit <= 0 -> if (spent > 0) BudgetLevel.OVER else BudgetLevel.UNDER
        spent > limit -> BudgetLevel.OVER
        spent == limit -> BudgetLevel.REACHED
        spent * 100 >= limit * 90 -> BudgetLevel.NEAR
        else -> BudgetLevel.UNDER
    }

    /**
     * Whole percent used. Rounded down while under the limit (so 89.6% never reads "90%" in blue),
     * exactly 100 at the limit, and at least 101 once over.
     */
    fun percent(spent: Long, limit: Long): Int {
        if (limit <= 0) return if (spent > 0) 100 else 0
        return when {
            spent < limit -> (spent * 100 / limit).toInt().coerceIn(0, 99)
            spent == limit -> 100
            else -> Math.round(spent * 100.0 / limit).toInt().coerceAtLeast(101)
        }
    }

    /** Days left in [month] including [today]; 0 if the month is over, full length if it hasn't started. */
    fun daysLeft(month: YearMonth, today: LocalDate): Int = when {
        YearMonth.from(today).isAfter(month) -> 0
        YearMonth.from(today).isBefore(month) -> month.lengthOfMonth()
        else -> month.lengthOfMonth() - today.dayOfMonth + 1
    }

    /** "About ₹555 a day": left ÷ days left, rounded down to a whole rupee. Null when nothing is left. */
    fun dailyAmount(leftPaise: Long, daysLeft: Int): Long? =
        if (leftPaise <= 0 || daysLeft <= 0) null else (leftPaise / daysLeft) / 100 * 100

    /**
     * Works out [budget]'s month. [topCategoryOf] gives a payment's top-level category (custom ones
     * already rolled into their parent; null = not sorted yet). [refundCategoryOf] gives the
     * top-level category a refund belongs to (null = overall only). Refunds count in the month
     * they arrive.
     */
    fun status(
        budget: MonthBudget,
        payments: List<Payment>,
        received: List<BankCredit>,
        today: LocalDate,
        topCategoryOf: (Payment) -> String?,
        refundCategoryOf: (BankCredit) -> String?,
        zone: ZoneId = ZoneId.systemDefault(),
    ): BudgetStatus {
        val month = budget.month
        fun inMonth(at: Long) = YearMonth.from(Instant.ofEpochMilli(at).atZone(zone).toLocalDate()) == month

        val counted = payments.filter { Spending.counts(it) && inMonth(it.at) }.sortedByDescending { it.at }
        val refunds = received.filter { it.refund && inMonth(it.at) }

        val byCat = counted.groupBy(topCategoryOf)
        val refundByCat = refunds.groupBy(refundCategoryOf).mapValues { (_, l) -> l.sumOf { it.paise } }
        fun spentIn(cat: String) = maxOf(0L, byCat[cat].orEmpty().sumOf { it.paise } - (refundByCat[cat] ?: 0L))

        val totalSpent = maxOf(0L, counted.sumOf { it.paise } - refunds.sumOf { it.paise })
        val budgeted = budget.categories.map { it.category }.toSet()

        val lines = budget.categories.map { lim ->
            BudgetLine(lim.category, lim.limitPaise, spentIn(lim.category), byCat[lim.category].orEmpty().map { it.key })
        }
        val unsorted = byCat[null].orEmpty()
        val otherCats = byCat.keys.filterNotNull().filter { it !in budgeted }
        val noBudget = otherCats.map { it to spentIn(it) }.filter { it.second > 0 }.sortedByDescending { it.second }

        val overall = BudgetLine(null, budget.overallPaise, totalSpent, counted.map { it.key })
        val days = daysLeft(month, today)
        return BudgetStatus(
            budget = budget,
            overall = overall,
            categories = lines,
            unsortedPaise = unsorted.sumOf { it.paise },
            unsortedCount = unsorted.size,
            noBudgetPaise = noBudget.sumOf { it.second },
            noBudgetCategories = noBudget.map { it.first },
            daysLeft = days,
            dailyPaise = dailyAmount(overall.leftPaise, days),
        )
    }

    /** What was spent in [month] overall and per top-level category, biggest first. */
    fun monthSpend(
        payments: List<Payment>,
        month: YearMonth,
        topCategoryOf: (Payment) -> String?,
        zone: ZoneId = ZoneId.systemDefault(),
    ): MonthSpend {
        val inMonth = payments.filter {
            Spending.counts(it) && YearMonth.from(Instant.ofEpochMilli(it.at).atZone(zone).toLocalDate()) == month
        }
        val byCat = inMonth.groupBy(topCategoryOf).filterKeys { it != null }
            .map { (c, l) -> c!! to l.sumOf { it.paise } }
            .sortedByDescending { it.second }
        return MonthSpend(inMonth.sumOf { it.paise }, byCat)
    }

    /** Rounds up to a whole rupee, so "Start from October" fills clean numbers (₹5,770, not ₹5,769.50). */
    fun wholeRupeesUp(paise: Long): Long = (paise + 99) / 100 * 100

    // ---- Setup (bd2–bd5) ----

    /** "Left for savings and other plans": income minus the overall budget (negative = budget is above income). */
    fun savingsLeft(incomePaise: Long, overallPaise: Long): Long = incomePaise - overallPaise

    /** "₹17,000 of ₹25,000 split · ₹8,000 unallocated" or "₹2,000 over" (warn, never block). */
    fun split(overallPaise: Long, limits: List<Long>): SplitCheck = SplitCheck(limits.sum(), overallPaise)

    // ---- Alerts ----

    /** Which thresholds [line] has passed. [overall] lines don't have a 75% step. */
    fun crossed(line: BudgetLine, overall: Boolean = line.category == null): Set<Threshold> {
        val s = line.spentPaise
        val l = line.limitPaise
        if (l <= 0) return emptySet()
        return buildSet {
            if (!overall && s * 100 >= l * 75) add(Threshold.P75)
            if (s * 100 >= l * 90) add(Threshold.P90)
            if (s >= l) add(Threshold.P100)
            if (s > l) add(Threshold.OVER)
        }
    }

    /**
     * Alerts to send now: for each line, the highest threshold crossed that hasn't fired yet this
     * month (one payment crossing several sends only the highest). [fired] = thresholds already
     * recorded per key. Thresholds stay fired even if a correction drops spend below them.
     */
    fun due(status: BudgetStatus, fired: Map<String, Set<Threshold>>): List<AlertDue> {
        val all = listOf(OVERALL to status.overall) + status.categories.map { it.category!! to it }
        return all.mapNotNull { (key, line) ->
            val crossed = crossed(line, overall = key == OVERALL)
            val fresh = crossed - fired[key].orEmpty()
            fresh.maxOrNull()?.let { AlertDue(key, it, line, crossed) }
        }
    }

    /**
     * After a limit changes: thresholds the line is now below can fire again (re-armed); those it
     * is still past stay fired. Returns (kept, newlyPassed): newlyPassed were passed by the new
     * limit itself and are recorded without a notification.
     */
    fun rearm(line: BudgetLine, fired: Set<Threshold>): Pair<Set<Threshold>, Set<Threshold>> {
        val crossed = crossed(line)
        return (fired intersect crossed) to (crossed - fired)
    }
}
