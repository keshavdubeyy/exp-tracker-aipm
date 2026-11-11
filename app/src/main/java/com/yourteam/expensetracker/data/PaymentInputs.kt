package com.yourteam.expensetracker.data

import com.yourteam.expensetracker.insights.BankDebit
import com.yourteam.expensetracker.insights.ChatLine
import com.yourteam.expensetracker.insights.StartedPayment
import com.yourteam.expensetracker.insights.UserAnswer
import com.yourteam.expensetracker.parser.Amounts
import com.yourteam.expensetracker.parser.SmsAmountParser
import com.yourteam.expensetracker.sms.SmsMessage

/** Converts stored rows into the inputs of [com.yourteam.expensetracker.insights.Reconciler]. */
object PaymentInputs {

    fun started(p: PendingPayment): StartedPayment? {
        val paise = Amounts.toPaise(p.amountText) ?: return null
        val answer = when (p.status) {
            PendingPayment.STATUS_USER_CONFIRMED -> UserAnswer.YES
            PendingPayment.STATUS_DISMISSED -> UserAnswer.NO
            else -> UserAnswer.NONE
        }
        return StartedPayment(p.id, paise, p.payee, p.detectedAt, answer, manual = p.appName == PendingPayment.APP_MANUAL)
    }

    fun chat(s: ScreenPayment): ChatLine? =
        Amounts.toPaise(s.amountText)?.let { ChatLine(s.dedupeKey, it, s.payee, s.capturedAt) }

    /** Null if the SMS isn't a debit with a readable amount. */
    fun debit(m: SmsMessage): BankDebit? =
        SmsAmountParser.debitAmountText(m.body)?.let(Amounts::toPaise)?.let { BankDebit(m.id, it, m.dateMillis, m.address) }
}
