package com.yourteam.expensetracker.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * A payment that has started (UPI PIN screen seen) but isn't confirmed yet.
 * Later steps link it to a bank SMS / chat line, or ask the user.
 */
@Entity(
    tableName = "pending_payments",
    indices = [Index(value = ["dedupeKey"], unique = true)],
)
data class PendingPayment(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val dedupeKey: String,
    val appName: String,
    val amountText: String,
    val payee: String,
    val detectedAt: Long,
    val status: String = STATUS_PENDING,
) {
    companion object {
        const val STATUS_PENDING = "PENDING" // not confirmed yet
        const val STATUS_MATCHED = "MATCHED" // a bank SMS / chat line recorded it
        const val STATUS_USER_CONFIRMED = "USER_CONFIRMED" // user said "Yes, I paid"
        const val STATUS_DISMISSED = "DISMISSED" // user said "Didn't go through"

        /** [appName] for payments the user added by hand, from a bank account. Saved as USER_CONFIRMED. */
        const val APP_MANUAL = "Manual"
        /** Added by hand: money out, paid in cash. */
        const val APP_MANUAL_CASH = "Manual cash"
        /** Added by hand: money in, into a bank account. */
        const val APP_MANUAL_IN = "Manual in"
        /** Added by hand: money in, received in cash. */
        const val APP_MANUAL_IN_CASH = "Manual in cash"

        fun isManualIn(appName: String) = appName == APP_MANUAL_IN || appName == APP_MANUAL_IN_CASH
        fun isManualOut(appName: String) = appName == APP_MANUAL || appName == APP_MANUAL_CASH
        fun isCash(appName: String) = appName == APP_MANUAL_CASH || appName == APP_MANUAL_IN_CASH

        /** [appName] for payments started from our own scanner (M7) and handed to a UPI app. */
        const val APP_SCAN = "Scan & Pay"
    }
}
