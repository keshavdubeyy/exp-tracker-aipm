package com.yourteam.expensetracker.ui.screens.budgets

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.yourteam.expensetracker.insights.BudgetLevel
import com.yourteam.expensetracker.insights.BudgetLine
import com.yourteam.expensetracker.insights.Money
import com.yourteam.expensetracker.ui.feedback.Feedback
import com.yourteam.expensetracker.ui.screens.insights.BackRow
import com.yourteam.expensetracker.ui.theme.AppType
import com.yourteam.expensetracker.ui.theme.Negative
import com.yourteam.expensetracker.ui.theme.PillShape
import com.yourteam.expensetracker.ui.theme.Positive
import com.yourteam.expensetracker.ui.theme.Spacing
import com.yourteam.expensetracker.ui.theme.Surface
import com.yourteam.expensetracker.ui.theme.SurfaceRaised
import com.yourteam.expensetracker.ui.theme.TextPrimary
import com.yourteam.expensetracker.ui.theme.TextSecondary
import java.time.YearMonth
import java.time.format.DateTimeFormatter

private val chipFormat = DateTimeFormatter.ofPattern("MMM")
private val titleFormat = DateTimeFormatter.ofPattern("MMMM yyyy")

/** bd12: past months, read only. Re-categorising a payment still updates these numbers. */
@Composable
fun PastBudgetsScreen(initial: YearMonth?, onBack: () -> Unit, viewModel: BudgetsViewModel = viewModel()) {
    val snap by viewModel.snapshot.collectAsStateWithLifecycle() // recompute when payments change
    val months = viewModel.pastMonths()
    var picked by rememberSaveable { mutableStateOf((initial?.takeIf { it in months } ?: months.firstOrNull())?.toString()) }
    val month = picked?.let(YearMonth::parse)
    val status = month?.let { snap?.let { _ -> viewModel.statusOf(it) } }

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = Spacing.screen, end = Spacing.screen, top = 8.dp, bottom = 40.dp),
    ) {
        item { BackRow("Past months", onBack) }
        if (months.isEmpty()) {
            item { Text("No past budgets yet. Each month you set a budget is kept here.", style = AppType.body, color = TextSecondary, modifier = Modifier.padding(top = 12.dp)) }
            return@LazyColumn
        }
        item {
            Row(Modifier.horizontalScroll(rememberScrollState()).padding(vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                months.forEach { m ->
                    val sel = m == month
                    Text(
                        chipFormat.format(m),
                        style = AppType.tab,
                        color = if (sel) TextPrimary else TextSecondary,
                        modifier = Modifier
                            .heightIn(min = Spacing.circle)
                            .clip(PillShape)
                            .background(if (sel) SurfaceRaised else Surface)
                            .clickable(role = Role.Tab) { if (!sel) { Feedback.tick(); picked = m.toString() } }
                            .semantics { selected = sel }
                            .padding(horizontal = 18.dp, vertical = 12.dp),
                    )
                }
            }
        }
        status?.let { st ->
            item {
                Spacer(Modifier.height(12.dp))
                Text(titleFormat.format(st.month), style = AppType.sectionTitle, color = TextPrimary, modifier = Modifier.semantics { heading() })
                Spacer(Modifier.height(4.dp))
                Text(
                    "${Money.formatWhole(st.overall.spentPaise)} spent of ${Money.formatWhole(st.overall.limitPaise)} · read only",
                    style = AppType.body.copy(fontSize = AppType.rowTitle.fontSize), color = TextSecondary,
                )
                Spacer(Modifier.height(18.dp))
                if (st.categories.isNotEmpty()) RowsCard(rows = st.categories.map { l -> { PastRow(l) } })
                InfoNote("Past budgets can't be edited. Fixing a payment's category still updates these numbers.")
            }
        }
    }
}

/** "Food & Dining · ₹4,800 of ₹5,000 · ₹200 unused" (green) or "₹500 over" (red). Words carry the meaning. */
@Composable
private fun PastRow(l: BudgetLine) {
    Row(Modifier.fillMaxWidth().padding(horizontal = Spacing.cardH, vertical = 16.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(l.category.orEmpty(), style = AppType.rowTitle.copy(fontSize = AppType.tab.fontSize.times(1.15f)), color = TextPrimary)
            Text("${Money.formatWhole(l.spentPaise)} of ${Money.formatWhole(l.limitPaise)}", style = AppType.body, color = TextSecondary)
        }
        Spacer(Modifier.width(12.dp))
        val over = l.level == BudgetLevel.OVER
        Text(
            if (over) "${Money.formatWhole(l.overPaise)} over" else "${Money.formatWhole(l.leftPaise)} unused",
            style = AppType.rowTitle,
            color = if (over) Negative else Positive,
        )
    }
}
