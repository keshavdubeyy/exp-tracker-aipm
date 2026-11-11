// ALL money math lives here, plain Kotlin only (AGENTS.md rule 1).
package com.yourteam.expensetracker.insights

import kotlin.math.abs

/** Something that recorded a payment: a bank SMS, a GPay chat line, … */
data class PaymentRecord(val paise: Long, val at: Long, val source: String)

/**
 * Decides whether a started payment (UPI PIN screen seen) was recorded by another source:
 * same amount, from 2 minutes before to 30 minutes after it started. Bank SMS can be late.
 */
object PaymentMatcher {
    const val BEFORE_MS = 2 * 60_000L
    const val AFTER_MS = 30 * 60_000L

    fun findMatch(paise: Long, startedAt: Long, records: List<PaymentRecord>): PaymentRecord? =
        records
            .filter { it.paise == paise && it.at in (startedAt - BEFORE_MS)..(startedAt + AFTER_MS) }
            .minByOrNull { abs(it.at - startedAt) }
}
