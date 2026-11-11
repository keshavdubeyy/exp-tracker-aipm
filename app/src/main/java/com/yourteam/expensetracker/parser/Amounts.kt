package com.yourteam.expensetracker.parser

/**
 * Turns amount text as shown ("20", "20.0", "1,250.50") into paise (2000, 2000, 125050).
 * Whole numbers only, no floating point. Returns null if the text isn't a plain amount.
 */
object Amounts {
    private val plain = Regex("""^([0-9][0-9,]*)(?:\.([0-9]{1,2}))?$""")

    fun toPaise(text: String): Long? {
        val m = plain.matchEntire(text.trim()) ?: return null
        val rupees = m.groupValues[1].replace(",", "").toLongOrNull() ?: return null
        val paise = m.groupValues[2].padEnd(2, '0').ifEmpty { "00" }.toLong()
        return rupees * 100 + paise
    }
}
