package com.yourteam.expensetracker.categoriser

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CategoriserTest {
    @Test fun keywordsPickCategory() {
        assertEquals(Categoriser.FOOD, Categoriser.categorise("Chai Point"))
        assertEquals(Categoriser.GROCERIES, Categoriser.categorise("Shree Ganesh Kirana"))
        assertEquals(Categoriser.TRANSPORT, Categoriser.categorise("Metro card recharge")) // transport before bills
        assertEquals(Categoriser.SUBSCRIPTIONS, Categoriser.categorise("NETFLIX"))
    }

    @Test fun unknownOrMissingNeedsATag() {
        assertNull(Categoriser.categorise("Ramesh Gupta"))
        assertNull(Categoriser.categorise(null))
    }
}
