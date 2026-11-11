package com.yourteam.expensetracker.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface ScreenPaymentDao {
    /** Returns -1 if this payment was already saved. */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(payment: ScreenPayment): Long

    @Query("SELECT * FROM screen_payments WHERE capturedAt BETWEEN :from AND :to")
    suspend fun between(from: Long, to: Long): List<ScreenPayment>

    @Query("SELECT * FROM screen_payments ORDER BY capturedAt DESC")
    fun observeAll(): Flow<List<ScreenPayment>>
}
