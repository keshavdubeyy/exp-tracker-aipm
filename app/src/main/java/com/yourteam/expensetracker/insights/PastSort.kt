package com.yourteam.expensetracker.insights

import com.yourteam.expensetracker.categoriser.Basis
import com.yourteam.expensetracker.categoriser.CategoryGuess
import com.yourteam.expensetracker.categoriser.CategoryMemory

/** All past payments to one payee (or one payment with no payee name), and what the app made of them. */
data class PayeeGroup(
    /** Payee key ("payee:…" / "vpa:…"), or the payment's own key when it has no payee. */
    val key: String,
    val payee: String?,
    val payments: List<Payment>,
    val totalPaise: Long,
    val guess: CategoryGuess,
)

/** Onboarding ob3b "Sort past payments": what the inbox found at setup holds, by payee. */
data class PastSortPlan(
    /** Counted payments in the import. */
    val total: Int,
    /** Payments with a category now (on their own or picked by the user). */
    val sorted: Int,
    /** Of [sorted], how many the AI placed (rules couldn't). */
    val sortedByAi: Int,
    /** Payees whose payments are filed, most payments first. */
    val filed: List<PayeeGroup>,
    /** Payees still with no category, most payments first: the ones to ask about. */
    val toAsk: List<PayeeGroup>,
) {
    val unsorted: Int get() = total - sorted
}

object PastSort {
    /** Plain Kotlin counts and sums (AGENTS.md rule 1). Payments of one payee share one guess, so they're asked once. */
    fun of(payments: List<Payment>, guessOf: (Payment) -> CategoryGuess): PastSortPlan {
        val counted = payments.filter(Spending::counts)
        val groups = counted
            .groupBy { p -> CategoryMemory.payeeKeys(p.payee, p.sms?.vpa).firstOrNull() ?: p.key }
            .map { (key, list) ->
                PayeeGroup(key, list.first().payee, list, list.sumOf { it.paise }, guessOf(list.first()))
            }
            .sortedWith(compareByDescending<PayeeGroup> { it.payments.size }.thenByDescending { it.totalPaise })
        // A one-off pick on a single payment can differ from its payee's guess, so count per payment.
        val guesses = counted.map(guessOf)
        val (filed, toAsk) = groups.partition { it.guess.category != null }
        return PastSortPlan(
            total = counted.size,
            sorted = guesses.count { it.category != null },
            sortedByAi = guesses.count { it.category != null && it.basis == Basis.AI },
            filed = filed,
            toAsk = toAsk,
        )
    }
}
