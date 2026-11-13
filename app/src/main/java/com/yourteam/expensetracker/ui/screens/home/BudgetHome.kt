package com.yourteam.expensetracker.ui.screens.home

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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.yourteam.expensetracker.insights.BudgetLevel
import com.yourteam.expensetracker.insights.BudgetStatus
import com.yourteam.expensetracker.insights.Money
import com.yourteam.expensetracker.ui.screens.PaymentCard
import com.yourteam.expensetracker.ui.screens.PaymentRowModel
import com.yourteam.expensetracker.ui.screens.SectionHeader
import com.yourteam.expensetracker.ui.screens.budgets.BudgetBar
import com.yourteam.expensetracker.ui.screens.budgets.CategoryBudgetRow
import com.yourteam.expensetracker.ui.screens.budgets.RowsCard
import com.yourteam.expensetracker.ui.screens.budgets.daysWords
import com.yourteam.expensetracker.ui.screens.budgets.levelColor
import com.yourteam.expensetracker.ui.theme.AppType
import com.yourteam.expensetracker.ui.theme.CardShape
import com.yourteam.expensetracker.ui.theme.SurfaceRaised
import com.yourteam.expensetracker.ui.theme.Negative
import com.yourteam.expensetracker.ui.theme.PillShape
import com.yourteam.expensetracker.ui.theme.Spacing
import com.yourteam.expensetracker.ui.theme.Surface
import com.yourteam.expensetracker.ui.theme.TextPrimary
import com.yourteam.expensetracker.ui.theme.TextSecondary

/**
 * Home with a budget (Feature 3 bd10): the "Spent in <month> so far" budget card, "● N to review", this month's transactions, then "Close to the limit" (categories at 90% or more). Taps go to Budgets.
 */
@Composable
internal fun BudgetHomeList(
    state: HomeUiState.Loaded,
    budget: BudgetStatus,
    listState: LazyListState,
    onOpenReview: () -> Unit,
    onOpenPayments: () -> Unit,
    onCheck: (PaymentRowModel) -> Unit,
    onTag: (PaymentRowModel) -> Unit,
    onOpenSheet: (String) -> Unit,
    onOpenBudgets: () -> Unit,
    onOpenInsights: () -> Unit,
) {
    val close = budget.categories.filter { it.level != BudgetLevel.UNDER }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        state = listState,
        contentPadding = PaddingValues(start = Spacing.screen, end = Spacing.screen, top = 8.dp, bottom = 120.dp),
    ) {
        item { BudgetHero(budget, onOpenBudgets, onOpenInsights, Modifier.padding(bottom = Spacing.section + 4.dp)) }
        if (state.toCheck.count > 0) item {
            Row(Modifier.fillMaxWidth().padding(bottom = Spacing.cardGap), horizontalArrangement = Arrangement.End) {
                ReviewChip(state.toCheck.count, onOpenReview)
            }
        }
        item(key = "transactions") {
            TransactionsSection(
                rows = state.monthRecent,
                onOpen = { onOpenSheet(it.key) },
                onCheck = onCheck,
                onTag = onTag,
                onSeeAll = onOpenPayments,
                empty = { Text("No transactions yet this month.", style = AppType.body, color = TextSecondary, modifier = Modifier.padding(vertical = 8.dp)) },
            )
        }
        if (close.isNotEmpty()) item {
            Spacer(Modifier.height(Spacing.section - Spacing.cardGap))
            SectionHeader("Close to the limit", onClick = onOpenBudgets)
            Spacer(Modifier.height(Spacing.cardGap))
            RowsCard(rows = close.map { line -> { CategoryBudgetRow(line, onClick = onOpenBudgets) } })
        }
    }
}

/**
 * Home's budget card: "Spent in November so far", the hero amount, the month's bar,
 * "of ₹25,000 budget" · "18 days left", then View budgets / View insights. All numbers come from
 * [BudgetStatus] (insights/), never computed here.
 */
@Composable
private fun BudgetHero(st: BudgetStatus, onOpenBudgets: () -> Unit, onOpenInsights: () -> Unit, modifier: Modifier = Modifier) {
    val o = st.overall
    val over = o.level == BudgetLevel.OVER
    val monthName = st.month.month.getDisplayName(java.time.format.TextStyle.FULL, java.util.Locale.getDefault())
    Column(modifier.fillMaxWidth().clip(CardShape).background(Surface).padding(Spacing.cardH)) {
        Text("Spent in $monthName so far", style = AppType.rowTitle, color = TextSecondary)
        Spacer(Modifier.height(6.dp))
        Text(Money.formatWhole(o.spentPaise), style = AppType.hero, color = TextPrimary, maxLines = 1, overflow = TextOverflow.Clip)
        Spacer(Modifier.height(16.dp))
        BudgetBar(o.fraction, levelColor(o.level), track = SurfaceRaised, height = 8.dp)
        Spacer(Modifier.height(12.dp))
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                if (over) "${Money.formatWhole(o.overPaise)} over your ${Money.formatWhole(o.limitPaise)} budget"
                else "of ${Money.formatWhole(o.limitPaise)} budget",
                style = AppType.rowTitle.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.Normal),
                color = if (over) Negative else TextSecondary,
                modifier = Modifier.weight(1f),
            )
            Text(daysWords(st.daysLeft), style = AppType.rowTitle.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.Normal), color = TextSecondary)
        }
        Spacer(Modifier.height(18.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            CardButton("View budgets", onOpenBudgets, Modifier.weight(1f))
            CardButton("View insights", onOpenInsights, Modifier.weight(1f))
        }
    }
}

@Composable
private fun CardButton(label: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Box(
        modifier
            .heightIn(min = Spacing.button)
            .clip(PillShape)
            .background(SurfaceRaised)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 14.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, style = AppType.tab.copy(fontSize = AppType.rowTitle.fontSize), color = TextPrimary, maxLines = 1)
    }
}

/** "● 2 to review": opens Review. Red dot paired with words. */
@Composable
private fun ReviewChip(count: Int, onClick: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .heightIn(min = Spacing.circle)
            .clip(PillShape)
            .background(Surface)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
    ) {
        Box(Modifier.size(8.dp).background(Negative, CircleShape))
        Spacer(Modifier.width(8.dp))
        Text("$count to review", style = AppType.tab, color = Negative)
    }
}
