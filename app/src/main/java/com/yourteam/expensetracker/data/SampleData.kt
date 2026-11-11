package com.yourteam.expensetracker.data

import com.yourteam.expensetracker.insights.BankDebit
import com.yourteam.expensetracker.insights.ChatLine
import com.yourteam.expensetracker.insights.Reconciler
import com.yourteam.expensetracker.insights.StartedPayment
import com.yourteam.expensetracker.insights.UserAnswer
import com.yourteam.expensetracker.sms.SmsMessage
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * Made-up payments for trying the app's screens. Nothing here is real: names, amounts and
 * SMS text are invented. Dates are relative to today so the screens always look current.
 * Covers every state: needs an answer, the bank disagrees, fully matched, SMS only, chat only.
 */
object SampleData {

    private const val SENDER = "AX-SBIUPI-S"

    fun snapshot(
        answers: Map<Long, UserAnswer>,
        today: LocalDate = LocalDate.now(),
        zone: ZoneId = ZoneId.systemDefault(),
        /** Payments added by hand while Sample data is on, kept in memory only. */
        manual: List<StartedPayment> = emptyList(),
    ): PaymentSnapshot {
        fun at(daysAgo: Long, hour: Int, minute: Int): Long =
            today.minusDays(daysAgo).atTime(LocalTime.of(hour, minute)).atZone(zone).toInstant().toEpochMilli()

        val started = mutableListOf<StartedPayment>()
        val chats = mutableListOf<ChatLine>()
        val sms = mutableListOf<SmsMessage>()
        var smsId = -1L

        fun start(id: Long, rupees: Long, payee: String, t: Long, default: UserAnswer) {
            started += StartedPayment(id, rupees * 100, payee, t, answers[id] ?: default)
        }
        fun chat(rupees: Long, payee: String, t: Long) {
            chats += ChatLine("sample-${chats.size}", rupees * 100, payee, t)
        }
        fun debitSms(rupees: Long, payee: String, t: Long) {
            val date = DateTimeFormatter.ofPattern("ddMMMyy").format(java.time.Instant.ofEpochMilli(t).atZone(zone))
            sms += SmsMessage(
                smsId--, SENDER,
                "Dear UPI user A/C X1234 debited by $rupees.0 on date $date trf to ${payee.uppercase()} " +
                    "Refno 6000${(t / 60_000) % 10_000}. If not u? call 1800111109. -SBI",
                t,
            )
        }

        // Needs an answer: started in GPay, nothing else recorded it yet.
        start(-1, 40, "Raju Tea Stall", at(0, 9, 12), UserAnswer.NONE)
        start(-2, 250, "Metro card recharge", at(1, 18, 40), UserAnswer.NONE)

        // The bank disagrees: user said "No", but SBI sent a debit SMS.
        start(-3, 120, "Sharma Medicals", at(2, 20, 5), UserAnswer.NO)
        debitSms(120, "Sharma Medicals", at(2, 20, 6))

        // Fully matched: started, confirmed, GPay chat and bank SMS.
        start(-4, 12, "Chai Point", at(0, 8, 30), UserAnswer.YES)
        chat(12, "Chai Point", at(0, 8, 30) + 20_000)
        debitSms(12, "Chai Point", at(0, 8, 31))

        // GPay chat + bank SMS.
        chat(349, "Swiggy", at(1, 13, 15)); debitSms(349, "Swiggy", at(1, 13, 16))
        chat(2450, "Big Bazaar", at(6, 19, 2)); debitSms(2450, "Big Bazaar", at(6, 19, 3))
        chat(180, "Chai Point", at(7, 17, 45)); debitSms(180, "Chai Point", at(7, 17, 46))
        val rentDay = (today.dayOfMonth - 1).toLong()
        chat(15000, "Ramesh Gupta", at(rentDay, 10, 0)); debitSms(15000, "Ramesh Gupta", at(rentDay, 10, 2))

        // Bank SMS only (no name yet), GPay chat only, and "Yes" only.
        debitSms(1499, "Airtel", at(3, 11, 20))
        debitSms(649, "Netflix", at(8, 7, 0))
        chat(60, "Priya Sharma", at(4, 21, 10))
        start(-5, 30, "Auto rickshaw", at(5, 9, 50), UserAnswer.YES)

        // Not payments: a credit SMS and a money-received notification.
        sms += SmsMessage(
            smsId--, SENDER,
            "Dear SBI User, your A/c X1234-credited by Rs.500 on ${DateTimeFormatter.ofPattern("ddMMMyy").format(today.minusDays(4))} " +
                "transfer from PRIYA SHARMA Ref No 600011112222 -SBI",
            at(4, 21, 30),
        )
        val notes = listOf(
            UpiNotification(-1, "com.google.android.apps.nbu.paisa.user", "GPay", "Priya Sharma paid you ₹500", "Paid to your SBI account", at(4, 21, 30)),
        )

        return PaymentSnapshot(
            bankSms = sms.sortedByDescending { it.dateMillis },
            notifications = notes,
            payments = Reconciler.reconcile(started + manual, chats, sms.mapNotNull(PaymentInputs::debit)),
        )
    }
}
