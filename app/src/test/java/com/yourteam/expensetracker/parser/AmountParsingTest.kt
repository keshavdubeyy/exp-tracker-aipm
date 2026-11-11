package com.yourteam.expensetracker.parser

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

// Made-up messages only (AGENTS.md rule 3).
class AmountParsingTest {

    @Test fun toPaise() {
        assertEquals(2000L, Amounts.toPaise("20"))
        assertEquals(2000L, Amounts.toPaise("20.0"))
        assertEquals(2000L, Amounts.toPaise("20.00"))
        assertEquals(125050L, Amounts.toPaise("1,250.50"))
        assertEquals(105L, Amounts.toPaise("1.05"))
        assertNull(Amounts.toPaise("abc"))
    }

    @Test fun sbiDebit() {
        val sms = "Dear UPI user A/C X1234 debited by 20.0 on date 09Nov26 trf to CHAI POINT Refno 123456789012. If not u? call 1800000000. -SBI"
        assertEquals("20.0", SmsAmountParser.debitAmountText(sms))
    }

    @Test fun otherBanksDebit() {
        assertEquals("20.00", SmsAmountParser.debitAmountText("Sent Rs.20.00 From HDFC Bank A/C *1234 To shop@upi On 09/11/26"))
        assertEquals("20.00", SmsAmountParser.debitAmountText("ICICI Bank Acct XX123 debited for Rs 20.00 on 09-Nov-26; SHOP credited."))
        assertEquals("1,250", SmsAmountParser.debitAmountText("INR 1,250 spent on card XX99 at STORE"))
    }

    @Test fun creditsAreNotDebits() {
        assertNull(SmsAmountParser.debitAmountText("Rs 500 credited to your A/c XX1234 by UPI. Received from Priya"))
        assertNull(SmsAmountParser.debitAmountText("Your A/c XX1234 balance is Rs 5,000"))
    }
}
