package com.yourteam.expensetracker.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface UpiNotificationDao {
    /** Returns -1 if this notification was already saved. */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(notification: UpiNotification): Long

    @Query("SELECT * FROM upi_notifications ORDER BY postedAt DESC")
    fun observeAll(): Flow<List<UpiNotification>>
}
