package com.yourteam.expensetracker.metrics

import android.content.Context
import com.yourteam.expensetracker.insights.InsightCard
import com.yourteam.expensetracker.insights.InsightType
import com.yourteam.expensetracker.ui.screens.insights.InsightFeedback
import java.time.LocalDate

/**
 * What happened to each Insights card on this phone (Feature 2, M16/M24): shown, opened, rated. Card IDs (which
 * can hold a category or payee name) stay here; only counts per insight kind are uploaded. Every count is tied to
 * the day the card was first shown, so "opened" and "rated" are always a subset of "shown".
 */
object InsightLog {
    private fun prefs(c: Context) = c.getSharedPreferences("insight_log", Context.MODE_PRIVATE)
    private const val KEEP_DAYS = 60L

    /** Upload key for each kind. Must match InsightKind in server/metrics.py. */
    val InsightType.key: String
        get() = when (this) {
            InsightType.CATEGORY_CHANGE -> "change"
            InsightType.CATEGORY_SHARE -> "share"
            InsightType.REPEAT_MERCHANT -> "repeat"
            InsightType.WEEKEND_WEEKDAY -> "weekend"
            InsightType.DAY_CONCENTRATION -> "concentration"
        }

    /** A card was drawn on the Insights screen. Also marks today as a day Insights had something to show. */
    fun shown(c: Context, card: InsightCard, today: LocalDate = LocalDate.now()) {
        val p = prefs(c)
        val days = p.getStringSet("card_days", emptySet()).orEmpty()
        val e = p.edit()
        if (today.toString() !in days) e.putStringSet("card_days", keep(days + today.toString(), today))
        val was = p.getString("s:${card.id}", null)
        // "TYPE|day" or "TYPE|day|ai" once the card has been shown in AI words (Feature 2).
        if (was == null) e.putString("s:${card.id}", "${card.type.name}|${today.toEpochDay()}" + if (card.aiWritten) "|ai" else "")
        else if (card.aiWritten && !was.endsWith("|ai")) e.putString("s:${card.id}", "$was|ai")
        e.apply()
        prune(c, today)
    }

    /** The card's detail page was opened. */
    fun opened(c: Context, cardId: String) {
        if (prefs(c).contains("s:$cardId")) prefs(c).edit().putBoolean("o:$cardId", true).apply()
    }

    /** The user rated the card; null = rating undone. */
    fun rated(c: Context, cardId: String, rating: InsightFeedback?) {
        if (!prefs(c).contains("s:$cardId")) return
        prefs(c).edit().apply { if (rating == null) remove("r:$cardId") else putString("r:$cardId", rating.name) }.apply()
    }

    data class Kind(
        var shown: Int = 0, var opened: Int = 0, var helpful: Int = 0, var notRelevant: Int = 0, var dismissed: Int = 0,
        /** The same, for cards shown in AI words. */
        var aiShown: Int = 0, var aiHelpful: Int = 0, var aiRated: Int = 0, var aiNegative: Int = 0,
    ) {
        val rated get() = helpful + notRelevant + dismissed
    }

    /** Cards first shown in the last [last] days, by kind. */
    fun byKind(c: Context, last: Int = 30, today: LocalDate = LocalDate.now()): Map<InsightType, Kind> {
        val from = today.minusDays(last.toLong() - 1).toEpochDay()
        val all = prefs(c).all
        val out = linkedMapOf<InsightType, Kind>()
        all.forEach { (k, v) ->
            if (!k.startsWith("s:")) return@forEach
            val parts = (v as? String)?.split("|")?.takeIf { it.size >= 2 } ?: return@forEach
            val (type, day) = parts
            val ai = parts.getOrNull(2) == "ai"
            if ((day.toLongOrNull() ?: return@forEach) < from) return@forEach
            val t = runCatching { InsightType.valueOf(type) }.getOrNull() ?: return@forEach
            val id = k.removePrefix("s:")
            val kind = out.getOrPut(t) { Kind() }
            kind.shown++
            if (all["o:$id"] == true) kind.opened++
            val rating = all["r:$id"]
            when (rating) {
                InsightFeedback.HELPFUL.name -> kind.helpful++
                InsightFeedback.NOT_RELEVANT.name -> kind.notRelevant++
                InsightFeedback.DISMISSED.name -> kind.dismissed++
            }
            if (ai) {
                kind.aiShown++
                if (rating != null) kind.aiRated++
                if (rating == InsightFeedback.HELPFUL.name) kind.aiHelpful++
                if (rating == InsightFeedback.NOT_RELEVANT.name || rating == InsightFeedback.DISMISSED.name) kind.aiNegative++
            }
        }
        return out
    }

    /** Days in the last [last] on which Insights showed at least one card. */
    fun cardDaysIn(c: Context, last: Int, today: LocalDate = LocalDate.now()): Int {
        val from = today.minusDays(last.toLong() - 1)
        return prefs(c).getStringSet("card_days", emptySet()).orEmpty()
            .count { d -> runCatching { LocalDate.parse(d) }.getOrNull()?.let { !it.isBefore(from) } == true }
    }

    private fun keep(days: Set<String>, today: LocalDate) =
        days.filter { runCatching { LocalDate.parse(it) }.getOrNull()?.isAfter(today.minusDays(KEEP_DAYS)) == true }.toSet()

    private fun prune(c: Context, today: LocalDate) {
        val cutoff = today.minusDays(KEEP_DAYS).toEpochDay()
        val all = prefs(c).all
        val old = all.filter { (k, v) -> k.startsWith("s:") && ((v as? String)?.substringAfter('|')?.toLongOrNull() ?: 0) < cutoff }.keys
        if (old.isEmpty()) return
        prefs(c).edit().apply {
            old.forEach { k -> val id = k.removePrefix("s:"); remove(k); remove("o:$id"); remove("r:$id") }
        }.apply()
    }
}
