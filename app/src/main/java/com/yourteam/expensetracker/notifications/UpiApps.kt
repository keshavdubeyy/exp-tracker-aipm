package com.yourteam.expensetracker.notifications

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.core.app.NotificationManagerCompat
import com.yourteam.expensetracker.sms.SenderFilter

/**
 * Payment apps. Notifications from these are kept when they mention an amount. Any other app's
 * notification is kept only if it clearly says money was paid or received ([UpiNotificationFilter]).
 */
object UpiApps {
    val byPackage: Map<String, String> = mapOf(
        "com.google.android.apps.nbu.paisa.user" to "GPay",
        "com.phonepe.app" to "PhonePe",
        "net.one97.paytm" to "Paytm",
        "in.org.npci.upiapp" to "BHIM",
        "com.dreamplug.androidapp" to "CRED",
        "com.naviapp" to "Navi",
        "in.amazon.mShop.android.shopping" to "Amazon Pay",
        "com.mobikwik_new" to "MobiKwik",
        "com.freecharge.android" to "Freecharge",
        "money.super.payments" to "super.money",
        "money.jupiter" to "Jupiter",
        "com.epifi.paisa" to "Fi",
        "indwin.c3.shareapp" to "slice",
        "com.sbi.upi" to "BHIM SBI Pay",
        "com.sbi.lotusintouch" to "YONO SBI",
        "com.csam.icici.bank.imobile" to "iMobile",
        "com.snapwork.hdfc" to "HDFC Bank",
        "com.axis.mobile" to "Axis Mobile",
        "com.msf.kbank.mobile" to "Kotak",
    )

    /**
     * Never read these: chats (anyone can type "paid you ₹500") and SMS apps (we read bank SMS
     * directly, so their copies would count twice).
     */
    val ignored: Set<String> = setOf(
        "com.whatsapp", "com.whatsapp.w4b", "org.telegram.messenger", "com.instagram.android", "com.facebook.orca",
        "com.snapchat.android", "com.google.android.gm", "com.google.android.apps.messaging", "com.android.mms",
        "com.samsung.android.messaging", "com.oneplus.mms", "com.truecaller", "com.android.systemui",
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

/** Which notifications we keep. OTPs and promotions never. */
object UpiNotificationFilter {
    // "₹250", "₹ 1,000", "Rs.250", "Rs 250", "INR 250"
    private val amountRegex = Regex("""(₹|\brs\.?|\binr)\s?\d""", RegexOption.IGNORE_CASE)

    /** A known payment app: any notification with an amount (so the developer screen can show what we skipped). */
    fun isPayment(title: String, text: String): Boolean {
        val all = "$title\n$text"
        return amountRegex.containsMatchIn(all) && !SenderFilter.isOtpOrPromo(all)
    }

    /** Any other app: only if it clearly says money was paid or received. */
    fun isClearPayment(title: String, text: String): Boolean =
        isPayment(title, text) &&
            (com.yourteam.expensetracker.parser.UpiPaidParser.parse(title, text) != null ||
                com.yourteam.expensetracker.parser.UpiReceivedParser.parse(title, text) != null)
}
