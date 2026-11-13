package com.yourteam.expensetracker

import android.app.Activity
import android.content.Context
import android.util.Log
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.appdistribution.FirebaseAppDistribution
import com.google.firebase.appdistribution.FirebaseAppDistributionException
import com.yourteam.expensetracker.ui.screens.onboarding.Onboarding

/**
 * Tester builds only (TESTING.md): when a newer build is on Firebase App Distribution, Firebase shows
 * "New version available" and installs it in place. The first time, the tester signs in with the Google
 * account that was invited. In builds without the full SDK (debug, release) the call does nothing.
 */
object AppUpdates {
    private const val TAG = "AppUpdates"
    /** At most one check per this long, so a tester who says "Not now" isn't asked on every open. */
    private const val MIN_GAP_MS = 6 * 60 * 60 * 1000L

    private val configured: Boolean
        get() = BuildConfig.FIREBASE_APP_ID.isNotBlank() && BuildConfig.FIREBASE_API_KEY.isNotBlank() && BuildConfig.FIREBASE_PROJECT_ID.isNotBlank()

    fun checkOnOpen(activity: Activity) {
        if (!BuildConfig.TEST_BUILD || !configured) return
        if (!Onboarding.isComplete(activity)) return // never interrupt a first run
        val prefs = activity.getSharedPreferences("app_updates", Context.MODE_PRIVATE)
        val now = System.currentTimeMillis()
        if (now - prefs.getLong("last_check", 0L) < MIN_GAP_MS) return
        prefs.edit().putLong("last_check", now).apply()
        try {
            ensureFirebase(activity.applicationContext)
            FirebaseAppDistribution.getInstance().updateIfNewReleaseAvailable()
                .addOnFailureListener { e ->
                    val why = (e as? FirebaseAppDistributionException)?.errorCode?.name ?: e.javaClass.simpleName
                    Log.w(TAG, "update check: $why") // e.g. NOT_IMPLEMENTED in debug, AUTHENTICATION_CANCELED
                }
        } catch (e: Exception) {
            Log.w(TAG, "update check failed: ${e.javaClass.simpleName}")
        }
    }

    /** No google-services.json: Firebase is set up from local.properties values instead. */
    private fun ensureFirebase(c: Context) {
        if (FirebaseApp.getApps(c).isNotEmpty()) return
        FirebaseApp.initializeApp(
            c,
            FirebaseOptions.Builder()
                .setApplicationId(BuildConfig.FIREBASE_APP_ID)
                .setApiKey(BuildConfig.FIREBASE_API_KEY)
                .setProjectId(BuildConfig.FIREBASE_PROJECT_ID)
                .build(),
        )
    }
}
