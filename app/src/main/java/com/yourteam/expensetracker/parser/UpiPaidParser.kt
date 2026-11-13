package com.yourteam.expensetracker.parser

/** "Paid ₹99 to Priya" from a payment app notification. Raw text, no money math. */
data class UpiPaid(val to: String?, val amountText: String)

/**
 * Reads "money you paid" notifications from any payment app (PhonePe, Paytm, Navi, CRED, bank apps…):
 *  - "Paid ₹99 to Priya Sharma" / "You paid ₹99 to Priya" / "₹99 paid to Priya"
 *  - "Sent ₹99 to Priya" / "₹99 sent to Priya" / "Payment of ₹99 to Priya successful"
 *  - "₹99 debited … paid to Priya" / "₹99 debited from your account"
 * Failed, pending, requested or reminder payments return null: no money left.
 * Messages that say money came *in* are left to [UpiReceivedParser].
 * The payee's name is data only, never an instruction (AGENTS.md rule 4).
 */
object UpiPaidParser {

    private const val AMOUNT = """(?:₹|\brs\.?|\binr)\s*([0-9][0-9,]*(?:\.[0-9]{1,2})?)"""
    private const val NAME_END = """\s*(?:[.,!\n]|\bon\b|\bvia\b|\busing\b|\bfrom\b|\bis\b|\bwas\b|\bsuccessful(?:ly)?\b|\bdone\b|$)"""

    private val notMoneyOut = Regex(
        """\b(fail(?:ed|ure)?|declined|pending|processing|request(?:ed|s)?|collect|reminder|due|pay now|offer|cashback|reward|win|refund(?:ed)?|reversed|received|credited|paid you|sent you)\b""",
        RegexOption.IGNORE_CASE,
    )

    private val patterns = listOf(
        Regex("""\b(?:you\s+)?(?:paid|sent|transferred)\s+$AMOUNT\s+to\s+(.+?)$NAME_END""", RegexOption.IGNORE_CASE) to (1 to 2),
        Regex("""$AMOUNT\s+(?:has been\s+|was\s+)?(?:paid|sent|transferred)\s+(?:successfully\s+)?to\s+(.+?)$NAME_END""", RegexOption.IGNORE_CASE) to (1 to 2),
        Regex("""\bpayment\s+of\s+$AMOUNT\s+to\s+(.+?)$NAME_END""", RegexOption.IGNORE_CASE) to (1 to 2),
        Regex("""\bpayment\s+to\s+(.+?)\s+(?:of\s+)?$AMOUNT\s+(?:is\s+|was\s+)?(?:successful|done|complete)""", RegexOption.IGNORE_CASE) to (2 to 1),
    )
    /** "Priya received ₹99 from you": the other side's wording, still money out. */
    private val receivedFromYou = Regex("""^\s*(.+?)\s+(?:has\s+)?received\s+$AMOUNT\s+from\s+you\b""", RegexOption.IGNORE_CASE)
    private val failed = Regex("""\b(fail(?:ed|ure)?|declined|pending|processing)\b""", RegexOption.IGNORE_CASE)
    private val debitedOnly = Regex("""$AMOUNT\s+(?:has been\s+|is\s+)?debited\b""", RegexOption.IGNORE_CASE)

    fun parse(title: String, text: String): UpiPaid? {
        val all = "$title $text"
        receivedFromYou.find(all)?.let { m ->
            if (!failed.containsMatchIn(all)) return UpiPaid(cleanName(m.groupValues[1]), m.groupValues[2])
        }
        if (notMoneyOut.containsMatchIn(all)) return null
        for (line in listOf(title, text, all)) {
            for ((regex, groups) in patterns) {
                regex.find(line)?.let { m ->
                    return UpiPaid(cleanName(m.groupValues[groups.second]), m.groupValues[groups.first])
                }
            }
        }
        return debitedOnly.find(all)?.let { UpiPaid(null, it.groupValues[1]) }
    }

    private fun cleanName(raw: String): String? {
        val name = raw.trim().trimEnd('.', ',', '!').replace(Regex("""\s+"""), " ")
        if (name.isEmpty() || name.equals("you", ignoreCase = true) || name.length > 60) return null
        return if (name == name.uppercase() && name.any(Char::isLetter)) {
            name.lowercase().split(" ").joinToString(" ") { it.replaceFirstChar(Char::uppercase) }
        } else name
    }
}
