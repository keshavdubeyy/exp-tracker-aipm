// Known merchants and shop-type words (Feature 1 §3.1). Words only, no numbers.
package com.yourteam.expensetracker.categoriser

import com.yourteam.expensetracker.categoriser.Categories.BILLS
import com.yourteam.expensetracker.categoriser.Categories.EDUCATION
import com.yourteam.expensetracker.categoriser.Categories.ENTERTAINMENT
import com.yourteam.expensetracker.categoriser.Categories.FOOD
import com.yourteam.expensetracker.categoriser.Categories.GROCERIES
import com.yourteam.expensetracker.categoriser.Categories.HEALTH
import com.yourteam.expensetracker.categoriser.Categories.OTHERS
import com.yourteam.expensetracker.categoriser.Categories.PERSONAL_CARE
import com.yourteam.expensetracker.categoriser.Categories.RENT
import com.yourteam.expensetracker.categoriser.Categories.SHOPPING
import com.yourteam.expensetracker.categoriser.Categories.TRAVEL

object MerchantMap {

    /** Brand names (and the short forms banks print) → category. Matched as whole words. */
    private val merchants: Map<String, String> = buildMap {
        fun add(category: String, vararg names: String) = names.forEach { put(it, category) }
        add(FOOD, "zomato", "swiggy", "dominos", "domino s", "mcdonalds", "mcdonald s", "kfc", "burger king", "pizza hut",
            "starbucks", "chaayos", "chai point", "haldiram", "haldirams", "barbeque nation", "eatfit", "faasos", "box8", "subway")
        add(SHOPPING, "amazon", "amzn", "flipkart", "myntra", "ajio", "meesho", "nykaa", "tata cliq", "snapdeal",
            "croma", "reliance digital", "decathlon", "ikea", "lenskart", "firstcry", "shoppers stop", "lifestyle", "westside", "zara", "h m")
        add(TRAVEL, "uber", "ola", "rapido", "irctc", "redbus", "makemytrip", "goibibo", "cleartrip", "indigo", "air india",
            "vistara", "akasa", "spicejet", "yatra", "ixigo", "namma yatri", "blusmart", "fastag", "metro rail", "dmrc", "bmrcl")
        add(GROCERIES, "dmart", "d mart", "avenue supermarts", "reliance fresh", "reliance smart", "bigbasket", "big basket",
            "blinkit", "zepto", "instamart", "jiomart", "more retail", "more supermarket", "spencers", "nature s basket", "star bazaar", "ratnadeep", "licious")
        add(ENTERTAINMENT, "netflix", "spotify", "hotstar", "disney", "jiocinema", "prime video", "sonyliv", "zee5",
            "bookmyshow", "pvr", "inox", "cinepolis", "youtube premium", "steam", "playstation")
        add(HEALTH, "apollo pharmacy", "apollo", "1mg", "tata 1mg", "pharmeasy", "netmeds", "medplus", "practo", "cult fit", "cultfit")
        add(BILLS, "airtel", "jio", "vodafone", "vi", "bsnl", "act fibernet", "bescom", "tata power", "adani electricity",
            "mahanagar gas", "indane", "bharat gas", "hp gas", "tneb", "msedcl", "bses", "tata play", "dish tv", "lic")
        add(EDUCATION, "byjus", "byju s", "unacademy", "coursera", "udemy", "vedantu", "physics wallah", "upgrad", "duolingo")
        add(PERSONAL_CARE, "urban company", "urbanclap", "lakme salon", "naturals", "green trends", "looks salon")
        add(RENT, "nobroker", "nestaway", "housing com", "magicbricks")
    }

    /**
     * Shop-type words in a payee name. [strength] 0–1: how sure the word alone is (tuned by testing,
     * see [Categoriser.HIGH]). Suggestions are listed most likely first.
     */
    data class ShopWord(val strength: Double, val suggestions: List<String>)

    private val shopWords: Map<String, ShopWord> = buildMap {
        fun add(strength: Double, suggestions: List<String>, vararg words: String) = words.forEach { put(it, ShopWord(strength, suggestions)) }
        // Strong words: the kind of shop is clear.
        add(0.9, listOf(FOOD, GROCERIES, ENTERTAINMENT), "restaurant", "cafe", "dhaba", "bakery", "bakers", "biryani", "pizza", "canteen", "chai", "tea stall", "coffee", "juice", "sweets", "mithai", "tiffin", "kitchen")
        add(0.9, listOf(GROCERIES, FOOD, SHOPPING), "kirana", "provision", "provisions", "supermarket", "vegetables", "vegetable", "fruits", "dairy", "milk")
        add(0.9, listOf(HEALTH, PERSONAL_CARE, SHOPPING), "pharmacy", "chemist", "chemists", "medical", "medicals", "hospital", "clinic", "diagnostic", "diagnostics", "dental", "pathology")
        add(0.9, listOf(PERSONAL_CARE, HEALTH, SHOPPING), "salon", "parlour", "parlor", "barber", "spa", "beauty")
        add(0.9, listOf(EDUCATION, SHOPPING, OTHERS), "school", "college", "tuition", "tuitions", "coaching", "academy", "institute", "university")
        add(0.9, listOf(TRAVEL, BILLS, OTHERS), "petrol", "fuel", "filling station", "petroleum", "parking", "travels", "tours", "cab", "cabs", "taxi", "auto rickshaw", "rickshaw", "toll", "fastag")
        add(0.9, listOf(RENT, BILLS, OTHERS), "rent", "landlord", "pg", "hostel")
        add(0.9, listOf(BILLS, RENT, OTHERS), "electricity", "broadband", "recharge", "postpaid", "water bill", "gas agency", "maintenance", "insurance")
        add(0.9, listOf(ENTERTAINMENT, FOOD, OTHERS), "cinema", "cinemas", "multiplex", "theatre", "gaming")
        // Travel words beat "recharge": "Metro card recharge" is travel, not a phone bill.
        add(0.95, listOf(TRAVEL, BILLS, OTHERS), "metro", "irctc", "railway", "bus pass")
        // Weak words: a shop, but of what kind is unclear.
        add(0.6, listOf(SHOPPING, FOOD, GROCERIES), "hotel", "foods")
        add(0.6, listOf(GROCERIES, SHOPPING, FOOD), "mart", "bazaar", "bazar", "general store", "general stores", "departmental")
        add(0.5, listOf(GROCERIES, FOOD, SHOPPING), "store", "stores", "traders", "trading", "enterprises", "agency", "agencies")
        add(0.5, listOf(SHOPPING, PERSONAL_CARE, OTHERS), "fashion", "garments", "textiles", "footwear", "electronics", "mobiles", "jewellers", "jewellery", "collection")
    }

    /** Lower case, letters and digits only, single spaces, padded with spaces for whole-word search. */
    fun normalise(text: String): String =
        " " + text.lowercase().replace(Regex("""[^a-z0-9]+"""), " ").trim().replace(Regex("""\s+"""), " ") + " "

    /** Words to search: the payee name plus the UPI ID's name part ("zomato.order@hdfc" → "zomato order"). */
    private fun searchText(payee: String?, vpa: String?): String {
        val vpaName = vpa?.substringBefore('@')?.replace(Regex("""\d+"""), " ")
        val payeeText = payee?.let { if ('@' in it) it.substringBefore('@').replace(Regex("""\d+"""), " ") else it }
        return normalise(listOfNotNull(payeeText, vpaName).joinToString(" "))
    }

    /** The known merchant's category, or null. Also catches "swiggyupi@…" style UPI IDs. */
    fun merchant(payee: String?, vpa: String?): String? {
        val text = searchText(payee, vpa)
        if (text.isBlank()) return null
        // Longest names first, so "apollo pharmacy" wins over "apollo".
        merchants.keys.sortedByDescending { it.length }.forEach { name ->
            if (text.contains(" $name ")) return merchants.getValue(name)
        }
        // UPI IDs glue words together ("swiggyupi", "amazonpay"): allow a prefix for longer names.
        val tokens = text.trim().split(' ')
        merchants.keys.filter { it.length >= 5 && ' ' !in it }.sortedByDescending { it.length }.forEach { name ->
            if (tokens.any { it.startsWith(name) }) return merchants.getValue(name)
        }
        // One letter off ("zomoto", "flipkrt", "swigy"): longer names only, same first letter ("tomato" is not "zomato").
        merchants.keys.filter { it.length >= 6 && ' ' !in it }.forEach { name ->
            if (tokens.any { it.length >= 5 && it[0] == name[0] && oneEditApart(it, name) }) return merchants.getValue(name)
        }
        return null
    }

    /** True if [a] and [b] differ by exactly one inserted, deleted or changed letter. */
    internal fun oneEditApart(a: String, b: String): Boolean {
        if (a == b || kotlin.math.abs(a.length - b.length) > 1) return false
        var i = 0
        var j = 0
        var edits = 0
        while (i < a.length && j < b.length) {
            if (a[i] == b[j]) { i++; j++; continue }
            if (++edits > 1) return false
            when {
                a.length > b.length -> i++
                a.length < b.length -> j++
                else -> { i++; j++ }
            }
        }
        return edits + (a.length - i) + (b.length - j) == 1
    }

    /** The strongest shop-type word in the name, or null. */
    fun shopWord(payee: String?, vpa: String?): ShopWord? {
        val text = searchText(payee, vpa)
        return shopWords.entries.filter { text.contains(" ${it.key} ") }.maxByOrNull { it.value.strength }?.value
    }
}
