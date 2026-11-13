package com.yourteam.expensetracker.data

import android.content.Context
import com.yourteam.expensetracker.BuildConfig
import com.yourteam.expensetracker.insights.InsightCard
import com.yourteam.expensetracker.insights.InsightFacts
import com.yourteam.expensetracker.insights.InsightWordingCheck
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

/**
 * Feature 2: asks the backend's AI (POST /explain) to word an insight card from facts the app worked out.
 * The app keeps its own wording until an answer arrives, and whenever the AI says "no insight", the server's guard
 * or [InsightWordingCheck] rejects it, or the switch (Developer screen) is off.
 *
 * Each insight is asked once: the wording is saved on the phone under a fingerprint of its facts, so the same insight
 * reuses it and only a real change in the numbers asks again (golden case INS-013, no duplicate cards).
 */
class InsightWriter private constructor(private val app: Context) {
    private val prefs = app.getSharedPreferences("insight_words", Context.MODE_PRIVATE)
    private val tried = mutableSetOf<String>()
    private val _version = MutableStateFlow(0)
    /** Bumps whenever a new wording is saved, so screens re-apply it. */
    val version: StateFlow<Int> = _version.asStateFlow()

    val available: Boolean get() = BuildConfig.AI_BASE_URL.isNotBlank()
    fun isOn(): Boolean = available && prefs.getBoolean(SWITCH, true)
    fun setOn(on: Boolean) {
        prefs.edit().putBoolean(SWITCH, on).apply()
        _version.value++
    }

    /** [card] with the AI's words when there are safe ones saved, else unchanged. */
    fun apply(card: InsightCard): InsightCard {
        val f = card.facts ?: return card
        if (!isOn()) return card
        val saved = prefs.getString(fingerprint(f), null)?.let { runCatching { JSONObject(it) }.getOrNull() } ?: return card
        if (saved.optString("status") != "generate_explanation") return card
        val title = saved.optString("title")
        val detail = saved.optString("explanation")
        if (InsightWordingCheck.problems(f, title, detail).isNotEmpty()) return card
        return card.copy(title = title, detail = detail, aiWritten = true)
    }

    /** Asks for wording for [cards] that have none saved yet. Failures are silent: the app's words stay. */
    suspend fun fetch(cards: List<InsightCard>) = withContext(Dispatchers.IO) {
        if (!isOn()) return@withContext
        var saved = 0
        cards.mapNotNull { it.facts }.forEach { f ->
            val fp = fingerprint(f)
            if (prefs.contains(fp) || !tried.add(fp)) return@forEach
            ask(f, fp)?.let { answer ->
                prefs.edit().putString(fp, answer.put("at", System.currentTimeMillis()).toString()).apply()
                saved++
            }
        }
        if (saved > 0) {
            prune()
            _version.value++
        }
    }

    private fun ask(f: InsightFacts, fp: String): JSONObject? = try {
        val conn = (URL(BuildConfig.AI_BASE_URL.trimEnd('/') + "/explain").openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 5_000
            readTimeout = 25_000
            doOutput = true
            setRequestProperty("Content-Type", "application/json")
            if (BuildConfig.AI_TESTER_KEY.isNotBlank()) setRequestProperty("X-Tester-Key", BuildConfig.AI_TESTER_KEY)
        }
        conn.outputStream.use { it.write(request(f, fp).toString().toByteArray()) }
        if (conn.responseCode != 200) {
            android.util.Log.w("InsightWriter", "explain failed: HTTP ${conn.responseCode}")
            null
        } else {
            val json = JSONObject(conn.inputStream.bufferedReader().use { it.readText() })
            JSONObject().put("status", json.optString("status"))
                .put("title", json.optString("title").takeIf { !json.isNull("title") } ?: "")
                .put("explanation", json.optString("explanation").takeIf { !json.isNull("explanation") } ?: "")
        }.also { conn.disconnect() }
    } catch (e: Exception) {
        // Error type only: the request holds category and payee names.
        android.util.Log.w("InsightWriter", "explain failed: ${e.javaClass.simpleName}")
        null
    }

    private fun prune() {
        val all = prefs.all.filterKeys { it != SWITCH }
        if (all.size <= KEEP) return
        val oldest = all.entries.sortedBy { (it.value as? String)?.let { v -> runCatching { JSONObject(v).optLong("at") }.getOrNull() } ?: 0L }
            .take(all.size - KEEP).map { it.key }
        prefs.edit().apply { oldest.forEach(::remove) }.apply()
    }

    companion object {
        private const val SWITCH = "switch_on"
        private const val KEEP = 300
        private val CONSTRAINTS = listOf(
            "short_title", "plain_language_explanation", "reference_only_supplied_metric_and_evidence_keys",
            "no_new_financial_numbers", "no_unsupported_causal_or_predictive_claims",
        )

        /** The golden dataset's Feature 2 input, field for field (server/explain.py ExplainRequest). */
        fun request(f: InsightFacts, fp: String): JSONObject = JSONObject().apply {
            put("approved_insight_type", f.insightType)
            put("verified_metrics", JSONObject().apply {
                f.metrics.forEach { (k, v) -> put(k, if (v is Number) v else v.toString().take(60)) }
            })
            put("period_and_comparison_labels", JSONObject().put("current_period", f.currentPeriod.take(60)).apply {
                f.comparisonPeriod?.let { put("comparison_period", it.take(60)) }
            })
            put("approved_pattern_candidate", JSONObject().put("candidate_id", fp).put("pattern", f.pattern.take(200)).put("fingerprint", fp))
            put("validated_evidence_facts_or_references", JSONObject()
                .put("metric_keys", JSONArray(f.metrics.keys.toList()))
                .put("evidence_refs", JSONArray((1..f.evidenceCount.coerceIn(0, 40)).map { "E$it" })))
            put("output_constraints", JSONArray(CONSTRAINTS))
        }

        /** Same facts = same fingerprint = the saved wording is reused. */
        fun fingerprint(f: InsightFacts): String {
            val text = listOf(f.insightType, f.metrics.entries.joinToString("|") { "${it.key}=${it.value}" },
                f.currentPeriod, f.comparisonPeriod.orEmpty(), f.pattern).joinToString("\n")
            return "w-" + MessageDigest.getInstance("SHA-256").digest(text.toByteArray()).joinToString("") { "%02x".format(it) }.take(24)
        }

        @Volatile private var instance: InsightWriter? = null
        fun get(context: Context): InsightWriter =
            instance ?: synchronized(this) { instance ?: InsightWriter(context.applicationContext).also { instance = it } }
    }
}
