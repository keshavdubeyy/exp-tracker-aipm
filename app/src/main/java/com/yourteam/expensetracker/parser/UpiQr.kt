package com.yourteam.expensetracker.parser

import java.net.URLDecoder
import java.net.URLEncoder

/**
 * A UPI QR code ("upi://pay?pa=shop@okicici&pn=Raju%20Tea&am=40&mc=5812…"). Raw text only;
 * the amount is kept as written, never computed (AGENTS.md rule 1).
 */
data class UpiQr(
    /** The payee's UPI ID ("pa"). */
    val vpa: String,
    /** The payee's name ("pn"), if the QR has one. */
    val name: String?,
    /** A fixed amount ("am"), if the QR has one: then the user can't change it. */
    val amountText: String?,
    /** Merchant category code ("mc"): set on shop QRs. */
    val merchantCode: String?,
    /** All parameters as in the QR, in order, so signed merchant QRs are passed on unchanged. */
    val params: List<Pair<String, String>>,
) {
    val isMerchant: Boolean get() = !merchantCode.isNullOrBlank() && merchantCode != "0000"
    /** What to show and remember the payee as. */
    val displayName: String get() = name?.takeIf { it.isNotBlank() } ?: vpa

    /**
     * The link handed to the UPI app. Keeps the QR's own parameters; adds the amount the user
     * typed (only if the QR had none) and INR. [amountText] is already validated by the caller.
     */
    fun payUri(amountText: String?): String {
        val out = params.toMutableList()
        if (this.amountText.isNullOrBlank() && amountText != null) out.removeAll { it.first == "am" }.also { out += "am" to amountText }
        if (out.none { it.first == "cu" }) out += "cu" to "INR"
        return "upi://pay?" + out.joinToString("&") { (k, v) -> "$k=${enc(v)}" }
    }

    companion object {
        /** Null if the text isn't a UPI payment QR with a UPI ID. */
        fun parse(raw: String?): UpiQr? {
            val text = raw?.trim() ?: return null
            if (!text.startsWith("upi://pay", ignoreCase = true)) return null
            val query = text.substringAfter('?', "")
            if (query.isEmpty()) return null
            val params = query.split('&').mapNotNull { part ->
                val k = part.substringBefore('=').trim()
                if (k.isEmpty()) null else k to dec(part.substringAfter('=', ""))
            }
            fun get(k: String) = params.firstOrNull { it.first.equals(k, ignoreCase = true) }?.second?.trim()?.takeIf(String::isNotEmpty)
            val vpa = get("pa")?.takeIf { '@' in it } ?: return null
            val amount = get("am")?.takeIf { Regex("""^\d{1,7}(\.\d{1,2})?$""").matches(it) && it.toDoubleOrNull()?.let { a -> a > 0 } == true }
            return UpiQr(vpa, get("pn"), amount, get("mc"), params)
        }

        private fun dec(s: String) = runCatching { URLDecoder.decode(s.replace("+", "%2B"), "UTF-8") }.getOrDefault(s)
        private fun enc(s: String) = URLEncoder.encode(s, "UTF-8").replace("+", "%20").replace("%40", "@")
    }
}

/** What the UPI app said when it handed control back (NPCI intent response). */
enum class UpiResult { SUCCESS, FAILURE, UNKNOWN }

object UpiResponse {
    /**
     * Reads "txnId=…&responseCode=00&Status=SUCCESS&txnRef=…" (GPay, BHIM) or the same keys as
     * extras. SUBMITTED, missing or unreadable = UNKNOWN: we ask the user.
     */
    fun parse(response: String?, statusExtra: String? = null): UpiResult {
        val status = statusExtra ?: response?.split('&')
            ?.map { it.substringBefore('=').trim() to it.substringAfter('=', "").trim() }
            ?.firstOrNull { it.first.equals("status", ignoreCase = true) }?.second
        return when (status?.uppercase()) {
            "SUCCESS" -> UpiResult.SUCCESS
            "FAILURE", "FAILED" -> UpiResult.FAILURE
            else -> UpiResult.UNKNOWN
        }
    }
}
