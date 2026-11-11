package com.yourteam.expensetracker.insights

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PaymentMatcherTest {
    private val min = 60_000L
    private val start = 1_000_000_000L

    @Test fun matchesSameAmountShortlyAfter() {
        val sms = PaymentRecord(2000, start + 2 * min, "sms")
        assertEquals(sms, PaymentMatcher.findMatch(2000, start, listOf(sms)))
    }

    @Test fun chatLineRoundedToMinuteJustBeforeStillMatches() {
        val chat = PaymentRecord(2000, start - 40_000, "chat")
        assertEquals(chat, PaymentMatcher.findMatch(2000, start, listOf(chat)))
    }

    @Test fun differentAmountOrTooLateDoesNotMatch() {
        assertNull(PaymentMatcher.findMatch(2000, start, listOf(PaymentRecord(2100, start + min, "sms"))))
        assertNull(PaymentMatcher.findMatch(2000, start, listOf(PaymentRecord(2000, start + 31 * min, "sms"))))
        assertNull(PaymentMatcher.findMatch(2000, start, listOf(PaymentRecord(2000, start - 3 * min, "sms"))))
    }

    @Test fun picksClosestInTime() {
        val far = PaymentRecord(2000, start + 20 * min, "far")
        val near = PaymentRecord(2000, start + 1 * min, "near")
        assertEquals(near, PaymentMatcher.findMatch(2000, start, listOf(far, near)))
    }
}
