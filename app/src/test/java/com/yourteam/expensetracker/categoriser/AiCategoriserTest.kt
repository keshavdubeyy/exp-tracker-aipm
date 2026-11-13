package com.yourteam.expensetracker.categoriser

import org.junit.Assert.assertEquals
import org.junit.Test

class AiCategoriserTest {
    private fun c(payee: String?) = PaymentFacts(payee).let { it to Categoriser.guess(it) }

    @Test fun asksOnlyAboutUndecidedNamedPayeesOnce() {
        val picks = AiCategoriser.pick(
            listOf(c("Zomato"), c("Ramesh Gupta"), c("RAMESH GUPTA."), c(null), c("Lakshmi Stores"), c("Known Already")),
            haveHint = { it == "payee:known already" },
            tried = emptySet(),
        )
        // Zomato is decided, null has no name, duplicates collapse, already-answered is skipped.
        assertEquals(listOf("payee:ramesh gupta", "payee:lakshmi stores"), picks.map { it.first })
    }

    @Test fun capsEachRun() {
        val many = (1..30).map { c("Person $it") }
        assertEquals(AiCategoriser.MAX_PER_RUN, AiCategoriser.pick(many, { false }, emptySet()).size)
    }
}
