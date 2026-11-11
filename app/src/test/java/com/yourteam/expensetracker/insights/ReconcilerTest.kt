package com.yourteam.expensetracker.insights

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ReconcilerTest {
    private val min = 60_000L
    private val t = 1_000_000_000L

    private fun started(id: Long, paise: Long, at: Long, answer: UserAnswer = UserAnswer.NONE) =
        StartedPayment(id, paise, "Chai Point", at, answer)

    @Test fun yesThenLateSmsIsOnePayment() {
        val s = started(1, 1200, t, UserAnswer.YES)
        val sms = BankDebit(10, 1200, t + 4 * min, "AX-SBIUPI-S")
        val payments = Reconciler.reconcile(listOf(s), emptyList(), listOf(sms))
        assertEquals(1, payments.size)
        assertEquals(s, payments[0].started)
        assertEquals(sms, payments[0].sms)
        assertEquals("Chai Point", payments[0].payee)
    }

    @Test fun startedChatAndSmsAllLinkTogether() {
        val s = started(1, 2000, t)
        val chat = ChatLine("c1", 2000, "Chai Point", t - 30_000)
        val sms = BankDebit(10, 2000, t + 2 * min, "AX-SBIUPI-S")
        val p = Reconciler.reconcile(listOf(s), listOf(chat), listOf(sms)).single()
        assertEquals(chat, p.chat)
        assertEquals(sms, p.sms)
        assertFalse(p.needsAnswer)
    }

    @Test fun oneSmsNeverConfirmsTwoPayments() {
        val a = started(1, 2000, t)
        val b = started(2, 2000, t + 3 * min)
        val sms = BankDebit(10, 2000, t + 4 * min, "AX-SBIUPI-S")
        val payments = Reconciler.reconcile(listOf(a, b), emptyList(), listOf(sms))
        assertEquals(2, payments.size)
        assertEquals(sms, payments.first { it.started == b }.sms) // b is closer in time
        assertNull(payments.first { it.started == a }.sms)
        assertTrue(payments.first { it.started == a }.needsAnswer)
    }

    @Test fun noWithoutEvidenceIsHidden() {
        assertTrue(Reconciler.reconcile(listOf(started(1, 500, t, UserAnswer.NO)), emptyList(), emptyList()).isEmpty())
    }

    @Test fun noButBankDebitedIsAConflict() {
        val p = Reconciler.reconcile(
            listOf(started(1, 1200, t, UserAnswer.NO)), emptyList(), listOf(BankDebit(10, 1200, t + min, "AX-SBIUPI-S")),
        ).single()
        assertTrue(p.conflict)
    }

    @Test fun unmatchedSmsAndChatStandAlone() {
        val payments = Reconciler.reconcile(
            emptyList(),
            listOf(ChatLine("c1", 500, "Priya", t)),
            listOf(BankDebit(10, 9900, t + min, "AX-SBIUPI-S")),
        )
        assertEquals(2, payments.size)
        assertEquals(9900L, payments[0].paise) // newest first
    }

    @Test fun differentAmountsDoNotLink() {
        val payments = Reconciler.reconcile(
            listOf(started(1, 1200, t, UserAnswer.YES)), emptyList(), listOf(BankDebit(10, 1300, t + min, "X")),
        )
        assertEquals(2, payments.size)
    }

    @Test fun moneyFormat() {
        assertEquals("₹12.00", Money.format(1200))
        assertEquals("₹1.05", Money.format(105))
        assertEquals("₹1,250.50", Money.format(125050))
        assertEquals("₹1,23,456.50", Money.format(12345650))
    }

    @Test fun manualPaymentCountsAndMergesWithLaterSms() {
        val manual = StartedPayment(5, 4000, "Raju Tea Stall", t, UserAnswer.YES, manual = true)
        val alone = Reconciler.reconcile(listOf(manual), emptyList(), emptyList()).single()
        assertFalse(alone.needsAnswer) // already confirmed, so it counts in totals
        assertTrue(Spending.counts(alone))

        // Same amount, bank texts a minute later: one payment, not two.
        val sms = BankDebit(11, 4000, t + min, "AX-SBIUPI-S")
        val merged = Reconciler.reconcile(listOf(manual), emptyList(), listOf(sms))
        assertEquals(1, merged.size)
        assertEquals(sms, merged[0].sms)
    }
}
