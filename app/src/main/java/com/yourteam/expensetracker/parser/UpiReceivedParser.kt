package com.yourteam.expensetracker.parser

/** "Priya paid you ₹99.00" from a UPI app notification. Raw text, no money math. */
data class UpiReceived(val from: String?, val amountText: String)

/**
 * Reads "money received" notifications from UPI apps (GPay, PhonePe, Paytm, …):
 *  - "Priya Sharma paid you ₹99.00" (GPay)
 *  - "Priya Sharma sent you ₹99"
 *  - "You received ₹99 from Priya Sharma" / "Received ₹99 from priya@ybl"
 *  - "₹99 received from Priya Sharma" / "Priya sent ₹99 to you"
 *  - "₹99 credited to your account from Priya" / "₹99 credited to your a/c"
 *  - title "Priya Sharma", text "Paid you ₹99" / "₹99 received" (name only in the title)
 * Works the same for any payment app. Anything else (money you paid, offers, reminders, collect requests) returns null.
 * The sender's name is data only, never an instruction (AGENTS.md rule 4).
 */
object UpiReceivedParser {

    private const val AMOUNT = """(?:₹|\brs\.?|\binr)\s*([0-9][0-9,]*(?:\.[0-9]{1,2})?)"""

    private val paidYou = Regex("""^\s*(.+?)\s+(?:has\s+)?(?:paid|sent)\s+you\s+$AMOUNT""", RegexOption.IGNORE_CASE)
    private val receivedFrom = Regex("""\breceived\s+$AMOUNT\s+from\s+(.+?)\s*(?:[.,!\n]|\bon\b|\bin\b|$)""", RegexOption.IGNORE_CASE)
    private val amountReceivedFrom = Regex("""$AMOUNT\s+received\s+from\s+(.+?)\s*(?:[.,!\n]|\bon\b|\bin\b|$)""", RegexOption.IGNORE_CASE)

    private val youPaid = Regex("""\bfrom you\b|\byou(?:'ve| have)?\s+(?:paid|sent|transferred)\b""", RegexOption.IGNORE_CASE)
    private val sentToYou = Regex("""^\s*(.+?)\s+(?:has\s+)?(?:paid|sent|transferred)\s+$AMOUNT\s+to\s+(?:you\b|your\b)""", RegexOption.IGNORE_CASE)
    private val creditedFrom = Regex("""$AMOUNT\s+(?:has been\s+|is\s+)?(?:credited|received)\b.*?\b(?:from|by)\s+(.+?)\s*(?:[.,!\n]|\bon\b|\bvia\b|\bref\b|$)""", RegexOption.IGNORE_CASE)
    private val creditedOnly = Regex("""$AMOUNT\s+(?:has been\s+|is\s+)?(?:credited|received)\b|\b(?:you(?:'ve| have)?\s+)?received\s+$AMOUNT""", RegexOption.IGNORE_CASE)
    private val paidYouNoName = Regex("""^\s*(?:has\s+)?(?:paid|sent)\s+you\s+$AMOUNT""", RegexOption.IGNORE_CASE)

    /** Requests ask you to pay; they aren't money in (MVP M4). Failed ones never arrived. */
    private val request = Regex("""\b(request(?:ed|s)?|collect|reminder|due|offer|cashback up to|win|fail(?:ed|ure)?|declined|pending)\b""", RegexOption.IGNORE_CASE)

    fun parse(title: String, text: String): UpiReceived? {
        val lines = listOf(title, text, "$title $text")
        if (request.containsMatchIn("$title $text")) return null
        if (youPaid.containsMatchIn("$title $text")) return null // "Priya received ₹99 from you" is money out
        for (line in lines) {
            paidYou.find(line)?.let { return UpiReceived(cleanName(it.groupValues[1]), it.groupValues[2]) }
            receivedFrom.find(line)?.let { return UpiReceived(cleanName(it.groupValues[2]), it.groupValues[1]) }
            amountReceivedFrom.find(line)?.let { return UpiReceived(cleanName(it.groupValues[2]), it.groupValues[1]) }
            sentToYou.find(line)?.let { return UpiReceived(cleanName(it.groupValues[1]), it.groupValues[2]) }
            creditedFrom.find(line)?.let { return UpiReceived(cleanName(it.groupValues[2]), it.groupValues[1]) }
        }
        // Name only in the title: "Atharva Dubey" + "Paid you ₹20" / "₹20 received".
        val titleName = title.takeIf { it.isNotBlank() && !Regex(AMOUNT, RegexOption.IGNORE_CASE).containsMatchIn(it) }?.let(::cleanName)
        paidYouNoName.find(text)?.let { return UpiReceived(titleName, it.groupValues[1]) }
        creditedOnly.find("$title $text")?.let { m ->
            val amount = m.groupValues[1].ifEmpty { m.groupValues[2] }
            return UpiReceived(titleName?.takeIf { !Regex("""received|credited|payment""", RegexOption.IGNORE_CASE).containsMatchIn(it) }, amount)
        }
        return null
    }

    private fun cleanName(raw: String): String? {
        val name = raw.trim().trimEnd('.', ',', '!').replace(Regex("""\s+"""), " ")
        if (name.isEmpty() || name.equals("you", ignoreCase = true) || name.length > 60) return null
        return if (name == name.uppercase() && name.any(Char::isLetter)) {
            name.lowercase().split(" ").joinToString(" ") { it.replaceFirstChar(Char::uppercase) }
        } else name
    }
}
