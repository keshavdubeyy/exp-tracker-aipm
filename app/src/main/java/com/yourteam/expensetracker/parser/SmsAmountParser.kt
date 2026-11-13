package com.yourteam.expensetracker.parser

/** Which way the money moved in a bank SMS. */
enum class SmsDirection { DEBIT, CREDIT }

/** Kinds of money coming in that the SMS itself names. */
enum class CreditKind { SALARY, REFUND, INTEREST, CASHBACK }

/**
 * Reads a bank SMS: is it money out (debit) or money in (credit), how much, and for credits,
 * who sent it. Text only, no money math (AGENTS.md rule 1).
 *
 * Direction is decided by whichever cue comes **first** in the message, because banks put the
 * account holder's side first and the other party after:
 *  - "A/C X1234 debited by 20.0 … trf to SHOP" → debit
 *  - "Rs 500 debited from A/c XX1234 and credited to priya@okaxis" → debit
 *  - "A/c XX1234 credited with INR 500 … transferred by PRIYA" → credit
 *  - "Refund of Rs 349 … credited to your A/c" → credit
 * "sent by / paid by / transferred by / paid you" are credit cues, not debit ones.
 */
object SmsAmountParser {

    private val creditCue = Regex(
        """\b(credited|credit alert|received|deposited|refund(?:ed)?|reversed|reversal|cashback|""" +
            """(?:sent|paid|transferred) by|transfer from|paid you|sent you)\b""",
        RegexOption.IGNORE_CASE,
    )
    private val debitCue = Regex(
        """\b(debited|debit alert|sent(?!\s+(?:by|you)\b)|paid(?!\s+(?:by|you)\b)|spent|withdrawn|withdrawal|""" +
            """purchase|trf to|transferred to)\b""",
        RegexOption.IGNORE_CASE,
    )

    /** Money paid *towards a credit card* isn't money arriving in the bank account. */
    private val cardPayment = Regex("""\b(credit card|card bill|towards your card)\b""", RegexOption.IGNORE_CASE)

    private val currencyAmount = Regex(
        """(?:\brs\.?|\binr|₹)\s*([0-9][0-9,]*(?:\.[0-9]{1,2})?)""",
        RegexOption.IGNORE_CASE,
    )
    // SBI style: "debited by 20.0", "credited by Rs.500", "credited with INR 500".
    private val verbAmount = Regex(
        """\b(?:debited|credited)\s+(?:by|with|for)\s*(?:rs\.?|inr|₹)?\s*([0-9][0-9,]*(?:\.[0-9]{1,2})?)""",
        RegexOption.IGNORE_CASE,
    )

    /** Null if the SMS says neither (balance alerts, failed payments, ...). */
    fun direction(body: String): SmsDirection? {
        val credit = creditCue.find(body)?.range?.first
        val debit = debitCue.find(body)?.range?.first
        return when {
            credit == null && debit == null -> null
            credit == null -> SmsDirection.DEBIT
            debit == null -> SmsDirection.CREDIT
            debit < credit -> SmsDirection.DEBIT
            else -> SmsDirection.CREDIT
        }
    }

    /** Amount text of a debit SMS ("20.0", "1,250"); null for credits or if no amount. */
    fun debitAmountText(body: String): String? =
        if (direction(body) == SmsDirection.DEBIT) amountText(body) else null

    /** Amount text of a credit SMS into the bank account; null for debits or if no amount. */
    fun creditAmountText(body: String): String? =
        if (direction(body) == SmsDirection.CREDIT && !cardPayment.containsMatchIn(body)) amountText(body) else null

    /** True for refunds, reversals and cashback, so they can be told apart from money sent to you. */
    fun isRefund(body: String): Boolean = refundCue.containsMatchIn(body)

    private val refundCue = Regex("""\b(refund(?:ed)?|reversed|reversal|cashback)\b""", RegexOption.IGNORE_CASE)

    private fun amountText(body: String): String? =
        (verbAmount.find(body) ?: currencyAmount.find(body))?.groupValues?.get(1)

    // ---- Who sent a credit ----

    // Axis style: "Info- UPI/P2A/123456789012/PRIYA SHARMA".
    private val upiInfoName = Regex("""\bUPI/P2[AM]/\d+/([A-Za-z][A-Za-z .]*?)\s*(?:/|\.|$|\s(?:Avl|Not|-))""", RegexOption.IGNORE_CASE)
    // "from PRIYA SHARMA.", "by PRIYA (UPI Ref", "transferred by PRIYA SHARMA via IMPS".
    private val fromName = Regex(
        """\b(?:from|by)\s+([A-Za-z][A-Za-z .&'-]{0,40}?)""" +
            """(?=\s*[.,;:()\[\]\n]|\s+-|\s+(?:on|via|ref|upi|avl|info|in|to|at|thru|through|with|for|and|is|has|dated|a/c)\b|\s*$)""",
        RegexOption.IGNORE_CASE,
    )
    private val vpa = Regex("""\b([a-z0-9][a-z0-9._-]*@[a-z][a-z0-9]+)\b""", RegexOption.IGNORE_CASE)
    private val notAName = setOf(
        "upi", "neft", "imps", "rtgs", "transfer", "you", "your", "us", "the", "a", "rs", "inr", "cash",
        "mob bk", "mobile banking", "net banking", "netbanking", "bank", "clearing", "vpa", "self",
    )

    /**
     * Who sent the money, as written in the SMS ("Priya Sharma", "priya@okaxis"); null if not named.
     * ALL-CAPS names are shown in title case. Never used for any number.
     */
    fun creditFrom(body: String): String? {
        upiInfoName.find(body)?.groupValues?.get(1)?.let(::cleanName)?.let { return it }
        fromName.findAll(body).forEach { m -> cleanName(m.groupValues[1])?.let { return it } }
        return vpa.find(body)?.groupValues?.get(1)?.lowercase()
    }

    // ---- Who a debit went to ----

    // ICICI style: "debited for Rs 20.00 on 09-Nov-26; SHOP credited."
    private val semicolonCredited = Regex(""";\s*([A-Za-z0-9][A-Za-z0-9 .&'*-]{0,40}?)\s+credited\b""", RegexOption.IGNORE_CASE)
    // "trf to CHAI POINT", "To shop@upi", "paid Rs 120 to Chai Point", "at STORE", "credited to priya@okaxis".
    private val toCue = Regex("""\b(?:trf to|transferred to|transfer to|paid to|sent to|credited to|towards|to|at)\s+(?:vpa\s+)?""", RegexOption.IGNORE_CASE)
    private val vpaAtStart = Regex("""^([a-z0-9][a-z0-9._-]*@[a-z][a-z0-9]+)\b""", RegexOption.IGNORE_CASE)
    private val nameAtStart = Regex(
        """^([A-Za-z0-9][A-Za-z0-9 .&'*-]{0,40}?)""" +
            """(?=\s*[.,;:()\[\]\n]|\s+-|\s+(?:on|via|ref|refno|upi|avl|info|in|from|at|thru|through|with|for|and|is|has|dated|a/c|txn|if)\b|\s*$)""",
        RegexOption.IGNORE_CASE,
    )
    /** "to A/c XX5678", "to your card": an account, not a payee. */
    private val accountLike = Regex("""^(a/?c|acct|account|card|your|ur|xx|x\d|\*|self|mobile|beneficiary)\b""", RegexOption.IGNORE_CASE)

    /**
     * Who a debit went to, as written in the SMS ("Chai Point", "shop@upi"); null if not named.
     * Words only, never a number (AGENTS.md rule 2).
     */
    fun debitTo(body: String): String? {
        if (direction(body) != SmsDirection.DEBIT) return null
        upiInfoName.find(body)?.groupValues?.get(1)?.let(::cleanName)?.let { return it }
        semicolonCredited.find(body)?.groupValues?.get(1)?.let(::cleanName)?.let { return it }
        toCue.findAll(body).forEach { m ->
            val rest = body.substring(m.range.last + 1)
            vpaAtStart.find(rest)?.let { return it.groupValues[1].lowercase() }
            if (accountLike.containsMatchIn(rest)) return@forEach
            nameAtStart.find(rest)?.groupValues?.get(1)?.let(::cleanName)
                ?.takeIf { it.any(Char::isLetter) }
                ?.let { return it }
        }
        return vpa.find(body)?.groupValues?.get(1)?.lowercase()
    }

    /** The other side's UPI ID if the SMS shows one ("zomato@hdfcbank"). */
    fun vpaOf(body: String): String? = vpa.find(body)?.groupValues?.get(1)?.lowercase()

    /** How the money moved, if the SMS says: "UPI", "IMPS", "NEFT", "RTGS", "Card", "ATM", "Net banking". */
    fun methodOf(body: String): String? {
        val b = body.lowercase()
        return when {
            Regex("""\batm\b""").containsMatchIn(b) -> "ATM"
            Regex("""\bimps\b""").containsMatchIn(b) -> "IMPS"
            Regex("""\bneft\b""").containsMatchIn(b) -> "NEFT"
            Regex("""\brtgs\b""").containsMatchIn(b) -> "RTGS"
            Regex("""\bupi\b""").containsMatchIn(b) || vpa.containsMatchIn(body) -> "UPI"
            Regex("""\b(card|pos)\b""").containsMatchIn(b) -> "Card"
            Regex("""\b(net ?banking|netbanking|ib)\b""").containsMatchIn(b) -> "Net banking"
            else -> null
        }
    }

    /** What kind of money came in, when the SMS says so: used for income categories (Feature 1 §3.7). */
    fun creditKind(body: String): CreditKind? {
        val b = body.lowercase()
        return when {
            Regex("""\bcashback\b""").containsMatchIn(b) -> CreditKind.CASHBACK
            refundCue.containsMatchIn(body) -> CreditKind.REFUND
            Regex("""\b(salary|sal for|payroll)\b""").containsMatchIn(b) -> CreditKind.SALARY
            Regex("""\b(interest|int\.? ?pd|int\.? ?credit)\b""").containsMatchIn(b) -> CreditKind.INTEREST
            else -> null
        }
    }

    private fun cleanName(raw: String): String? {
        val name = raw.trim().trimEnd('.', '-', ' ', '*').replace(Regex("""\s+"""), " ")
        val lower = name.lowercase()
        if (name.length < 2 || lower in notAName || lower.startsWith("your ") || lower.startsWith("the ")) return null
        return if (name == name.uppercase()) {
            name.lowercase().split(" ").joinToString(" ") { it.replaceFirstChar(Char::uppercase) }
        } else name
    }
}

/**
 * Debug help only: an SMS with every digit as "#" and every word that isn't a common bank
 * keyword as "•", so the *wording* can be checked without any name, number or account leaving
 * the phone (AGENTS.md rule 3).
 */
object SmsMasker {
    private val keep = setOf(
        "dear", "customer", "user", "upi", "your", "ur", "a/c", "ac", "acct", "account", "is", "has", "been", "was",
        "credited", "credit", "debited", "debit", "alert", "received", "deposited", "sent", "paid", "spent", "transferred",
        "transfer", "trf", "withdrawn", "refund", "reversed", "by", "from", "to", "with", "for", "in", "on", "at", "of",
        "and", "via", "vpa", "ref", "refno", "no", "rs", "rs.", "inr", "₹", "info", "avl", "bal", "balance", "not", "you",
        "if", "call", "imps", "neft", "rtgs", "money", "linked", "the", "towards", "card", "bank", "mob", "bk", "p2a", "p2m",
    )

    fun masked(body: String): String =
        body.replace(Regex("[0-9]"), "#").split(Regex("\\s+")).joinToString(" ") { token ->
            val bare = token.lowercase().trim { !it.isLetterOrDigit() && it != '#' && it != '/' && it != '₹' }
            when {
                bare.isEmpty() || bare in keep -> token
                bare.all { !it.isLetter() } -> token
                // Keep the structure of "UPI/P2A/#/NAME" and "rs.###" without the name.
                bare.startsWith("rs") && bare.drop(2).all { !it.isLetter() } -> token
                else -> "•"
            }
        }
}
