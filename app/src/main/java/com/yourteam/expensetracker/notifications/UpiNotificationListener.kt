package com.yourteam.expensetracker.notifications

import android.app.Notification
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import com.yourteam.expensetracker.data.AppDatabase
import com.yourteam.expensetracker.data.UpiNotification
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * Saves payment notifications from payment apps (and money alerts from any other app) into the
 * on-phone database.
 * Runs only after the user turns on notification access. Never logs notification text
 * (AGENTS.md rule 3).
 */
class UpiNotificationListener : NotificationListenerService() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val dao by lazy { AppDatabase.get(this).upiNotificationDao() }

    override fun onListenerConnected() {
        // Starts the payment list and its auto-categorise pass in this process, so a new alert is
        // categorised (or asked about) even if the app hasn't been opened since the phone started.
        com.yourteam.expensetracker.data.PaymentRepository.get(applicationContext).refresh()
        // Pick up anything still sitting in the notification shade.
        runCatching { activeNotifications }.getOrNull()?.forEach(::save)
        // Without accessibility: watch for the UPI PIN screen opening and closing (Usage Access).
        pinWatch?.cancel()
        pinWatch = scope.launch {
            val power = getSystemService(android.os.PowerManager::class.java)
            while (true) {
                // Every 0.7 s while a UPI app is in front, so we ask within ~2 s of the PIN screen
                // closing; every 2.5 s otherwise; slowly with the screen off.
                val on = power?.isInteractive != false
                val upiInFront = on && runCatching { PinScreenWatcher.tick(applicationContext) }.getOrDefault(false)
                kotlinx.coroutines.delay(if (upiInFront) 700 else if (on) 2_500 else 20_000)
            }
        }
    }

    override fun onListenerDisconnected() {
        pinWatch?.cancel()
        super.onListenerDisconnected()
    }

    private var pinWatch: kotlinx.coroutines.Job? = null

    override fun onNotificationPosted(sbn: StatusBarNotification) = save(sbn)

    private fun save(sbn: StatusBarNotification) {
        if (sbn.packageName == packageName || sbn.packageName in UpiApps.ignored) return
        val n = sbn.notification
        if (n.flags and Notification.FLAG_GROUP_SUMMARY != 0) return
        val known = UpiApps.byPackage[sbn.packageName]
        if (known == null && n.category == Notification.CATEGORY_MESSAGE) return // chats from other apps

        val extras = n.extras
        val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString().orEmpty()
        val text = (extras.getCharSequence(Notification.EXTRA_BIG_TEXT)
            ?: extras.getCharSequence(Notification.EXTRA_TEXT))?.toString().orEmpty()
        val keep = if (known != null) UpiNotificationFilter.isPayment(title, text) else UpiNotificationFilter.isClearPayment(title, text)
        if (!keep) return
        val appName = known ?: runCatching {
            packageManager.getApplicationLabel(packageManager.getApplicationInfo(sbn.packageName, 0)).toString()
        }.getOrDefault("Payment app") // Android 11+ may hide other apps' names

        val entry = UpiNotification(
            packageName = sbn.packageName,
            appName = appName,
            title = title,
            text = text,
            postedAt = if (n.`when` > 0) n.`when` else sbn.postTime,
        )
        scope.launch {
            if (dao.insert(entry) != -1L) com.yourteam.expensetracker.data.PaymentRepository.get(applicationContext) // make sure the pass is running
        }
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }
}
