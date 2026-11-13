package com.yourteam.expensetracker.parser

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class UpiQrTest {
    @Test fun readsShopQr() {
        val qr = UpiQr.parse("upi://pay?pa=rajutea@okicici&pn=Raju%20Tea%20Stall&mc=5812&tr=ABC123")!!
        assertEquals("rajutea@okicici", qr.vpa)
        assertEquals("Raju Tea Stall", qr.displayName)
        assertNull(qr.amountText)
        assertTrue(qr.isMerchant)
        // Typed amount added, the QR's own parameters kept, INR added.
        assertEquals("upi://pay?pa=rajutea@okicici&pn=Raju%20Tea%20Stall&mc=5812&tr=ABC123&am=40&cu=INR", qr.payUri("40"))
    }

    @Test fun fixedAmountIsKept() {
        val qr = UpiQr.parse("upi://pay?pa=shop@ybl&pn=Shop&am=250.00&cu=INR")!!
        assertEquals("250.00", qr.amountText)
        assertEquals("upi://pay?pa=shop@ybl&pn=Shop&am=250.00&cu=INR", qr.payUri("999"))
    }

    @Test fun personalQrAndJunk() {
        val qr = UpiQr.parse("upi://pay?pa=atharva@okaxis")!!
        assertEquals("atharva@okaxis", qr.displayName)
        assertTrue(!qr.isMerchant)
        assertNull(UpiQr.parse("https://example.com"))
        assertNull(UpiQr.parse("upi://pay?pn=NoId"))
    }

    @Test fun readsAppResponse() {
        assertEquals(UpiResult.SUCCESS, UpiResponse.parse("txnId=1&responseCode=00&Status=SUCCESS&txnRef=x"))
        assertEquals(UpiResult.FAILURE, UpiResponse.parse("Status=FAILURE"))
        assertEquals(UpiResult.UNKNOWN, UpiResponse.parse("Status=SUBMITTED"))
        assertEquals(UpiResult.UNKNOWN, UpiResponse.parse(null))
        assertEquals(UpiResult.SUCCESS, UpiResponse.parse(null, "success"))
    }
}
