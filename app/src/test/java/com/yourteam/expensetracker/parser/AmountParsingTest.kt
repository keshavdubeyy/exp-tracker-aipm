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

    // Bug: these credits used to come back as debits because they also say "transferred / paid / sent".
    @Test fun creditsThatMentionDebitWordsAreNotDebits() {
        val leaks = listOf(
            "Your a/c XX1234 is credited with INR 500.00 on 10-11-26. Amount transferred by PRIYA SHARMA via IMPS. -PNB",
            "Rs.500 credited to A/c XX1234 on 10-11-26. Paid by RAVI KUMAR (UPI Ref 600011112222)",
            "INR 500.00 credited to A/c XX1234. Money sent by Priya Sharma. UPI Ref 600011112222",
            "Priya Sharma has paid you Rs 500 on Google Pay",
        )
        leaks.forEach {
            assertNull(it, SmsAmountParser.debitAmountText(it))
            assertEquals(it, SmsDirection.CREDIT, SmsAmountParser.direction(it))
        }
    }

    @Test fun direction() {
        assertEquals(SmsDirection.DEBIT, SmsAmountParser.direction("Rs 500.00 debited from A/c XX1234 and credited to priya@okaxis UPI Ref 1234"))
        assertEquals(SmsDirection.DEBIT, SmsAmountParser.direction("ICICI Bank Acct XX123 debited for Rs 20.00 on 09-Nov-26; SHOP credited."))
        assertEquals(SmsDirection.DEBIT, SmsAmountParser.direction("You paid Rs 120 to Chai Point"))
        assertEquals(SmsDirection.CREDIT, SmsAmountParser.direction("Received Rs.500.00 in your Kotak Bank a/c X1234 from priya@ybl on 10-11-26."))
        assertNull(SmsAmountParser.direction("Your A/c XX1234 balance is Rs 5,000"))
    }

    @Test fun creditAmounts() {
        assertEquals("500", SmsAmountParser.creditAmountText("Dear SBI UPI User, ur A/cX1234 credited by Rs500 on 10Nov26 by (Ref no 123456789012)"))
        assertEquals("500.00", SmsAmountParser.creditAmountText("Credit Alert!\nRs.500.00 credited to HDFC Bank A/c XX1234 on 10-11-26 from VPA priya@okaxis (UPI 123)"))
        assertEquals("45,000.00", SmsAmountParser.creditAmountText("INR 45,000.00 credited to A/c no. XX1234 on 01-11-26. Info- NEFT-ACME PVT LTD. Avl Bal INR 50,000.00"))
        assertNull(SmsAmountParser.creditAmountText("Sent Rs.20.00 From HDFC Bank A/C *1234 To shop@upi On 09/11/26"))
        // Paying a credit card bill isn't money arriving in the bank account.
        assertNull(SmsAmountParser.creditAmountText("Payment of Rs 5,000 received towards your Credit Card XX9999. Thank you"))
    }

    @Test fun whoSentIt() {
        assertEquals("priya@okaxis", SmsAmountParser.creditFrom("Rs.500.00 credited to HDFC Bank A/c XX1234 on 10-11-26 from VPA priya@okaxis (UPI 123)"))
        assertEquals("Priya Sharma", SmsAmountParser.creditFrom("Acct XX123 is credited with Rs 500.00 on 10-Nov-26 from PRIYA SHARMA. UPI:123-ICICI Bank."))
        assertEquals("Priya Sharma", SmsAmountParser.creditFrom("INR 500.00 credited to A/c no. XX1234 on 10-11-26. Info- UPI/P2A/123456789012/PRIYA SHARMA. Not you? Call 1800 - Axis Bank"))
        assertEquals("Priya Sharma", SmsAmountParser.creditFrom("your A/c X1234-credited by Rs.500 on 10Nov26 transfer from PRIYA SHARMA Ref No 600011112222 -SBI"))
        assertEquals("Ravi Kumar", SmsAmountParser.creditFrom("Your a/c XX1234 is credited with INR 500.00. Amount transferred by RAVI KUMAR via IMPS. -PNB"))
        assertEquals("priya@ybl", SmsAmountParser.creditFrom("Received Rs.500.00 in your Kotak Bank a/c X1234 from priya@ybl on 10-11-26."))
        assertNull(SmsAmountParser.creditFrom("Dear SBI UPI User, ur A/cX1234 credited by Rs500 on 10Nov26 by (Ref no 123456789012)"))
        assertNull(SmsAmountParser.creditFrom("Rs 500 credited to your A/c XX1234 by UPI."))
    }

    @Test fun refunds() {
        val sms = "Refund of Rs.349.00 for your Swiggy order has been credited to your A/c XX1234. -HDFC"
        assertEquals("349.00", SmsAmountParser.creditAmountText(sms))
        assertEquals(true, SmsAmountParser.isRefund(sms))
        assertEquals(false, SmsAmountParser.isRefund("Rs 500 credited to A/c XX1234 from PRIYA."))
    }
}
