package com.yourteam.expensetracker.ui.screens.insights

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** How the user rated an insight. A rated insight is never shown again (design §5, in13). */
enum class InsightFeedback { HELPFUL, NOT_RELEVANT, DISMISSED }

/**
 * One place for the ratings the user has given insights, so the main screen and the insight
 * drill-down always agree and a dismissed card stays gone across both. Kept on the phone only.
 */
class InsightFeedbackStore private constructor(private val app: Context) {
    private val prefs = app.getSharedPreferences("insight_feedback", Context.MODE_PRIVATE)
    private val _state = MutableStateFlow(load())
    val state: StateFlow<Map<String, InsightFeedback>> = _state.asStateFlow()

    fun rate(id: String, rating: InsightFeedback) {
        prefs.edit().putString(id, rating.name).apply()
        com.yourteam.expensetracker.metrics.InsightLog.rated(app, id, rating) // M24: counts per insight kind
        _state.value = _state.value + (id to rating)
    }

    /** Rate [card] (design §5), confirm with the message, and offer Undo (FEEDBACK.md). */
    fun rateAndConfirm(card: com.yourteam.expensetracker.insights.InsightCard, rating: InsightFeedback) {
        rate(card.id, rating)
        val message = when (rating) {
            InsightFeedback.DISMISSED -> "Hidden. We won't show “${card.title}” again."
            InsightFeedback.NOT_RELEVANT -> "Thanks. We'll show this one less."
            InsightFeedback.HELPFUL -> "Glad it helped."
        }
        com.yourteam.expensetracker.ui.feedback.Feedback.success(message) { clear(card.id) }
    }

    fun clear(id: String) {
        prefs.edit().remove(id).apply()
        com.yourteam.expensetracker.metrics.InsightLog.rated(app, id, null)
        _state.value = _state.value - id
    }

    private fun load(): Map<String, InsightFeedback> = prefs.all.mapNotNull { (k, v) ->
        (v as? String)?.let { runCatching { InsightFeedback.valueOf(it) }.getOrNull() }?.let { k to it }
    }.toMap()

    companion object {
        @Volatile private var instance: InsightFeedbackStore? = null
        fun get(context: Context): InsightFeedbackStore =
            instance ?: synchronized(this) { instance ?: InsightFeedbackStore(context.applicationContext).also { instance = it } }
    }
}
