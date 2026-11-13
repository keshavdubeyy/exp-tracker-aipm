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

    @Test fun readsOtherAppsWordings() {
        // Navi / Paytm style: "Sending ₹20", "To: X", a bare amount line.
        val a = UpiPinScreenParser.parse(listOf("Sending ₹20", "To: Vindya Canteen", "ENTER 4-DIGIT UPI PIN") + keypad)!!
        assertEquals("20", a.amountText); assertEquals("Vindya Canteen", a.payee)
        val b = UpiPinScreenParser.parse(listOf("₹ 150.50", "Paying to Ravi Kumar", "Enter UPI PIN") + keypad)!!
        assertEquals("150.50", b.amountText); assertEquals("Ravi Kumar", b.payee)
    }

    @Test fun amountKeypadIsNotThePinScreen() {
        // An app's own amount entry: bare amount + payee + keypad, but no "UPI PIN".
        assertNull(UpiPinScreenParser.parse(listOf("₹20", "To Ravi Kumar") + keypad))
    }
}
