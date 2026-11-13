package com.yourteam.expensetracker.parser

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.Month
import java.time.ZoneId

/** One "Payment to X ₹N Paid …" line from a GPay chat. Raw text, no money math. */
data class GPayChatPayment(
    val payee: String,
    val amountText: String,
    val occurredAt: Long,
    /** Same payment always gets the same key, whether shown as "5:24 pm" today or "9 Nov" later. */
    val dedupeKey: String,
)

/**
 * GPay's success screen is an animation with no readable text, but the chat it returns to
 * lists each payment as one line, e.g. "Payment to Ramesh ₹1 Paid · 5:24 pm" or
 * "Payment to Ramesh ₹20 Paid · 7 Nov". This reads those lines.
 * Money *received* in the same chat reads "Payment to you ₹301 Paid"; those are skipped, since
 * they aren't spending (the bank's credit SMS records them as Received).
 */
object GPayChatParser {

    /** True for "Payment to you …": money that came in, not money spent. */
    fun isToYou(payee: String): Boolean = payee.trim().equals("you", ignoreCase = true)

    private val lineRegex = Regex(
        """^\s*payment to\s+(.+?)\s+₹\s*([0-9][0-9,]*(?:\.[0-9]{1,2})?)\s+paid\b(.*)$""",
        setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL),
    )
    private val timeRegex = Regex("""\b(\d{1,2}):(\d{2})\s*([ap]\.?m\.?)?""", RegexOption.IGNORE_CASE)
    private val dateRegex = Regex(
        """\b(\d{1,2})\s+(jan|feb|mar|apr|may|jun|jul|aug|sep|oct|nov|dec)[a-z]*\b(?:,?\s+(\d{4}))?""",
        RegexOption.IGNORE_CASE,
    )

    fun parse(
        texts: List<String>,
        now: LocalDateTime = LocalDateTime.now(),
        zone: ZoneId = ZoneId.systemDefault(),
    ): List<GPayChatPayment> {
        val seen = mutableMapOf<String, Int>()
        return texts.mapNotNull { text ->
            val m = lineRegex.find(text) ?: return@mapNotNull null
            val payee = m.groupValues[1].trim()
            if (isToYou(payee)) return@mapNotNull null
            val amount = m.groupValues[2]
            val rest = m.groupValues[3]

            val date = parseDate(rest, now.toLocalDate()) ?: now.toLocalDate()
            val time = parseTime(rest)
            val occurredAt = when {
                time != null -> LocalDateTime.of(date, time)
                date == now.toLocalDate() -> now
                else -> date.atTime(12, 0)
            }.atZone(zone).toInstant().toEpochMilli()

            // Two identical payments on the same day are told apart by their order on screen.
            val base = "gpay-chat|${payee.lowercase()}|$amount|$date"
            val n = (seen[base] ?: 0) + 1
            seen[base] = n

            GPayChatPayment(payee, amount, occurredAt, "$base|$n")
        }
    }

    private fun parseTime(s: String): LocalTime? {
        val m = timeRegex.find(s) ?: return null
        var hour = m.groupValues[1].toInt()
        val minute = m.groupValues[2].toInt()
        val ampm = m.groupValues[3].lowercase().replace(".", "")
        if (ampm == "pm" && hour < 12) hour += 12
        if (ampm == "am" && hour == 12) hour = 0
        return if (hour in 0..23 && minute in 0..59) LocalTime.of(hour, minute) else null
    }

    private fun parseDate(s: String, today: LocalDate): LocalDate? {
        val m = dateRegex.find(s) ?: return null
        val day = m.groupValues[1].toInt()
        val month = Month.entries.first { it.name.startsWith(m.groupValues[2].uppercase()) }
        val year = m.groupValues[3].toIntOrNull()
            ?: if (month.value > today.monthValue) today.year - 1 else today.year
        return runCatching { LocalDate.of(year, month, day) }.getOrNull()
    }
}
