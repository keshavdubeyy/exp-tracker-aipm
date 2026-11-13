package com.yourteam.expensetracker.ui.screens.onboarding

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.core.content.ContextCompat

/**
 * What onboarding remembers between launches (ONBOARDING.md §4). SharedPreferences rather than
 * DataStore: the app has no DataStore yet and these are a handful of flags read once at start.
 */
object Onboarding {
    private fun prefs(c: Context) = c.getSharedPreferences("onboarding", Context.MODE_PRIVATE)

    fun isComplete(c: Context) = prefs(c).getBoolean("onboarding_complete", false)
    fun markComplete(c: Context) {
        prefs(c).edit().putBoolean("onboarding_complete", true).apply()
        com.yourteam.expensetracker.metrics.UsageLog.onboardingDone(c) // M24: time to Home
    }

    /** Got past Welcome once, so a restart without SMS resumes at the SMS step. */
    fun isStarted(c: Context) = prefs(c).getBoolean("started", false)
    fun markStarted(c: Context) {
        prefs(c).edit().putBoolean("started", true).apply()
        com.yourteam.expensetracker.metrics.UsageLog.onboardingStarted(c) // M24: time to Home
    }

    fun isImportDone(c: Context) = prefs(c).getBoolean("import_done", false)
    fun markImportDone(c: Context) =
        prefs(c).edit().putBoolean("import_done", true).putLong("import_last_run_at", System.currentTimeMillis()).apply()

    /** ob3b "Sort past payments" finished or skipped. */
    fun isSortDone(c: Context) = prefs(c).getBoolean("sort_done", false)
    fun markSortDone(c: Context) = prefs(c).edit().putBoolean("sort_done", true).apply()

    fun isNotifAsked(c: Context) = prefs(c).getBoolean("notif_asked", false)
    fun markNotifAsked(c: Context) = prefs(c).edit().putBoolean("notif_asked", true).apply()

    /** "gpay" or "listener": the user went to Settings for it at least once. */
    fun wasAttempted(c: Context, kind: String) = prefs(c).getBoolean("extras_attempted_$kind", false)
    fun markAttempted(c: Context, kind: String) = prefs(c).edit().putBoolean("extras_attempted_$kind", true).apply()

    /** Analytics only; permanent denial is detected with the rationale check, not this count. */
    fun countSmsDenied(c: Context) = prefs(c).run { edit().putInt("sms_denied_count", getInt("sms_denied_count", 0) + 1).apply() }

    /**
     * Both are needed: READ_SMS reads the inbox, RECEIVE_SMS wakes the app the moment a bank SMS arrives. With only
     * READ_SMS a payment is picked up only when the app is next opened, so no "saved" / "what was it for?" notification.
     */
    val SMS_PERMISSIONS = arrayOf(Manifest.permission.READ_SMS, Manifest.permission.RECEIVE_SMS)

    fun hasSms(c: Context) = SMS_PERMISSIONS.all { ContextCompat.checkSelfPermission(c, it) == PackageManager.PERMISSION_GRANTED }
}

internal tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

internal fun appDetailsIntent(c: Context) =
    Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", c.packageName, null))
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
