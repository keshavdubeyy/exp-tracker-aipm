package com.yourteam.expensetracker.parser

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class UpiPaidParserTest {
    @Test fun moneyOutFromAnyApp() {
        assertEquals(UpiPaid("Priya Sharma", "99"), UpiPaidParser.parse("Paid ₹99 to Priya Sharma", ""))
        assertEquals(UpiPaid("Raju Tea Stall", "20.00"), UpiPaidParser.parse("Payment successful", "You paid ₹20.00 to RAJU TEA STALL"))
        assertEquals(UpiPaid("Ramesh", "1,250"), UpiPaidParser.parse("₹1,250 sent to Ramesh", "via Navi UPI"))
        assertEquals(UpiPaid("Zomato", "349"), UpiPaidParser.parse("Payment of Rs.349 to Zomato successful", ""))
        assertEquals(UpiPaid("Atharva", "20"), UpiPaidParser.parse("Atharva received ₹20 from you", ""))
        assertEquals(UpiPaid(null, "500"), UpiPaidParser.parse("Rs 500 debited from your a/c XX1234", ""))
    }

    @Test fun notMoneyOut() {
        assertNull(UpiPaidParser.parse("Payment of ₹99 to Priya failed", ""))
        assertNull(UpiPaidParser.parse("Priya requested ₹99", "Pay now"))
        assertNull(UpiPaidParser.parse("Priya paid you ₹99", ""))
        assertNull(UpiPaidParser.parse("₹99 credited to your account", ""))
        assertNull(UpiPaidParser.parse("Get ₹100 cashback on your next payment", ""))
    }
}
