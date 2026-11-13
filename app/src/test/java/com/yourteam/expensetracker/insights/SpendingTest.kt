package com.yourteam.expensetracker.insights

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneOffset

class SpendingTest {
    private val zone = ZoneOffset.UTC
    private val today = LocalDate.of(2026, 11, 9)
    private fun at(day: Int, month: Int = 11) =
        LocalDate.of(2026, month, day).atTime(12, 0).toInstant(zone).toEpochMilli()

    private fun sms(paise: Long, day: Int, month: Int = 11, payee: String? = null) =
        Payment(paise, payee, at(day, month), sms = BankDebit(day.toLong() * 100 + month, paise, at(day, month), "X"))

    private fun waiting(paise: Long, day: Int) =
        Payment(paise, "Shop", at(day), started = StartedPayment(day.toLong(), paise, "Shop", at(day), UserAnswer.NONE))

    @Test fun monthTotalAverageAndCount() {
        val s = Spending.summarise(
            listOf(sms(2000, 9, payee = "Chai Point"), sms(1000, 1), sms(99900, 30, month = 10)),
            today, zone,
        )
        assertEquals(3000L, s.totalPaise) // October excluded
        assertEquals(2, s.paymentCount)
        assertEquals(333L, s.averagePerDayPaise) // 3000 / 9 days, rounded down
    }

    @Test fun unconfirmedIsSeparateFromTotal() {
        val s = Spending.summarise(listOf(sms(2000, 9), waiting(500, 9)), today, zone)
        assertEquals(2000L, s.totalPaise)
        assertEquals(500L, s.unconfirmedPaise)
        assertEquals(1, s.unconfirmedCount)
    }

    @Test fun lastSevenDaysOldestFirst() {
        val s = Spending.summarise(listOf(sms(2000, 9), sms(700, 3), sms(100, 2)), today, zone)
        assertEquals(7, s.lastSevenDays.size)
        assertEquals(LocalDate.of(2026, 11, 3), s.lastSevenDays.first().date)
        assertEquals(700L, s.lastSevenDays.first().totalPaise) // the 2nd is outside the window
        assertEquals(2000L, s.lastSevenDays.last().totalPaise)
    }

    @Test fun topPayeesGroupedAndUnnamedBucket() {
        val s = Spending.summarise(
            listOf(sms(2000, 9, payee = "Chai Point"), sms(1500, 8, payee = "chai point "), sms(9000, 7)),
            today, zone,
        )
        assertEquals(2, s.topPayees.size)
        assertEquals(Spending.UNNAMED, s.topPayees[0].payee)
        assertEquals(9000L, s.topPayees[0].totalPaise)
        assertEquals("Chai Point", s.topPayees[1].payee) // case and spaces ignored
        assertEquals(3500L, s.topPayees[1].totalPaise)
        assertEquals(2, s.topPayees[1].count)
    }

    @Test fun barFractionsRelativeToBiggestDay() {
        val days = listOf(DayTotal(today, 1000), DayTotal(today, 500), DayTotal(today, 0))
        assertEquals(listOf(1f, 0.5f, 0f), Spending.barFractions(days))
        assertEquals(listOf(0f), Spending.barFractions(listOf(DayTotal(today, 0))))
    }

    // ---- Home hero: this period vs the same stretch last period ----

    @Test fun weekComparesSameDaysOfLastWeek() {
        val wed = LocalDate.of(2026, 11, 11) // weeks run Mon 9 – Sun 15 Nov
        val s = Spending.periodSpend(
            listOf(
                sms(300_000, 9), sms(89_700, 11),  // this week, Mon + Wed
                sms(400_000, 2), sms(30_000, 4),   // last week, Mon + Wed: counted
                sms(999_900, 6),                   // last week, Fri: after "this time last week", ignored
                waiting(50_000, 10),               // still waiting for an answer: not counted
            ),
            DateRange.of(RangePreset.THIS_WEEK, wed), wed, zone,
        )
        assertEquals(LocalDate.of(2026, 11, 9), s.range.start)
        assertEquals(wed, s.range.end) // never past today
        assertEquals(389_700L, s.totalPaise)
        assertEquals(430_000L, s.previousPaise)
        assertEquals(40_300L, s.differencePaise)
        assertEquals(true, s.spentLess)
        assertEquals(-94L, s.changeTenthsPercent) // −9.37% rounds to −9.4%
    }

    @Test fun monthStopsAtEndOfShorterPreviousMonth() {
        val s = Spending.periodSpend(
            listOf(sms(1000, 31, month = 3), sms(500, 28, month = 2)),
            DateRange.of(RangePreset.THIS_MONTH, LocalDate.of(2026, 3, 31)), LocalDate.of(2026, 3, 31), zone,
        )
        assertEquals(1000L, s.totalPaise)
        assertEquals(500L, s.previousPaise) // all of February, nothing from March
        assertEquals(1000L, s.changeTenthsPercent) // +100.0%
    }

    @Test fun noPercentWhenNothingSpentLastTime() {
        val s = Spending.periodSpend(listOf(sms(1000, 9)), DateRange.of(RangePreset.THIS_WEEK, today), today, zone)
        assertEquals(null, s.changeTenthsPercent)
        assertEquals(false, s.spentLess)
    }

    @Test fun heroFormatting() {
        assertEquals("₹3,897", Money.formatWhole(389_650))
        assertEquals("₹1,24,000", Money.formatWhole(12_400_000))
        assertEquals("\u2212₹86", Money.formatWhole(-8_600))
        assertEquals("\u22129.6%", Money.percent(-96))
        assertEquals("+12.0%", Money.percent(120))
    }

    @Test fun toCheckCountsUnansweredOnly() {
        val t = Spending.toCheck(listOf(sms(2000, 9), waiting(500, 9), waiting(700, 8)))
        assertEquals(2, t.count)
        assertEquals(1200L, t.paise)
    }

    @Test fun byCategoryComparesSameStretchLastWeek() {
        // Today is Mon 9 Nov: this week = 9 Nov, last week's same stretch = Mon 2 Nov.
        val cats = Spending.byCategory(
            listOf(sms(2000, 9, payee = "Chai Point"), sms(500, 2, payee = "Chai Point"), sms(900, 9)),
            DateRange.of(RangePreset.THIS_WEEK, today), today, { it.payee?.let { "Food" } }, zone,
        )
        assertEquals(listOf("Food", null), cats.map { it.category })
        assertEquals(2000L, cats[0].totalPaise)
        assertEquals(500L, cats[0].previousPaise)
        assertEquals(1500L, cats[0].changePaise)
    }

    @Test fun debitFormat() {
        assertEquals("\u2212₹86", Money.formatDebit(8600))
        assertEquals("\u2212₹1,24,000", Money.formatDebit(12400000))
        assertEquals("\u2212₹12.50", Money.formatDebit(1250))
    }

    // ---- Date range picker: presets and what each is compared with ----

    @Test fun presetsFromMonday9Nov() {
        fun r(p: RangePreset) = DateRange.of(p, today).let { it.start to it.end }
        val d = { day: Int, m: Int -> LocalDate.of(2026, m, day) }
        assertEquals(d(9, 11) to d(9, 11), r(RangePreset.TODAY))
        assertEquals(d(9, 11) to d(9, 11), r(RangePreset.THIS_WEEK)) // stops at today
        assertEquals(d(3, 11) to d(9, 11), r(RangePreset.LAST_7_DAYS))
        assertEquals(d(1, 11) to d(9, 11), r(RangePreset.THIS_MONTH)) // stops at today
        assertEquals(d(1, 10) to d(31, 10), r(RangePreset.LAST_MONTH))
        assertEquals(7L, DateRange.of(RangePreset.LAST_7_DAYS, today).days)
    }

    @Test fun last7DaysComparesWithThe7DaysBefore() {
        val s = Spending.periodSpend(
            listOf(
                sms(1000, 3), sms(2000, 9),      // 3–9 Nov: counted
                sms(400, 27, month = 10),        // 27 Oct–2 Nov: the 7 days before
                sms(600, 2),
                sms(9999, 26, month = 10),       // 26 Oct: outside both
            ),
            DateRange.of(RangePreset.LAST_7_DAYS, today), today, zone,
        )
        assertEquals(3000L, s.totalPaise)
        assertEquals(1000L, s.previousPaise)
    }

    @Test fun lastMonthComparesWholeMonthBefore() {
        val s = Spending.periodSpend(
            listOf(sms(1000, 31, month = 10), sms(700, 30, month = 9), sms(5000, 9)),
            DateRange.of(RangePreset.LAST_MONTH, today), today, zone,
        )
        assertEquals(1000L, s.totalPaise)    // October only, not November
        assertEquals(700L, s.previousPaise)  // all of September
    }

    @Test fun customRangeEitherOrderAndSameLengthBefore() {
        val r = DateRange.custom(LocalDate.of(2026, 11, 6), LocalDate.of(2026, 11, 4)) // tapped backwards
        assertEquals(LocalDate.of(2026, 11, 4), r.start)
        assertEquals(3L, r.days)
        val s = Spending.periodSpend(listOf(sms(100, 4), sms(200, 6), sms(50, 1), sms(80, 3), sms(999, 7)), r, today, zone)
        assertEquals(300L, s.totalPaise)    // 4–6 Nov
        assertEquals(130L, s.previousPaise) // 1–3 Nov
    }

    @Test fun todayComparesWithYesterday() {
        val s = Spending.periodSpend(listOf(sms(500, 9), sms(800, 8), sms(300, 7)), DateRange.of(RangePreset.TODAY, today), today, zone)
        assertEquals(500L, s.totalPaise)
        assertEquals(800L, s.previousPaise)
    }

    @Test fun paymentsInUsesSameDaysAsTotals() {
        val list = listOf(sms(100, 9), waiting(200, 5), sms(300, 2), sms(400, 1))
        val got = Spending.paymentsIn(list, DateRange.of(RangePreset.LAST_7_DAYS, today), today, zone)
        assertEquals(listOf(100L, 200L), got.map { it.paise }) // 3–9 Nov, waiting ones included
        assertEquals(emptyList<Long>(), Spending.paymentsIn(list, DateRange.custom(LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 5)), today, zone).map { it.paise })
    }

    @Test fun trackedRupeesSkipsWaitingAndRoundsDown() {
        val list = listOf(sms(12_050, 1), sms(10_099, 2), waiting(50_000, 3))
        assertEquals(221L, Spending.trackedRupees(list)) // ₹120.50 + ₹100.99, the waiting ₹500 not counted
    }
}
