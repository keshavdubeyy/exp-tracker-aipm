package com.yourteam.expensetracker.ui.screens.insights

import com.yourteam.expensetracker.insights.InsightPeriod
import com.yourteam.expensetracker.insights.PeriodKind
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/** Words for an [InsightPeriod]: the date label and the "than …" phrase. Dates only; no money. */
object InsightsWords {
    private val dayMonth = DateTimeFormatter.ofPattern("d MMM")
    private val weekdayDayMonth = DateTimeFormatter.ofPattern("EEE d MMM")
    private val monthYear = DateTimeFormatter.ofPattern("MMMM yyyy")

    /** The dropdown label, e.g. "1–13 Nov 2026", "This week · 10–16 Nov", "Sun 9 Nov", "5–15 Nov · 11 days". */
    fun label(p: InsightPeriod, today: LocalDate): String = when (p.kind) {
        PeriodKind.MONTH ->
            if (containsToday(p, today)) "1–${today.dayOfMonth} ${DateTimeFormatter.ofPattern("MMM yyyy").format(today)}"
            else monthYear.format(p.start)
        PeriodKind.WEEK -> {
            val span = span(p.start, p.end)
            if (containsToday(p, today)) "This week · $span" else span
        }
        PeriodKind.DAY -> if (p.start == today) "Today · ${weekdayDayMonth.format(p.start)}" else weekdayDayMonth.format(p.start)
        PeriodKind.CUSTOM -> "${span(p.start, p.end)} · ${p.days} days"
    }

    /** Short tab label on the segmented control. */
    fun tab(kind: PeriodKind): String = when (kind) {
        PeriodKind.MONTH -> "Monthly"
        PeriodKind.WEEK -> "Weekly"
        PeriodKind.DAY -> "Daily"
        PeriodKind.CUSTOM -> "Custom"
    }

    /** The unit in a sentence: "month", "week", "day", "dates". */
    fun unit(kind: PeriodKind): String = when (kind) {
        PeriodKind.MONTH -> "month"
        PeriodKind.WEEK -> "week"
        PeriodKind.DAY -> "day"
        PeriodKind.CUSTOM -> "dates"
    }

    /** "3–9 Nov", "30 Nov–6 Dec", "9 Nov". */
    fun span(start: LocalDate, end: LocalDate): String = when {
        start == end -> dayMonth.format(start)
        start.month == end.month && start.year == end.year -> "${start.dayOfMonth}–${dayMonth.format(end)}"
        else -> "${dayMonth.format(start)}–${dayMonth.format(end)}"
    }

    fun containsToday(p: InsightPeriod, today: LocalDate): Boolean = !today.isBefore(p.start) && !today.isAfter(p.end)

    /** Previous calendar month's name, for the insufficient-history line. */
    fun previousMonthName(p: InsightPeriod): String =
        DateTimeFormatter.ofPattern("MMMM").format(p.start.minusMonths(1))
}
