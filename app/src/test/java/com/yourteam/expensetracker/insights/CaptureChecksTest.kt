package com.yourteam.expensetracker.insights

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CaptureChecksTest {
    private val min = 60_000L
    private fun sms(id: Long, paise: Long, at: Long) = Payment(paise, null, at, sms = BankDebit(id, paise, at, "AX-SBIUPI"))
    private fun alert(key: String, paise: Long, at: Long) = Payment(paise, "Zomato", at, chat = ChatLine(key, paise, "Zomato", at, "PhonePe alert"))

    @Test fun unlinkedAlertAndSmsForSameAmountAreAPossibleDouble() {
        val list = listOf(alert("a", 35_000, 0), sms(1, 35_000, 20 * min))
        assertEquals(1, CaptureChecks.possibleDoubles(list))
    }

    @Test fun differentAmountsOrFarApartAreNot() {
        assertEquals(0, CaptureChecks.possibleDoubles(listOf(alert("a", 35_000, 0), sms(1, 36_000, 5 * min))))
        assertEquals(0, CaptureChecks.possibleDoubles(listOf(alert("a", 35_000, 0), sms(1, 35_000, 45 * min))))
    }

    @Test fun twoSmsForTheSameAmountAreRealRepeats() {
        assertEquals(0, CaptureChecks.possibleDoubles(listOf(sms(1, 2_000, 0), sms(2, 2_000, 3 * min))))
    }

    @Test fun eachPaymentPairsOnce() {
        val list = listOf(alert("a", 2_000, 0), alert("b", 2_000, min), sms(1, 2_000, 2 * min))
        assertEquals(1, CaptureChecks.possibleDoubles(list))
    }

    @Test fun payeeNamed() {
        assertTrue(CaptureChecks.payeeNamed(alert("a", 100, 0)))
        assertFalse(CaptureChecks.payeeNamed(sms(1, 100, 0)))
        assertFalse(CaptureChecks.payeeNamed(Payment(100, Spending.UNNAMED, 0)))
    }
}
