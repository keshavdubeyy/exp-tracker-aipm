package com.yourteam.expensetracker.insights

import com.yourteam.expensetracker.categoriser.Categories
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneOffset

/** Feature 2: the facts each card gives the AI, and the check that keeps unsafe AI words off the screen. */
class InsightWordingCheckTest {
    private val zone = ZoneOffset.UTC
    private val today = LocalDate.of(2026, 11, 13)
    private fun at(day: Int, month: Int = 11, hour: Int = 12) = LocalDate.of(2026, month, day).atTime(hour, 0).toInstant(zone).toEpochMilli()
    private fun pay(paise: Long, day: Int, month: Int = 11, payee: String? = null, hour: Int = 12) =
        Payment(paise, payee, at(day, month, hour), sms = BankDebit(day.toLong() * 10_000 + month * 100 + hour, paise, at(day, month, hour), "X", payee))

    private val change = InsightFacts(
        insightType = "category_comparison",
        metrics = linkedMapOf("category" to "Food & Dining", "current_inr" to 7500L, "previous_inr" to 5770L, "change_inr" to 1730L, "change_percent" to 30),
        currentPeriod = "1–13 Nov", comparisonPeriod = "1–13 Oct", pattern = "Food & Dining spending is up", evidenceCount = 1,
    )

    @Test fun cardsCarryTheNumbersTheyShow() {
        val s = Insights.summarise(
            listOf(pay(750_000, 5, payee = "Zomato"), pay(577_000, 6, month = 10, payee = "Zomato")),
            emptyList(), InsightPeriod.of(PeriodKind.MONTH, today), today, { p -> p.payee?.let { mapOf("Zomato" to Categories.FOOD)[it] } }, { it }, zone,
        )
        val f = s.cards.first { it.type == InsightType.CATEGORY_CHANGE }.facts!!
        assertEquals("category_comparison", f.insightType)
        assertEquals(7500L, f.metrics["current_inr"])
        assertEquals(5770L, f.metrics["previous_inr"])
        assertEquals(30, f.metrics["change_percent"])
        assertEquals("1–13 Nov", f.currentPeriod)
        assertEquals("1–13 Oct", f.comparisonPeriod)
        // Every card has facts, and the app's own wording passes the same check the AI's must pass.
        s.cards.forEach { c ->
            val facts = c.facts!!
            assertEquals(emptyList<String>(), InsightWordingCheck.problems(facts, c.title, c.detail.replace("8 pm", "night")))
        }
    }

    @Test fun groundedWordingPasses() {
        assertEquals(emptyList<String>(), InsightWordingCheck.problems(change, "Food & Dining is up 30%",
            "You spent ₹7,500 on Food & Dining in 1–13 Nov, ₹1,730 more than ₹5,770 in 1–13 Oct."))
    }

    @Test fun inventedNumberCauseAdviceAreBlocked() {
        assertTrue("number" in InsightWordingCheck.problems(change, "Food is up", "You spent ₹8,000 on food.").joinToString())
        assertTrue("advice" in InsightWordingCheck.problems(change, "Food is up", "Food rose 30%; try cooking at home.").joinToString())
        assertTrue("advice" in InsightWordingCheck.problems(change, "Food is up", "Food rose 30% because of festivals.").joinToString())
        assertTrue("advice" in InsightWordingCheck.problems(change, "Food is up", "Food will keep rising after 30% this time.").joinToString())
        assertTrue(InsightWordingCheck.problems(change, "", "").isNotEmpty())
    }

    @Test fun rankingOnlyWhenTheInsightMakesIt() {
        assertTrue("ranking" in InsightWordingCheck.problems(change, "Food is your top category", "₹7,500 in 1–13 Nov.").joinToString())
        val repeat = change.copy(pattern = "The most frequent place paid this month")
        assertEquals(emptyList<String>(), InsightWordingCheck.problems(repeat, "Swiggy, 30 times", "Your most frequent place, ₹7,500 in 1–13 Nov."))
    }

    @Test fun signsAndIndianGroupingAreReadAsTheSameNumber() {
        val down = change.copy(metrics = linkedMapOf("total_inr" to 125000L, "change_percent" to -40))
        assertEquals(emptyList<String>(), InsightWordingCheck.problems(down, "Spending down 40%", "₹1,25,000 in 1–13 Nov, 40% less."))
    }
}
