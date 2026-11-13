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

    // "Pay ₹20" (GPay, PhonePe), "Paying ₹20" / "Sending ₹20" / "Amount ₹20" (Navi, Paytm, BHIM),
    // or a line that is just the amount, as on the shared UPI PIN screen.
    private val payLine = Regex("""^(?:pay|paying|sending|amount)?\s*:?\s*(?:₹|rs\.?|inr)\s*([0-9][0-9,]*(?:\.[0-9]{1,2})?)$""", RegexOption.IGNORE_CASE)
    private val toLine = Regex("""^(?:to|paying to|pay to)\s*:?\s+(.+)$""", RegexOption.IGNORE_CASE)
    private val keypadKey = Regex("""^[0-9]$""")

    fun parse(texts: List<String>): PinScreenPayment? {
        val lines = texts.map(String::trim).filter(String::isNotEmpty)

        val keypadDigits = lines.filter { keypadKey.matches(it) }.toSet()
        if (keypadDigits.size < 8) return null

        // The looser wordings also fit an app's own amount keypad, so they count only when the
        // screen also says "UPI PIN" (the shared PIN screen always does).
        val saysPin = lines.any { it.contains("upi pin", ignoreCase = true) }
        val amount = lines.firstNotNullOfOrNull { l ->
            payLine.find(l)?.takeIf { saysPin || l.trimStart().startsWith("pay", ignoreCase = true) }?.groupValues?.get(1)
        } ?: return null
        val payee = lines.firstNotNullOfOrNull { toLine.find(it)?.groupValues?.get(1)?.trim() } ?: return null
        return PinScreenPayment(amount, payee)
    }

    /** Yes/no and counts only, for the Developer screen. Never the screen's words. */
    fun diagnose(texts: List<String>): String {
        val lines = texts.map(String::trim).filter(String::isNotEmpty)
        val keys = lines.filter { keypadKey.matches(it) }.toSet().size
        val amount = lines.any { payLine.find(it) != null }
        val payee = lines.any { toLine.find(it) != null }
        val pin = lines.any { it.contains("upi pin", ignoreCase = true) }
        return "keypad $keys/10 · \"UPI PIN\" ${if (pin) "shown" else "not shown"} · amount ${if (amount) "found" else "not found"} · payee ${if (payee) "found" else "not found"}"
    }
}
