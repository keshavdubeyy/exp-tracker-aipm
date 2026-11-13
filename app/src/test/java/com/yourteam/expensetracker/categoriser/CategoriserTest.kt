package com.yourteam.expensetracker.categoriser

import com.yourteam.expensetracker.parser.CreditKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CategoriserTest {

    private fun guess(payee: String?, vpa: String? = null) = Categoriser.guess(PaymentFacts(payee, vpa))

    // Feature 1 §3.1 examples.
    @Test fun knownMerchantsAreHighConfidence() {
        mapOf(
            "ZOMATO" to Categories.FOOD, "Swiggy" to Categories.FOOD,
            "Amazon Pay India" to Categories.SHOPPING, "FLIPKART INTERNET" to Categories.SHOPPING,
            "Uber India" to Categories.TRAVEL, "OLA" to Categories.TRAVEL,
            "Avenue Supermarts Ltd" to Categories.GROCERIES, "Reliance Fresh" to Categories.GROCERIES,
            "NETFLIX" to Categories.ENTERTAINMENT, "Spotify" to Categories.ENTERTAINMENT,
            "Apollo Pharmacy" to Categories.HEALTH,
        ).forEach { (payee, category) ->
            val g = guess(payee)
            assertEquals(payee, category, g.category)
            assertEquals(payee, Confidence.HIGH, g.confidence)
            assertEquals(payee, Basis.KNOWN_MERCHANT, g.basis)
        }
    }

    @Test fun merchantFromUpiIdVariations() {
        assertEquals(Categories.FOOD, guess(null, "zomato@hdfcbank").category)
        assertEquals(Categories.FOOD, guess("swiggyupi@axb").category)
        assertEquals(Categories.SHOPPING, guess("amazonpay.merchant@apl").category)
        // Name variations from bank SMS and UPI IDs.
        listOf("Zomato Orders", "ZOMATO LTD", "zomato.order@hdfc", "zomatoonline@ybl", "PAYTM*ZOMATO", "Zomoto", "UPI-ZOMATO-ZOMATO.PAYU@AXISBANK")
            .forEach { assertEquals(it, Categories.FOOD, guess(it).category) }
        listOf("D-Mart", "DMART READY", "dmartready@ybl", "Swiggy Instamart").forEach { assertEquals(it, Categories.GROCERIES, guess(it).category) }
        // A different word that is one letter off a brand is not that brand.
        assertNull(guess("Tomato Traders").let { if (it.basis == Basis.KNOWN_MERCHANT) it.category else null })
    }

    // Feature 1 §3.3 (three-tier): a weak shop word alone (~50%) is "Fairly sure" — offered, not filed.
    @Test fun unknownShopIsFairlySureNotFiled() {
        val g = guess("Lakshmi Stores")
        assertNull(g.category)
        assertEquals(Confidence.MEDIUM, g.confidence)
        assertEquals(listOf(Categories.GROCERIES, Categories.FOOD, Categories.SHOPPING), g.suggestions)
    }

    @Test fun clearShopWordsAreHigh() {
        assertEquals(Categories.HEALTH, guess("Sri Sai Medicals").category)
        assertEquals(Categories.FOOD, guess("Raju Tea Stall Cafe").category)
    }

    @Test fun travelWordsBeatRecharge() {
        assertEquals(Categories.TRAVEL, guess("Metro card recharge").category)
        assertEquals(Categories.BILLS, guess("Airtel prepaid recharge").category)
        assertEquals(Categories.TRAVEL, guess("Auto rickshaw").category)
    }

    @Test fun nothingKnownIsLowWithThreeSuggestions() {
        val g = guess("Ramesh Gupta")
        assertNull(g.category)
        assertEquals(Confidence.LOW, g.confidence)
        assertEquals(3, g.suggestions.size)
        assertNull(guess(null).category)
    }

    // Feature 1 §3.3: amount and time alone never decide a category.
    @Test fun amountAndTimeOnlyReorderSuggestions() {
        val lunch = java.time.LocalDate.of(2026, 11, 13).atTime(13, 0).atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()
        val g = Categoriser.guess(PaymentFacts("Ramesh Gupta", paise = 150_00, at = lunch))
        assertNull(g.category)
        assertEquals(Categories.FOOD, g.suggestions.first())
    }

    // 13 Nov 2026: once the AI has answered, its first pick is filed (marked ✦ AI), confident or not; confidence is kept.
    @Test fun aiPickIsFiled() {
        val confident = Categoriser.guess(PaymentFacts("Bright Minds"), AiHint(listOf(Categories.EDUCATION, Categories.OTHERS), confident = true))
        assertEquals(Categories.EDUCATION, confident.category)
        assertEquals(Confidence.MEDIUM, confident.confidence)
        assertEquals(Basis.AI, confident.basis)
        val unsure = Categoriser.guess(PaymentFacts("Ramesh Gupta"), AiHint(listOf(Categories.EDUCATION), confident = false))
        assertEquals(Categories.EDUCATION, unsure.category)
        assertEquals(Confidence.LOW, unsure.confidence)
        assertEquals(Basis.AI, unsure.basis)
        // A category the AI named that the user has since deleted isn't filed.
        val gone = Categoriser.guess(PaymentFacts("Ramesh Gupta"), AiHint(listOf("Old custom"), confident = true))
        assertNull(gone.category)
        // No AI answer yet: rules alone still need Sure to file.
        assertNull(Categoriser.guess(PaymentFacts("Ramesh Gupta")).category)
    }

    @Test fun shopWordsAndAiAgreeingRaisesConfidence() {
        // A weak shop word + unsure AI agreeing is surer than either alone; the AI's pick is filed.
        val g = Categoriser.guess(PaymentFacts("Lakshmi Stores"), AiHint(listOf(Categories.GROCERIES), confident = false))
        assertEquals(Categories.GROCERIES, g.category)
        assertEquals(Confidence.MEDIUM, g.confidence)
        assertTrue(g.score > 0.6)
        // A strong shop word + confident AI agreeing clears the bar: "Sure", filed by the rules.
        val sure = Categoriser.guess(PaymentFacts("Sri Sai Medicals"), AiHint(listOf(Categories.HEALTH), confident = true))
        assertEquals(Confidence.HIGH, sure.confidence)
        assertEquals(Categories.HEALTH, sure.category)
    }

    @Test fun oneLetterOff() {
        assertTrue(MerchantMap.oneEditApart("zomoto", "zomato"))
        assertTrue(MerchantMap.oneEditApart("swigy", "swiggy"))
        assertTrue(!MerchantMap.oneEditApart("zomato", "zomato"))
        assertTrue(!MerchantMap.oneEditApart("zmoto", "zomato1"))
    }

    // Feature 1 §3.5 example table.
    @Test fun learnsPayeeDefaultAndOneOffOverrides() {
        val facts = PaymentFacts("Lakshmi Stores")
        val keys = CategoryMemory.payeeKeys("Lakshmi Stores", null)
        var memory = CategoryMemory(keys.associateWith { Categories.GROCERIES })
        assertEquals(Categories.GROCERIES, Categoriser.decide("p2", facts, memory).category)
        assertEquals(Categories.GROCERIES, Categoriser.decide("p3", PaymentFacts("LAKSHMI STORES."), memory).category)
        // One payment changed to Shopping: only that one.
        memory = CategoryMemory(memory.choices + (CategoryMemory.txnKey("p3") to Categories.SHOPPING))
        assertEquals(Categories.SHOPPING, Categoriser.decide("p3", facts, memory).category)
        assertEquals(Basis.THIS_PAYMENT, Categoriser.decide("p3", facts, memory).basis)
        assertEquals(Categories.GROCERIES, Categoriser.decide("p4", facts, memory).category)
        // Default changed to Shopping: future payments follow.
        memory = CategoryMemory(memory.choices + keys.associateWith { Categories.SHOPPING })
        assertEquals(Categories.SHOPPING, Categoriser.decide("p5", facts, memory).category)
    }

    @Test fun userDefaultBeatsMerchantMap() {
        val memory = CategoryMemory(CategoryMemory.payeeKeys("Amazon", null).associateWith { Categories.GROCERIES })
        assertEquals(Categories.GROCERIES, Categoriser.decide("p1", PaymentFacts("Amazon"), memory).category)
    }

    @Test fun customCategoriesAreSuggestedOnceUsed() {
        val allowed = Categories.expenseChoices(listOf(CustomCategory("Coffee & Snacks", Categories.FOOD)))
        val g = Categoriser.guess(PaymentFacts("Ramesh Gupta"), popular = listOf("Coffee & Snacks"), allowed = allowed)
        assertEquals("Coffee & Snacks", g.suggestions.first())
        assertEquals(Categories.FOOD, Categories.parentOf("Coffee & Snacks", listOf(CustomCategory("Coffee & Snacks", Categories.FOOD))))
    }

    // Feature 1 §3.7 + three-tier §3.3.
    @Test fun incomeCategories() {
        val m = CategoryMemory()
        // The SMS itself says what it is → "Sure", filed on its own.
        assertEquals(Categories.SALARY, Categoriser.decideIncome("c1", CreditKind.SALARY, "Acme Pvt Ltd", m).category)
        assertEquals(Categories.REFUND, Categoriser.decideIncome("c2", CreditKind.REFUND, null, m).category)
        // Only a name to go on (person / company / shop) → "Fairly sure": best guess offered, not filed.
        val friend = Categoriser.decideIncome("c3", null, "Atharva", m)
        assertNull(friend.category)
        assertEquals(Confidence.MEDIUM, friend.confidence)
        assertEquals(Categories.TRANSFERS, friend.suggestions.first())
        assertTrue(Categories.income.containsAll(friend.suggestions))
        assertNull(Categoriser.decideIncome("c4", null, "Acme Technologies Pvt Ltd", m).category)
        assertEquals(Categories.SALARY, Categoriser.decideIncome("c4", null, "Acme Technologies Pvt Ltd", m).suggestions.first())
        assertNull(Categoriser.decideIncome("c5", null, "Amazon", m).category)
        assertEquals(Categories.REFUND, Categoriser.decideIncome("c5", null, "Amazon", m).suggestions.first())
        // No sender name: we can't tell, so we ask with no guess.
        assertNull(Categoriser.decideIncome("c6", null, null, m).category)
        assertEquals(Confidence.LOW, Categoriser.decideIncome("c6", null, null, m).confidence)
    }

    @Test fun oldNamesUpgrade() {
        assertEquals(Categories.TRAVEL, Categories.upgrade("Transport"))
        assertEquals(Categories.FOOD, Categories.upgrade("Food & dining"))
    }
}
