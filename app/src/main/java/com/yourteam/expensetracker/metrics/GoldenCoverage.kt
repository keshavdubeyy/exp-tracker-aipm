package com.yourteam.expensetracker.metrics

import com.yourteam.expensetracker.categoriser.Basis
import com.yourteam.expensetracker.categoriser.CategoryMemory
import com.yourteam.expensetracker.categoriser.CategoryRecord
import com.yourteam.expensetracker.categoriser.GoldenScenario
import com.yourteam.expensetracker.data.PaymentSnapshot

/**
 * Real payments the AI answered for, sorted into golden-dataset scenarios (MVP M20/M24). Uploaded as counts per
 * scenario only; the payee names of uncovered ones stay on this phone for the Developer screen.
 */
object GoldenCoverage {

    data class Counts(var asked: Int = 0, var picked: Int = 0, var top3: Int = 0, var first: Int = 0)

    data class Result(val byScenario: Map<GoldenScenario, Counts>, val uncoveredExamples: List<Pair<GoldenScenario, String>>)

    fun compute(snap: PaymentSnapshot, record: (String) -> CategoryRecord?): Result {
        val by = linkedMapOf<GoldenScenario, Counts>()
        val examples = linkedMapOf<String, GoldenScenario>()
        snap.payments.forEach { p ->
            val facts = PaymentSnapshot.factsOf(p)
            val keys = CategoryMemory.payeeKeys(facts.payee, facts.vpa)
            if (keys.none { it in snap.aiHints }) return@forEach // the AI never answered for this payee
            val s = GoldenScenario.of(facts.payee, facts.vpa)
            val c = by.getOrPut(s) { Counts() }
            c.asked++
            val r = record(p.key)
            if (r?.pickBasis == Basis.AI && r.pickedRank != null) {
                c.picked++
                if (r.pickedRank in 1..3) c.top3++
                if (r.pickedRank == 1) c.first++
            }
            if (!s.covered) examples.putIfAbsent(facts.payee ?: facts.vpa ?: "", s)
        }
        return Result(by, examples.entries.take(20).map { it.value to it.key })
    }
}
