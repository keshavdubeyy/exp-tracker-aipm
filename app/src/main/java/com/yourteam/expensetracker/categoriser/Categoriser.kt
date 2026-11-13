// Picks a category for a transaction. AI may pick a category, never a number (AGENTS.md rule 2).
package com.yourteam.expensetracker.categoriser

import com.yourteam.expensetracker.parser.CreditKind
import java.time.Instant
import java.time.ZoneId

/**
 * How sure we are (Feature 1 §3.3, three-tier flow). Only HIGH ("Sure", ≥ 0.85) is filed on its
 * own; MEDIUM ("Fairly sure", 0.3–0.85) is offered with its three guesses; LOW ("Not sure", under
 * 0.3) asks with no guess. The user's own choice and a known merchant are always HIGH.
 */
enum class Confidence { HIGH, MEDIUM, LOW }

/** Why we picked what we picked. Shown nowhere yet; used for the developer dashboard and tests. */
enum class Basis { THIS_PAYMENT, YOUR_DEFAULT, KNOWN_MERCHANT, SHOP_WORDS, AI, NONE }

/** What the SMS (or GPay) told us about a payment. Amount and time are supporting signals only. */
data class PaymentFacts(
    val payee: String?,
    val vpa: String? = null,
    val paise: Long = 0,
    val at: Long = 0,
    val method: String? = null,
)

/** What the AI said for a payee (Feature 1 §3.2 step 4): its top picks, and whether it was sure. */
data class AiHint(val suggestions: List<String>, val confident: Boolean)

/**
 * The result for one payment. [category] is set only when the score is at least [Categoriser.HIGH]
 * ("Sure"): otherwise it is null and [suggestions] are offered, most likely first (always 3), with
 * [confidence] telling the UI whether to show the guesses (MEDIUM) or ask with none (LOW).
 * [score] is 0..1, for the developer screen and tests only.
 */
data class CategoryGuess(
    val category: String?,
    val confidence: Confidence,
    val suggestions: List<String>,
    val basis: Basis,
    val score: Double = if (category != null) 1.0 else 0.0,
)

/**
 * The user's saved choices (Feature 1 §3.5, "always learn"). Keys:
 *  - "txn:<payment key>"  this payment only
 *  - "payee:<name>" and "vpa:<upi id>"  the default for that payee
 *  - "in-txn:<credit key>", "in-payee:<name>"  the same for money received
 */
data class CategoryMemory(val choices: Map<String, String> = emptyMap()) {
    fun forPayment(paymentKey: String): String? = choices[txnKey(paymentKey)]
    fun forPayee(payee: String?, vpa: String?): String? = payeeKeys(payee, vpa).firstNotNullOfOrNull { choices[it] }

    companion object {
        fun txnKey(paymentKey: String) = "txn:$paymentKey"
        fun incomeTxnKey(creditKey: String) = "in-txn:$creditKey"

        /** "Lakshmi Stores", "LAKSHMI STORES." and "lakshmi  stores" are the same payee. */
        fun payeeKeys(payee: String?, vpa: String?): List<String> = listOfNotNull(
            payee?.takeIf { '@' !in it }?.let(MerchantMap::normalise)?.trim()?.takeIf(String::isNotEmpty)?.let { "payee:$it" },
            (vpa ?: payee?.takeIf { '@' in it })?.trim()?.lowercase()?.takeIf(String::isNotEmpty)?.let { "vpa:$it" },
        )

        fun incomePayeeKey(from: String?): String? =
            from?.let(MerchantMap::normalise)?.trim()?.takeIf(String::isNotEmpty)?.let { "in-payee:$it" }
    }
}

object Categoriser {

    /** Score at or above which we call it "Sure" and file it on our own (Feature 1 §3.3). Tune on the eval set (M20). */
    const val HIGH = 0.85
    /** Score at or above which we're "Fairly sure": not filed, but we offer the three guesses. Below it, "Not sure". */
    const val AUTO = 0.3
    /** How much an AI answer counts. A sure answer is filed on its own; an unsure one is only offered. */
    const val AI_CONFIDENT = 0.75
    const val AI_UNSURE = 0.2
    /** Added when the shop words and the AI agree on the same category (capped at [AGREE_CAP]). */
    const val AGREE_BONUS = 0.15
    const val AGREE_CAP = 0.95

    /**
     * Full chain for one payment (Feature 1 §3.2): this payment's own choice, the payee's saved
     * default, known merchant, shop-type words, the AI's hint, then supporting signals only.
     * [popular] = the user's most used categories, most used first. [allowed] limits suggestions.
     */
    fun decide(
        paymentKey: String,
        facts: PaymentFacts,
        memory: CategoryMemory,
        ai: AiHint? = null,
        popular: List<String> = emptyList(),
        allowed: List<String> = Categories.expense,
    ): CategoryGuess {
        memory.forPayment(paymentKey)?.let { return decided(it, Basis.THIS_PAYMENT, facts, popular, allowed) }
        memory.forPayee(facts.payee, facts.vpa)?.let { return decided(it, Basis.YOUR_DEFAULT, facts, popular, allowed) }
        return guess(facts, ai, popular, allowed)
    }

    /** Without the user's memory: merchant map, shop words, AI hint, supporting signals. */
    fun guess(
        facts: PaymentFacts,
        ai: AiHint? = null,
        popular: List<String> = emptyList(),
        allowed: List<String> = Categories.expense,
    ): CategoryGuess {
        MerchantMap.merchant(facts.payee, facts.vpa)?.let { return decided(it, Basis.KNOWN_MERCHANT, facts, popular, allowed) }

        val word = MerchantMap.shopWord(facts.payee, facts.vpa)
        val aiPicks = ai?.suggestions.orEmpty()
        val aiScore = if (aiPicks.isEmpty()) null else if (ai!!.confident) AI_CONFIDENT else AI_UNSURE
        val (score, basis, firstPicks) = when {
            // Shop words and AI agree on the top category: surer than either alone.
            word != null && aiScore != null && aiPicks.first() == word.suggestions.first() ->
                Triple(minOf(AGREE_CAP, maxOf(word.strength, aiScore) + AGREE_BONUS), Basis.SHOP_WORDS, word.suggestions)
            word != null && (aiScore == null || word.strength >= aiScore) -> Triple(word.strength, Basis.SHOP_WORDS, word.suggestions)
            aiScore != null -> Triple(aiScore, Basis.AI, aiPicks + word?.suggestions.orEmpty())
            else -> Triple(0.0, Basis.NONE, emptyList())
        }
        val suggestions = topThree(firstPicks, facts, popular, allowed)
        val confidence = when {
            score >= HIGH -> Confidence.HIGH
            score >= AUTO -> Confidence.MEDIUM
            else -> Confidence.LOW
        }
        // Rules file only when Sure (≥ HIGH). Once the AI has answered, its first pick is filed too (13 Nov 2026,
        // team decision): marked ✦ as AI-picked, still easy to change. Confidence is kept, so unsure picks can be checked.
        if (score < HIGH && aiPicks.firstOrNull() in allowed) { // a category the user deleted since doesn't count
            val picks = topThree(aiPicks + firstPicks, facts, popular, allowed)
            return CategoryGuess(picks.first(), confidence, picks, Basis.AI, score)
        }
        return CategoryGuess(if (score >= HIGH) suggestions.first() else null, confidence, suggestions, basis, score)
    }

    /**
     * Money received (Feature 1 §3.7), same rule as payments: filed on its own when we're 30%+
     * sure, otherwise the user is asked. What the SMS says it is → a shop paying back (refund) →
     * a company (salary) → a person (transfer). No sender name at all: we ask.
     */
    fun decideIncome(creditKey: String, kind: CreditKind?, from: String?, memory: CategoryMemory): CategoryGuess {
        val all = Categories.income
        fun withFirst(c: String) = (listOf(c) + all).distinct().take(3)
        memory.choices[CategoryMemory.incomeTxnKey(creditKey)]?.let { return CategoryGuess(it, Confidence.HIGH, withFirst(it), Basis.THIS_PAYMENT) }
        CategoryMemory.incomePayeeKey(from)?.let(memory.choices::get)?.let { return CategoryGuess(it, Confidence.HIGH, withFirst(it), Basis.YOUR_DEFAULT) }
        val named = when (kind) {
            CreditKind.SALARY -> Categories.SALARY
            CreditKind.REFUND -> Categories.REFUND
            CreditKind.INTEREST -> Categories.INTEREST
            CreditKind.CASHBACK -> Categories.CASHBACK
            null -> null
        }
        if (named != null) return CategoryGuess(named, Confidence.HIGH, withFirst(named), Basis.SHOP_WORDS)
        // Below are only hints (Fairly sure): we offer a best guess but don't file it (Feature 1 §3.3, three-tier).
        val company = from != null && Regex("""\b(pvt|ltd|limited|private|llp|technologies|solutions|services|inc)\b""", RegexOption.IGNORE_CASE).containsMatchIn(from)
        val shop = from != null && MerchantMap.merchant(from, null) != null
        return when {
            shop -> CategoryGuess(null, Confidence.MEDIUM, withFirst(Categories.REFUND), Basis.KNOWN_MERCHANT, 0.7)
            company -> CategoryGuess(null, Confidence.MEDIUM, withFirst(Categories.SALARY), Basis.SHOP_WORDS, 0.6)
            from != null -> CategoryGuess(null, Confidence.MEDIUM, withFirst(Categories.TRANSFERS), Basis.SHOP_WORDS, 0.6)
            else -> CategoryGuess(null, Confidence.LOW, listOf(Categories.TRANSFERS, Categories.REFUND, Categories.OTHER_INCOME), Basis.NONE, 0.0)
        }
    }

    private fun decided(category: String, basis: Basis, facts: PaymentFacts, popular: List<String>, allowed: List<String>) =
        CategoryGuess(category, Confidence.HIGH, (listOf(category) + topThree(emptyList(), facts, popular, allowed)).distinct().take(3), basis, 1.0)

    /**
     * Three suggestions: [firstPicks] first, then the user's most used, then supporting signals
     * (time of day, amount, card). Signals only reorder suggestions; they never decide a category.
     */
    internal fun topThree(firstPicks: List<String>, facts: PaymentFacts, popular: List<String>, allowed: List<String>): List<String> {
        val hints = buildList {
            val hour = if (facts.at > 0) Instant.ofEpochMilli(facts.at).atZone(ZoneId.systemDefault()).hour else -1
            val mealTime = hour in 7..10 || hour in 12..15 || hour in 19..23
            if (mealTime && facts.paise in 1..60_000) add(Categories.FOOD) // up to ₹600 around meals
            if (facts.paise in 1..250_000) add(Categories.GROCERIES)       // up to ₹2,500
            if (facts.method == "Card") add(Categories.SHOPPING)
            add(Categories.SHOPPING)
            add(Categories.FOOD)
            add(Categories.OTHERS)
        }
        return (firstPicks + popular.take(2) + hints + allowed).distinct().filter { it in allowed }.take(3)
    }
}
