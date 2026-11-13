package com.yourteam.expensetracker.metrics

import android.content.Context

/**
 * Feature 1 capture metrics kept on the phone (MVP M24). Ids and times only: never SMS text, names or amounts.
 *
 * - How a started payment was confirmed: notification button, pop-up over the payment app, inside our app, or
 *   the Scan & Pay result.
 * - How long a bank SMS took to show up in the app after it arrived ("Captured within 1 minute").
 */
object CaptureLog {

    enum class Via { NOTIFICATION, POPUP, APP, SCAN }

    private const val MAX_TIMED = 300
    const val ONE_MINUTE_MS = 60_000L

    private fun prefs(c: Context) = c.applicationContext.getSharedPreferences("capture_log", Context.MODE_PRIVATE)

    /** A "Yes / No" answer for started payment [pendingId]. The latest answer wins. */
    fun answered(c: Context, pendingId: Long, via: Via) {
        if (pendingId <= 0) return // demo payments use negative ids
        prefs(c).edit().putString("via_$pendingId", via.name).apply()
    }

    fun via(c: Context, pendingId: Long): Via? =
        prefs(c).getString("via_$pendingId", null)?.let { runCatching { Via.valueOf(it) }.getOrNull() }

    /**
     * The app has just seen bank SMS [smsId], which arrived at [smsAt]. Only SMS that arrived after this
     * tracking started are timed, so the first read of an old inbox doesn't count as slow.
     */
    fun smsSeen(c: Context, smsId: Long, smsAt: Long, now: Long = System.currentTimeMillis()) {
        val p = prefs(c)
        val since = p.getLong("timing_since", 0L).takeIf { it > 0 } ?: now.also { p.edit().putLong("timing_since", it).apply() }
        if (smsId <= 0 || smsAt < since) return
        val timed = p.getStringSet("timed", emptySet()).orEmpty()
        if (timed.any { it.substringBefore(':') == smsId.toString() }) return
        val delay = (now - smsAt).coerceAtLeast(0)
        val next = (timed + "$smsId:$delay").sortedByDescending { it.substringBefore(':').toLongOrNull() ?: 0 }.take(MAX_TIMED).toSet()
        p.edit().putStringSet("timed", next).apply()
    }

    /** Delays (ms) from a bank SMS arriving to the app seeing it, most recent first. */
    fun smsDelays(c: Context): List<Long> =
        prefs(c).getStringSet("timed", emptySet()).orEmpty().mapNotNull { it.substringAfter(':').toLongOrNull() }
}
