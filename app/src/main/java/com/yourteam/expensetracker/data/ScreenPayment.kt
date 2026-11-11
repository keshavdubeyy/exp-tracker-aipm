package com.yourteam.expensetracker.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * A payment read off GPay's success screen. Raw text only; parsing amounts into numbers
 * happens later in plain Kotlin. [dedupeKey] is the UPI ref when shown, otherwise
 * amount + payee + minute, so the same screen isn't saved twice.
 */
@Entity(
    tableName = "screen_payments",
    indices = [Index(value = ["dedupeKey"], unique = true)],
)
data class ScreenPayment(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val dedupeKey: String,
    val appName: String,
    val amountText: String,
    val payee: String,
    val upiRef: String?,
    val capturedAt: Long,
)
