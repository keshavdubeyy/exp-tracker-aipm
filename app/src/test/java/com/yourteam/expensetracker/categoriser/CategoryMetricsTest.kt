package com.yourteam.expensetracker.categoriser

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CategoryMetricsTest {
    private val auto = CategoryRecord(Basis.KNOWN_MERCHANT, true, Categories.FOOD)
    private val asked = CategoryRecord(Basis.SHOP_WORDS, false, null)
    private val learned = CategoryRecord(Basis.YOUR_DEFAULT, true, Categories.GROCERIES)

    @Test fun correctionsAndPicksAreRecorded() {
        assertEquals(true, CategoryMetrics.onUserPick(auto, Categories.SHOPPING, emptyList()).corrected)
        assertEquals(false, CategoryMetrics.onUserPick(auto, Categories.FOOD, emptyList()).corrected) // same = confirm
        val top3 = listOf(Categories.GROCERIES, Categories.FOOD, Categories.SHOPPING)
        assertEquals(2, CategoryMetrics.onUserPick(asked, Categories.FOOD, top3).pickedRank)
        assertEquals(0, CategoryMetrics.onUserPick(asked, Categories.RENT, top3).pickedRank)
    }

    @Test fun sixMetricsFromRecords() {
        val records = listOf(
            auto, auto, auto, auto.copy(corrected = true),   // 4 auto, 1 corrected
            learned, learned.copy(corrected = true),           // 2 learned, 1 corrected
            asked.copy(pickedRank = 1), asked.copy(pickedRank = 0), asked, asked,
        )
        val m = CategoryMetrics.compute(records).associateBy { it.name }
        assertEquals(67, m.getValue("Categorisation accuracy").percent)   // 4 of 6 auto kept
        assertEquals(60, m.getValue("Auto-categorisation rate").percent)  // 6 of 10
        assertEquals(33, m.getValue("User correction rate").percent)      // 2 of 6
        assertEquals(50, m.getValue("Merchant learning rate").percent)    // 1 of 2
        assertEquals(40, m.getValue("Needs Review rate").percent)         // 4 of 10
        assertEquals(50, m.getValue("Suggestion hit rate").percent)       // 1 of 2 picks
        assertNull(CategoryMetrics.compute(emptyList()).first().percent)
    }

    @Test fun encodeRoundTrip() {
        val r = CategoryRecord(Basis.AI, false, null, pickedRank = 3, corrected = false)
        assertEquals(r, CategoryRecord.decode(r.encode()))
        val withPick = r.copy(pickBasis = Basis.AI)
        assertEquals(withPick, CategoryRecord.decode(withPick.encode()))
        // Records saved by older versions (5 fields) still load, with no pick basis.
        assertEquals(r, CategoryRecord.decode(listOf("AI", "0", "", "3", "0").joinToString("\u0001")))
    }

    @Test fun aiCountsUseTheGuessesShownAtPickTime() {
        val top3 = listOf(Categories.GROCERIES, Categories.FOOD, Categories.SHOPPING)
        // Made before the AI answered (shop words), picked after the AI answered: counts as an AI pick.
        val first = CategoryMetrics.onUserPick(asked, Categories.GROCERIES, top3, Basis.AI)
        val second = CategoryMetrics.onUserPick(asked, Categories.FOOD, top3, Basis.AI)
        val miss = CategoryMetrics.onUserPick(asked, Categories.RENT, top3, Basis.AI)
        val words = CategoryMetrics.onUserPick(asked, Categories.GROCERIES, top3, Basis.SHOP_WORDS)
        val c = CategoryMetrics.counts(listOf(first, second, miss, words, auto))
        assertEquals(3, c["cat_ai_picked"])
        assertEquals(2, c["cat_ai_top3"])
        assertEquals(1, c["cat_ai_first"])
        assertEquals(4, c["cat_picked"])
        val m = CategoryMetrics.compute(listOf(first, second, miss, words)).associateBy { it.name }
        assertEquals(67, m.getValue("AI suggestion hit rate").percent)
        assertEquals(33, m.getValue("AI first pick accepted").percent)
    }

    @Test fun pastPaymentsAtInstall() {
        fun g(cat: String?, basis: Basis) = CategoryGuess(cat, Confidence.HIGH, emptyList(), basis)
        val install = 1_000L
        val payments = listOf(
            10L to g(Categories.FOOD, Basis.KNOWN_MERCHANT),
            20L to g(Categories.GROCERIES, Basis.AI),
            30L to g(Categories.RENT, Basis.YOUR_DEFAULT), // the user picked
            40L to g(null, Basis.NONE),                     // still unsorted
            2_000L to g(Categories.FOOD, Basis.KNOWN_MERCHANT), // after install: not history
        )
        val c = CategoryMetrics.pastCounts(payments, install)
        assertEquals(4, c["past_pay"])
        assertEquals(3, c["past_sorted"])
        assertEquals(2, c["past_sorted_auto"])
        assertEquals(1, c["past_sorted_ai"])
    }

    @Test fun aiFiledLaterCountsAsAutomatic() {
        val record = CategoryRecord(Basis.NONE, false, null)
        val ai = CategoryGuess(Categories.FOOD, Confidence.MEDIUM, listOf(Categories.FOOD), Basis.AI)
        val filed = CategoryMetrics.onAiFiled(record, ai)
        assertEquals(true, filed.firstAuto)
        assertEquals(Basis.AI, filed.firstBasis)
        record.copy(pickedRank = 1).let { picked -> assertEquals(picked, CategoryMetrics.onAiFiled(picked, ai)) } // the user already picked
        val changed = CategoryMetrics.onUserPick(filed, Categories.SHOPPING, emptyList())
        val c = CategoryMetrics.counts(listOf(filed, changed))
        assertEquals(2, c["cat_ai_auto"])
        assertEquals(1, c["cat_ai_auto_kept"])
    }
}
