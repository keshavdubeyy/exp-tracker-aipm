package com.yourteam.expensetracker.metrics

import android.content.Context
import java.security.SecureRandom
import java.time.LocalDate

/**
 * What this phone remembers for the tester metrics (MVP M24). Counts and dates only: never SMS
 * text, payee names or amounts. The tester ID is a one-way hash made here ([installId]), not linked
 * to the phone number, Google account or any person.
 */
object UsageLog {
    private fun prefs(c: Context) = c.getSharedPreferences("usage_log", Context.MODE_PRIVATE)

    /**
     * Tester ID: a one-way hash of Android's ID for this app on this phone, so reinstalling or clearing app data
     * keeps the same tester on the dashboard (no duplicates). Android's ID is per app and per phone, not the phone
     * number or Google account, and the hash can't be turned back into it. Falls back to a random ID when the
     * phone gives none.
     */
    fun installId(c: Context): String = deviceId(c) ?: randomId(c)

    /** The random ID this phone used before tester IDs came from the device. Sent once so the server merges it. */
    fun oldId(c: Context): String? = prefs(c).getString("install_id", null)?.takeIf { it != installId(c) }

    /** Called after the server merged [oldId] into the current ID. */
    fun forgetOldId(c: Context) = prefs(c).edit().remove("install_id").apply()

    private fun deviceId(c: Context): String? {
        val raw = android.provider.Settings.Secure.getString(c.contentResolver, android.provider.Settings.Secure.ANDROID_ID)
        if (raw.isNullOrBlank() || raw == "9774d56d682e549c") return null // known shared value on some old phones
        val hash = java.security.MessageDigest.getInstance("SHA-256").digest("expense-tracker-tester:$raw".toByteArray())
        return hash.joinToString("") { "%02x".format(it) }.take(32)
    }

    private fun randomId(c: Context): String = prefs(c).getString("install_id", null) ?: run {
        val bytes = ByteArray(16).also { SecureRandom().nextBytes(it) }
        bytes.joinToString("") { "%02x".format(it) }.also { prefs(c).edit().putString("install_id", it).apply() }
    }

    // ---- Consent: nothing is sent until the tester says yes ----

    /**
     * Wording the tester agreed to. 1 = counts only ("never amounts"). 2 = counts plus one all-time total of the
     * money tracked. A tester who agreed to 1 is asked again, and the total is sent only after they agree to 2.
     */
    const val CONSENT_VERSION = 2

    /** null = not asked yet. */
    fun consent(c: Context): Boolean? = if (prefs(c).contains("consent")) prefs(c).getBoolean("consent", false) else null
    fun setConsent(c: Context, on: Boolean) = prefs(c).edit().putBoolean("consent", on).putInt("consent_v", CONSENT_VERSION).apply()

    /** Show the consent question: never asked, or agreed to older wording. */
    fun needsAsk(c: Context): Boolean = consent(c) == null || (consent(c) == true && prefs(c).getInt("consent_v", 1) < CONSENT_VERSION)

    /** The tester agreed to wording that includes the total tracked. */
    fun totalAllowed(c: Context): Boolean = consent(c) == true && prefs(c).getInt("consent_v", 1) >= 2

    // ---- Days the app / Insights were opened (last 60 kept) ----

    fun appOpened(c: Context) = addDay(c, "open_days")
    fun insightsOpened(c: Context) = addDay(c, "insights_days")
    fun openDaysIn(c: Context, last: Int) = daysIn(c, "open_days", last)
    fun insightsDaysIn(c: Context, last: Int) = daysIn(c, "insights_days", last)

    private fun addDay(c: Context, key: String) {
        val today = LocalDate.now()
        val set = prefs(c).getStringSet(key, emptySet()).orEmpty()
        if (today.toString() in set) return
        val kept = (set + today.toString()).filter { runCatching { LocalDate.parse(it) }.getOrNull()?.isAfter(today.minusDays(60)) == true }
        prefs(c).edit().putStringSet(key, kept.toSet()).apply()
    }

    private fun daysIn(c: Context, key: String, last: Int): Int {
        val from = LocalDate.now().minusDays(last.toLong() - 1)
        return prefs(c).getStringSet(key, emptySet()).orEmpty().count { d -> runCatching { LocalDate.parse(d) }.getOrNull()?.let { !it.isBefore(from) } == true }
    }

    // ---- Onboarding timing ----

    fun onboardingStarted(c: Context) {
        if (!prefs(c).contains("onboarding_started_at")) prefs(c).edit().putLong("onboarding_started_at", System.currentTimeMillis()).apply()
    }

    fun onboardingDone(c: Context) {
        if (!prefs(c).contains("onboarding_done_at")) prefs(c).edit().putLong("onboarding_done_at", System.currentTimeMillis()).apply()
    }

    /** Seconds from Get started to Home; null if either end wasn't seen on this install. */
    fun onboardingSeconds(c: Context): Long? {
        val a = prefs(c).getLong("onboarding_started_at", 0)
        val b = prefs(c).getLong("onboarding_done_at", 0)
        return if (a > 0 && b >= a) (b - a) / 1000 else null
    }

    // ---- Upload status, for the Developer screen ----

    fun lastUpload(c: Context): Long = prefs(c).getLong("last_upload", 0)
    fun lastResult(c: Context): String = prefs(c).getString("last_result", "Not sent yet").orEmpty()
    fun recordUpload(c: Context, ok: Boolean, result: String) {
        prefs(c).edit().putString("last_result", result).apply { if (ok) putLong("last_upload", System.currentTimeMillis()) }.apply()
    }
}
