package com.yourteam.expensetracker.insights

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneOffset

class ReceivedTest {
    private val zone = ZoneOffset.UTC
    private val today = LocalDate.of(2026, 11, 12)
    private fun at(day: Int) = LocalDate.of(2026, 11, day).atTime(12, 0).toInstant(zone).toEpochMilli()

    @Test fun groupsBySenderThisMonthOnly() {
        val credits = listOf(
            BankCredit(1, 500_00, at(10), "Priya Sharma", refund = false),
            BankCredit(2, 250_00, at(11), "PRIYA SHARMA ", refund = false),
            BankCredit(3, 349_00, at(9), null, refund = true),
            BankCredit(4, 100_00, at(8), null, refund = false),
            BankCredit(5, 9_999_00, LocalDate.of(2026, 10, 30).atTime(12, 0).toInstant(zone).toEpochMilli(), "Old", refund = false),
        )
        val s = Received.summarise(credits, today, zone)
        assertEquals(1_199_00L, s.totalPaise)
        assertEquals(4, s.count)
        assertEquals(
            listOf(PayeeTotal("PRIYA SHARMA", 750_00, 2), PayeeTotal(Received.REFUNDS, 349_00, 1), PayeeTotal(Received.UNNAMED, 100_00, 1)),
            s.bySender,
        )
    }

    @Test fun smsAndAppAlertForSameMoneyCountOnce() {
        val sms = listOf(BankCredit(1, 99_00, at(10) + 60_000, null, refund = false))
        val apps = listOf(
            BankCredit(7, 99_00, at(10), "Priya Sharma", refund = false, source = "GPay"),   // same money as the SMS
            BankCredit(8, 99_00, at(11), "Ravi", refund = false, source = "GPay"),           // no SMS: kept on its own
        )
        val merged = Received.merge(sms, apps)
        assertEquals(2, merged.size)
        assertEquals(listOf("Ravi", "Priya Sharma"), merged.map { it.from })
        assertEquals("GPay", merged[1].alsoIn)
        assertEquals(198_00L, Received.summarise(merged, today, zone).totalPaise)
    }

    @Test fun farApartAreTwoPayments() {
        val sms = listOf(BankCredit(1, 99_00, at(10), null, refund = false))
        val apps = listOf(BankCredit(7, 99_00, at(10) + Received.MATCH_MS + 1, "Priya", refund = false, source = "GPay"))
        assertEquals(2, Received.merge(sms, apps).size)
    }

    @Test fun totalForTodayOnly() {
        val credits = listOf(
            BankCredit(1, 99_00, at(12), "Priya", refund = false),
            BankCredit(2, 301_00, at(12), "Ravi", refund = false, source = "GPay"),
            BankCredit(3, 500_00, at(11), "Old", refund = false),
        )
        assertEquals(ReceivedTotal(2, 400_00), Received.total(credits, DateRange.of(RangePreset.TODAY, today), today, zone))
        assertEquals(ReceivedTotal(3, 900_00), Received.total(credits, DateRange.of(RangePreset.THIS_WEEK, today), today, zone))
    }

    // Bug: GPay re-posts the same "paid you" alert ~12 s later and both copies were counted.
    @Test fun repostedAlertCountsOnce() {
        val apps = listOf(
            BankCredit(1, 1_00, at(12), "Priya", refund = false, source = "GPay"),
            BankCredit(2, 1_00, at(12) + 12_000, "Priya", refund = false, source = "GPay"),   // re-post
            BankCredit(3, 1_00, at(12) + 322_000, "Priya", refund = false, source = "GPay"),  // real second ₹1
        )
        assertEquals(listOf(3L, 1L), Received.merge(emptyList(), apps).map { it.smsId })
    }
}
