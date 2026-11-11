package com.yourteam.expensetracker.parser

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

// Made-up screens only.
class UpiPinScreenParserTest {

    private val keypad = (0..9).map(Int::toString)

    @Test fun readsPinScreen() {
        val p = UpiPinScreenParser.parse(
            listOf("State Bank of India", "Bank Name", "XXXX1234", "Account Number", "Close",
                "Pay ₹20.00", "To Chai Point", "Enter your UPI PIN") + keypad + "Pay",
        )!!
        assertEquals("20.00", p.amountText)
        assertEquals("Chai Point", p.payee)
    }

    @Test fun needsTheKeypad() {
        // Amount-entry or summary screens may say "Pay ₹20" but have no number keypad.
        assertNull(UpiPinScreenParser.parse(listOf("Pay ₹20.00", "To Chai Point", "Pay")))
    }

    @Test fun needsAmountAndPayee() {
        assertNull(UpiPinScreenParser.parse(listOf("Enter your UPI PIN") + keypad))
        assertNull(UpiPinScreenParser.parse(listOf("Pay ₹20.00", "Enter your UPI PIN") + keypad))
    }
}
