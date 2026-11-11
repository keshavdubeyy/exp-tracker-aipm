package com.yourteam.expensetracker.parser

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AccountParserTest {
    @Test fun bankFromSender() {
        assertEquals("SBI", AccountParser.bank("AX-SBIUPI-S"))
        assertEquals("HDFC Bank", AccountParser.bank("VM-HDFCBK"))
        assertNull(AccountParser.bank("AX-SWIGGY"))
    }

    @Test fun lastFourOnly() {
        assertEquals("1234", AccountParser.last4("Dear UPI user A/C X1234 debited by 20.0"))
        assertEquals("5678", AccountParser.last4("your A/c XX5678-credited by Rs.500"))
        assertNull(AccountParser.last4("Paid to Chai Point"))
    }
}
