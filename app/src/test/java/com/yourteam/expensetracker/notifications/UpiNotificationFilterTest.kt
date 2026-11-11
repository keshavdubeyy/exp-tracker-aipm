package com.yourteam.expensetracker.notifications

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

// Made-up notifications only (AGENTS.md rule 3).
class UpiNotificationFilterTest {

    @Test fun keepsPayments() {
        assertTrue(UpiNotificationFilter.isPayment("₹250 paid to Ramesh", "Paid from HDFC Bank 1234"))
        assertTrue(UpiNotificationFilter.isPayment("Payment successful", "Rs.1,200 sent to shop@upi"))
        assertTrue(UpiNotificationFilter.isPayment("Money received", "You received ₹ 500 from Priya"))
        assertTrue(UpiNotificationFilter.isPayment("Paid", "INR 99 paid to Swiggy"))
    }

    @Test fun dropsNonPayments() {
        assertFalse(UpiNotificationFilter.isPayment("Priya sent you a message", "Hey, dinner tonight?"))
        assertFalse(UpiNotificationFilter.isPayment("You won a scratch card!", "Win up to ₹1000 cashback"))
        assertFalse(UpiNotificationFilter.isPayment("Special offer", "Get ₹100 off on recharge"))
        assertFalse(UpiNotificationFilter.isPayment("Verify", "Your OTP for ₹250 payment is 4321"))
    }
}
