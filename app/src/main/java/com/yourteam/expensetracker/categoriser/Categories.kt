// Category names (Feature 1 §3.6, §3.7). Words only, no numbers.
package com.yourteam.expensetracker.categoriser

/** A category the user made. [parent] is one of [Categories.expense] (or null), used to group totals. */
data class CustomCategory(val name: String, val parent: String? = null)

object Categories {
    const val FOOD = "Food & Dining"
    const val TRAVEL = "Travel"
    const val SHOPPING = "Shopping"
    const val GROCERIES = "Groceries"
    const val BILLS = "Bills & Utilities"
    const val HEALTH = "Healthcare"
    const val ENTERTAINMENT = "Entertainment"
    const val EDUCATION = "Education"
    const val PERSONAL_CARE = "Personal Care"
    const val RENT = "Rent"
    const val OTHERS = "Others"

    /** Predefined expense categories, in picker order (Feature 1 §3.6). */
    val expense = listOf(FOOD, TRAVEL, SHOPPING, GROCERIES, BILLS, HEALTH, ENTERTAINMENT, EDUCATION, PERSONAL_CARE, RENT, OTHERS)

    const val SALARY = "Salary"
    const val REFUND = "Refund"
    const val INTEREST = "Interest"
    const val CASHBACK = "Cashback"
    const val TRANSFERS = "Transfers"
    const val OTHER_INCOME = "Other Income"

    /** Income categories, kept apart from expense ones (Feature 1 §3.7). */
    val income = listOf(SALARY, REFUND, INTEREST, CASHBACK, TRANSFERS, OTHER_INCOME)

    /** Names used before 13 Nov 2026, mapped to the new list so saved choices keep working. */
    private val renamed = mapOf(
        "Food & dining" to FOOD,
        "Transport" to TRAVEL,
        "Bills & recharges" to BILLS,
        "Subscriptions" to ENTERTAINMENT,
        "Health" to HEALTH,
        "Other" to OTHERS,
    )

    fun upgrade(name: String): String = renamed[name] ?: name

    /** Expense picker list: predefined first, then the user's own (not already predefined), Others last. */
    fun expenseChoices(custom: List<CustomCategory>): List<String> =
        (expense - OTHERS) + custom.map { it.name }.filter { it !in expense }.distinct() + OTHERS

    /** The predefined category a name rolls up into: itself if predefined, its parent if custom. */
    fun parentOf(name: String, custom: List<CustomCategory>): String =
        if (name in expense || name in income) name else custom.firstOrNull { it.name == name }?.parent ?: name

    /** Tidies a typed name: trimmed, single spaces, first letter upper case, at most 30 characters. */
    fun cleanName(raw: String): String? =
        raw.trim().replace(Regex("""\s+"""), " ").take(30).replaceFirstChar(Char::uppercase).ifEmpty { null }
}
