// ALL money math lives here, plain Kotlin only (AGENTS.md rule 1).
package com.yourteam.expensetracker.insights

import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.TextStyle
import java.util.Locale

/** One reply on the Ask screen: a sentence plus the numbers behind it. */
data class Answer(
    val text: String,
    /** "label" to "value" rows shown under the sentence, e.g. "Swiggy" to "₹349.00". */
    val rows: List<Pair<String, String>> = emptyList(),
)

/**
 * Answers spending questions from the payment list. No AI: the question is matched by
 * keywords, every number is computed here, and the sentence is a fixed template.
 * (A later AI step may only reword [Answer.text]; it never sees or changes the numbers.)
 */
object Answers {

    val suggestions = listOf(
        "How much did I spend this month?",
        "What about this week?",
        "Where does most of my money go?",
        "What was my biggest payment?",
        "How much at Chai Point?",
        "Anything I need to check?",
    )

    fun answer(question: String, payments: List<Payment>, today: LocalDate, zone: ZoneId = ZoneId.systemDefault()): Answer {
        val q = question.lowercase().trim()
        fun dateOf(p: Payment): LocalDate = Instant.ofEpochMilli(p.at).atZone(zone).toLocalDate()
        val counted = payments.filter(Spending::counts)
        val month = YearMonth.from(today)
        val thisMonth = counted.filter { YearMonth.from(dateOf(it)) == month }
        val monthName = month.month.getDisplayName(TextStyle.FULL, Locale.ENGLISH)
        fun times(n: Int) = if (n == 1) "1 payment" else "$n payments"

        // A payee named in the question wins over everything else.
        val payee = counted.mapNotNull { it.payee?.trim() }.filter(String::isNotEmpty)
            .distinctBy(String::lowercase)
            .firstOrNull { q.contains(it.lowercase()) }
        if (payee != null) {
            val list = thisMonth.filter { it.payee?.trim()?.equals(payee, ignoreCase = true) == true }
            val total = list.sumOf { it.paise }
            return if (list.isEmpty()) Answer("Nothing to $payee in $monthName so far.")
            else Answer(
                "You've paid $payee ${Money.format(total)} in $monthName, over ${times(list.size)}.",
                list.sortedByDescending { it.at }.map { dateLabel(dateOf(it)) to Money.format(it.paise) },
            )
        }

        return when {
            q.containsAny("check", "review", "confirm", "pending", "waiting") -> {
                val open = payments.filter { it.needsAnswer || it.conflict }
                if (open.isEmpty()) Answer("Nothing needs you. Every payment is accounted for.")
                else Answer(
                    "${times(open.size).replaceFirstChar(Char::uppercase)} need a quick look on the Review tab. " +
                        "Unanswered ones aren't in your totals yet.",
                    open.map { (it.payee ?: "Bank payment") to Money.format(it.paise) },
                )
            }
            q.containsAny("biggest", "largest", "most expensive", "highest") -> {
                val top = thisMonth.maxByOrNull { it.paise }
                    ?: return Answer("No payments in $monthName yet.")
                Answer(
                    "Your biggest payment in $monthName was ${Money.format(top.paise)}" +
                        (top.payee?.let { " to $it" } ?: "") + ", on ${dateLabel(dateOf(top))}.",
                )
            }
            q.containsAny("where", "most", "top", "who") -> {
                val top = Spending.summarise(payments, today, zone).topPayees
                if (top.isEmpty()) Answer("No payments in $monthName yet.")
                else Answer(
                    "Most of it went to ${top.first().payee}: ${Money.format(top.first().totalPaise)} in $monthName.",
                    top.map { it.payee to Money.format(it.totalPaise) },
                )
            }
            q.contains("today") -> {
                val list = counted.filter { dateOf(it) == today }
                Answer("Today you've spent ${Money.format(list.sumOf { it.paise })} across ${times(list.size)}.")
            }
            q.contains("yesterday") -> {
                val list = counted.filter { dateOf(it) == today.minusDays(1) }
                Answer("Yesterday you spent ${Money.format(list.sumOf { it.paise })} across ${times(list.size)}.")
            }
            q.contains("week") -> {
                val from = today.minusDays(6)
                val list = counted.filter { dateOf(it) in from..today }
                val days = Spending.summarise(payments, today, zone).lastSevenDays
                Answer(
                    "In the last 7 days you spent ${Money.format(list.sumOf { it.paise })} across ${times(list.size)}.",
                    days.reversed().filter { it.totalPaise > 0 }.map { dateLabel(it.date) to Money.format(it.totalPaise) },
                )
            }
            q.containsAny("month", "spend", "spent", "total", "how much") -> {
                val s = Spending.summarise(payments, today, zone)
                Answer(
                    "You've spent ${Money.format(s.totalPaise)} in $monthName across ${times(s.paymentCount)}, " +
                        "about ${Money.format(s.averagePerDayPaise)} a day.",
                )
            }
            else -> Answer(
                "I can tell you totals for today, this week or this month, where your money went, " +
                    "your biggest payment, or how much you paid someone. Try one of the suggestions.",
            )
        }
    }

    private fun String.containsAny(vararg words: String) = words.any { contains(it) }

    private fun dateLabel(d: LocalDate) = "${d.dayOfMonth} ${d.month.getDisplayName(TextStyle.SHORT, Locale.ENGLISH)}"
}
