package com.yourteam.expensetracker.insights

import kotlin.math.abs

/** Feature 1 capture checks over the payment list (MVP M24). Counts only, plain Kotlin. */
object CaptureChecks {

    /** An unlinked app alert and bank SMS for the same amount this close together are probably one payment. */
    const val DOUBLE_WINDOW_MS = 30 * 60_000L

    /**
     * Possible double counts: a payment recorded only by an app alert or a "Yes, paid" answer, and a separate
     * payment recorded only by a bank SMS, for the same amount within [DOUBLE_WINDOW_MS]. Each payment is used
     * once, closest in time first. Two payments from the same kind of record are real repeats, not counted.
     */
    fun possibleDoubles(payments: List<Payment>): Int {
        val smsOnly = payments.filter { it.sms != null && it.chat == null && it.started == null }.toMutableList()
        val noSms = payments.filter { it.sms == null && (it.chat != null || it.started != null) && !it.needsAnswer }
        var pairs = 0
        noSms.sortedBy { it.at }.forEach { p ->
            val match = smsOnly.filter { it.paise == p.paise && abs(it.at - p.at) <= DOUBLE_WINDOW_MS }
                .minByOrNull { abs(it.at - p.at) } ?: return@forEach
            smsOnly.remove(match)
            pairs++
        }
        return pairs
    }

    /** The payment has a real payee name, not "Other bank payments". */
    fun payeeNamed(p: Payment): Boolean = !p.payee.isNullOrBlank() && p.payee != Spending.UNNAMED
}
