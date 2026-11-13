package com.yourteam.expensetracker.ui.screens.insights

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.yourteam.expensetracker.categoriser.Categories
import com.yourteam.expensetracker.insights.InsightCard
import com.yourteam.expensetracker.insights.InsightPeriod
import com.yourteam.expensetracker.insights.Insights
import com.yourteam.expensetracker.insights.Money
import com.yourteam.expensetracker.ui.screens.PaymentCard
import com.yourteam.expensetracker.ui.screens.paymentRow
import com.yourteam.expensetracker.ui.theme.AppCard
import com.yourteam.expensetracker.ui.theme.AppType
import com.yourteam.expensetracker.ui.theme.CardShape
import com.yourteam.expensetracker.ui.theme.DeltaChipShape
import com.yourteam.expensetracker.ui.theme.Negative
import com.yourteam.expensetracker.ui.theme.Positive
import com.yourteam.expensetracker.ui.theme.Spacing
import com.yourteam.expensetracker.ui.theme.Surface
import com.yourteam.expensetracker.ui.theme.SurfaceRaised
import com.yourteam.expensetracker.ui.theme.TextPrimary
import com.yourteam.expensetracker.ui.theme.TextSecondary
import java.time.LocalDate

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun InsightDetailScreen(
    insightId: String,
    period: InsightPeriod,
    onBack: () -> Unit,
    onOpenPayment: (String) -> Unit,
    viewModel: InsightsDetailViewModel = viewModel(),
) {
    val snap by viewModel.snapshot.collectAsStateWithLifecycle()
    val s = snap ?: return
    val today = LocalDate.now()
    val summary = Insights.summarise(
        payments = s.payments,
        received = s.received,
        period = period,
        today = today,
        categoryOf = s::categoryOf,
        parentOf = { Categories.parentOf(it, s.customCategories) },
    )
    val writer = com.yourteam.expensetracker.data.InsightWriter.get(androidx.compose.ui.platform.LocalContext.current)
    val writerVersion by writer.version.collectAsState()
    val card = remember(summary, writerVersion) { summary.cards.find { it.id == insightId }?.let(writer::apply) }
    val logCtx = androidx.compose.ui.platform.LocalContext.current
    androidx.compose.runtime.LaunchedEffect(card?.id) { card?.let { com.yourteam.expensetracker.metrics.InsightLog.opened(logCtx, it.id) } } // M24

    if (card == null) {
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(horizontal = Spacing.screen, vertical = 8.dp)) {
            item { BackRow("Insight", onBack) }
            item {
                Spacer(Modifier.height(24.dp))
                Text("This insight is no longer available. The numbers behind it have changed.", style = AppType.body, color = TextSecondary, modifier = Modifier.padding(4.dp))
            }
        }
        return
    }

    val evidence = card.evidenceKeys.mapNotNull { key -> s.payments.find { it.key == key }?.let { paymentRow(it, s) } }
    val contributors = evidence.groupBy { it.payee }
        .map { (name, list) -> name to list.sumOf { it.paise } }
        .sortedByDescending { it.second }.take(3)

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = Spacing.screen, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        item { BackRow("Insight · ${periodLabel(period)}", onBack) }
        item {
            Column(Modifier.fillMaxWidth().padding(top = 8.dp)) {
                SparkleLabel()
                Spacer(Modifier.height(8.dp))
                Text(card.title, style = AppType.sectionTitle, color = TextPrimary)
                Spacer(Modifier.height(10.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.Center) {
                    Text(card.amountText, style = AppType.hero, color = TextPrimary, maxLines = 1, overflow = TextOverflow.Clip)
                    card.deltaText?.let { d ->
                        val colour = if (card.deltaUp == true) Negative else Positive
                        Text(
                            d, style = AppType.chip, color = colour,
                            modifier = Modifier.clip(DeltaChipShape).background(colour.copy(alpha = 0.18f)).padding(horizontal = 8.dp, vertical = 3.dp),
                        )
                    }
                }
                Spacer(Modifier.height(10.dp))
                Text(card.detail, style = AppType.body, color = TextSecondary)
                if (card.aiWritten) AiWordsNote()
            }
        }

        // How we worked it out (only when a comparison backs the card).
        if (card.previousPaise != null && card.currentPaise != null) {
            item { Spacer(Modifier.height(20.dp)); SmallLabel("HOW WE WORKED IT OUT") }
            item { WorkedOut(card) }
        }

        if (contributors.size > 1) {
            item { Spacer(Modifier.height(20.dp)); SmallLabel("TOP PLACES") }
            item { ContributorsCard(contributors) }
        }

        if (evidence.isNotEmpty()) {
            item { Spacer(Modifier.height(20.dp)); SmallLabel("${evidence.size} ${if (evidence.size == 1) "PAYMENT" else "PAYMENTS"}") }
            items(evidence, key = { it.key }) { row ->
                PaymentCard(row, onClick = { onOpenPayment(row.key) }, groupedByDay = true)
                Spacer(Modifier.height(8.dp))
            }
        }

        item {
            Text(
                "A payment in the wrong category? Tap it to fix it. This insight recalculates.",
                style = AppType.body, color = TextSecondary, modifier = Modifier.padding(horizontal = 4.dp, vertical = 16.dp),
            )
        }
        item {
            Text("Was this useful?", style = AppType.rowTitle, color = TextPrimary, modifier = Modifier.padding(start = 4.dp, bottom = 12.dp))
            FeedbackButtons(onRate = { rating -> viewModel.rate(card, rating); onBack() })
            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun WorkedOut(card: InsightCard) {
    Box(Modifier.fillMaxWidth().clip(CardShape).background(Surface)) {
        Column(Modifier.padding(20.dp)) {
            WorkedRow("This period", Money.formatWhole(card.currentPaise!!))
            Spacer(Modifier.height(12.dp))
            WorkedRow(card.comparisonLabel ?: "Last period", Money.formatWhole(card.previousPaise!!))
            Spacer(Modifier.height(12.dp))
            HorizontalDivider(thickness = 1.dp, color = SurfaceRaised)
            Spacer(Modifier.height(12.dp))
            val diff = card.currentPaise - card.previousPaise
            val up = diff > 0
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("Difference", style = AppType.rowTitle, color = TextPrimary, modifier = Modifier.weight(1f))
                Text(
                    "${if (up) "+" else "−"}${Money.formatWhole(kotlin.math.abs(diff))}",
                    style = AppType.rowTitle, color = if (up) Negative else Positive,
                )
            }
            Spacer(Modifier.height(12.dp))
            Text(
                "Same number of days in both periods. Only completed payments. Refunds are subtracted. Pending and failed payments aren't counted.",
                style = AppType.body, color = TextSecondary,
            )
        }
    }
}

@Composable
private fun WorkedRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = AppType.body, color = TextSecondary, modifier = Modifier.weight(1f))
        Text(value, style = AppType.rowTitle, color = TextPrimary)
    }
}

@Composable
private fun ContributorsCard(contributors: List<Pair<String, Long>>) {
    Box(Modifier.fillMaxWidth().clip(CardShape).background(Surface)) {
        Column(Modifier.padding(horizontal = 20.dp, vertical = 4.dp)) {
            contributors.forEachIndexed { i, (name, paise) ->
                Row(Modifier.fillMaxWidth().padding(vertical = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(name, style = AppType.rowTitle, color = TextPrimary, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(Money.formatWhole(paise), style = AppType.rowTitle, color = TextPrimary)
                }
                if (i < contributors.size - 1) HorizontalDivider(thickness = 1.dp, color = SurfaceRaised)
            }
        }
    }
}

@Composable
private fun FeedbackButtons(onRate: (InsightFeedback) -> Unit) = RateLinks(onRate)
