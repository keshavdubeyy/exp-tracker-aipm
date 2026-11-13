package com.yourteam.expensetracker.data

import com.yourteam.expensetracker.insights.BankCredit
import com.yourteam.expensetracker.insights.BankDebit
import com.yourteam.expensetracker.insights.ChatLine
import com.yourteam.expensetracker.insights.StartedPayment
import com.yourteam.expensetracker.insights.UserAnswer
import com.yourteam.expensetracker.parser.Amounts
import com.yourteam.expensetracker.parser.GPayChatParser
import com.yourteam.expensetracker.parser.SmsAmountParser
import com.yourteam.expensetracker.parser.UpiPaidParser
import com.yourteam.expensetracker.parser.UpiReceivedParser
import com.yourteam.expensetracker.sms.SmsMessage

/** Converts stored rows into the inputs of [com.yourteam.expensetracker.insights.Reconciler]. */
object PaymentInputs {

    /** Null for money in added by hand (see [manualCredit]). */
    fun started(p: PendingPayment): StartedPayment? {
        if (PendingPayment.isManualIn(p.appName)) return null
        val paise = Amounts.toPaise(p.amountText) ?: return null
        val answer = when (p.status) {
            PendingPayment.STATUS_USER_CONFIRMED -> UserAnswer.YES
            PendingPayment.STATUS_DISMISSED -> UserAnswer.NO
            else -> UserAnswer.NONE
        }
        return StartedPayment(
            p.id, paise, p.payee, p.detectedAt, answer,
            manual = PendingPayment.isManualOut(p.appName),
            app = if (p.appName == PendingPayment.APP_MANUAL_CASH) StartedPayment.CASH else p.appName,
        )
    }

    /** Money in typed in by hand (bank account or cash); null for every other row. */
    fun manualCredit(p: PendingPayment): BankCredit? {
        if (!PendingPayment.isManualIn(p.appName)) return null
        val paise = Amounts.toPaise(p.amountText)?.takeIf { it > 0 } ?: return null
        val source = if (PendingPayment.isCash(p.appName)) BankCredit.SOURCE_CASH else BankCredit.SOURCE_MANUAL
        return BankCredit(p.id, paise, p.detectedAt, p.payee.takeIf(String::isNotBlank), refund = false, source = source)
    }

    /** Null for "Payment to you" lines saved before they were skipped: that's money received. */
    fun chat(s: ScreenPayment): ChatLine? =
        if (GPayChatParser.isToYou(s.payee)) null else Amounts.toPaise(s.amountText)?.let { ChatLine(s.dedupeKey, it, s.payee, s.capturedAt) }

    /** Null if the SMS isn't a debit with a readable amount. */
    fun debit(m: SmsMessage): BankDebit? =
        SmsAmountParser.debitAmountText(m.body)?.let(Amounts::toPaise)?.let {
            BankDebit(m.id, it, m.dateMillis, m.address, SmsAmountParser.debitTo(m.body), SmsAmountParser.vpaOf(m.body), SmsAmountParser.methodOf(m.body))
        }

    /** "Paid ₹N to X" from any payment app's notification; null for anything else. */
    fun paid(n: UpiNotification): ChatLine? =
        UpiPaidParser.parse(n.title, n.text)?.let { r ->
            Amounts.toPaise(r.amountText)?.takeIf { it > 0 }?.let { ChatLine("n${n.id}", it, r.to ?: n.appName, n.postedAt, "${n.appName} alert") }
        }

    /** "X paid you ₹N" from a UPI app; null for any other notification. */
    fun credit(n: UpiNotification): BankCredit? =
        UpiReceivedParser.parse(n.title, n.text)?.let { r ->
            Amounts.toPaise(r.amountText)?.takeIf { it > 0 }?.let { BankCredit(n.id, it, n.postedAt, r.from, refund = false, source = n.appName) }
        }

    /** Null if the SMS isn't money coming into the account with a readable amount. */
    fun credit(m: SmsMessage): BankCredit? =
        SmsAmountParser.creditAmountText(m.body)?.let(Amounts::toPaise)?.takeIf { it > 0 }?.let {
            BankCredit(m.id, it, m.dateMillis, SmsAmountParser.creditFrom(m.body), SmsAmountParser.isRefund(m.body), kind = SmsAmountParser.creditKind(m.body))
        }
}
