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
)

/** A "Payment to X ₹N Paid" line read from a GPay chat. */
data class ChatLine(val key: String, val paise: Long, val payee: String, val at: Long)

/** A bank debit SMS. */
data class BankDebit(val smsId: Long, val paise: Long, val at: Long, val sender: String)

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
        val startedToChat = pairUp(started, chats, { it.paise }, { it.at }, { it.paise }, { it.at })
        val groups = started.map { s ->
            Payment(paise = s.paise, payee = s.payee, at = s.at, started = s, chat = startedToChat[s])
        } + chats.filter { it !in startedToChat.values }.map { c ->
            Payment(paise = c.paise, payee = c.payee, at = c.at, chat = c)
        }

        // 2. Each group <-> bank debit SMS.
        val groupToSms = pairUp(groups, debits, { it.paise }, { it.at }, { it.paise }, { it.at })
        val withSms = groups.map { g -> groupToSms[g]?.let { g.copy(sms = it) } ?: g }
        val smsOnly = debits.filter { it !in groupToSms.values }.map { d ->
            Payment(paise = d.paise, payee = null, at = d.at, sms = d)
        }

        return (withSms + smsOnly)
            .filterNot { it.started?.answer == UserAnswer.NO && it.chat == null && it.sms == null }
            .sortedByDescending { it.at }
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
