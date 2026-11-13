package com.yourteam.expensetracker.insights

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId

class BudgetsTest {
    private val zone = ZoneId.of("Asia/Kolkata")
    private val nov = YearMonth.of(2026, 11)
    private val today = LocalDate.of(2026, 11, 13)
    private fun r(rupees: Long) = rupees * 100
    private fun at(d: LocalDate) = d.atTime(12, 0).atZone(zone).toInstant().toEpochMilli()

    private fun paid(rupees: Long, payee: String, day: LocalDate, answered: Boolean = true) =
        Payment(r(rupees), payee, at(day), started = StartedPayment(payee.hashCode().toLong() + day.dayOfMonth, r(rupees), payee, at(day), if (answered) UserAnswer.YES else UserAnswer.NONE))

    private val cats = mapOf("Zomato" to "Food & Dining", "Myntra" to "Shopping", "Apollo" to "Healthcare")
    private fun status(budget: MonthBudget, payments: List<Payment>, received: List<BankCredit> = emptyList(), on: LocalDate = today) =
        Budgets.status(budget, payments, received, on, { cats[it.payee] }, { c -> cats[c.from] }, zone)

    private val budget = MonthBudget(nov, r(25_000), listOf(CategoryLimit("Food & Dining", r(5_000)), CategoryLimit("Shopping", r(3_000))))

    @Test fun countsCompletedOnlyAndSplitsBuckets() {
        val s = status(budget, listOf(
            paid(4_000, "Zomato", today), paid(2_700, "Myntra", today.minusDays(2)),
            paid(600, "Apollo", today), paid(1_700, "Unknown shop", today),
            paid(999, "Zomato", today, answered = false), // pending: never counted
            paid(500, "Zomato", LocalDate.of(2026, 10, 30)), // last month
        ))
        assertEquals(r(9_000), s.overall.spentPaise)
        assertEquals(r(4_000), s.line("Food & Dining")!!.spentPaise)
        assertEquals(r(1_700), s.unsortedPaise)
        assertEquals(1, s.unsortedCount)
        assertEquals(r(600), s.noBudgetPaise)
        assertEquals(listOf("Healthcare"), s.noBudgetCategories)
    }

    @Test fun daysLeftIncludeTodayAndDailyRoundsDown() {
        val s = status(budget, listOf(paid(15_000, "Unknown", today)))
        assertEquals(18, s.daysLeft) // 13–30 Nov
        assertEquals(r(555), s.dailyPaise) // ₹10,000 ÷ 18 = ₹555.55 → ₹555
        val over = status(budget, listOf(paid(26_300, "Unknown", today)))
        assertNull(over.dailyPaise)
        assertEquals(1, status(budget, emptyList(), on = LocalDate.of(2026, 11, 30)).daysLeft)
    }

    @Test fun levelsAndPercent() {
        assertEquals(BudgetLevel.UNDER, Budgets.level(r(4_000), r(5_000)))
        assertEquals(BudgetLevel.NEAR, Budgets.level(r(2_700), r(3_000)))
        assertEquals(BudgetLevel.REACHED, Budgets.level(r(3_000), r(3_000)))
        assertEquals(BudgetLevel.OVER, Budgets.level(r(2_500), r(2_000)))
        assertEquals(89, Budgets.percent(8_960, 10_000)) // never "90%" while still blue
        assertEquals(125, Budgets.percent(r(2_500), r(2_000)))
        assertEquals(101, Budgets.percent(10_001, 10_000))
    }

    @Test fun refundCountsAgainstItsCategoryInTheMonthItArrives() {
        val refund = BankCredit(1, r(500), at(today), "Myntra", refund = true)
        val s = status(budget, listOf(paid(2_700, "Myntra", today.minusDays(3))), listOf(refund))
        assertEquals(r(2_200), s.line("Shopping")!!.spentPaise)
        assertEquals(r(2_200), s.overall.spentPaise)
    }

    @Test fun alertsOncePerThresholdOnlyTheHighest() {
        val s = status(budget, listOf(paid(3_100, "Myntra", today)))
        val due = Budgets.due(s, emptyMap()).first { it.key == "Shopping" }
        assertEquals(Threshold.OVER, due.threshold) // crossed 75, 90, 100 and over at once
        assertTrue(Budgets.due(s, mapOf("Shopping" to due.crossed)).none { it.key == "Shopping" })
        // Overall has no 75% step.
        val o = status(budget, listOf(paid(19_000, "Unknown", today)))
        assertTrue(Budgets.due(o, emptyMap()).none { it.key == Budgets.OVERALL })
    }

    @Test fun raisingTheLimitRearmsLowerThresholds() {
        val line = BudgetLine("Shopping", r(4_000), r(2_700)) // 67% after raising from ₹3,000
        val (kept, passed) = Budgets.rearm(line, setOf(Threshold.P75, Threshold.P90))
        assertTrue(kept.isEmpty())
        assertTrue(passed.isEmpty())
    }

    @Test fun splitWarnsButNeverBlocks() {
        val ok = Budgets.split(r(25_000), listOf(r(5_000), r(3_000), r(4_000), r(3_000), r(2_000)))
        assertEquals(r(8_000), ok.unallocatedPaise)
        val over = Budgets.split(r(25_000), listOf(r(8_000), r(6_000), r(6_000), r(4_000), r(3_000)))
        assertEquals(r(2_000), over.overByPaise)
        assertEquals(r(15_000), Budgets.savingsLeft(r(40_000), r(25_000)))
    }
}
