package com.yourteam.expensetracker.parser

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

// Made-up notifications only (AGENTS.md rule 3).
class UpiReceivedParserTest {

    @Test fun gpayPaidYou() {
        assertEquals(UpiReceived("Priya Sharma", "99.00"), UpiReceivedParser.parse("PRIYA SHARMA paid you ₹99.00", "Payment from PhonePe"))
        assertEquals(UpiReceived("Ravi", "1,250"), UpiReceivedParser.parse("Ravi sent you ₹1,250", ""))
    }

    @Test fun receivedFrom() {
        assertEquals(UpiReceived("Priya Sharma", "500"), UpiReceivedParser.parse("Money received", "You received ₹ 500 from Priya Sharma"))
        assertEquals(UpiReceived("priya@ybl", "50"), UpiReceivedParser.parse("₹50 received from priya@ybl", "In your SBI account"))
    }

    @Test fun notMoneyIn() {
        assertNull(UpiReceivedParser.parse("₹250 paid to Ramesh", "Paid from HDFC Bank 1234"))
        assertNull(UpiReceivedParser.parse("Payment successful", "Rs.1,200 sent to shop@upi"))
        assertNull(UpiReceivedParser.parse("Priya has requested ₹500", "Pay now on GPay"))
        assertNull(UpiReceivedParser.parse("You won a scratch card!", "Win up to ₹1000 cashback"))
    }

    @Test fun moreWordingsFromOtherApps() {
        assertEquals(UpiReceived("Atharva Dubey", "20.00"), UpiReceivedParser.parse("Atharva Dubey", "Paid you ₹20.00"))
        assertEquals(UpiReceived("Atharva", "20"), UpiReceivedParser.parse("Atharva sent ₹20 to you", ""))
        assertEquals(UpiReceived("Atharva", "20"), UpiReceivedParser.parse("₹20 credited to your account from Atharva", ""))
        assertEquals(UpiReceived(null, "20"), UpiReceivedParser.parse("Money received", "₹20 received in your bank account"))
        assertEquals(UpiReceived("Atharva", "20"), UpiReceivedParser.parse("Atharva", "₹20 received"))
        assertEquals(null, UpiReceivedParser.parse("Atharva received ₹20 from you", ""))
        assertEquals(null, UpiReceivedParser.parse("Payment from Atharva failed", "₹20 could not be received"))
    }
}
