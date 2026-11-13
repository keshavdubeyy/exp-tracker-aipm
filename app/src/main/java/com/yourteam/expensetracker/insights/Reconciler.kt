// ALL money math lives here, plain Kotlin only (AGENTS.md rule 1).
package com.yourteam.expensetracker.insights

import kotlin.math.abs

/** The user's answer to "Did this payment go through?". */
enum class UserAnswer { NONE, YES, NO }

/**
 * A payment that started (UPI PIN screen seen), or one the user typed in ([manual], always YES).
 * Manual ones still go through matching, so a later bank SMS for the same payment merges with it.
 */
data class StartedPayment(
    val id: Long,
    val paise: Long,
    val payee: String,
    val at: Long,
    val answer: UserAnswer,
    val manual: Boolean = false,
    /** UPI app it was started in ("GPay", "PhonePe"), or [CASH] for cash typed in by hand. */
    val app: String = "GPay",
) {
    /** Cash never shows up in a bank SMS or app, so it is never paired with one. */
    val cash: Boolean get() = app == CASH

    companion object {
        const val CASH = "Cash"
    }
}

/**
 * A record from a payment app that money left: a "Payment to X ₹N Paid" line read from a GPay
 * chat, or a "Paid ₹N to X" notification from any payment app. [source] is shown as the record
 * ("GPay chat", "PhonePe alert").
 */
data class ChatLine(val key: String, val paise: Long, val payee: String, val at: Long, val source: String = "GPay chat")

/** A bank debit SMS. */
data class BankDebit(
    val smsId: Long,
    val paise: Long,
    val at: Long,
    val sender: String,
    /** Who was paid, as written in the SMS ("Chai Point", "shop@upi"); null if not named. */
    val payee: String? = null,
    /** The payee's UPI ID if the SMS shows one. */
    val vpa: String? = null,
    /** "UPI", "IMPS", "Card", … if the SMS says. */
    val method: String? = null,
)

/**
 * One real-world payment, with whatever evidence we have for it. Each piece of evidence
 * belongs to at most one payment, so nothing is counted twice.
 */
data class Payment(
    val paise: Long,
    val payee: String?,
    val at: Long,
    val started: StartedPayment? = null,
    val chat: ChatLine? = null,
    val sms: BankDebit? = null,
) {
    val key: String = started?.let { "started-${it.id}" } ?: chat?.let { "chat-${it.key}" } ?: "sms-${sms?.smsId}"

    /** Started, no answer yet, and nothing else recorded it. */
    val needsAnswer: Boolean get() = started?.answer == UserAnswer.NONE && chat == null && sms == null

    /** The user said "No", but GPay or the bank says money left the account. */
    val conflict: Boolean get() = started?.answer == UserAnswer.NO && (chat != null || sms != null)
}

/**
 * Builds the payment list from started payments, GPay chat lines and bank debit SMS.
 * Pieces are linked when the amount is the same and the time is within
 * [PaymentMatcher.BEFORE_MS] before to [PaymentMatcher.AFTER_MS] after; closest pairs first,
 * one-to-one. Started payments answered "No" with no other evidence are left out (they didn't
 * happen). Newest first.
 */
object Reconciler {

    fun reconcile(
        started: List<StartedPayment>,
        chats: List<ChatLine>,
        debits: List<BankDebit>,
    ): List<Payment> {
        // 1. Started payment <-> GPay chat line.
        val startedToChat = pairUp(started.filterNot { it.cash }, chats, { it.paise }, { it.at }, { it.paise }, { it.at })
        val groups = started.map { s ->
            Payment(paise = s.paise, payee = s.payee, at = s.at, started = s, chat = startedToChat[s])
        } + chats.filter { it !in startedToChat.values }.map { c ->
            Payment(paise = c.paise, payee = c.payee, at = c.at, chat = c)
        }

        // 2. Each group <-> bank debit SMS.
        val groupToSms = pairUp(groups.filterNot { it.started?.cash == true }, debits, { it.paise }, { it.at }, { it.paise }, { it.at })
        val withSms = groups.map { g -> groupToSms[g]?.let { g.copy(sms = it, payee = g.payee ?: it.payee) } ?: g }
        val smsOnly = debits.filter { it !in groupToSms.values }.map { d ->
            Payment(paise = d.paise, payee = d.payee, at = d.at, sms = d)
        }

        val all = (withSms + smsOnly)
            .filterNot { it.started?.answer == UserAnswer.NO && it.chat == null && it.sms == null }
        return dropDuplicates(all).sortedByDescending { it.at }
    }

    /** A retry: a PIN screen seen (and answered "Yes") this close to the real payment is the same payment. */
    const val RETRY_MS = 10 * 60_000L
    /** Payment apps re-post the same "Paid ₹N to X" alert within about a minute. */
    const val REPOST_MS = 60_000L

    /**
     * The same payment recorded twice, removed so it's counted once:
     *  1. A payment only seen as a PIN screen and answered "Yes" (no bank SMS, no app alert) for the same amount and payee as a
     *     payment a bank SMS or app alert confirmed, within [RETRY_MS]: an earlier or later attempt of it.
     *  2. Two app alerts from the same app for the same amount and payee within [REPOST_MS]: a re-post.
     * Payments typed in by hand and cash are never removed.
     */
    fun dropDuplicates(payments: List<Payment>): List<Payment> {
        fun samePayee(a: Payment, b: Payment) =
            a.payee != null && b.payee != null && a.payee.trim().equals(b.payee.trim(), ignoreCase = true)
        val confirmed = payments.filter { it.chat != null || it.sms != null }
        val retries = payments.filter { p ->
            val s = p.started ?: return@filter false
            s.answer == UserAnswer.YES && !s.manual && !s.cash && p.chat == null && p.sms == null && confirmed.any { q ->
                q.paise == p.paise && samePayee(p, q) && abs(q.at - p.at) <= RETRY_MS
            }
        }.toSet()
        val alertsOnly = payments.filter { it.chat != null && it.sms == null && it.started == null }.sortedBy { it.at }
        val reposts = HashSet<Payment>()
        alertsOnly.forEachIndexed { i, a ->
            if (a in reposts) return@forEachIndexed
            alertsOnly.drop(i + 1).takeWhile { it.at - a.at <= REPOST_MS }
                .filter { it !in reposts && it.paise == a.paise && it.chat!!.source == a.chat!!.source && samePayee(a, it) }
                .forEach { reposts += it }
        }
        return payments.filterNot { it in retries || it in reposts }
    }

    /** Greedy one-to-one pairing: same amount, within the time window, closest in time first. */
    private fun <A, B> pairUp(
        anchors: List<A>,
        records: List<B>,
        anchorPaise: (A) -> Long,
        anchorAt: (A) -> Long,
        recordPaise: (B) -> Long,
        recordAt: (B) -> Long,
    ): Map<A, B> {
        val candidates = anchors.flatMap { a ->
            records.filter { r ->
                recordPaise(r) == anchorPaise(a) &&
                    recordAt(r) in (anchorAt(a) - PaymentMatcher.BEFORE_MS)..(anchorAt(a) + PaymentMatcher.AFTER_MS)
            }.map { r -> Triple(a, r, abs(recordAt(r) - anchorAt(a))) }
        }.sortedBy { it.third }

        val result = LinkedHashMap<A, B>()
        val usedRecords = HashSet<B>()
        for ((a, r, _) in candidates) {
            if (a in result || r in usedRecords) continue
            result[a] = r
            usedRecords += r
        }
        return result
    }
}
