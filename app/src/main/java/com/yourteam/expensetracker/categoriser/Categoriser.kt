// Picks a category for a transaction. AI may pick a category, never a number (AGENTS.md rule 2).
package com.yourteam.expensetracker.categoriser

/**
 * Keyword rules on the payee name. Returns null when unsure: the UI shows "Needs a tag".
 * First matching category wins, so put more specific words first.
 */
object Categoriser {

    const val FOOD = "Food & dining"
    const val GROCERIES = "Groceries"
    const val TRANSPORT = "Transport"
    const val BILLS = "Bills & recharges"
    const val SUBSCRIPTIONS = "Subscriptions"
    const val HEALTH = "Health"
    const val SHOPPING = "Shopping"
    const val RENT = "Rent"

    const val OTHER = "Other"

    /** Choices offered in the category picker, in this order. */
    val all = listOf(FOOD, GROCERIES, TRANSPORT, SHOPPING, BILLS, SUBSCRIPTIONS, HEALTH, RENT, OTHER)

    private val rules: List<Pair<String, List<String>>> = listOf(
        TRANSPORT to listOf("metro", "auto", "rickshaw", "uber", "ola", "rapido", "petrol", "fuel", "irctc", "railway", "fastag", "parking"),
        FOOD to listOf("chai", "tea", "coffee", "cafe", "swiggy", "zomato", "restaurant", "dhaba", "bakery", "biryani", "pizza", "food", "canteen", "juice"),
        GROCERIES to listOf("kirana", "bazaar", "mart", "bigbasket", "blinkit", "zepto", "instamart", "grocery", "provision", "dmart", "vegetable", "dairy"),
        BILLS to listOf("airtel", "jio", "vodafone", " vi ", "bsnl", "electricity", "bescom", "recharge", "broadband", "gas", "water bill"),
        SUBSCRIPTIONS to listOf("netflix", "spotify", "prime", "hotstar", "youtube", "apple", "google play"),
        HEALTH to listOf("medical", "pharmacy", "chemist", "hospital", "clinic", "apollo", "1mg", "pharmeasy", "diagnostic"),
        SHOPPING to listOf("amazon", "flipkart", "myntra", "ajio", "meesho", "nykaa", "store", "fashion"),
        RENT to listOf("rent", "landlord", "pg ", "hostel"),
    )

    fun categorise(payee: String?): String? {
        val name = " ${payee?.lowercase()?.trim() ?: return null} "
        return rules.firstOrNull { (_, words) -> words.any { name.contains(it) } }?.first
    }
}
