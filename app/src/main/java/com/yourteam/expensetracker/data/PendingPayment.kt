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

        /** [appName] for payments the user added by hand (cash, other apps). Saved as USER_CONFIRMED. */
        const val APP_MANUAL = "Manual"
    }
}
