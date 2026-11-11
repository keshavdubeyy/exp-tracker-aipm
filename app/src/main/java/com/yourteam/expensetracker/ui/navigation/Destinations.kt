package com.yourteam.expensetracker.ui.navigation

/** The pill tabs at the top (MVP M14: Home · Review · Insights). Ask lives in Home's bottom bar. */
enum class TopLevelDestination(val route: String, val label: String) {
    Home("home", "Home"),
    Review("review", "Review"),
    Insights("insights", "Insights"),
}

/** Screens opened from a tab, shown without the tabs. */
object Routes {
    const val ASK = "ask"
    const val PAYMENTS = "payments"
    const val PROFILE = "profile"
    const val SOURCES = "sources" // opened from Profile; a better home for sources is still open (MVP §7 D6)
    const val PAYMENT = "payment" // payment/{key}
}
