package com.yourteam.expensetracker.data

import android.content.Context
import androidx.room.AutoMigration
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [UpiNotification::class, ScreenPayment::class, PendingPayment::class],
    version = 3,
    autoMigrations = [AutoMigration(from = 1, to = 2), AutoMigration(from = 2, to = 3)],
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun upiNotificationDao(): UpiNotificationDao
    abstract fun screenPaymentDao(): ScreenPaymentDao
    abstract fun pendingPaymentDao(): PendingPaymentDao

    companion object {
        @Volatile private var instance: AppDatabase? = null

        fun get(context: Context): AppDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "expense-tracker.db",
                ).build().also { instance = it }
            }
    }
}
