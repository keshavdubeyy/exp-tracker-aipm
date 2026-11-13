package com.yourteam.expensetracker.parser

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime
import java.time.ZoneOffset

// Made-up chats only.
class GPayChatParserTest {

    private val now = LocalDateTime.of(2026, 11, 9, 17, 30)
    private fun parse(vararg t: String) = GPayChatParser.parse(t.toList(), now, ZoneOffset.UTC)
    private fun at(y: Int, mo: Int, d: Int, h: Int, mi: Int) =
        LocalDateTime.of(y, mo, d, h, mi).toInstant(ZoneOffset.UTC).toEpochMilli()

    @Test fun readsTodaysPaymentWithTime() {
        val p = parse("Payment to Ramesh Kumar ₹1 Paid · 5:24 pm").single()
        assertEquals("Ramesh Kumar", p.payee)
        assertEquals("1", p.amountText)
        assertEquals(at(2026, 11, 9, 17, 24), p.occurredAt)
    }

    @Test fun readsOlderPaymentWithDate() {
        val p = parse("Payment to Priya ₹1,250.50 Paid ✓ 7 Nov").single()
        assertEquals("1,250.50", p.amountText)
        assertEquals(at(2026, 11, 7, 12, 0), p.occurredAt)
    }

    // Bug: money received showed up as spending to a payee called "you".
    @Test fun moneyReceivedIsSkipped() {
        val list = parse("Payment to Ramesh ₹20 Paid · 5:24 pm", "Payment to you ₹301 Paid · 6:10 pm")
        assertEquals(listOf("Ramesh"), list.map { it.payee })
    }

    @Test fun decemberSeenInNovemberIsLastYear() {
        val p = parse("Payment to Priya ₹50 Paid 28 Dec").single()
        assertEquals(at(2025, 12, 28, 12, 0), p.occurredAt)
    }

    @Test fun sameKeyTodayAndLater() {
        val today = parse("Payment to Priya ₹50 Paid · 5:24 pm").single()
        val later = GPayChatParser.parse(
            listOf("Payment to Priya ₹50 Paid 9 Nov"), now.plusDays(2), ZoneOffset.UTC,
        ).single()
        assertEquals(today.dedupeKey, later.dedupeKey)
    }

    @Test fun twoIdenticalPaymentsSameDayGetDifferentKeys() {
        val ps = parse("Payment to Priya ₹50 Paid · 1:00 pm", "Payment to Priya ₹50 Paid · 3:00 pm")
        assertEquals(2, ps.size)
        assertNotEquals(ps[0].dedupeKey, ps[1].dedupeKey)
    }

    @Test fun ignoresOtherLines() {
        assertTrue(parse("Pay ₹1.00", "To Ramesh", "Payment from Priya ₹100 Received", "Send message").isEmpty())
        assertTrue(parse("Payment to Priya ₹50 Failed · 1:00 pm").isEmpty())
    }
}
