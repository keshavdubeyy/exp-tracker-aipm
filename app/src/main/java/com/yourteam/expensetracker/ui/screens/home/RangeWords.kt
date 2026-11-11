package com.yourteam.expensetracker.ui.screens.home

import com.yourteam.expensetracker.insights.DateRange
import com.yourteam.expensetracker.insights.RangePreset
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/** Words for a [DateRange]: labels and "than …" phrases. Dates only; no money here. */
object RangeWords {

    private val dayMonth = DateTimeFormatter.ofPattern("d MMM")
    private val weekdayDayMonth = DateTimeFormatter.ofPattern("EEE d MMM")
    private val monthName = DateTimeFormatter.ofPattern("MMMM")

    /** Chip text on the date sheet. */
    fun chip(p: RangePreset): String = when (p) {
        RangePreset.TODAY -> "Today"
        RangePreset.THIS_WEEK -> "This week"
        RangePreset.LAST_7_DAYS -> "Last 7 days"
        RangePreset.THIS_MONTH -> "This month"
        RangePreset.LAST_MONTH -> "Last month"
        RangePreset.CUSTOM -> "Custom"
    }

    /** Home switcher: "Last 7 days · 3–9 Nov", "Today · Mon 9 Nov", "Last month · October", "3–9 Nov". */
    fun label(r: DateRange): String {
        val dates = when (r.preset) {
            RangePreset.TODAY -> weekdayDayMonth.format(r.start)
            RangePreset.THIS_MONTH, RangePreset.LAST_MONTH -> monthName.format(r.start)
            else -> span(r.start, r.end)
        }
        return if (r.preset == RangePreset.CUSTOM) dates else "${chip(r.preset)} · $dates"
    }

    /** "3–9 Nov", "30 Nov–6 Dec", "9 Nov" for a single day. */
    fun span(start: LocalDate, end: LocalDate): String = when {
        start == end -> dayMonth.format(start)
        start.month == end.month && start.year == end.year -> "${start.dayOfMonth}–${dayMonth.format(end)}"
        else -> "${dayMonth.format(start)}–${dayMonth.format(end)}"
    }

    /** Sheet summary: "Tue 3 Nov – Mon 9 Nov · 7 days", "Mon 9 Nov · 1 day". */
    fun summary(r: DateRange): String {
        val days = if (r.days == 1L) "1 day" else "${r.days} days"
        return if (r.start == r.end) "${weekdayDayMonth.format(r.start)} · $days"
        else "${weekdayDayMonth.format(r.start)} – ${weekdayDayMonth.format(r.end)} · $days"
    }

    /** What the range is compared with, for "₹412 less than …" (matches Spending.windows). */
    fun since(r: DateRange): String = when (r.preset) {
        RangePreset.TODAY -> "yesterday"
        RangePreset.THIS_WEEK -> "last week"
        RangePreset.THIS_MONTH -> "last month"
        RangePreset.LAST_MONTH -> "the month before"
        RangePreset.LAST_7_DAYS -> "the 7 days before"
        RangePreset.CUSTOM -> if (r.days == 1L) "the day before" else "the ${r.days} days before"
    }

    /** Hero and empty-state title when there are no payments in [r]. */
    fun nothingSpent(r: DateRange): String = when (r.preset) {
        RangePreset.TODAY -> "No payments yet today"
        RangePreset.THIS_WEEK -> "No payments yet this week"
        RangePreset.THIS_MONTH -> "No payments yet this month"
        RangePreset.LAST_MONTH -> "No payments in ${monthName.format(r.start)}"
        else -> "No payments from ${span(r.start, r.end)}"
    }

    /** True when [r] ends today, so a new payment could still land in it. */
    fun includesToday(r: DateRange, today: LocalDate): Boolean = r.end == today
}
