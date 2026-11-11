package com.yourteam.expensetracker.parser

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

// Made-up screens only.
class GPayScreenParserTest {

    @Test fun readsSuccessScreen() {
        val p = GPayScreenParser.parse(
            listOf("₹1", "Paid to Ramesh Kumar", "ramesh@okaxis", "UPI transaction ID", "123456789012", "Done"),
        )!!
        assertEquals("1", p.amountText)
        assertEquals("Ramesh Kumar", p.payee)
        assertEquals("123456789012", p.upiRef)
    }

    @Test fun payeeOnNextLineAndSplitRupeeSign() {
        val p = GPayScreenParser.parse(listOf("₹", "1,250.50", "Payment successful", "Paid to", "Chai Point"))!!
        assertEquals("1,250.50", p.amountText)
        assertEquals("Chai Point", p.payee)
        assertNull(p.upiRef)
    }

    @Test fun skipsFailedAndPending() {
        assertNull(GPayScreenParser.parse(listOf("₹50", "Paid to Shop", "Payment failed")))
        assertNull(GPayScreenParser.parse(listOf("₹50", "Paid to Shop", "Payment pending")))
    }

    @Test fun skipsHistoryListsWithManyAmounts() {
        assertNull(GPayScreenParser.parse(listOf("Paid to A", "₹20", "Paid to B", "₹45")))
    }

    @Test fun skipsScreensWithoutSuccessWording() {
        assertNull(GPayScreenParser.parse(listOf("Enter amount", "₹1", "Pay")))
        assertNull(GPayScreenParser.parse(listOf("Received from Priya", "₹100")))
    }

    @Test fun maskedHidesNamesAndNumbers() {
        val m = GPayScreenParser.masked(listOf("₹1", "Paid to Ramesh Kumar", "ramesh@okaxis", "UPI transaction ID 123456789012"))
        assertEquals("₹# | Paid to • • | • | UPI transaction ID ############", m)
    }
}
