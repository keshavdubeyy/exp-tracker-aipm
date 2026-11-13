package com.yourteam.expensetracker.parser

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

// Made-up messages only (AGENTS.md rule 3).
class DebitPayeeTest {

    @Test fun payeeFromCommonBankFormats() {
        assertEquals("Chai Point", SmsAmountParser.debitTo("Dear UPI user A/C X1234 debited by 20.0 on date 09Nov26 trf to CHAI POINT Refno 123456789012. If not u? call 1800000000. -SBI"))
        assertEquals("shop@upi", SmsAmountParser.debitTo("Sent Rs.20.00 From HDFC Bank A/C *1234 To shop@upi On 09/11/26"))
        assertEquals("Shop", SmsAmountParser.debitTo("ICICI Bank Acct XX123 debited for Rs 20.00 on 09-Nov-26; SHOP credited."))
        assertEquals("Store", SmsAmountParser.debitTo("INR 1,250 spent on card XX99 at STORE on 09-11-26"))
        assertEquals("priya@okaxis", SmsAmountParser.debitTo("Rs 500.00 debited from A/c XX1234 and credited to priya@okaxis UPI Ref 1234"))
        assertEquals("Chai Point", SmsAmountParser.debitTo("You paid Rs 120 to Chai Point"))
        assertEquals("Zomato", SmsAmountParser.debitTo("Rs 349 debited from A/c XX1234. Info- UPI/P2M/600011112222/ZOMATO/ Avl Bal Rs 5,000"))
    }

    @Test fun accountsAndCreditsAreNotPayees() {
        assertNull(SmsAmountParser.debitTo("Rs 5,000 debited from A/c XX1234 to A/c XX5678 on 09-11-26"))
        assertNull(SmsAmountParser.debitTo("Rs 500 credited to your A/c XX1234 by UPI. Received from Priya"))
    }

    @Test fun vpaAndMethod() {
        val sms = "Rs 349 debited from A/c XX1234 to VPA zomato@hdfcbank UPI Ref 600011112222"
        assertEquals("zomato@hdfcbank", SmsAmountParser.vpaOf(sms))
        assertEquals("zomato@hdfcbank", SmsAmountParser.debitTo(sms))
        assertEquals("UPI", SmsAmountParser.methodOf(sms))
        assertEquals("Card", SmsAmountParser.methodOf("INR 1,250 spent on card XX99 at STORE"))
        assertEquals("IMPS", SmsAmountParser.methodOf("Rs 900 debited via IMPS to RAVI"))
        assertNull(SmsAmountParser.methodOf("Rs 900 debited"))
    }

    @Test fun creditKinds() {
        assertEquals(CreditKind.SALARY, SmsAmountParser.creditKind("INR 45,000 credited to A/c XX1234. Info- SALARY NOV ACME PVT LTD"))
        assertEquals(CreditKind.INTEREST, SmsAmountParser.creditKind("Rs 120 credited to A/c XX1234 towards Interest"))
        assertEquals(CreditKind.CASHBACK, SmsAmountParser.creditKind("Cashback of Rs 25 credited to A/c XX1234"))
        assertEquals(CreditKind.REFUND, SmsAmountParser.creditKind("Refund of Rs 349 credited to your A/c XX1234"))
        assertNull(SmsAmountParser.creditKind("Rs 500 credited to A/c XX1234 from Priya"))
    }
}
