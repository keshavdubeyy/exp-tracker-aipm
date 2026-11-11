package com.yourteam.expensetracker.insights

import com.yourteam.expensetracker.data.SampleData
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneOffset

class AnswersTest {
    private val zone = ZoneOffset.UTC
    private val today = LocalDate.of(2026, 11, 9)
    private val payments = SampleData.snapshot(emptyMap(), today, zone).payments
    private fun ask(q: String) = Answers.answer(q, payments, today, zone)

    @Test fun sampleCoversEveryState() {
        assertEquals(2, payments.count { it.needsAnswer })
        assertEquals(1, payments.count { it.conflict })
        assertTrue(payments.any { it.started != null && it.chat != null && it.sms != null })
    }

    @Test fun monthTotalMatchesSpending() {
        val total = Spending.summarise(payments, today, zone).totalPaise
        assertTrue(ask("How much did I spend this month?").text.contains(Money.format(total)))
    }

    @Test fun payeeTotalIsCaseInsensitive() {
        val a = ask("how much at chai point")
        assertTrue(a.text, a.text.contains("₹192.00")) // 12 + 180
        assertEquals(2, a.rows.size)
    }

    @Test fun checkListsUnansweredAndConflicts() {
        assertEquals(3, ask("Anything I need to check?").rows.size)
    }

    @Test fun biggestPayment() {
        assertTrue(ask("What was my biggest payment?").text.contains("₹15,000.00 to Ramesh Gupta"))
    }

    @Test fun unknownQuestionGetsHelp() {
        assertTrue(ask("tell me a joke").text.startsWith("I can tell you"))
    }
}
