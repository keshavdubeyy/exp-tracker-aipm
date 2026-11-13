// Asks our backend's AI for category suggestions (Feature 1 §3.2 step 4, MVP M10).
// The AI only picks categories; it never sees SMS text and never returns a number (AGENTS.md rules 2–3).
package com.yourteam.expensetracker.categoriser

import com.yourteam.expensetracker.BuildConfig
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.time.Instant
import java.time.ZoneId

/** One payee to ask about, plus what we may send (payee, UPI ID, amount, hour, method, past choices). */
data class AiRequest(
    val payeeKey: String,
    val facts: PaymentFacts,
    val categories: List<String>,
    /** The user's confirmed categories, as (payee, category), at most 20. */
    val past: List<Pair<String, String>>,
)

object AiCategoriser {

    /** Off when no server is set in local.properties (`ai.baseUrl`). */
    val enabled: Boolean get() = BuildConfig.AI_BASE_URL.isNotBlank()

    /** At most this many payees per run, so one big import doesn't send a burst of requests. */
    const val MAX_PER_RUN = 10

    /**
     * Payees worth asking about: named, not HIGH (the AI can confirm or beat a shop-word guess), no AI answer yet, not tried this
     * session. Each payee is asked once; the answer is saved on the phone.
     */
    fun pick(
        candidates: List<Pair<PaymentFacts, CategoryGuess>>,
        haveHint: (String) -> Boolean,
        tried: Set<String>,
    ): List<Pair<String, PaymentFacts>> =
        candidates.asSequence()
            .filter { (_, g) -> g.confidence != Confidence.HIGH }
            .mapNotNull { (f, _) -> CategoryMemory.payeeKeys(f.payee, f.vpa).firstOrNull()?.let { it to f } }
            .filter { (key, _) -> !haveHint(key) && key !in tried }
            .distinctBy { it.first }
            .take(MAX_PER_RUN)
            .toList()

    /**
     * Calls POST /categorise. Returns null on any failure: the payment just stays "Add category"
     * (FEEDBACK.md F20, silent fallback). Runs on the caller's background thread.
     */
    fun ask(req: AiRequest): AiHint? = try {
        val body = JSONObject().apply {
            req.facts.payee?.takeIf { '@' !in it }?.let { put("payee", it.take(60)) }
            (req.facts.vpa ?: req.facts.payee?.takeIf { '@' in it })?.let { put("vpa", it.take(80)) }
            put("amount_paise", req.facts.paise.coerceAtLeast(0))
            if (req.facts.at > 0) put("hour", Instant.ofEpochMilli(req.facts.at).atZone(ZoneId.systemDefault()).hour)
            req.facts.method?.let { put("method", it) }
            put("categories", JSONArray(req.categories.take(60)))
            put("past", JSONArray(req.past.take(20).map { (p, c) -> JSONObject().put("payee", p.take(60)).put("category", c.take(40)) }))
        }
        val conn = (URL(BuildConfig.AI_BASE_URL.trimEnd('/') + "/categorise").openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 5_000
            readTimeout = 20_000
            doOutput = true
            setRequestProperty("Content-Type", "application/json")
            if (BuildConfig.AI_TESTER_KEY.isNotBlank()) setRequestProperty("X-Tester-Key", BuildConfig.AI_TESTER_KEY)
        }
        conn.outputStream.use { it.write(body.toString().toByteArray()) }
        if (conn.responseCode != 200) {
            android.util.Log.w("AiCategoriser", "ask failed: HTTP ${conn.responseCode}")
            null
        } else {
            val json = JSONObject(conn.inputStream.bufferedReader().use { it.readText() })
            val list = json.getJSONArray("suggestions").let { a -> (0 until a.length()).map(a::getString) }
                .filter { it in req.categories }.distinct().take(3)
            // An empty list is a real answer ("can't tell from the name"): saved, so the payee isn't asked again
            // and a weak shop word can't take over (golden eval #5, CAT-008). Only a failure returns null.
            AiHint(list, list.isNotEmpty() && json.optBoolean("confident", false))
        }.also { conn.disconnect() }
    } catch (e: Exception) {
        // Error type only: never log the request, it holds a payee name (AGENTS.md rule 3 spirit).
        android.util.Log.w("AiCategoriser", "ask failed: ${e.javaClass.simpleName}")
        null
    }

    /**
     * Calls POST /dedupe with names (and UPI IDs) and the category names. Returns groups of names
     * the AI thinks are the same person / category, or null on any failure. Suggestions only.
     */
    fun askDuplicates(names: List<NameUse>, categories: List<String>): Pair<List<List<String>>, List<List<String>>>? = try {
        val body = JSONObject().apply {
            put("names", JSONArray(names.sortedByDescending { it.uses }.take(80).map { n ->
                JSONObject().put("name", n.name.take(60)).apply { n.vpas.firstOrNull()?.let { put("vpa", it.take(80)) } }
            }))
            put("categories", JSONArray(categories.take(60).map { it.take(40) }))
        }
        val conn = (URL(BuildConfig.AI_BASE_URL.trimEnd('/') + "/dedupe").openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 5_000
            readTimeout = 30_000
            doOutput = true
            setRequestProperty("Content-Type", "application/json")
            if (BuildConfig.AI_TESTER_KEY.isNotBlank()) setRequestProperty("X-Tester-Key", BuildConfig.AI_TESTER_KEY)
        }
        conn.outputStream.use { it.write(body.toString().toByteArray()) }
        if (conn.responseCode != 200) {
            android.util.Log.w("AiCategoriser", "dedupe failed: HTTP ${conn.responseCode}")
            null
        } else {
            val json = JSONObject(conn.inputStream.bufferedReader().use { it.readText() })
            fun groups(key: String): List<List<String>> = json.optJSONArray(key)?.let { a ->
                (0 until a.length()).map { i -> a.getJSONArray(i).let { g -> (0 until g.length()).map(g::getString) } }
            }.orEmpty()
            groups("people") to groups("categories")
        }.also { conn.disconnect() }
    } catch (e: Exception) {
        android.util.Log.w("AiCategoriser", "dedupe failed: ${e.javaClass.simpleName}")
        null
    }
}
