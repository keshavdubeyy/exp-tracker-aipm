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
 * Saves payment notifications from UPI apps into the on-phone database.
 * Runs only after the user turns on notification access. Never logs notification text
 * (AGENTS.md rule 3).
 */
class UpiNotificationListener : NotificationListenerService() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val dao by lazy { AppDatabase.get(this).upiNotificationDao() }

    override fun onListenerConnected() {
        // Pick up anything still sitting in the notification shade.
        runCatching { activeNotifications }.getOrNull()?.forEach(::save)
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) = save(sbn)

    private fun save(sbn: StatusBarNotification) {
        val appName = UpiApps.byPackage[sbn.packageName] ?: return
        val n = sbn.notification
        if (n.flags and Notification.FLAG_GROUP_SUMMARY != 0) return

        val extras = n.extras
        val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString().orEmpty()
        val text = (extras.getCharSequence(Notification.EXTRA_BIG_TEXT)
            ?: extras.getCharSequence(Notification.EXTRA_TEXT))?.toString().orEmpty()
        if (!UpiNotificationFilter.isPayment(title, text)) return

        val entry = UpiNotification(
            packageName = sbn.packageName,
            appName = appName,
            title = title,
            text = text,
            postedAt = if (n.`when` > 0) n.`when` else sbn.postTime,
        )
        scope.launch { dao.insert(entry) }
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }
}
