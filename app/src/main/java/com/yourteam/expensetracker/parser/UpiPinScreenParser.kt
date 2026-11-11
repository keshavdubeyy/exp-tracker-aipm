package com.yourteam.expensetracker.parser

/** A payment that has *started*: the UPI PIN screen is showing. Raw text, no money math. */
data class PinScreenPayment(val amountText: String, val payee: String)

/**
 * Recognises the UPI PIN screen ("Pay ₹20.00", "To Chai Point", number keypad 0–9).
 * It appears for every UPI payment, to people and shops alike, so it tells us a payment
 * started even when no chat line or bank SMS follows.
 *
 * Never reads the PIN: the PIN shows as dots, and the reader never receives taps or typing.
 * Keypad digits are only counted to recognise the screen, never stored.
 */
object UpiPinScreenParser {

    private val payLine = Regex("""^pay\s*₹\s*([0-9][0-9,]*(?:\.[0-9]{1,2})?)$""", RegexOption.IGNORE_CASE)
    private val toLine = Regex("""^to\s+(.+)$""", RegexOption.IGNORE_CASE)
    private val keypadKey = Regex("""^[0-9]$""")

    fun parse(texts: List<String>): PinScreenPayment? {
        val lines = texts.map(String::trim).filter(String::isNotEmpty)

        val keypadDigits = lines.filter { keypadKey.matches(it) }.toSet()
        if (keypadDigits.size < 8) return null

        val amount = lines.firstNotNullOfOrNull { payLine.find(it)?.groupValues?.get(1) } ?: return null
        val payee = lines.firstNotNullOfOrNull { toLine.find(it)?.groupValues?.get(1)?.trim() } ?: return null
        return PinScreenPayment(amount, payee)
    }
}
