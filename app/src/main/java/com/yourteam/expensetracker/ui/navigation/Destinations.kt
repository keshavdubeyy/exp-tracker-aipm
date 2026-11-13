package com.yourteam.expensetracker.ui.navigation

/** The bottom navigation bar: Home · Insights · Budgets · Review (DESIGN.md §4.1). */
enum class TopLevelDestination(val route: String, val label: String) {
    Home("home", "Home"),
    Insights("insights", "Insights"),
    Budgets("budgets", "Budgets"),
    Review("review", "Review"),
}

/** Screens opened from a tab, shown without the tabs. */
object Routes {
    const val ASK = "ask"
    const val PAYMENTS = "payments"
    /** Payments with the "Received" filter already on (Home's received row). */
    const val PAYMENTS_RECEIVED = "payments/received"
    const val PROFILE = "profile"
    const val DUPLICATES = "duplicates" // Profile › Duplicates: same person or category under different names
    const val DEVELOPER = "developer" // hidden: tap the app version in Profile 7 times (MVP M24)
    const val CATEGORIES = "categories" // Profile › Categories (Feature 1 §3.6)
    const val SOURCES = "sources" // opened from Profile; a better home for sources is still open (MVP §7 D6)
    const val PAYMENT = "payment" // payment/{key}
    const val BUDGET_SETUP = "budget/setup" // budget/setup/{mode} (Feature 3 bd2–bd5)
    const val BUDGET_CATEGORY = "budget/category" // budget/category/{name} (bd7)
    const val BUDGET_PAST = "budget/past" // budget/past/{month or "latest"} (bd12)
}
