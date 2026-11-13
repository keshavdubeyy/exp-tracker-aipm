package com.yourteam.expensetracker.notifications

import android.content.Context

/**
 * The last time each UPI app showed a keypad screen: was it recognised as the PIN screen?
 * Yes/no and counts only, never the screen's words. Shown on the Developer screen.
 */
object PinScreenCheck {
    private const val PREFS = "pin_screen_check"

    fun record(context: Context, app: String, result: String) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString(app, "${System.currentTimeMillis()}|$result").apply()
    }

    /** App name to (time, result), newest first. */
    fun all(context: Context): List<Triple<String, Long, String>> =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).all.mapNotNull { (app, v) ->
            val s = v as? String ?: return@mapNotNull null
            Triple(app, s.substringBefore('|').toLongOrNull() ?: 0L, s.substringAfter('|'))
        }.sortedByDescending { it.second }
}
