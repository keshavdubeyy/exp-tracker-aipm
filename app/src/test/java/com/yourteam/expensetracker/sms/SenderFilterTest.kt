package com.yourteam.expensetracker.sms

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

// All messages here are made up. Never paste real SMS into tests (AGENTS.md rule 3).
class SenderFilterTest {

    private fun msg(address: String, body: String) = SmsMessage(0, address, body, 0)

    @Test fun acceptsBankSenderIds() {
        assertTrue(SenderFilter.isBankSender("XX-HDFCBK"))
        assertTrue(SenderFilter.isBankSender("AD-SBIINB"))
        assertTrue(SenderFilter.isBankSender("VM-ICICIB"))
        assertTrue(SenderFilter.isBankSender("JD-AXISBK-S"))
        assertTrue(SenderFilter.isBankSender("AX-KOTAKB-T"))
    }

    @Test fun rejectsOtherSenders() {
        assertFalse(SenderFilter.isBankSender("+919876543210"))
        assertFalse(SenderFilter.isBankSender("HDFCBK"))
        assertFalse(SenderFilter.isBankSender("AD-SBI"))
        assertFalse(SenderFilter.isBankSender("AD-SBIINBX"))
        assertFalse(SenderFilter.isBankSender("VM-ICICIB-P"))
    }

    @Test fun dropsOtpsAndPromos() {
        assertTrue(SenderFilter.isOtpOrPromo("123456 is your OTP for txn of Rs 500"))
        assertTrue(SenderFilter.isOtpOrPromo("Special offer just for you!"))
        assertTrue(SenderFilter.isOtpOrPromo("You could WIN a car"))
        assertTrue(SenderFilter.isOtpOrPromo("Your loan approved instantly, apply today"))
    }

    @Test fun keepsPaymentMessages() {
        val debit = msg("XX-HDFCBK", "Rs 250.00 debited from a/c XX1234 to VPA shop@upi on 01-11-26.")
        assertTrue(SenderFilter.keep(debit))
        // "win" inside another word must not trigger the promo filter.
        assertFalse(SenderFilter.isOtpOrPromo("Paid Rs 99 to Windows Store"))
    }

    @Test fun keepsBankAlertsWithOtpSafetyLine() {
        assertFalse(SenderFilter.isOtpOrPromo("Sent Rs.1.00 from A/c *1234 to x@upi. Never share your OTP/PIN with anyone."))
        assertFalse(SenderFilter.isOtpOrPromo("Rs 500 debited. Don't share OTP. Not you? Call 1800 000 000"))
        assertFalse(SenderFilter.isOtpOrPromo("Rs 500 debited. Never share your OTP, CVV or PIN."))
    }

    @Test fun stillDropsRealOtps() {
        assertTrue(SenderFilter.isOtpOrPromo("482913 is your OTP for login"))
        assertTrue(SenderFilter.isOtpOrPromo("OTP: 4821. Valid for 5 mins"))
        assertTrue(SenderFilter.isOtpOrPromo("Use verification code 1234"))
        assertTrue(SenderFilter.isOtpOrPromo("Your one time password is 9981"))
    }

    @Test fun keepRequiresBothRules() {
        assertFalse(SenderFilter.keep(msg("+919876543210", "Rs 250 debited")))
        assertFalse(SenderFilter.keep(msg("AD-SBIINB", "Your OTP is 4321")))
    }
}
