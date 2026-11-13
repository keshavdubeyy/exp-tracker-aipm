// ALL money math lives here, plain Kotlin only (AGENTS.md rule 1).
// Feature 2 (AI-Powered Spending Insights). The app calculates every number; the AI layer may
// only phrase the sentence around a verified number, never compute one. Everything here works
// with AI switched off: the summary and the deterministic insight text below are the ground truth.
package com.yourteam.expensetracker.insights

import com.yourteam.expensetracker.insights.Spending.Window
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalAdjusters
import java.time.format.DateTimeFormatter

/** The four Insights periods (design: Month · Week · Day · Custom). */
enum class PeriodKind { MONTH, WEEK, DAY, CUSTOM }

/**
 * A period the Insights screen is showing, resolved to concrete days. [start]..[end] are both
 * inclusive and are the days the user asked for; the counted window is clipped to today so an
 * ongoing month/week is compared fairly against the same elapsed days last time.
 */
data class InsightPeriod(val kind: PeriodKind, val start: LocalDate, val end: LocalDate) {
    init { require(!end.isBefore(start)) { "end before start" } }

    /** Days asked for, both ends included. */
    val days: Long get() = ChronoUnit.DAYS.between(start, end) + 1

    companion object {
        /** The period containing [anchor] for [kind]. CUSTOM needs explicit days, so use [custom]. */
        fun of(kind: PeriodKind, anchor: LocalDate): InsightPeriod = when (kind) {
            PeriodKind.MONTH -> InsightPeriod(kind, anchor.withDayOfMonth(1), anchor.withDayOfMonth(anchor.lengthOfMonth()))
            PeriodKind.WEEK -> anchor.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
                .let { InsightPeriod(kind, it, it.plusDays(6)) }
            PeriodKind.DAY -> InsightPeriod(kind, anchor, anchor)
            PeriodKind.CUSTOM -> InsightPeriod(kind, anchor, anchor)
        }

        /** Two tapped days, in either order. */
        fun custom(a: LocalDate, b: LocalDate): InsightPeriod =
            if (b.isBefore(a)) InsightPeriod(PeriodKind.CUSTOM, b, a) else InsightPeriod(PeriodKind.CUSTOM, a, b)
    }
}

/** One row of "Where it went": [category] null = "Not sorted yet" (counted in the total, never guessed). */
data class CategorySlice(
    val category: String?,
    val paise: Long,
    val previousPaise: Long,
    val count: Int,
    /** Whole-percent share of the period total, including uncategorised (design rounds to a whole %). */
    val percent: Int,
)

/** Why the "What changed" section is the way it is, kept apart from "no payments at all". */
enum class HistoryState {
    /** Enough data to compare and/or spot a pattern. */
    OK,
    /** Month/Week but no earlier period to compare with yet (in8: "Getting to know your spending"). */
    INSUFFICIENT,
    /** Enough data, but nothing stands out this period (in9: "Nothing stands out"). */
    NO_PATTERN,
    /** Custom range: comparisons are off by decision, patterns may still show (in5). */
    CUSTOM,
    /** A single day: no insight cards (in10). */
    SINGLE_DAY,
}

/** The five insight kinds, in the order they are built (design reference card). */
enum class InsightType { CATEGORY_CHANGE, CATEGORY_SHARE, REPEAT_MERCHANT, WEEKEND_WEEKDAY, DAY_CONCENTRATION }

/**
 * One "What changed" card. Every number is already computed and verified here; [title]/[detail]
 * are the deterministic phrasing used when AI is off (an AI layer may later re-phrase the same
 * facts, never change a number). [id] is a stable fingerprint so a dismissed card never returns.
 */
data class InsightCard(
    val id: String,
    val type: InsightType,
    val title: String,
    /** The one big value on the card: "₹7,500" or "24%". */
    val amountText: String,
    /** Delta chip, e.g. "30% more"; null when there is no chip. */
    val deltaText: String? = null,
    /** True = spending went up (red), false = down (green), null = neutral. */
    val deltaUp: Boolean? = null,
    /** One-line explanation under the value. */
    val detail: String,
    /** Category this card is about, for the drill-down filter (null for merchant/weekend cards). */
    val category: String? = null,
    /** Keys of the payments that make up this card, for "See the payments". */
    val evidenceKeys: List<String> = emptyList(),
    // "How we worked it out" (F4 / in3): shown only when a comparison backs the card.
    val currentPaise: Long? = null,
    val previousPaise: Long? = null,
    val comparisonLabel: String? = null,
    /** The verified facts behind this card, for the AI wording step (Feature 2). Built here, never by the AI. */
    val facts: InsightFacts? = null,
    /** True when [title] and [detail] were written by the AI and passed [InsightWordingCheck]. */
    val aiWritten: Boolean = false,
)

/**
 * What the AI may use to word one card (Golden Dataset v1.0, Feature 2 input). Amounts are whole rupees and percents
 * whole numbers, all worked out in this file; names and dates are words. The AI may not use any other number.
 */
data class InsightFacts(
    val insightType: String,
    val metrics: Map<String, Any>,
    val currentPeriod: String,
    val comparisonPeriod: String?,
    /** The insight the app chose, in plain words. */
    val pattern: String,
    val evidenceCount: Int,
)

/** Everything the Insights screen draws for one period. All amounts in paise. */
data class InsightSummary(
    val period: InsightPeriod,
    val totalPaise: Long,
    /** Same elapsed days last period; null when there is no valid comparison. */
    val previousPaise: Long?,
    val paymentCount: Int,
    /** "1–13 Oct": the days [previousPaise] covers, for the hero line; null when no comparison. */
    val comparisonLabel: String?,
    /** Money received in the window (shown as "Money in ₹…"); never part of [totalPaise]. */
    val moneyInPaise: Long,
    /** How many money-received entries make up [moneyInPaise]. */
    val moneyInCount: Int = 0,
    /** Categorised spend, biggest first (custom categories rolled up into their parent). */
    val categories: List<CategorySlice>,
    /** The "Not sorted yet" bucket, or null when everything is categorised. */
    val uncategorised: CategorySlice?,
    /** Pending payments in the window, shown separately, never counted (in10). */
    val pendingPaise: Long,
    val pendingCount: Int,
    val historyState: HistoryState,
    val cards: List<InsightCard>,
) {
    val changePaise: Long? get() = previousPaise?.let { totalPaise - it }

    /** Money in minus money spent this period. Positive = more came in than went out. */
    val netPaise: Long get() = moneyInPaise - totalPaise

    /** Payments out plus money received. */
    val transactionCount: Int get() = paymentCount + moneyInCount

    /** Percent change vs last period, whole number; null when there is no baseline or it was ₹0. */
    val changePercent: Int?
        get() = previousPaise?.takeIf { it > 0 }?.let { Math.round(changePaise!! * 100.0 / it).toInt() }
}

/** One line of "Inside this category" (in2): a predefined category or a custom one rolled into it. */
data class SubSlice(val name: String, val paise: Long, val custom: Boolean)

/** Everything the category drill-down (in2) draws. */
data class CategoryDetail(
    val displayCategory: String,
    val totalPaise: Long,
    val percent: Int,
    val previousPaise: Long?,
    val comparisonLabel: String?,
    val subBreakdown: List<SubSlice>,
    /** Payment keys in this category this period, newest first. */
    val paymentKeys: List<String>,
)

object Insights {

    /** At most three cards on screen (design decision). */
    const val MAX_CARDS = 3

    // Thresholds. Deliberately conservative: never manufacture an insight to fill the screen.
    private const val MIN_CHANGE_PAISE = 50_000L        // ₹500 absolute move to call a category "up/down"
    private const val MIN_CHANGE_PERCENT = 15           // …and at least a 15% move
    private const val SHARE_THRESHOLD = 20              // a category is "a big part" at ≥ 20%
    private const val REPEAT_MIN_COUNT = 3              // ≥ 3 payments to the same place = "repeat"
    private const val CONCENTRATION_SHARE = 70          // ≥ 70% of a category on 1–2 days = concentration
    private const val WEEKEND_WEEKS = 8L                // weekend-vs-weekday looks back 8 weeks
    private const val WEEKEND_MIN_RATIO = 1.3           // weekend day avg ≥ 1.3× weekday avg to show it

    /**
     * The counted window (clipped to today) and the comparison window for [period].
     * Comparison is null for CUSTOM and DAY, and for an ongoing month/week whose elapsed portion
     * has no matching days last time.
     */
    fun windows(period: InsightPeriod, today: LocalDate): Pair<Window, Window?> {
        val countedEnd = minOf(period.end, today)
        val current = Window(period.start, countedEnd)
        val previous: Window? = when (period.kind) {
            PeriodKind.CUSTOM, PeriodKind.DAY -> null
            PeriodKind.MONTH, PeriodKind.WEEK -> {
                val ongoing = !today.isBefore(period.start) && !today.isAfter(period.end)
                val prevStart = if (period.kind == PeriodKind.MONTH) period.start.minusMonths(1) else period.start.minusWeeks(1)
                if (ongoing) {
                    val elapsed = ChronoUnit.DAYS.between(period.start, countedEnd) // 0-based last index
                    Window(prevStart, prevStart.plusDays(elapsed))
                } else {
                    val prevEnd = if (period.kind == PeriodKind.MONTH) prevStart.withDayOfMonth(prevStart.lengthOfMonth())
                    else prevStart.plusDays(6)
                    Window(prevStart, prevEnd)
                }
            }
        }
        return current to previous
    }

    /** Builds the whole summary for [period], counting the same payments as the rest of the app. */
    fun summarise(
        payments: List<Payment>,
        received: List<BankCredit>,
        period: InsightPeriod,
        today: LocalDate,
        categoryOf: (Payment) -> String?,
        parentOf: (String) -> String,
        zone: ZoneId = ZoneId.systemDefault(),
    ): InsightSummary {
        val (current, previous) = windows(period, today)
        fun dateOf(at: Long) = Instant.ofEpochMilli(at).atZone(zone).toLocalDate()
        fun Payment.inWindow(w: Window) = dateOf(at).let { !it.isBefore(w.from) && !it.isAfter(w.to) }

        val counted = payments.filter(Spending::counts)
        val nowAll = counted.filter { it.inWindow(current) }
        val prevAll = previous?.let { w -> counted.filter { it.inWindow(w) } }.orEmpty()

        val total = nowAll.sumOf { it.paise }
        val prevTotal = previous?.let { prevAll.sumOf { it.paise } }

        // Group by the display (parent) category; null = not sorted yet.
        fun displayCat(p: Payment): String? = categoryOf(p)?.let(parentOf)
        val nowByCat = nowAll.groupBy(::displayCat)
        val prevByCat = prevAll.groupBy(::displayCat)
        fun sliceFor(cat: String?, list: List<Payment>): CategorySlice {
            val sum = list.sumOf { it.paise }
            return CategorySlice(
                category = cat,
                paise = sum,
                previousPaise = prevByCat[cat].orEmpty().sumOf { it.paise },
                count = list.size,
                percent = if (total > 0) Math.round(sum * 100.0 / total).toInt() else 0,
            )
        }
        val categories = nowByCat.filterKeys { it != null }
            .map { (cat, list) -> sliceFor(cat, list) }
            .sortedByDescending { it.paise }
        val uncategorised = nowByCat[null]?.let { sliceFor(null, it) }

        val receivedNow = received.filter {
            dateOf(it.at).let { d -> !d.isBefore(current.from) && !d.isAfter(current.to) }
        }
        val moneyIn = receivedNow.sumOf { it.paise }

        val pending = payments.filter { it.needsAnswer && it.inWindow(current) }

        val state = historyState(period, prevTotal, nowAll.size)
        val cards = if (state == HistoryState.INSUFFICIENT || state == HistoryState.SINGLE_DAY) emptyList()
        else buildCards(period, today, nowAll, prevAll, counted, current, previous, categories, total, categoryOf, parentOf, zone)

        return InsightSummary(
            period = period,
            totalPaise = total,
            previousPaise = prevTotal,
            paymentCount = nowAll.size,
            comparisonLabel = previous?.let { label(it) },
            moneyInPaise = moneyIn,
            moneyInCount = receivedNow.size,
            categories = categories,
            uncategorised = uncategorised,
            pendingPaise = pending.sumOf { it.paise },
            pendingCount = pending.size,
            historyState = if (cards.isEmpty() && state == HistoryState.OK) HistoryState.NO_PATTERN else state,
            cards = cards.take(MAX_CARDS),
        )
    }

    /** The drill-down for one display category in [period] (in2). Newest payments first. */
    fun categoryDetail(
        payments: List<Payment>,
        period: InsightPeriod,
        today: LocalDate,
        displayCategory: String,
        categoryOf: (Payment) -> String?,
        parentOf: (String) -> String,
        isCustom: (String) -> Boolean,
        zone: ZoneId = ZoneId.systemDefault(),
    ): CategoryDetail {
        val (current, previous) = windows(period, today)
        fun dateOf(at: Long) = Instant.ofEpochMilli(at).atZone(zone).toLocalDate()
        fun Payment.inWindow(w: Window) = dateOf(at).let { !it.isBefore(w.from) && !it.isAfter(w.to) }
        val counted = payments.filter(Spending::counts)
        val grandTotal = counted.filter { it.inWindow(current) }.sumOf { it.paise }
        val inCat = counted.filter { it.inWindow(current) && categoryOf(it)?.let(parentOf) == displayCategory }
        val catTotal = inCat.sumOf { it.paise }
        val prevCat = previous?.let { w -> counted.filter { it.inWindow(w) && categoryOf(it)?.let(parentOf) == displayCategory }.sumOf { it.paise } }

        // "Inside this category": the parent itself first, then its custom children, biggest first.
        val sub = inCat.groupBy { categoryOf(it) ?: displayCategory }
            .map { (name, list) -> SubSlice(name, list.sumOf { it.paise }, isCustom(name)) }
            .sortedWith(compareByDescending<SubSlice> { it.name == displayCategory }.thenByDescending { it.paise })

        return CategoryDetail(
            displayCategory = displayCategory,
            totalPaise = catTotal,
            percent = if (grandTotal > 0) Math.round(catTotal * 100.0 / grandTotal).toInt() else 0,
            previousPaise = prevCat,
            comparisonLabel = previous?.let { label(it) },
            subBreakdown = sub,
            paymentKeys = inCat.sortedByDescending { it.at }.map { it.key },
        )
    }

    private fun historyState(period: InsightPeriod, prevTotal: Long?, countInNow: Int): HistoryState = when (period.kind) {
        PeriodKind.DAY -> HistoryState.SINGLE_DAY
        PeriodKind.CUSTOM -> HistoryState.CUSTOM
        PeriodKind.MONTH, PeriodKind.WEEK ->
            if ((prevTotal ?: 0L) <= 0L) HistoryState.INSUFFICIENT else HistoryState.OK
    }

    // ---- Insight candidates (deterministic; evidence-backed; capped by the caller) ----

    private fun buildCards(
        period: InsightPeriod,
        today: LocalDate,
        now: List<Payment>,
        prev: List<Payment>,
        allCounted: List<Payment>,
        current: Window,
        previous: Window?,
        categories: List<CategorySlice>,
        total: Long,
        categoryOf: (Payment) -> String?,
        parentOf: (String) -> String,
        zone: ZoneId,
    ): List<InsightCard> {
        val cards = mutableListOf<InsightCard>()
        val compLabel = previous?.let { label(it) }
        val nowLabel = label(current)
        val periodWord = periodWord(period.kind)
        fun displayCat(p: Payment): String? = categoryOf(p)?.let(parentOf)

        // 1. Category up/down vs the same days last period.
        if (previous != null) {
            categories.filter { it.category != null && it.previousPaise >= MIN_CHANGE_PAISE }
                .map { it to (it.paise - it.previousPaise) }
                .filter { (s, diff) ->
                    kotlin.math.abs(diff) >= MIN_CHANGE_PAISE &&
                        kotlin.math.abs(Math.round(diff * 100.0 / s.previousPaise)) >= MIN_CHANGE_PERCENT
                }
                .sortedByDescending { kotlin.math.abs(it.second) }
                .firstOrNull()?.let { (slice, diff) ->
                    val up = diff > 0
                    val pct = kotlin.math.abs(Math.round(diff * 100.0 / slice.previousPaise)).toInt()
                    val keys = now.filter { displayCat(it) == slice.category }.map { it.key }
                    cards += InsightCard(
                        id = "change-${slice.category}-${period.start}",
                        type = InsightType.CATEGORY_CHANGE,
                        title = "${slice.category} is ${if (up) "up" else "down"}",
                        amountText = Money.formatWhole(slice.paise),
                        // A huge percent off a small base is more noise than signal — show it only when it's meaningful.
                        deltaText = if (pct <= 300) "$pct% ${if (up) "more" else "less"}" else null,
                        deltaUp = up,
                        detail = "${Money.formatWhole(kotlin.math.abs(diff))} ${if (up) "more" else "less"} than " +
                            "${compLabel?.let { "the same ${previous.days()} days in ${monthOf(previous)}" } ?: "last period"} " +
                            "(${Money.formatWhole(slice.previousPaise)}).",
                        category = slice.category,
                        evidenceKeys = keys,
                        currentPaise = slice.paise,
                        previousPaise = slice.previousPaise,
                        comparisonLabel = compLabel,
                        facts = InsightFacts(
                            insightType = "category_comparison",
                            metrics = linkedMapOf(
                                "category" to slice.category!!, "current_inr" to rupees(slice.paise),
                                "previous_inr" to rupees(slice.previousPaise), "change_inr" to rupees(kotlin.math.abs(diff)),
                                "change_percent" to if (up) pct else -pct,
                            ),
                            currentPeriod = nowLabel, comparisonPeriod = compLabel,
                            pattern = "${slice.category} spending is ${if (up) "up" else "down"} on the same days last period",
                            evidenceCount = keys.size,
                        ),
                    )
                }
        }

        // 2. Biggest category share (with last period's share when we can compare).
        categories.firstOrNull()?.takeIf { it.category != null && it.percent >= SHARE_THRESHOLD && total > 0 }?.let { slice ->
            val prevShare = if (previous != null && prev.isNotEmpty()) {
                val prevTotal = prev.sumOf { it.paise }
                if (prevTotal > 0) Math.round(slice.previousPaise * 100.0 / prevTotal).toInt() else null
            } else null
            val keys = now.filter { displayCat(it) == slice.category }.map { it.key }
            cards += InsightCard(
                id = "share-${slice.category}-${period.start}",
                type = InsightType.CATEGORY_SHARE,
                title = "${slice.category} is ${shareWord(slice.percent)} of your spending",
                amountText = "${slice.percent}%",
                detail = "${Money.formatWhole(slice.paise)} of ${Money.formatWhole(total)}." +
                    (prevShare?.let { " In ${monthOf(previous!!)} it was $it%." } ?: ""),
                category = slice.category,
                evidenceKeys = keys,
                currentPaise = slice.paise,
                previousPaise = if (prevShare != null) slice.previousPaise else null,
                comparisonLabel = compLabel,
                facts = InsightFacts(
                    insightType = "category_share",
                    metrics = linkedMapOf<String, Any>(
                        "category" to slice.category!!, "category_inr" to rupees(slice.paise), "total_inr" to rupees(total),
                        "share_percent" to slice.percent,
                    ).apply { prevShare?.let { put("previous_share_percent", it) } },
                    currentPeriod = nowLabel, comparisonPeriod = if (prevShare != null) compLabel else null,
                    pattern = "${slice.category} is ${slice.percent}% of spending",
                    evidenceCount = keys.size,
                ),
            )
        }

        // 3. Most frequent merchant.
        now.filter { it.payee != null }
            .groupBy { it.payee!!.trim().lowercase() }
            .filterKeys { it.isNotEmpty() }
            .map { (_, list) -> list }
            .filter { it.size >= REPEAT_MIN_COUNT }
            .maxByOrNull { it.size }?.let { list ->
                val name = list.first().payee!!.trim()
                val afterEight = list.count { Instant.ofEpochMilli(it.at).atZone(zone).hour >= 20 }
                val lateLine = if (afterEight >= 2) " $afterEight of the ${list.size} were after 8 pm." else ""
                cards += InsightCard(
                    id = "repeat-${name.lowercase()}-${period.start}",
                    type = InsightType.REPEAT_MERCHANT,
                    title = "$name, ${list.size} times",
                    amountText = Money.formatWhole(list.sumOf { it.paise }),
                    detail = "Your most frequent place this $periodWord.$lateLine",
                    category = null,
                    evidenceKeys = list.map { it.key },
                    facts = InsightFacts(
                        insightType = "repeat_merchant",
                        metrics = linkedMapOf<String, Any>(
                            "payee" to name, "payment_count" to list.size, "total_inr" to rupees(list.sumOf { it.paise }),
                        ).apply { if (afterEight >= 2) put("after_8pm_count", afterEight) },
                        currentPeriod = nowLabel, comparisonPeriod = null,
                        pattern = "The most frequent place paid this $periodWord",
                        evidenceCount = list.size,
                    ),
                )
            }

        // 4. Weekend vs weekday (Week view, needs enough history).
        if (period.kind == PeriodKind.WEEK) weekendVsWeekday(allCounted, today, zone)?.let { cards += it }

        // 5. Day concentration (Custom view): a big category landing on just 1–2 days.
        if (period.kind == PeriodKind.CUSTOM) {
            categories.firstOrNull { it.category != null }?.let { slice ->
                val catPayments = now.filter { displayCat(it) == slice.category }
                val byDay = catPayments.groupBy { Instant.ofEpochMilli(it.at).atZone(zone).toLocalDate() }
                    .mapValues { it.value.sumOf { p -> p.paise } }
                    .entries.sortedByDescending { it.value }
                if (byDay.size >= 3) {
                    val topTwo = byDay.take(2)
                    val topSum = topTwo.sumOf { it.value }
                    val share = if (slice.paise > 0) Math.round(topSum * 100.0 / slice.paise).toInt() else 0
                    if (share >= CONCENTRATION_SHARE) {
                        val dayWords = topTwo.map { it.key }.sorted().joinToString(" and ") { dayMonth.format(it) }
                        cards += InsightCard(
                            id = "concentration-${slice.category}-${period.start}",
                            type = InsightType.DAY_CONCENTRATION,
                            title = "${slice.category} on ${topTwo.size} days",
                            amountText = Money.formatWhole(topSum),
                            detail = "$share% of ${slice.category} in this range was on $dayWords.",
                            category = slice.category,
                            facts = InsightFacts(
                                insightType = "day_concentration",
                                metrics = linkedMapOf<String, Any>(
                                    "category" to slice.category!!, "category_inr" to rupees(slice.paise),
                                    "top_days_inr" to rupees(topSum), "share_percent" to share,
                                ).apply { topTwo.map { it.key }.sorted().forEachIndexed { i, d -> put("day_${i + 1}", dayMonth.format(d)) } },
                                currentPeriod = nowLabel, comparisonPeriod = null,
                                pattern = "Most ${slice.category} spending in this range fell on ${topTwo.size} days",
                                evidenceCount = catPayments.size,
                            ),
                            evidenceKeys = topTwo.flatMap { (d, _) ->
                                catPayments.filter { p -> Instant.ofEpochMilli(p.at).atZone(zone).toLocalDate() == d }.map { it.key }
                            },
                        )
                    }
                }
            }
        }

        return cards
    }

    /**
     * Weekend days average vs weekday days average over the last [WEEKEND_WEEKS] full weeks.
     * Needs [allCounted] (every counted payment); pass it in from [summariseWeekendContext].
     * Returns null when there isn't enough history or the gap isn't meaningful.
     */
    fun weekendVsWeekday(allCounted: List<Payment>, today: LocalDate, zone: ZoneId): InsightCard? {
        if (allCounted.isEmpty()) return null
        val lastMonday = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        val windowStart = lastMonday.minusWeeks(WEEKEND_WEEKS)
        val windowEnd = lastMonday.minusDays(1) // last full Sunday
        if (windowEnd.isBefore(windowStart)) return null
        fun dateOf(at: Long) = Instant.ofEpochMilli(at).atZone(zone).toLocalDate()
        val inWindow = allCounted.filter { dateOf(it.at).let { d -> !d.isBefore(windowStart) && !d.isAfter(windowEnd) } }
        if (inWindow.size < 10) return null // too little to be a pattern
        val weekendSum = inWindow.filter { dateOf(it.at).dayOfWeek in setOf(DayOfWeek.SATURDAY, DayOfWeek.SUNDAY) }.sumOf { it.paise }
        val weekdaySum = inWindow.sumOf { it.paise } - weekendSum
        val weekendDays = WEEKEND_WEEKS * 2
        val weekdayDays = WEEKEND_WEEKS * 5
        val weekendAvg = weekendSum / weekendDays
        val weekdayAvg = weekdaySum / weekdayDays
        if (weekdayAvg <= 0 || weekendAvg < weekdayAvg * WEEKEND_MIN_RATIO) return null
        return InsightCard(
            id = "weekend-$lastMonday",
            type = InsightType.WEEKEND_WEEKDAY,
            title = "You spend more on weekends",
            amountText = "${Money.formatWhole(weekendAvg)} vs ${Money.formatWhole(weekdayAvg)}",
            detail = "Average per day on Sat–Sun vs Mon–Fri, over the last $WEEKEND_WEEKS weeks.",
            facts = InsightFacts(
                insightType = "weekend_vs_weekday",
                metrics = linkedMapOf(
                    "weekend_daily_avg_inr" to rupees(weekendAvg), "weekday_daily_avg_inr" to rupees(weekdayAvg),
                    "weeks_looked_at" to WEEKEND_WEEKS,
                ),
                currentPeriod = label(Window(windowStart, windowEnd)), comparisonPeriod = null,
                pattern = "Weekend days average more spending than weekdays",
                evidenceCount = inWindow.size,
            ),
            evidenceKeys = inWindow.filter { dateOf(it.at).dayOfWeek in setOf(DayOfWeek.SATURDAY, DayOfWeek.SUNDAY) }.map { it.key },
        )
    }

    /** Whole rupees for the AI facts, rounded the same way as [Money.formatWhole]. */
    fun rupees(paise: Long): Long = Math.round(paise / 100.0)

    // ---- small date/number words (no money here) ----

    private val dayMonth = DateTimeFormatter.ofPattern("d MMM")
    private val monthShort = DateTimeFormatter.ofPattern("MMM")
    private val monthFull = DateTimeFormatter.ofPattern("MMMM")

    private fun Window.days(): Long = ChronoUnit.DAYS.between(from, to) + 1

    /** "1–13 Oct" for a comparison window. */
    fun label(w: Window): String =
        if (w.from.month == w.to.month) "${w.from.dayOfMonth}–${w.to.dayOfMonth} ${monthShort.format(w.to)}"
        else "${dayMonth.format(w.from)}–${dayMonth.format(w.to)}"

    private fun monthOf(w: Window): String = monthFull.format(w.from)

    private fun periodWord(kind: PeriodKind): String = when (kind) {
        PeriodKind.MONTH -> "month"
        PeriodKind.WEEK -> "week"
        PeriodKind.DAY -> "day"
        PeriodKind.CUSTOM -> "range"
    }

    /** A friendly share word for the title: 24% → "a quarter", 33% → "a third", 50% → "half". */
    private fun shareWord(pct: Int): String = when (pct) {
        in 22..27 -> "a quarter"
        in 30..36 -> "a third"
        in 45..55 -> "half"
        in 62..70 -> "two thirds"
        else -> "a big part"
    }
}
