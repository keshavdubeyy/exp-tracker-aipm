package com.yourteam.expensetracker.insights

import com.yourteam.expensetracker.categoriser.Categories
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneOffset

class InsightsTest {
    private val zone = ZoneOffset.UTC
    private val today = LocalDate.of(2026, 11, 13)

    private fun at(day: Int, month: Int = 11, hour: Int = 12) =
        LocalDate.of(2026, month, day).atTime(hour, 0).toInstant(zone).toEpochMilli()

    /** A completed payment (bank SMS), optionally with a payee and category. */
    private fun pay(paise: Long, day: Int, month: Int = 11, payee: String? = null, hour: Int = 12): Payment =
        Payment(paise, payee, at(day, month, hour), sms = BankDebit(day.toLong() * 10_000 + month * 100 + hour, paise, at(day, month, hour), "X", payee))

    private fun waiting(paise: Long, day: Int) =
        Payment(paise, "Shop", at(day), started = StartedPayment(day.toLong(), paise, "Shop", at(day), UserAnswer.NONE))

    /** Category by payee name; parent is identity for predefined categories. */
    private fun catByPayee(map: Map<String, String>): (Payment) -> String? = { p -> p.payee?.let { map[it] } }
    private val parent: (String) -> String = { it }

    private fun month(payments: List<Payment>, cats: Map<String, String> = emptyMap()) =
        Insights.summarise(payments, emptyList(), InsightPeriod.of(PeriodKind.MONTH, today), today, catByPayee(cats), parent, zone)

    @Test fun monthToDateComparesSameElapsedDaysLastMonth() {
        val (current, previous) = Insights.windows(InsightPeriod.of(PeriodKind.MONTH, today), today)
        assertEquals(LocalDate.of(2026, 11, 1), current.from)
        assertEquals(LocalDate.of(2026, 11, 13), current.to)
        assertEquals(LocalDate.of(2026, 10, 1), previous!!.from)
        assertEquals(LocalDate.of(2026, 10, 13), previous.to) // same 13 days
    }

    @Test fun totalComparisonAndCount() {
        val s = month(listOf(pay(700_000, 5), pay(50_000, 10), pay(99_900, 3, month = 10), pay(200_000, 11, month = 10)))
        assertEquals(750_000L, s.totalPaise) // Nov 1–13
        assertEquals(299_900L, s.previousPaise) // Oct 1–13
        assertEquals(2, s.paymentCount)
        assertEquals(450_100L, s.changePaise)
    }

    @Test fun pendingShownSeparatelyNeverCounted() {
        val s = month(listOf(pay(200_000, 5), waiting(50_000, 5)))
        assertEquals(200_000L, s.totalPaise)
        assertEquals(50_000L, s.pendingPaise)
        assertEquals(1, s.pendingCount)
    }

    @Test fun categoryBreakdownPercentAndUncategorisedBucket() {
        val cats = mapOf("Zomato" to Categories.FOOD, "Myntra" to Categories.SHOPPING)
        val s = month(
            listOf(pay(600_000, 5, payee = "Zomato"), pay(300_000, 6, payee = "Myntra"), pay(100_000, 7, payee = "Unknown")),
            cats,
        )
        assertEquals(1_000_000L, s.totalPaise)
        assertEquals(Categories.FOOD, s.categories[0].category)
        assertEquals(60, s.categories[0].percent) // 600k / 1000k
        assertNull(s.categories.firstOrNull { it.category == null }) // uncategorised is not in the list
        assertEquals(100_000L, s.uncategorised!!.paise) // …it's its own bucket
        assertEquals(10, s.uncategorised!!.percent)
    }

    @Test fun customCategoriesRollUpIntoParent() {
        val cats = mapOf("Chai Point" to "Coffee & Snacks", "Zomato" to Categories.FOOD)
        val toParent: (String) -> String = { if (it == "Coffee & Snacks") Categories.FOOD else it }
        val s = Insights.summarise(
            listOf(pay(100_000, 5, payee = "Chai Point"), pay(200_000, 6, payee = "Zomato")),
            emptyList(), InsightPeriod.of(PeriodKind.MONTH, today), today, catByPayee(cats), toParent, zone,
        )
        assertEquals(1, s.categories.size)
        assertEquals(Categories.FOOD, s.categories[0].category)
        assertEquals(300_000L, s.categories[0].paise) // both rolled into Food
    }

    @Test fun noEarlierDataIsInsufficientHistory() {
        val s = month(listOf(pay(120_000, 5), pay(400_00, 6)))
        assertEquals(HistoryState.INSUFFICIENT, s.historyState)
        assertNull(s.previousPaise?.takeIf { it > 0 })
        assertTrue(s.cards.isEmpty())
    }

    @Test fun categoryChangeCard() {
        val cats = mapOf("Zomato" to Categories.FOOD)
        val s = month(
            listOf(
                pay(750_000, 5, payee = "Zomato"),           // Nov: ₹7,500 Food
                pay(577_000, 6, month = 10, payee = "Zomato"), // Oct: ₹5,770 Food
            ),
            cats,
        )
        val card = s.cards.first { it.type == InsightType.CATEGORY_CHANGE }
        assertEquals("Food & Dining is up", card.title)
        assertEquals(true, card.deltaUp)
        assertEquals("30% more", card.deltaText) // (7500-5770)/5770 = 29.98 -> 30
        assertEquals(750_000L, card.currentPaise)
        assertEquals(577_000L, card.previousPaise)
    }

    @Test fun repeatMerchantCard() {
        val payments = (1..4).map { pay(30_000, it, payee = "Swiggy", hour = 21) } +
            pay(900_000, 5, month = 10, payee = "Swiggy") // gives a comparison so state is OK
        val s = month(payments)
        val card = s.cards.first { it.type == InsightType.REPEAT_MERCHANT }
        assertEquals("Swiggy, 4 times", card.title)
        assertTrue(card.detail.contains("after 8 pm"))
        assertEquals(4, card.evidenceKeys.size)
    }

    @Test fun categoryShareCard() {
        val cats = mapOf("Myntra" to Categories.SHOPPING, "Zomato" to Categories.FOOD)
        val s = month(
            listOf(
                pay(600_000, 5, payee = "Myntra"), pay(400_000, 6, payee = "Zomato"), // Nov: Shopping 60%
                pay(100_000, 5, month = 10, payee = "Zomato"),                          // Oct baseline
            ),
            cats,
        )
        val card = s.cards.first { it.type == InsightType.CATEGORY_SHARE }
        assertEquals("60%", card.amountText)
        assertEquals(Categories.SHOPPING, card.category)
    }

    @Test fun singleDayHasNoCards() {
        val s = Insights.summarise(
            listOf(pay(50_000, 13)), emptyList(), InsightPeriod.of(PeriodKind.DAY, today), today, catByPayee(emptyMap()), parent, zone,
        )
        assertEquals(HistoryState.SINGLE_DAY, s.historyState)
        assertTrue(s.cards.isEmpty())
        assertNull(s.previousPaise)
    }

    @Test fun customRangeHasNoComparisonButCanConcentrate() {
        val cats = mapOf("Myntra" to Categories.SHOPPING, "Zomato" to Categories.FOOD)
        val period = InsightPeriod.custom(LocalDate.of(2026, 11, 5), LocalDate.of(2026, 11, 13))
        val s = Insights.summarise(
            listOf(
                pay(300_000, 7, payee = "Myntra"), pay(300_000, 8, payee = "Myntra"), // ₹6,000 on 7–8…
                pay(100_000, 10, payee = "Myntra"),                                     // …vs ₹1,000 on a third day = 86%
                pay(100_000, 11, payee = "Zomato"),
            ),
            emptyList(), period, today, catByPayee(cats), parent, zone,
        )
        assertNull(s.previousPaise) // no comparison for custom
        val card = s.cards.first { it.type == InsightType.DAY_CONCENTRATION }
        assertEquals(Categories.SHOPPING, card.category)
        assertTrue(card.detail.contains("7 Nov and 8 Nov"))
    }
}
