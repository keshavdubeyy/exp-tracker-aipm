package com.yourteam.expensetracker.parser

/** What we read off GPay's "payment successful" screen. Raw text, no money math here. */
data class GPayScreenPayment(
    val amountText: String, // as shown, e.g. "1" or "1,250.00"
    val payee: String,
    val upiRef: String?, // 12-digit UPI transaction ID, if shown
)

/**
 * Decides whether the texts on a GPay screen are a *completed payment you sent*, and pulls out
 * amount, payee and UPI ref. Pure Kotlin so it can be unit-tested.
 *
 * Rules (all must hold):
 * - a "paid to" / "sent to" / "payment successful" style phrase,
 * - exactly one rupee amount on screen (history lists show many, so they're skipped),
 * - no failed / pending / declined wording.
 */
object GPayScreenParser {

    private val successRegex = Regex(
        """\b(paid to|sent to|payment successful|transaction successful|money sent|payment sent)\b""",
        RegexOption.IGNORE_CASE,
    )
    private val notDoneRegex = Regex(
        """\b(failed|declined|pending|processing|cancelled|canceled)\b""",
        RegexOption.IGNORE_CASE,
    )
    private val amountRegex = Regex("""₹\s*([0-9][0-9,]*(?:\.[0-9]{1,2})?)""")
    private val payeeLineRegex = Regex("""^\s*(paid|sent)\s+to\b[:\s]*(.*)$""", RegexOption.IGNORE_CASE)
    private val upiRefRegex = Regex("""(?<!\d)\d{12}(?!\d)""")

    /** Yes/no checks only, for debug logs. Never contains screen words. */
    fun diagnose(texts: List<String>): String {
        val joined = texts.joinToString("\n")
        val amounts = amountRegex.findAll(joined).map { it.groupValues[1] }.toSet().size
        return "texts=${texts.size} rupeeSign=${joined.contains('₹')} amounts=$amounts " +
            "success=${successRegex.containsMatchIn(joined)} notDone=${notDoneRegex.containsMatchIn(joined)} " +
            "upiRef=${upiRefRegex.containsMatchIn(joined)} parsed=${parse(texts) != null}"
    }

    // Generic app-interface words. Anything else (names, UPI IDs, banks) is shown as "•".
    private val uiWords = setOf(
        "paid", "pay", "payment", "payments", "to", "from", "sent", "send", "received", "receive",
        "successful", "success", "successfully", "completed", "complete", "done", "processing",
        "pending", "failed", "declined", "money", "transaction", "transactions", "id", "upi", "ref",
        "reference", "no", "number", "share", "screenshot", "receipt", "view", "details", "detail",
        "split", "close", "back", "again", "amount", "bank", "account", "a/c", "debited", "credited",
        "via", "on", "at", "for", "the", "your", "you", "of", "and", "with", "is", "has", "been",
        "google", "gpay", "rupees", "rs", "inr", "₹", "check", "balance", "history", "help", "more",
        "new", "add", "note", "message", "say", "thanks", "rewards", "reward", "scratch", "card",
        "cashback", "earned", "won", "verified", "name", "date", "time", "today", "am", "pm",
        "status", "copy", "copied", "open", "ok", "okay", "home", "settings", "contact", "contacts",
        "self", "transfer", "transferred", "wallet", "lite", "autopay", "mandate", "request", "requested",
        "jan", "feb", "mar", "apr", "may", "jun", "jul", "aug", "sep", "oct", "nov", "dec",
    )

    /** Debug only: GPay's interface words kept, every other word -> "•", every digit -> "#". */
    fun masked(texts: List<String>): String = texts.joinToString(" | ") { text ->
        text.replace(Regex("[0-9]"), "#").split(Regex("\\s+")).joinToString(" ") { token ->
            val bare = token.lowercase().trim { !it.isLetterOrDigit() && it != '₹' && it != '#' && it != '/' }
            when {
                bare.isEmpty() -> token
                bare in uiWords -> token
                bare.all { it == '#' || it == ',' || it == '.' || it == '₹' } -> token
                else -> "•"
            }
        }
    }

    fun parse(texts: List<String>): GPayScreenPayment? {
        val lines = texts.map(String::trim).filter(String::isNotEmpty)
        val joined = lines.joinToString("\n")

        if (!successRegex.containsMatchIn(joined)) return null
        if (notDoneRegex.containsMatchIn(joined)) return null

        val amounts = amountRegex.findAll(joined).map { it.groupValues[1] }.toSet()
        if (amounts.size != 1) return null

        return GPayScreenPayment(
            amountText = amounts.single(),
            payee = findPayee(lines).orEmpty(),
            upiRef = upiRefRegex.find(joined)?.value,
        )
    }

    private fun findPayee(lines: List<String>): String? {
        lines.forEachIndexed { i, line ->
            val m = payeeLineRegex.find(line) ?: return@forEachIndexed
            val sameLine = m.groupValues[2].trim()
            return sameLine.ifEmpty { lines.getOrNull(i + 1) }
        }
        return null
    }
}
