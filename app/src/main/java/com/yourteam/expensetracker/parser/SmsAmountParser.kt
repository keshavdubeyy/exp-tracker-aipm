package com.yourteam.expensetracker.parser

/**
 * Finds the amount in a bank *debit* SMS. Returns null for credits or if no amount is found.
 * Handles "debited by 20.0" (SBI), "Sent Rs.20.00" (HDFC), "debited for Rs 20.00" (ICICI),
 * "INR 20", "₹20".
 */
object SmsAmountParser {

    private val debitWords = Regex(
        """\b(debited|sent|paid|spent|withdrawn|transferred|trf to)\b""",
        RegexOption.IGNORE_CASE,
    )
    private val creditOnly = Regex("""\b(credited to your|received|deposited)\b""", RegexOption.IGNORE_CASE)
    private val currencyAmount = Regex(
        """(?:\brs\.?|\binr|₹)\s*([0-9][0-9,]*(?:\.[0-9]{1,2})?)""",
        RegexOption.IGNORE_CASE,
    )
    private val sbiAmount = Regex("""\bdebited by\s*([0-9][0-9,]*(?:\.[0-9]{1,2})?)""", RegexOption.IGNORE_CASE)

    fun debitAmountText(body: String): String? {
        if (!debitWords.containsMatchIn(body)) return null
        if (creditOnly.containsMatchIn(body) && !body.contains("debited", ignoreCase = true)) return null
        return (sbiAmount.find(body) ?: currencyAmount.find(body))?.groupValues?.get(1)
    }
}
