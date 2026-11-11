package com.yourteam.expensetracker.notifications

import com.yourteam.expensetracker.BuildConfig
import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.CoroutineWorker
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.ExistingWorkPolicy
import androidx.work.workDataOf
import com.yourteam.expensetracker.MainActivity
import com.yourteam.expensetracker.R
import com.yourteam.expensetracker.data.AppDatabase
import com.yourteam.expensetracker.data.PendingPayment
import com.yourteam.expensetracker.insights.PaymentMatcher
import com.yourteam.expensetracker.insights.Reconciler
import com.yourteam.expensetracker.data.PaymentInputs
import com.yourteam.expensetracker.sms.SenderFilter
import com.yourteam.expensetracker.sms.SmsReader
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.time.Duration
import java.time.LocalDateTime
import java.util.concurrent.TimeUnit

/**
 * After a payment starts (UPI PIN screen), checks whether a bank SMS or GPay chat line
 * recorded it. If not, asks the user: "Did your ₹20 to X go through?".
 *
 * The quick ask is the pop-up ([PaymentOverlay], ~10 s after the PIN screen closes).
 * These checks are the fallback, e.g. if the pop-up was closed with "Ask me later":
 * Check 1 (10 min; 2 min in debug): matched → done; else notify and schedule check 2.
 * Check 2 (+20 min; 5 min in debug): a late SMS matched → mark it and remove the notification.
 */
object PaymentCheck {
    // High importance so it pops up as a banner. Channel importance can't be raised after
    // creation, so the old default-importance channel is deleted and replaced.
    private const val CHANNEL_ID = "payment_check_alerts"
    private const val OLD_CHANNEL_ID = "payment_check"
    const val ACTION_CONFIRM = "com.yourteam.expensetracker.CONFIRM_PAYMENT"
    const val ACTION_DISMISS = "com.yourteam.expensetracker.DISMISS_PAYMENT"
    const val EXTRA_ID = "pending_id"
    internal const val KEY_ID = "id"
    internal const val KEY_ATTEMPT = "attempt"

    fun schedule(context: Context, pendingId: Long, attempt: Int = 1) {
        val debug = BuildConfig.TEST_BUILD
        val delaySeconds = when {
            attempt == 1 && debug -> 2 * 60L
            attempt == 1 -> 10 * 60L
            debug -> 5 * 60L
            else -> 20 * 60L
        }
        val request = OneTimeWorkRequestBuilder<PaymentCheckWorker>()
            .setInitialDelay(delaySeconds, TimeUnit.SECONDS)
            .setInputData(workDataOf(KEY_ID to pendingId, KEY_ATTEMPT to attempt))
            .build()
        WorkManager.getInstance(context)
            .enqueueUniqueWork("payment-check-$pendingId-$attempt", ExistingWorkPolicy.KEEP, request)
    }

    /**
     * The user closed the pop-up with ✕ ("not now"): remind them with a notification at 9 PM
     * today, or in 30 minutes if it's already past 8:30 PM. 5 seconds in debug builds.
     * Replaces the normal fallback check, so they're asked once, not twice.
     */
    fun scheduleReminder(context: Context, pendingId: Long) {
        val debug = BuildConfig.TEST_BUILD
        val delaySeconds = if (debug) 5L else secondsUntilEvening(LocalDateTime.now())
        val request = OneTimeWorkRequestBuilder<PaymentCheckWorker>()
            .setInitialDelay(delaySeconds, TimeUnit.SECONDS)
            .setInputData(workDataOf(KEY_ID to pendingId, KEY_ATTEMPT to 1))
            .build()
        WorkManager.getInstance(context)
            .enqueueUniqueWork("payment-check-$pendingId-1", ExistingWorkPolicy.REPLACE, request)
    }

    internal fun secondsUntilEvening(now: LocalDateTime): Long {
        val evening = now.toLocalDate().atTime(21, 0)
        return if (now.isBefore(evening.minusMinutes(30))) {
            Duration.between(now, evening).seconds
        } else {
            30 * 60L
        }
    }

    /**
     * If a GPay chat line or bank SMS recorded this payment, marks it MATCHED and returns where
     * it was found ("GPay chat" / "bank SMS"). Otherwise null. Uses the same one-to-one
     * [Reconciler] as Home, so one SMS can't confirm two payments.
     */
    suspend fun tryMatch(context: Context, pending: PendingPayment): String? {
        // Wide enough to include neighbouring payments that might compete for the same SMS.
        val from = pending.detectedAt - PaymentMatcher.AFTER_MS - PaymentMatcher.BEFORE_MS
        val to = pending.detectedAt + PaymentMatcher.AFTER_MS + PaymentMatcher.BEFORE_MS
        val db = AppDatabase.get(context)

        val started = db.pendingPaymentDao().between(from, to).mapNotNull(PaymentInputs::started)
        val chats = db.screenPaymentDao().between(from, to).mapNotNull(PaymentInputs::chat)
        val debits = if (hasPermission(context, Manifest.permission.READ_SMS)) {
            SmsReader(context).readBetween(from, to).filter(SenderFilter::keep).mapNotNull(PaymentInputs::debit)
        } else {
            emptyList()
        }

        val payment = Reconciler.reconcile(started, chats, debits).firstOrNull { it.started?.id == pending.id }
        val source = when {
            payment?.chat != null -> "GPay chat"
            payment?.sms != null -> "bank SMS"
            else -> return null
        }
        if (pending.status == PendingPayment.STATUS_PENDING) {
            db.pendingPaymentDao().setStatus(pending.id, PendingPayment.STATUS_MATCHED)
        }
        cancelNotification(context, pending.id)
        return source
    }

    fun notifyUnconfirmed(context: Context, pending: PendingPayment) {
        if (!canNotify(context)) return // it stays on Home with the same buttons
        ensureChannel(context)

        val open = PendingIntent.getActivity(
            context, requestCode(pending.id, 0),
            Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_payment)
            .setContentTitle("Did your ₹${pending.amountText} to ${pending.payee} go through?")
            .setContentText("We couldn't find a bank SMS or GPay record for it.")
            .setContentIntent(open)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH) // banner on Android 7 and older
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .addAction(0, "Yes, I paid", actionIntent(context, pending.id, ACTION_CONFIRM, 1))
            .addAction(0, "Didn't go through", actionIntent(context, pending.id, ACTION_DISMISS, 2))
            .build()
        @Suppress("MissingPermission") // checked in canNotify()
        NotificationManagerCompat.from(context).notify(notificationId(pending.id), notification)
    }

    fun cancelNotification(context: Context, pendingId: Long) =
        NotificationManagerCompat.from(context).cancel(notificationId(pendingId))

    fun canNotify(context: Context): Boolean =
        NotificationManagerCompat.from(context).areNotificationsEnabled() &&
            (Build.VERSION.SDK_INT < 33 || hasPermission(context, Manifest.permission.POST_NOTIFICATIONS))

    private fun actionIntent(context: Context, id: Long, action: String, slot: Int) =
        PendingIntent.getBroadcast(
            context, requestCode(id, slot),
            Intent(context, PaymentCheckReceiver::class.java).setAction(action).putExtra(EXTRA_ID, id),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

    private fun ensureChannel(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.deleteNotificationChannel(OLD_CHANNEL_ID)
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "Payment checks", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "Asks whether a payment went through when no bank SMS recorded it."
            },
        )
    }

    private fun hasPermission(context: Context, permission: String) =
        ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED

    private fun notificationId(id: Long) = 10_000 + id.toInt()
    private fun requestCode(id: Long, slot: Int) = id.toInt() * 4 + slot
}

class PaymentCheckWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val id = inputData.getLong(PaymentCheck.KEY_ID, -1)
        val attempt = inputData.getInt(PaymentCheck.KEY_ATTEMPT, 1)
        val pending = AppDatabase.get(applicationContext).pendingPaymentDao().get(id) ?: return Result.success()
        if (pending.status != PendingPayment.STATUS_PENDING) return Result.success()

        if (PaymentCheck.tryMatch(applicationContext, pending) != null) return Result.success()
        if (attempt == 1) {
            PaymentCheck.notifyUnconfirmed(applicationContext, pending)
            PaymentCheck.schedule(applicationContext, id, attempt = 2)
        }
        return Result.success()
    }
}

/** Handles the notification's "Yes, I paid" / "Didn't go through" buttons. */
class PaymentCheckReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val id = intent.getLongExtra(PaymentCheck.EXTRA_ID, -1)
        val status = when (intent.action) {
            PaymentCheck.ACTION_CONFIRM -> PendingPayment.STATUS_USER_CONFIRMED
            PaymentCheck.ACTION_DISMISS -> PendingPayment.STATUS_DISMISSED
            else -> return
        }
        val result = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                AppDatabase.get(context).pendingPaymentDao().setStatus(id, status)
                PaymentCheck.cancelNotification(context, id)
            } finally {
                result.finish()
            }
        }
    }
}
