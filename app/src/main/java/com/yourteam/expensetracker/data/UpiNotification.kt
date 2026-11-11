package com.yourteam.expensetracker.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * A payment notification from a UPI app (GPay, PhonePe, ...). Stored on the phone only
 * (AGENTS.md rule 3). Unique on (app, title, text, time) so reposts aren't saved twice.
 */
@Entity(
    tableName = "upi_notifications",
    indices = [Index(value = ["packageName", "title", "text", "postedAt"], unique = true)],
)
data class UpiNotification(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val packageName: String,
    val appName: String,
    val title: String,
    val text: String,
    val postedAt: Long,
)
