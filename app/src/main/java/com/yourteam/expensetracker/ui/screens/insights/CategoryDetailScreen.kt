package com.yourteam.expensetracker.ui.screens.insights

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.yourteam.expensetracker.insights.CategoryDetail
import com.yourteam.expensetracker.insights.InsightPeriod
import com.yourteam.expensetracker.insights.Insights
import com.yourteam.expensetracker.insights.Money
import com.yourteam.expensetracker.insights.PeriodKind
import com.yourteam.expensetracker.ui.screens.PaymentCard
import com.yourteam.expensetracker.ui.screens.paymentRow
import com.yourteam.expensetracker.ui.theme.AppType
import com.yourteam.expensetracker.ui.theme.Negative
import com.yourteam.expensetracker.ui.theme.PillShape
import com.yourteam.expensetracker.ui.theme.Positive
import com.yourteam.expensetracker.ui.theme.Spacing
import com.yourteam.expensetracker.ui.theme.Surface
import com.yourteam.expensetracker.ui.theme.SurfaceRaised
import com.yourteam.expensetracker.ui.theme.TextPrimary
import com.yourteam.expensetracker.ui.theme.TextSecondary
import com.yourteam.expensetracker.categoriser.Categories
import java.time.LocalDate
import java.time.format.DateTimeFormatter

@Composable
fun CategoryDetailScreen(
    category: String,
    period: InsightPeriod,
    onBack: () -> Unit,
    onOpenPayment: (String) -> Unit,
    viewModel: InsightsDetailViewModel = viewModel(),
) {
    val snap by viewModel.snapshot.collectAsStateWithLifecycle()
    val s = snap ?: return
    val today = LocalDate.now()
    val detail = Insights.categoryDetail(
        payments = s.payments,
        period = period,
        today = today,
        displayCategory = category,
        categoryOf = s::categoryOf,
        parentOf = { Categories.parentOf(it, s.customCategories) },
        isCustom = { name -> s.customCategories.any { it.name == name } },
    )
    val rows = detail.paymentKeys.mapNotNull { key -> s.payments.find { it.key == key }?.let { paymentRow(it, s) } }

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = Spacing.screen, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        item { BackRow(periodLabel(period), onBack) }
        item { Header(detail) }

        if (detail.subBreakdown.size > 1) {
            item { Spacer(Modifier.height(20.dp)); SmallLabel("INSIDE THIS CATEGORY") }
            item { InsideCard(detail) }
        }

        item {
            Spacer(Modifier.height(20.dp))
            Row(Modifier.fillMaxWidth().padding(start = 4.dp, end = 4.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                SmallLabel("${rows.size} ${if (rows.size == 1) "PAYMENT" else "PAYMENTS"}")
                Spacer(Modifier.weight(1f))
                Text("Newest first", style = AppType.chip, color = TextSecondary)
            }
        }
        items(rows, key = { it.key }) { row ->
            PaymentCard(row, onClick = { onOpenPayment(row.key) }, groupedByDay = true)
            Spacer(Modifier.height(8.dp))
        }
        item {
            Text(
                "Wrong category? Tap a payment to change it. Totals and insights update straight away.",
                style = AppType.body, color = TextSecondary, modifier = Modifier.padding(horizontal = 4.dp, vertical = 16.dp),
            )
        }
    }
}

@Composable
private fun Header(detail: CategoryDetail) {
    Column(Modifier.fillMaxWidth().padding(top = 8.dp)) {
        Text(detail.displayCategory, style = AppType.sectionTitle, color = TextPrimary)
        Spacer(Modifier.height(4.dp))
        Text(Money.formatWhole(detail.totalPaise), style = AppType.hero, color = TextPrimary, maxLines = 1, overflow = TextOverflow.Clip)
        Spacer(Modifier.height(10.dp))
        val change = detail.previousPaise?.let { detail.totalPaise - it }
        Text(
            buildAnnotatedString {
                append("${detail.percent}% of your spending")
                if (change != null && change != 0L && detail.comparisonLabel != null) {
                    append(" · ")
                    val up = change > 0
                    withStyle(SpanStyle(color = if (up) Negative else Positive, fontWeight = AppType.rowTitle.fontWeight)) {
                        append("${Money.formatWhole(kotlin.math.abs(change))} ${if (up) "more" else "less"}")
                    }
                    append(" than ${detail.comparisonLabel}")
                }
            },
            style = AppType.body.copy(fontSize = AppType.rowTitle.fontSize),
            color = TextSecondary,
        )
    }
}

@Composable
private fun InsideCard(detail: CategoryDetail) {
    Box(Modifier.fillMaxWidth().clip(com.yourteam.expensetracker.ui.theme.CardShape).background(Surface)) {
        Column(Modifier.padding(horizontal = 20.dp, vertical = 4.dp)) {
            detail.subBreakdown.forEachIndexed { i, sub ->
                Row(Modifier.fillMaxWidth().padding(vertical = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(sub.name, style = AppType.rowTitle, color = TextPrimary, modifier = Modifier.weight(1f, fill = false), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    if (sub.custom) Text(" · yours", style = AppType.body, color = TextSecondary)
                    Spacer(Modifier.weight(1f))
                    Text(Money.formatWhole(sub.paise), style = AppType.rowTitle, color = TextPrimary)
                }
                if (i < detail.subBreakdown.size - 1) HorizontalDivider(thickness = 1.dp, color = SurfaceRaised)
            }
        }
    }
}

@Composable
internal fun BackRow(label: String, onBack: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier.size(40.dp).clip(PillShape).background(Surface).clickable(role = Role.Button, onClick = onBack),
            contentAlignment = Alignment.Center,
        ) {
            Icon(com.yourteam.expensetracker.ui.theme.HugeIcons.ArrowBack, contentDescription = "Back", tint = TextPrimary, modifier = Modifier.size(20.dp))
        }
        Spacer(Modifier.size(12.dp))
        Text(label, style = AppType.rowTitle, color = TextSecondary)
    }
}

@Composable
internal fun SmallLabel(text: String) {
    Text(text, style = AppType.chip, color = TextSecondary, modifier = Modifier.padding(start = 4.dp))
}

internal fun periodLabel(p: InsightPeriod): String = when (p.kind) {
    PeriodKind.MONTH -> "${p.start.dayOfMonth}–${DATE.format(p.end)} ${YEAR.format(p.end)}"
    else -> "${DATE.format(p.start)} – ${DATE.format(p.end)}"
}

private val DATE = DateTimeFormatter.ofPattern("d MMM")
private val YEAR = DateTimeFormatter.ofPattern("yyyy")
