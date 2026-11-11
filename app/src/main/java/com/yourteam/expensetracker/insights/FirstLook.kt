package com.yourteam.expensetracker.insights

import java.time.LocalDate
import java.time.ZoneId

/** Onboarding ob4 "First look": what the last [days] days of imported payments add up to. */
data class FirstLook(
    val totalPaise: Long,
    val paymentCount: Int,
    /** Biggest categories first (at most 3), each with its bar length relative to the biggest. */
    val top: List<Pair<CategorySpend, Float>>,
    /** Counted payments with no category yet ("Add category"). */
    val uncategorised: Int,
)

object FirstLooks {
    const val DAYS = 90L

    /** Plain Kotlin sums over the same payments the Home totals count (AGENTS.md rule 1). */
    fun of(
        payments: List<Payment>,
        today: LocalDate,
        categoryOf: (Payment) -> String?,
        zone: ZoneId = ZoneId.systemDefault(),
    ): FirstLook {
        val range = DateRange.custom(today.minusDays(DAYS - 1), today)
        val inRange = Spending.paymentsIn(payments, range, today, zone).filter(Spending::counts)
        val categories = Spending.byCategory(payments, range, today, categoryOf, zone).filter { it.category != null }
        val biggest = categories.firstOrNull()?.totalPaise ?: 0L
        return FirstLook(
            totalPaise = inRange.sumOf { it.paise },
            paymentCount = inRange.size,
            top = categories.take(3).map { it to if (biggest > 0) it.totalPaise.toFloat() / biggest else 0f },
            uncategorised = inRange.count { categoryOf(it) == null },
        )
    }
}
