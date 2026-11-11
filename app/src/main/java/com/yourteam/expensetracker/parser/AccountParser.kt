package com.yourteam.expensetracker.parser

/** Reads the bank and the last 4 account digits from a bank SMS. Never the full number (DESIGN.md §6). */
object AccountParser {

    private val last4 = Regex("""\bA/?c\.?\s*(?:no\.?\s*)?[X*x]*(\d{4})\b""", RegexOption.IGNORE_CASE)

    private val banks = listOf(
        "SBI" to "SBI", "HDFC" to "HDFC Bank", "ICICI" to "ICICI Bank", "AXIS" to "Axis Bank",
        "KOTAK" to "Kotak", "PNB" to "PNB", "BOB" to "Bank of Baroda", "CANARA" to "Canara Bank",
        "UNION" to "Union Bank", "IDFC" to "IDFC First", "YES" to "Yes Bank", "INDUS" to "IndusInd",
    )

    /** "AX-SBIUPI-S" -> "SBI". Null if the sender isn't a bank we know. */
    fun bank(sender: String): String? {
        val code = sender.uppercase().split('-').getOrNull(1) ?: sender.uppercase()
        return banks.firstOrNull { code.contains(it.first) }?.second
    }

    /** "A/C X1234 debited…" -> "1234". */
    fun last4(body: String): String? = last4.find(body)?.groupValues?.get(1)
}
