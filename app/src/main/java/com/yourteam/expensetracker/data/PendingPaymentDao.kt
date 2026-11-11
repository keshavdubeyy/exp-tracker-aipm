package com.yourteam.expensetracker.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface PendingPaymentDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(payment: PendingPayment): Long

    @Query("SELECT * FROM pending_payments WHERE id = :id")
    suspend fun get(id: Long): PendingPayment?

    @Query("SELECT * FROM pending_payments WHERE status = 'PENDING' AND detectedAt > :since")
    suspend fun pendingSince(since: Long): List<PendingPayment>

    @Query("SELECT * FROM pending_payments WHERE detectedAt BETWEEN :from AND :to")
    suspend fun between(from: Long, to: Long): List<PendingPayment>

    /** Undo for a payment the user added by hand. */
    @Query("DELETE FROM pending_payments WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("UPDATE pending_payments SET status = :status WHERE id = :id")
    suspend fun setStatus(id: Long, status: String)

    @Query("SELECT * FROM pending_payments ORDER BY detectedAt DESC")
    fun observeAll(): Flow<List<PendingPayment>>
}
