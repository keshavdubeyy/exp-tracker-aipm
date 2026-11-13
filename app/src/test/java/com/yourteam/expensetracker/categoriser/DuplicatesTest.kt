package com.yourteam.expensetracker.categoriser

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

// Made-up names only.
class DuplicatesTest {

    @Test fun sameUpiIdIsOnePerson() {
        val g = Duplicates.people(listOf(NameUse("ASHATA SHARMA", setOf("ashata@okaxis"), 3), NameUse("Asha S", setOf("ashata@okaxis"), 1)))
        assertEquals(1, g.size)
        assertEquals("Same UPI ID", g[0].reason)
        assertEquals("Ashata Sharma", g[0].suggested)
    }

    @Test fun initialsAndShortForms() {
        val g = Duplicates.people(listOf(NameUse("Ashata Sharma"), NameUse("Ashata S"), NameUse("Ashata"), NameUse("Ravi Kumar")))
        assertEquals(1, g.size)
        assertEquals(setOf("Ashata Sharma", "Ashata S", "Ashata"), g[0].members.toSet())
        assertEquals("Ashata Sharma", g[0].suggested)
    }

    @Test fun shortNameThatFitsTwoPeopleIsLeftAlone() {
        val g = Duplicates.people(listOf(NameUse("Rahul"), NameUse("Rahul Gupta"), NameUse("Rahul Verma")))
        assertTrue(g.isEmpty())
    }

    @Test fun differentPeopleStayApart() {
        assertTrue(Duplicates.people(listOf(NameUse("Ravi Kumar"), NameUse("Ravi Sharma"), NameUse("Priya Kumar"))).isEmpty())
    }

    @Test fun rejectedGroupsAreNotShownAgain() {
        val names = listOf(NameUse("Ashata Sharma"), NameUse("Ashata S"))
        val id = Duplicates.people(names).single().id
        assertTrue(Duplicates.people(names, rejected = setOf(id)).isEmpty())
    }

    @Test fun categorySpellingsAndShortForms() {
        val g = Duplicates.categories(listOf("P2P", "Person-to-person", "Snacks", "Snack", "Travel"), uses = mapOf("P2P" to 5, "Person-to-person" to 1))
        assertEquals(2, g.size)
        val p2p = g.first { "P2P" in it.members }
        assertEquals(setOf("P2P", "Person-to-person"), p2p.members.toSet())
        assertEquals("P2P", p2p.suggested) // most used
    }

    @Test fun builtInCategoryIsKeptAndTwoBuiltInsNeverMerge() {
        val builtIn = setOf("Bills & Utilities", "Travel", "Transfers")
        val g = Duplicates.categories(listOf("Bills & Utilities", "Bills and utilities", "Travel", "Transfers"), builtIn = builtIn)
        assertEquals(1, g.size)
        assertEquals("Bills & Utilities", g[0].suggested)
    }
}
