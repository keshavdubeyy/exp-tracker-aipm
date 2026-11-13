// ALL money math lives here, plain Kotlin only (AGENTS.md rule 1).
package com.yourteam.expensetracker.insights

import java.time.DayOfWeek
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalAdjusters
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId

/** Totals for the Insights screen. All amounts in paise. */
data class SpendingSummary(
    val month: YearMonth,
    val totalPaise: Long,
    val paymentCount: Int,
    /** Total ÷ days elapsed this month (or days in a past month), rounded down. */
    val averagePerDayPaise: Long,
    /** Last 7 days ending today, oldest first. */
    val lastSevenDays: List<DayTotal>,
    /** Biggest payees this month, at most 5. */
    val topPayees: List<PayeeTotal>,
    /** Started payments still waiting for "Yes / No": shown separately, not in the total. */
    val unconfirmedPaise: Long,
    val unconfirmedCount: Int,
)

/** Quick picks on Home's date sheet. CUSTOM = two days the user tapped. */
enum class RangePreset { TODAY, THIS_WEEK, LAST_7_DAYS, THIS_MONTH, LAST_MONTH, CUSTOM }

/**
 * The days Home's totals cover, both ends included. Weeks start on Monday.
 * Never reaches past today: This week / This month run from their first day to today.
 */
data class DateRange(val start: LocalDate, val end: LocalDate, val preset: RangePreset = RangePreset.CUSTOM) {
    init {
        require(!end.isBefore(start)) { "end before start" }
    }

    /** Days in the range, both ends included: 3–9 Nov = 7. */
    val days: Long get() = ChronoUnit.DAYS.between(start, end) + 1

    companion object {
        fun of(preset: RangePreset, today: LocalDate): DateRange = when (preset) {
            RangePreset.TODAY, RangePreset.CUSTOM -> DateRange(today, today, preset)
            RangePreset.THIS_WEEK -> DateRange(today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)), today, preset)
            RangePreset.LAST_7_DAYS -> DateRange(today.minusDays(6), today, preset)
            RangePreset.THIS_MONTH -> DateRange(today.withDayOfMonth(1), today, preset)
            RangePreset.LAST_MONTH -> today.withDayOfMonth(1).minusMonths(1)
                .let { DateRange(it, it.plusMonths(1).minusDays(1), preset) }
        }

        /** Two tapped days, in either order. */
        fun custom(a: LocalDate, b: LocalDate): DateRange =
            if (b.isBefore(a)) DateRange(b, a) else DateRange(a, b)
    }
}

/** Spent in [range] (up to today), against what [range] is compared with (see [Spending.windows]). */
data class PeriodSpend(
    val range: DateRange,
    val totalPaise: Long,
    val previousPaise: Long,
) {
    val changePaise: Long get() = totalPaise - previousPaise
    /** Size of the change, always ≥ 0. Read [spentLess] for the direction. */
    val differencePaise: Long get() = kotlin.math.abs(changePaise)
    val spentLess: Boolean get() = changePaise < 0
    /** Change in tenths of a percent (−96 = −9.6%). Null when nothing was spent last time. */
    val changeTenthsPercent: Long?
        get() = if (previousPaise <= 0L) null else Math.round(changePaise * 1000.0 / previousPaise)
}

/** "To check" on Home: payments waiting for an answer plus ones the bank disagrees with. */
data class ToCheck(val count: Int, val paise: Long)

/** One category card on Home: this period so far vs the same stretch last period. */
data class CategorySpend(val category: String?, val totalPaise: Long, val previousPaise: Long) {
    val changePaise: Long get() = totalPaise - previousPaise
}

data class DayTotal(val date: LocalDate, val totalPaise: Long)
data class PayeeTotal(val payee: String, val totalPaise: Long, val count: Int)

object Spending {

    /** Payee name used when only a bank SMS (no name yet) recorded the payment. */
    const val UNNAMED = "Other bank payments"

    /**
     * Counted as spent: anything recorded by the user ("Yes"), a GPay chat line or a bank SMS.
     * Not counted: payments still waiting for an answer.
     */
    fun counts(p: Payment): Boolean = !p.needsAnswer

    /** All-time total spent across [payments], in whole rupees (rounded down), for the team dashboard's "Tracked so far". */
    fun trackedRupees(payments: List<Payment>): Long = payments.filter(::counts).sumOf { it.paise } / 100

    /** Bar heights for a chart: each day as a share (0..1) of the biggest day. */
    fun barFractions(days: List<DayTotal>): List<Float> {
        val max = days.maxOfOrNull { it.totalPaise } ?: 0L
        return days.map { if (max <= 0L) 0f else it.totalPaise.toFloat() / max }
    }

    fun toCheck(payments: List<Payment>): ToCheck {
        val open = payments.filter { it.needsAnswer || it.conflict }
        return ToCheck(open.size, open.sumOf { it.paise })
    }

    /** First and last day of a window, both included. */
    data class Window(val from: LocalDate, val to: LocalDate)

    /**
     * What gets counted for [range], and what it's compared with:
     *  - This week / This month: days so far vs the same stretch last week / month,
     *    so a half-finished week is a fair comparison.
     *  - Last month: the whole month vs the whole month before.
     *  - Today, Last 7 days, custom: the same number of days just before.
     */
    fun windows(range: DateRange, today: LocalDate): Pair<Window, Window> {
        val countedEnd = minOf(range.end, today)
        val current = Window(range.start, countedEnd)
        val previous = when (range.preset) {
            RangePreset.THIS_WEEK, RangePreset.THIS_MONTH -> {
                val prevStart = if (range.preset == RangePreset.THIS_WEEK) range.start.minusWeeks(1) else range.start.minusMonths(1)
                val daysIn = ChronoUnit.DAYS.between(range.start, countedEnd)
                Window(prevStart, minOf(prevStart.plusDays(daysIn), range.start.minusDays(1)))
            }
            RangePreset.LAST_MONTH -> Window(range.start.minusMonths(1), range.start.minusDays(1))
            else -> {
                val prevEnd = range.start.minusDays(1)
                Window(prevEnd.minusDays(range.days - 1), prevEnd)
            }
        }
        return current to previous
    }

    private fun Payment.dayIn(w: Window, zone: ZoneId): Boolean {
        val d = Instant.ofEpochMilli(at).atZone(zone).toLocalDate()
        return !d.isBefore(w.from) && !d.isAfter(w.to)
    }

    /**
     * Spend per category in [range], against its comparison window (same as [periodSpend]).
     * [categoryOf] returns null for "Needs a tag". Biggest first; categories with nothing
     * spent in [range] are left out.
     */
    fun byCategory(
        payments: List<Payment>,
        range: DateRange,
        today: LocalDate,
        categoryOf: (Payment) -> String?,
        zone: ZoneId = ZoneId.systemDefault(),
    ): List<CategorySpend> {
        val (current, previous) = windows(range, today)
        val counted = payments.filter(::counts)
        val now = counted.filter { it.dayIn(current, zone) }.groupBy(categoryOf)
        val before = counted.filter { it.dayIn(previous, zone) }.groupBy(categoryOf)
        return now.map { (cat, list) ->
            CategorySpend(cat, list.sumOf { it.paise }, before[cat].orEmpty().sumOf { it.paise })
        }.sortedByDescending { it.totalPaise }
    }

    /**
     * Every payment dated inside [range] (up to today), newest first as given, including ones
     * still waiting for an answer: Home's Payments list uses the same days as the totals.
     */
    fun paymentsIn(
        payments: List<Payment>,
        range: DateRange,
        today: LocalDate,
        zone: ZoneId = ZoneId.systemDefault(),
    ): List<Payment> {
        val (current, _) = windows(range, today)
        return payments.filter { it.dayIn(current, zone) }
    }

    /** Totals for the Home hero. Counts the same payments as [summarise]. */
    fun periodSpend(
        payments: List<Payment>,
        range: DateRange,
        today: LocalDate,
        zone: ZoneId = ZoneId.systemDefault(),
    ): PeriodSpend {
        val (current, previous) = windows(range, today)
        val counted = payments.filter(::counts)
        return PeriodSpend(
            range = range,
            totalPaise = counted.filter { it.dayIn(current, zone) }.sumOf { it.paise },
            previousPaise = counted.filter { it.dayIn(previous, zone) }.sumOf { it.paise },
        )
    }

    fun summarise(payments: List<Payment>, today: LocalDate, zone: ZoneId = ZoneId.systemDefault()): SpendingSummary {
        val month = YearMonth.from(today)
        fun dateOf(p: Payment): LocalDate = Instant.ofEpochMilli(p.at).atZone(zone).toLocalDate()

        val thisMonth = payments.filter { counts(it) && YearMonth.from(dateOf(it)) == month }
        val total = thisMonth.sumOf { it.paise }
        val daysSoFar = today.dayOfMonth.toLong()

        val counted = payments.filter(::counts)
        val week = (6 downTo 0).map { back ->
            val day = today.minusDays(back.toLong())
            DayTotal(day, counted.filter { dateOf(it) == day }.sumOf { it.paise })
        }

        // Same payee regardless of case or extra spaces; shown with the newest spelling.
        val payees = thisMonth
            .groupBy { it.payee?.trim()?.lowercase()?.takeIf(String::isNotEmpty) }
            .map { (key, list) ->
                val name = if (key == null) UNNAMED else list.first().payee!!.trim()
                PayeeTotal(name, list.sumOf { it.paise }, list.size)
            }
            .sortedByDescending { it.totalPaise }
            .take(5)

        val unconfirmed = payments.filter { it.needsAnswer }
        return SpendingSummary(
            month = month,
            totalPaise = total,
            paymentCount = thisMonth.size,
            averagePerDayPaise = if (daysSoFar > 0) total / daysSoFar else 0,
            lastSevenDays = week,
            topPayees = payees,
            unconfirmedPaise = unconfirmed.sumOf { it.paise },
            unconfirmedCount = unconfirmed.size,
        )
    }
}
