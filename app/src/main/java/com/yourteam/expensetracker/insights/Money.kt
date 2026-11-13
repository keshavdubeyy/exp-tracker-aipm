// ALL money math lives here, plain Kotlin only (AGENTS.md rule 1).
package com.yourteam.expensetracker.insights

/** Formats paise as rupees with Indian digit grouping: 12345650 -> "₹1,23,456.50". */
object Money {
    fun format(paise: Long): String {
        val sign = if (paise < 0) "-" else ""
        val abs = kotlin.math.abs(paise)
        val rupees = (abs / 100).toString()
        val fraction = (abs % 100).toString().padStart(2, '0')
        return "$sign₹${groupIndian(rupees)}.$fraction"
    }

    /** Whole rupees, rounded to the nearest rupee: 389650 -> "₹3,897". Negative gets a true minus. */
    fun formatWhole(paise: Long): String {
        val sign = if (paise < 0) "\u2212" else ""
        val rupees = (kotlin.math.abs(paise) + 50) / 100
        return "$sign₹${groupIndian(rupees.toString())}"
    }

    /** A payment going out, as on a payment row: "−₹86", "−₹1,240", "−₹12.50" (paise only when not whole). */
    fun formatDebit(paise: Long): String {
        val abs = kotlin.math.abs(paise)
        val whole = groupIndian((abs / 100).toString())
        val fraction = abs % 100
        return "\u2212₹$whole" + if (fraction == 0L) "" else ".${fraction.toString().padStart(2, '0')}"
    }

    /** For a UPI payment link ("am"): 4050 -> "40.50". No grouping, no symbol. */
    fun plainAmount(paise: Long): String = "${paise / 100}.${(paise % 100).toString().padStart(2, '0')}"

    /** An amount with no sign, as on a payment row: "₹86", "₹1,240", "₹12.50" (paise only when not whole). */
    fun formatAmount(paise: Long): String = formatDebit(paise).removePrefix("\u2212")

    /** Tenths of a percent as text: -96 -> "−9.6%", 120 -> "+12.0%", 0 -> "0.0%". */
    fun percent(tenths: Long): String {
        val sign = when {
            tenths < 0 -> "\u2212"
            tenths > 0 -> "+"
            else -> ""
        }
        val abs = kotlin.math.abs(tenths)
        return "$sign${abs / 10}.${abs % 10}%"
    }

    /** Indian grouping for a plain digit string, as typed in an amount field: "2500000" -> "25,00,000". */
    fun groupDigits(digits: String): String = groupIndian(digits)

    private fun groupIndian(digits: String): String {
        if (digits.length <= 3) return digits
        val last3 = digits.takeLast(3)
        val rest = digits.dropLast(3).reversed().chunked(2).joinToString(",").reversed()
        return "$rest,$last3"
    }
}
