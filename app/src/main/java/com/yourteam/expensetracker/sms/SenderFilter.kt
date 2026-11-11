package com.yourteam.expensetracker.sms

/**
 * Keeps only bank/payment messages.
 *
 * 1. Sender must look like an Indian business sender ID: 2 letters, dash, 6 letters
 *    (e.g. "XX-HDFCBK", "AD-SBIINB", "VM-ICICIB"). Newer IDs may end in a DLT type suffix
 *    ("-S" service, "-T" transactional, "-G" government); those are accepted too.
 *    "-P" (promotional) is dropped.
 * 2. Body must not be an OTP or a promotion.
 *    Only messages *worded* like an OTP count ("123456 is your OTP", "OTP is 1234",
 *    "OTP for ..."). Bank alerts that just say "never share your OTP" are kept.
 */
object SenderFilter {

    private val senderPattern = Regex("^[A-Za-z]{2}-[A-Za-z]{6}(-[STGstg])?$")

    // "123456 is your OTP", "OTP is 1234", "OTP for login", "OTP: 1234", "code 1234".
    private val otpRegex = Regex(
        """\bis your (otp|one time password|verification code)\b|""" +
            """\b(otp|one time password|verification code)\s*(is\b|for\b|:|-|\d)""",
        RegexOption.IGNORE_CASE,
    )

    // Whole words / phrases, case-insensitive, so "win" doesn't match "Windows".
    private val blockedPhrases = listOf(
        "offer",
        "offers",
        "win",
        "won",
        "winner",
        "congratulations",
        "loan approved",
        "pre-approved",
        "pre approved",
        "preapproved",
        "apply now",
        "limited time",
    )

    private val blockedRegex = Regex(
        blockedPhrases.joinToString("|", prefix = "\\b(", postfix = ")\\b") { Regex.escape(it) },
        RegexOption.IGNORE_CASE,
    )

    fun isBankSender(address: String): Boolean = senderPattern.matches(address.trim())

    fun isOtpOrPromo(body: String): Boolean =
        otpRegex.containsMatchIn(body) || blockedRegex.containsMatchIn(body)

    fun keep(message: SmsMessage): Boolean =
        isBankSender(message.address) && !isOtpOrPromo(message.body)

    fun filter(messages: List<SmsMessage>): List<SmsMessage> = messages.filter(::keep)
}
