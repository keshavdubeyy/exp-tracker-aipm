// ALL money math lives here, plain Kotlin only (AGENTS.md rule 1).
package com.yourteam.expensetracker.insights

import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId

/**
 * Money that came *into* the account, from a bank credit SMS or a UPI app's "X paid you ₹N"
 * notification. Kept apart from [Payment] so it can never be added to spending: every spending
 * total only looks at payments.
 */
data class BankCredit(
    /** SMS id, or notification row id when [source] isn't "Bank SMS". */
    val smsId: Long,
    val paise: Long,
    val at: Long,
    /** Who sent it, as written in the SMS; null if the SMS doesn't say. */
    val from: String?,
    /** Refund, reversal or cashback rather than someone sending you money. */
    val refund: Boolean,
    /** "Bank SMS", or the UPI app that told us ("GPay", "PhonePe", …). */
    val source: String = SOURCE_SMS,
    /** The app notification that recorded the same money, when the bank SMS came too. */
    val alsoIn: String? = null,
    /** What the SMS says this money is (salary, interest, cashback, refund); null if it doesn't say. */
    val kind: com.yourteam.expensetracker.parser.CreditKind? = null,
) {
    val key: String = when (source) {
        SOURCE_SMS -> "in-$smsId"
        SOURCE_MANUAL, SOURCE_CASH -> "in-manual-$smsId"
        else -> "in-app-$smsId"
    }

    companion object {
        const val SOURCE_SMS = "Bank SMS"
        /** Money in typed in by hand, into a bank account (merges with the bank's credit SMS). */
        const val SOURCE_MANUAL = "Added by you"
        /** Cash received, typed in by hand. Never merged with anything. */
        const val SOURCE_CASH = "Cash"
    }
}

/** Money received on the days a Home range covers. */
data class ReceivedTotal(val count: Int, val paise: Long)

/** Money received in a month, and from whom. All amounts in paise. */
data class ReceivedSummary(
    val month: YearMonth,
    val totalPaise: Long,
    val count: Int,
    /** Biggest senders first. Refunds with no name are grouped as [Received.REFUNDS]. */
    val bySender: List<PayeeTotal>,
)

object Received {

    const val UNNAMED = "Other bank credits"
    const val REFUNDS = "Refunds"

    /** A bank SMS and an app notification for the same money: same amount, at most 30 min apart. */
    const val MATCH_MS = 30 * 60_000L

    /**
     * UPI apps re-post the same "paid you" alert a few seconds later (seen: 11–15 s on GPay), and
     * each copy is saved. Same app, sender and amount within this window = one alert. A real
     * second payment from the same person takes far longer (seen: 5 min).
     */
    const val REPOST_MS = 60_000L

    /** Drops re-posted copies of the same app alert, keeping the first. */
    fun collapseReposts(apps: List<BankCredit>): List<BankCredit> {
        val kept = mutableListOf<BankCredit>()
        for (c in apps.sortedBy { it.at }) {
            val copy = kept.lastOrNull { k ->
                k.source == c.source && k.paise == c.paise && k.from.equals(c.from, ignoreCase = true) && c.at - k.at <= REPOST_MS
            }
            if (copy == null) kept += c
        }
        return kept
    }

    /**
     * One entry per money-in: re-posted app alerts are collapsed first ([collapseReposts]), then each app notification is paired with at most one bank credit SMS
     * (same amount, within [MATCH_MS], closest first). A pair keeps the SMS and takes the sender's
     * name from the app if the SMS had none. App notifications with no SMS are kept on their own.
     * Newest first.
     */
    fun merge(sms: List<BankCredit>, allApps: List<BankCredit>): List<BankCredit> {
        val apps = collapseReposts(allApps)
        val candidates = sms.flatMap { s ->
            apps.filter { a -> a.paise == s.paise && kotlin.math.abs(a.at - s.at) <= MATCH_MS }
                .map { a -> Triple(s, a, kotlin.math.abs(a.at - s.at)) }
        }.sortedBy { it.third }
        val pairs = LinkedHashMap<BankCredit, BankCredit>()
        val usedApps = HashSet<BankCredit>()
        for ((s, a, _) in candidates) {
            if (s in pairs || a in usedApps) continue
            pairs[s] = a
            usedApps += a
        }
        val merged = sms.map { s -> pairs[s]?.let { a -> s.copy(from = s.from ?: a.from, alsoIn = a.source) } ?: s }
        return (merged + apps.filter { it !in usedApps }).sortedByDescending { it.at }
    }

    /** Money received on the days [range] covers (same days as [Spending.paymentsIn]), newest first as given. */
    fun inRange(credits: List<BankCredit>, range: DateRange, today: LocalDate, zone: ZoneId = ZoneId.systemDefault()): List<BankCredit> {
        val (current, _) = Spending.windows(range, today)
        return credits.filter {
            val d = Instant.ofEpochMilli(it.at).atZone(zone).toLocalDate()
            !d.isBefore(current.from) && !d.isAfter(current.to)
        }
    }

    /** Home's "Received today · 2 · +₹400": same days as the spending hero. */
    fun total(credits: List<BankCredit>, range: DateRange, today: LocalDate, zone: ZoneId = ZoneId.systemDefault()): ReceivedTotal {
        val list = inRange(credits, range, today, zone)
        return ReceivedTotal(list.size, list.sumOf { it.paise })
    }

    /** Name shown for a credit: the sender, else "Refunds" / "Other bank credits". */
    fun senderOf(c: BankCredit): String = c.from?.trim()?.takeIf(String::isNotEmpty) ?: if (c.refund) REFUNDS else UNNAMED

    fun summarise(credits: List<BankCredit>, today: LocalDate, zone: ZoneId = ZoneId.systemDefault()): ReceivedSummary {
        val month = YearMonth.from(today)
        val thisMonth = credits.filter { YearMonth.from(Instant.ofEpochMilli(it.at).atZone(zone).toLocalDate()) == month }
        // Same sender regardless of case or extra spaces; shown with the newest spelling.
        val bySender = thisMonth
            .sortedByDescending { it.at }
            .groupBy { senderOf(it).lowercase() }
            .map { (_, list) -> PayeeTotal(senderOf(list.first()), list.sumOf { it.paise }, list.size) }
            .sortedByDescending { it.totalPaise }
        return ReceivedSummary(month, thisMonth.sumOf { it.paise }, thisMonth.size, bySender)
    }
}
