// Feature 1 §7 success metrics. Counts and percentages of payments only, never money (AGENTS.md rule 1).
package com.yourteam.expensetracker.categoriser

/**
 * What happened to one payment's category: how the app first placed it, and what the user did.
 * Kept on the phone; no payee names or amounts.
 */
data class CategoryRecord(
    /** How it was placed the first time the app saw it. */
    val firstBasis: Basis,
    /** True if the app categorised it on its own the first time (HIGH). */
    val firstAuto: Boolean,
    val firstCategory: String?,
    /** Where the user's first pick sat in the suggestions: 1–3, 0 = not suggested, null = never picked. */
    val pickedRank: Int? = null,
    /** True once the user changed a category the app had chosen on its own. */
    val corrected: Boolean = false,
    /**
     * Which step made the guesses the user saw when they picked (set with [pickedRank]). Not [firstBasis]: the
     * record is made when the payment appears, before the AI's answer arrives a few seconds later.
     */
    val pickBasis: Basis? = null,
) {
    fun encode(): String = listOf(
        firstBasis.name, if (firstAuto) "1" else "0", firstCategory.orEmpty(), pickedRank?.toString().orEmpty(), if (corrected) "1" else "0",
        pickBasis?.name.orEmpty(),
    ).joinToString("\u0001")

    companion object {
        fun decode(s: String): CategoryRecord? = s.split('\u0001').takeIf { it.size == 5 || it.size == 6 }?.let { p ->
            CategoryRecord(
                firstBasis = runCatching { Basis.valueOf(p[0]) }.getOrDefault(Basis.NONE),
                firstAuto = p[1] == "1",
                firstCategory = p[2].ifEmpty { null },
                pickedRank = p[3].toIntOrNull(),
                corrected = p[4] == "1",
                pickBasis = p.getOrNull(5)?.takeIf { it.isNotEmpty() }?.let { b -> runCatching { Basis.valueOf(b) }.getOrNull() },
            )
        }
    }
}

/** One metric: value as a whole-number percent (null when nothing to measure yet), plus its n. */
data class Metric(val name: String, val meaning: String, val percent: Int?, val count: Int, val target: String)

object CategoryMetrics {

    /** Update when the user saves a category for a payment. */
    fun onUserPick(record: CategoryRecord, picked: String, suggestions: List<String>, basis: Basis? = null): CategoryRecord = when {
        record.firstAuto && picked != record.firstCategory -> record.copy(corrected = true)
        !record.firstAuto && record.pickedRank == null ->
            record.copy(pickedRank = suggestions.indexOf(picked).let { if (it in 0..2) it + 1 else 0 }, pickBasis = basis)
        else -> record
    }

    /**
     * The AI's pick was filed after the record was made (the AI answers a few seconds later). Until the user has
     * picked something, the payment now counts as categorised automatically by the AI, so a later change is a correction.
     */
    fun onAiFiled(record: CategoryRecord, now: CategoryGuess): CategoryRecord =
        if (!record.firstAuto && record.pickedRank == null && now.basis == Basis.AI && now.category != null)
            record.copy(firstBasis = Basis.AI, firstAuto = true, firstCategory = now.category)
        else record

    private fun pct(part: Int, whole: Int): Int? = if (whole == 0) null else Math.round(part * 100.0 / whole).toInt()

    /**
     * The six metrics in Feature 1 §7. "Accuracy" uses the user's behaviour as the judge: an
     * automatic category the user never changed counts as correct. The golden-set eval (M20)
     * gives the strict figure; this one shows real use.
     */
    /** Raw counts behind [compute], for the daily upload (MVP M24). The server pools them across testers. */
    fun counts(records: Collection<CategoryRecord>): Map<String, Int> {
        val auto = records.filter { it.firstAuto }
        val learned = records.filter { it.firstBasis == Basis.YOUR_DEFAULT }
        val asked = records.filter { !it.firstAuto }
        val picked = asked.filter { it.pickedRank != null }
        val aiPicked = picked.filter { it.pickBasis == Basis.AI } // the AI made the guesses the user picked from
        return mapOf(
            "cat_all" to records.size,
            "cat_auto" to auto.size,
            "cat_auto_kept" to auto.count { !it.corrected },
            "cat_learned" to learned.size,
            "cat_learned_kept" to learned.count { !it.corrected },
            "cat_asked" to asked.size,
            "cat_picked" to picked.size,
            "cat_picked_top3" to picked.count { it.pickedRank in 1..3 },
            "cat_ai_picked" to aiPicked.size,
            "cat_ai_top3" to aiPicked.count { it.pickedRank in 1..3 },
            "cat_ai_first" to aiPicked.count { it.pickedRank == 1 },
            "cat_ai_auto" to auto.count { it.firstBasis == Basis.AI },
            "cat_ai_auto_kept" to auto.count { it.firstBasis == Basis.AI && !it.corrected },
        )
    }

    /**
     * Payments already on the phone when the app was installed (dated before [installedAt]), and how many of them
     * have a category now: on their own (merchant map, shop words, AI) or because the user picked. [payments] is
     * each counted payment's time and its current guess. Counts only, for the team dashboard (MVP M24).
     */
    fun pastCounts(payments: List<Pair<Long, CategoryGuess>>, installedAt: Long): Map<String, Int> {
        val past = payments.filter { (at, _) -> at < installedAt }.map { it.second }
        val sorted = past.filter { it.category != null }
        val byApp = sorted.filter { it.basis in setOf(Basis.KNOWN_MERCHANT, Basis.SHOP_WORDS, Basis.AI) }
        return mapOf(
            "past_pay" to past.size,
            "past_sorted" to sorted.size,
            "past_sorted_auto" to byApp.size,
            "past_sorted_ai" to byApp.count { it.basis == Basis.AI },
        )
    }

    fun compute(records: Collection<CategoryRecord>): List<Metric> {
        val all = records.size
        val auto = records.filter { it.firstAuto }
        val corrected = auto.count { it.corrected }
        val learned = records.filter { it.firstBasis == Basis.YOUR_DEFAULT }
        val asked = records.filter { !it.firstAuto }
        val picked = asked.filter { it.pickedRank != null }
        val aiPicked = picked.filter { it.pickBasis == Basis.AI }
        return listOf(
            Metric("AI suggestion hit rate", "When the AI made the guesses, picks that were one of its top 3", pct(aiPicked.count { it.pickedRank in 1..3 }, aiPicked.size), aiPicked.size, "≥ 80%"),
            Metric("AI first pick accepted", "When the AI's guesses were shown, picks that were its first suggestion", pct(aiPicked.count { it.pickedRank == 1 }, aiPicked.size), aiPicked.size, "≥ 50%"),
            Metric("Categorisation accuracy", "Automatic categories the user kept", pct(auto.size - corrected, auto.size), auto.size, "≥ 85%"),
            Metric("Auto-categorisation rate", "Payments categorised without the user", pct(auto.size, all), all, "Higher is better"),
            Metric("User correction rate", "Automatic categories the user changed", pct(corrected, auto.size), auto.size, "≤ 15%"),
            Metric("Merchant learning rate", "Repeat payees placed right from the user's past choice", pct(learned.count { !it.corrected }, learned.size), learned.size, "≥ 95%"),
            Metric("Needs Review rate", "Payments that needed the user to categorise", pct(asked.size, all), all, "Lower is better"),
            Metric("Suggestion hit rate", "Picks that were one of our top 3 suggestions", pct(picked.count { it.pickedRank in 1..3 }, picked.size), picked.size, "≥ 80%"),
            // Needs the "Same payment / Paid twice" answers (MVP M13), which aren't built yet.
            Metric("Duplicate detection accuracy", "Not measured yet: needs the duplicate check", null, 0, "—"),
        )
    }
}
