package com.yourteam.expensetracker.insights

/**
 * Plain-code check on AI wording for an insight card, before it may replace the app's own words (AGENTS.md rule 2,
 * DESIGN.md §6). Mirrors the server's guard (server/explain.py `check`), so a wording is shown only if both agree:
 * - every number in the words is one of the card's verified facts (or inside a date label or name),
 * - no cause, prediction or advice words,
 * - short enough for the card.
 */
object InsightWordingCheck {
    const val TITLE_MAX = 70
    const val TEXT_MAX = 240

    private val NUMBER = Regex("""\d[\d,]*(?:\.\d+)?""")
    private val BANNED = Regex(
        """\b(because|due to|caused|driven by|as a result|thanks to|likely|probably|perhaps|maybe|will|going to|expect\w*|""" +
            """forecast\w*|predict\w*|next (?:week|month|year)|should|consider|try|recommend\w*|suggest\w*|advice|advise|""" +
            """cut back|cut down|reduce|save more|saving more|set a budget|festiv\w*|impulse|habit\w*|overspend\w*)\b""",
        RegexOption.IGNORE_CASE,
    )

    /** Allowed only when the app's own insight makes that ranking (golden INS-004: "top" without a full ranking). */
    private val RANKING = Regex("""\b(top|tops|topped|highest|largest|biggest|most|least|lowest|smallest|leading|led|leads)\b""", RegexOption.IGNORE_CASE)

    /** A ranking limited by the insight ("among the listed categories") must stay limited in the title (golden INS-004). */
    private val QUALIFIER = Regex("""\blisted\b""", RegexOption.IGNORE_CASE)

    fun numbers(text: String): List<Double> =
        NUMBER.findAll(text).mapNotNull { it.value.replace(",", "").toDoubleOrNull() }.toList()

    /** Numbers the words may use. */
    fun allowed(f: InsightFacts): Set<Double> = buildSet {
        f.metrics.values.forEach { v ->
            when (v) {
                is Number -> add(kotlin.math.abs(v.toDouble()))
                else -> addAll(numbers(v.toString()))
            }
        }
        addAll(numbers(f.currentPeriod))
        f.comparisonPeriod?.let { addAll(numbers(it)) }
    }

    /** Reasons not to show [title] / [detail]; empty = safe. */
    fun problems(f: InsightFacts, title: String, detail: String): List<String> {
        if (title.isBlank() || detail.isBlank()) return listOf("missing title or explanation")
        val out = mutableListOf<String>()
        val text = "$title $detail"
        val ok = allowed(f)
        if (numbers(text).any { it !in ok }) out += "number not in the facts"
        if (BANNED.containsMatchIn(text)) out += "cause, prediction or advice words"
        val made = RANKING.findAll(f.pattern).map { it.value.lowercase() }.toSet()
        if (RANKING.findAll(text).any { it.value.lowercase() !in made }) out += "ranking claim the insight doesn't make"
        else if (QUALIFIER.containsMatchIn(f.pattern) && RANKING.containsMatchIn(title) && !QUALIFIER.containsMatchIn(title))
            out += "title drops the insight's qualifier"
        if (title.length > TITLE_MAX || detail.length > TEXT_MAX) out += "too long"
        return out
    }
}
