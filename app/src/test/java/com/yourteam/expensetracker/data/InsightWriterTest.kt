package com.yourteam.expensetracker.data

import com.yourteam.expensetracker.insights.InsightFacts
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

/** Golden INS-013: an unchanged insight reuses its saved wording (no duplicate card); a real change asks again. */
class InsightWriterTest {
    private val food = InsightFacts(
        insightType = "category_comparison",
        metrics = linkedMapOf("food_current_inr" to 6000L, "food_previous_inr" to 4000L, "change_percent" to 50),
        currentPeriod = "October 2026", comparisonPeriod = "September 2026",
        pattern = "Food spending increased month-on-month", evidenceCount = 2,
    )

    @Test fun sameFactsSameFingerprint() {
        assertEquals(InsightWriter.fingerprint(food), InsightWriter.fingerprint(food.copy(metrics = LinkedHashMap(food.metrics))))
        // How many payments back it isn't part of the wording, so it doesn't make a new card.
        assertEquals(InsightWriter.fingerprint(food), InsightWriter.fingerprint(food.copy(evidenceCount = 3)))
    }

    @Test fun realChangeAsksAgain() {
        assertNotEquals(InsightWriter.fingerprint(food), InsightWriter.fingerprint(food.copy(metrics = food.metrics + ("food_current_inr" to 6500L))))
        assertNotEquals(InsightWriter.fingerprint(food), InsightWriter.fingerprint(food.copy(currentPeriod = "November 2026")))
    }
}
