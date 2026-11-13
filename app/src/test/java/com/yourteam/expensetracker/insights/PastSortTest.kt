package com.yourteam.expensetracker.insights

import com.yourteam.expensetracker.categoriser.Basis
import com.yourteam.expensetracker.categoriser.CategoryGuess
import com.yourteam.expensetracker.categoriser.Confidence
import org.junit.Assert.assertEquals
import org.junit.Test

class PastSortTest {
    private var id = 0L
    private fun pay(payee: String?, paise: Long) = (++id).let { Payment(paise, payee, it * 1000, sms = BankDebit(it, paise, it * 1000, "X", payee)) }

    private val sure = { c: String, b: Basis -> CategoryGuess(c, Confidence.HIGH, listOf(c), b) }
    private val unsure = CategoryGuess(null, Confidence.LOW, listOf("Food", "Groceries", "Shopping"), Basis.AI, 0.2)

    @Test fun groupsByPayeeAndCounts() {
        val payments = listOf(
            pay("Swiggy", 300_00), pay("Swiggy", 200_00), // known shop
            pay("Ravi Tea Stall", 20_00),                  // AI, sure
            pay("Raju", 50_00), pay("Raju", 40_00), pay("Raju", 30_00), // unsure: ask once
            pay(null, 10_00),                              // no name: its own group
        )
        val plan = PastSort.of(payments) { p ->
            when (p.payee) {
                "Swiggy" -> sure("Food", Basis.KNOWN_MERCHANT)
                "Ravi Tea Stall" -> sure("Food", Basis.AI)
                else -> unsure
            }
        }
        assertEquals(7, plan.total)
        assertEquals(3, plan.sorted)
        assertEquals(1, plan.sortedByAi)
        assertEquals(4, plan.unsorted)
        assertEquals(listOf("Swiggy", "Ravi Tea Stall"), plan.filed.map { it.payee })
        // Most payments first; Raju's three are one question.
        assertEquals(listOf("Raju", null), plan.toAsk.map { it.payee })
        assertEquals(120_00L, plan.toAsk.first().totalPaise)
    }

    @Test fun pendingAnswersAreLeftOut() {
        val pending = Payment(5_00, "X", 1, started = StartedPayment(1, 5_00, "X", 1, UserAnswer.NONE))
        assertEquals(0, PastSort.of(listOf(pending)) { unsure }.total)
    }
}
