package com.yourteam.expensetracker.categoriser

import org.junit.Assert.assertEquals
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File

/**
 * Golden dataset v1.0, Feature 1, app layer. The model layer (server/evals/run_f1.py) saves the AI's real
 * answers; this feeds each one through the app's own [Categoriser.guess] (known merchants, shop words, the AI
 * hint, the Sure / Fairly sure / Not sure thresholds) and records what the user would see:
 * filed on its own, guesses offered, or asked with no guess.
 *
 * Skipped when the model layer hasn't been run. Writes server/evals/results/f1_app_latest.tsv.
 * With GOLDEN_STRICT=1, fails on the dataset's critical failures: a category filed on its own when the case expects the
 * user to confirm or the AI to abstain, or a wrong category filed on its own.
 */
class GoldenF1AppLayerTest {

    private val root = File("..").absoluteFile.normalize() // gradle runs unit tests from app/

    @Test fun appDecisionsOnGoldenCases() {
        val input = File(root, "server/evals/results/f1_app_input.tsv")
        assumeTrue("Run server/evals/run_f1.py first", input.exists())

        val lines = mutableListOf("case\tai_sent\tapp_outcome\tfiled\tshown_first\tbasis\tscore\texpected_top1\tcritical")
        val critical = mutableListOf<String>()
        var filed = 0; var filedRight = 0
        var abstainCases = 0; var abstainSafe = 0; var abstainNoGuess = 0
        var suggestCases = 0; var shownFirstRight = 0; var shownTop3Right = 0

        input.readLines().filter { it.isNotBlank() }.forEach { line ->
            val c = line.split('\t')
            val id = c[0]; val payee = c[1].ifEmpty { null }; val vpa = c[2].ifEmpty { null }
            val paise = c[3].toLong()
            val aiPicks = c[4].split(';').filter { it.isNotEmpty() }
            val confident = c[5] == "1"; val sent = c[6] == "1"
            val expectedTop1 = c[7]; val acceptable = c[8].split(';').filter { it.isNotEmpty() }
            val mustConfirm = c[9] == "1"

            // The app only uses an AI answer it would actually have asked for (named payee, money out).
            val ai = if (sent && aiPicks.isNotEmpty()) AiHint(aiPicks, confident) else null
            val g = Categoriser.guess(PaymentFacts(payee, vpa, paise, method = "UPI"), ai)
            val outcome = when {
                g.category != null -> "filed on its own"
                g.confidence == Confidence.MEDIUM -> "guesses offered"
                else -> "asked, no guess"
            }
            val shown = if (g.confidence == Confidence.LOW) emptyList() else g.suggestions

            val crit = when {
                expectedTop1 == "ABSTAIN" && g.category != null -> "filed a category on an abstain case"
                mustConfirm && g.category != null -> "filed on its own where the user must confirm"
                g.category != null && g.category != expectedTop1 -> "filed the wrong category"
                else -> ""
            }
            if (crit.isNotEmpty()) critical += "$id: $crit"

            if (g.category != null) { filed++; if (g.category == expectedTop1) filedRight++ }
            if (expectedTop1 == "ABSTAIN") {
                abstainCases++
                if (g.category == null) abstainSafe++
                if (shown.isEmpty()) abstainNoGuess++
            } else {
                suggestCases++
                val first = g.category ?: shown.firstOrNull()
                if (first == expectedTop1) shownFirstRight++
                if (expectedTop1 in (listOfNotNull(g.category) + shown).take(3)) shownTop3Right++
            }
            lines += listOf(id, if (sent) "yes" else "no", outcome, g.category ?: "", shown.firstOrNull() ?: "", g.basis.name,
                "%.2f".format(g.score), expectedTop1, crit).joinToString("\t")
            if (acceptable.isEmpty() && expectedTop1 != "ABSTAIN") error("$id has no acceptable list")
        }

        fun pct(a: Int, b: Int) = if (b == 0) "–" else "${Math.round(100.0 * a / b)}%"
        lines += ""
        lines += "auto_assigned\t$filed\tprecision\t${pct(filedRight, filed)}"
        lines += "abstain_cases_not_filed\t${pct(abstainSafe, abstainCases)}\tasked_with_no_guess\t${pct(abstainNoGuess, abstainCases)}"
        lines += "suggest_cases_first_shown_right\t${pct(shownFirstRight, suggestCases)}\tin_first_3\t${pct(shownTop3Right, suggestCases)}"
        lines += "critical_failures\t${critical.size}\t${critical.joinToString("; ")}"
        File(root, "server/evals/results/f1_app_latest.tsv").writeText(lines.joinToString("\n") + "\n")
        println(lines.joinToString("\n"))

        // Report-only by default so a known finding doesn't block every build. Gate releases with GOLDEN_STRICT=1.
        if (System.getenv("GOLDEN_STRICT") == "1") assertEquals("Critical failures: $critical", 0, critical.size)
    }
}
