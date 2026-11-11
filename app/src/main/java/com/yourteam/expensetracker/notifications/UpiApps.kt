package com.yourteam.expensetracker.notifications

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.core.app.NotificationManagerCompat
import com.yourteam.expensetracker.sms.SenderFilter

/** UPI apps whose notifications we keep. Everything else is ignored. */
object UpiApps {
    val byPackage: Map<String, String> = mapOf(
        "com.google.android.apps.nbu.paisa.user" to "GPay",
        "com.phonepe.app" to "PhonePe",
        "net.one97.paytm" to "Paytm",
        "in.org.npci.upiapp" to "BHIM",
        "com.dreamplug.androidapp" to "CRED",
    )

    fun isNotificationAccessOn(context: Context): Boolean =
        context.packageName in NotificationManagerCompat.getEnabledListenerPackages(context)

    /** Opens the app's notification-access toggle (Android 11+), or the general list. */
    fun notificationAccessIntent(context: Context): Intent {
        val intent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Intent(Settings.ACTION_NOTIFICATION_LISTENER_DETAIL_SETTINGS).putExtra(
                Settings.EXTRA_NOTIFICATION_LISTENER_COMPONENT_NAME,
                ComponentName(context, UpiNotificationListener::class.java).flattenToString(),
            )
        } else {
            Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
        }
        return intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
}

/** Keeps notifications that mention an amount and aren't OTPs or promotions. */
object UpiNotificationFilter {
    // "₹250", "₹ 1,000", "Rs.250", "Rs 250", "INR 250"
    private val amountRegex = Regex("""(₹|\brs\.?|\binr)\s?\d""", RegexOption.IGNORE_CASE)

    fun isPayment(title: String, text: String): Boolean {
        val all = "$title\n$text"
        return amountRegex.containsMatchIn(all) && !SenderFilter.isOtpOrPromo(all)
    }
}
