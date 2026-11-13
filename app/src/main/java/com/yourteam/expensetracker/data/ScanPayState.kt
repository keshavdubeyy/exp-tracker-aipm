package com.yourteam.expensetracker.data

import android.content.Context

/**
 * The Scan & Pay payment handed to a UPI app and not settled yet (M7). Kept on disk so that if
 * the user goes Home from GPay and opens us later, we can still ask about it once.
 */
object ScanPayState {
    data class Current(
        val id: Long, val payee: String, val amountText: String, val startedAt: Long,
        val app: String = "", val category: String? = null,
    )

    private const val PREFS = "scan_pay"

    fun set(context: Context, c: Current) = prefs(context).edit()
        .putLong("id", c.id).putString("payee", c.payee).putString("amount", c.amountText).putLong("at", c.startedAt)
        .putString("appName", c.app).putString("category", c.category).apply()

    fun current(context: Context): Current? {
        val p = prefs(context)
        val id = p.getLong("id", -1L).takeIf { it > 0 } ?: return null
        val at = p.getLong("at", 0)
        if (System.currentTimeMillis() - at > 24 * 3_600_000L) { clear(context); return null } // stale
        return Current(
            id, p.getString("payee", "").orEmpty(), p.getString("amount", "").orEmpty(), at,
            p.getString("appName", "").orEmpty(), p.getString("category", null),
        )
    }

    /** Removes the payment in progress; keeps the chosen UPI app. */
    fun clear(context: Context) = prefs(context).edit().remove("id").remove("payee").remove("amount").remove("at").remove("appName").remove("category").apply()

    /** The UPI app the user picked last time (package name), offered first next time. */
    fun lastApp(context: Context): String? = prefs(context).getString("app", null)
    fun setLastApp(context: Context, pkg: String) = prefs(context).edit().putString("app", pkg).apply()

    /** True for 10 minutes after handing off, so the old PIN-screen reader doesn't record it twice. */
    fun handingOff(context: Context): Boolean =
        current(context)?.let { System.currentTimeMillis() - it.startedAt < 10 * 60_000 } ?: false

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}
