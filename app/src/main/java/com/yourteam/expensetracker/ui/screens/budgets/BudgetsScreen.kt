package com.yourteam.expensetracker.ui.screens.budgets

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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.yourteam.expensetracker.insights.BudgetLevel
import com.yourteam.expensetracker.insights.BudgetStatus
import com.yourteam.expensetracker.insights.Money
import com.yourteam.expensetracker.ui.feedback.ACTION_DELAY_MS
import com.yourteam.expensetracker.ui.screens.SectionHeader
import com.yourteam.expensetracker.ui.screens.home.HomeSkeleton
import com.yourteam.expensetracker.ui.theme.Accent
import com.yourteam.expensetracker.ui.theme.AppType
import com.yourteam.expensetracker.ui.theme.CardShape
import com.yourteam.expensetracker.ui.theme.Spacing
import com.yourteam.expensetracker.ui.theme.Surface
import com.yourteam.expensetracker.ui.theme.SurfaceRaised
import com.yourteam.expensetracker.ui.theme.TextPrimary
import com.yourteam.expensetracker.ui.theme.TextSecondary
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.YearMonth
import java.time.format.DateTimeFormatter

/** Setup modes for [BudgetNav.setup]. */
object SetupMode {
    const val NEW = "new"            // bd1 "Set a budget": blank amounts, last month as hints
    const val FROM_LAST = "last"     // bd1 "Start from October's spending": last month's amounts filled in
    const val EDIT = "edit"          // "Edit" on the overview: straight to the categories step
    const val EDIT_ALL = "edit_all"  // bd11 "Edit amounts": from the first step
}

/** Feature 3 Budgets tab. */
@Composable
fun BudgetsScreen(
    onSetup: (mode: String) -> Unit,
    onOpenCategory: (String) -> Unit,
    onOpenPast: (YearMonth?) -> Unit,
    onOpenReview: () -> Unit,
    viewModel: BudgetsViewModel = viewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    when (val s = state) {
        BudgetsUiState.Loading -> HomeSkeleton()
        is BudgetsUiState.NoBudget -> NoBudget(s, onSetup, onOpenPast)
        is BudgetsUiState.Active -> {
            Overview(s, onSetup, onOpenCategory, onOpenPast, onOpenReview)
            s.newMonth?.let { prev ->
                NewMonthSheet(
                    month = s.status.month,
                    prev = prev,
                    onKeep = { viewModel.keepSame(s.status.month) },
                    onEdit = { onSetup(SetupMode.EDIT_ALL) },
                    onSeePrevious = { onOpenPast(prev.month) },
                    onDismiss = { viewModel.keepSame(s.status.month) },
                )
            }
        }
    }
}

private val monthFull = DateTimeFormatter.ofPattern("MMMM")
private val monthShort = DateTimeFormatter.ofPattern("MMM")

/** bd1: spent so far, "Plan your month", Set a budget / Start from last month. */
@Composable
private fun NoBudget(s: BudgetsUiState.NoBudget, onSetup: (String) -> Unit, onOpenPast: (YearMonth?) -> Unit) {
    val last = s.month.minusMonths(1)
    val canCopy = s.lastMonthSpend.totalPaise > 0
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = Spacing.screen, end = Spacing.screen, top = 8.dp, bottom = 40.dp),
    ) {
        item {
            HeroLabel("Spent so far · 1–${s.today.dayOfMonth} ${monthShort.format(s.today)}")
            Spacer(Modifier.height(6.dp))
            HeroAmount(Money.formatWhole(s.spentPaise))
            Spacer(Modifier.height(4.dp))
            Text("No budget set for ${monthFull.format(s.month)}", style = AppType.body.copy(fontSize = AppType.rowTitle.fontSize), color = TextSecondary)
            Spacer(Modifier.height(Spacing.section + 4.dp))
        }
        item {
            Column(Modifier.fillMaxWidth().clip(CardShape).background(Surface).padding(horizontal = Spacing.cardH, vertical = 22.dp)) {
                Text("Plan your month", style = AppType.sectionTitle, color = TextPrimary, modifier = Modifier.semantics { heading() })
                Spacer(Modifier.height(10.dp))
                Text(
                    "Set how much you want to spend, split it across categories, and we'll track it from your payments automatically.",
                    style = AppType.body.copy(fontSize = AppType.rowTitle.fontSize, lineHeight = AppType.sectionTitle.lineHeight),
                    color = TextSecondary,
                )
                Spacer(Modifier.height(20.dp))
                BudgetButton("Set a budget") { onSetup(SetupMode.NEW) }
                if (canCopy) {
                    Spacer(Modifier.height(12.dp))
                    BudgetButton("Start from ${monthFull.format(last)}'s spending", filled = false) { onSetup(SetupMode.FROM_LAST) }
                }
            }
        }
        if (canCopy) item {
            InfoNote("“Start from ${monthFull.format(last)}” fills in what you actually spent per category last month. You can change every number.")
        }
        if (s.hasPast) item {
            Spacer(Modifier.height(12.dp))
            LinkText("Past months") { onOpenPast(null) }
        }
    }
}

/** bd6 (under), bd13 (over), bd14 (nothing spent yet). */
@Composable
private fun Overview(
    s: BudgetsUiState.Active,
    onSetup: (String) -> Unit,
    onOpenCategory: (String) -> Unit,
    onOpenPast: (YearMonth?) -> Unit,
    onOpenReview: () -> Unit,
) {
    val st = s.status
    val next = st.month.plusMonths(1)
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = Spacing.screen, end = Spacing.screen, top = 8.dp, bottom = 40.dp),
    ) {
        item { OverallHero(st, Modifier.padding(bottom = Spacing.section + 4.dp)) }

        item {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Box(Modifier.weight(1f)) { SectionHeader("Category budgets") }
                LinkText("Edit") { onSetup(SetupMode.EDIT) }
            }
            Spacer(Modifier.height(Spacing.cardGap))
        }
        item {
            if (st.categories.isEmpty()) {
                Column(Modifier.fillMaxWidth().clip(CardShape).background(Surface).padding(horizontal = Spacing.cardH, vertical = 18.dp)) {
                    Text("No category budgets yet", style = AppType.rowTitle, color = TextPrimary)
                    Spacer(Modifier.height(4.dp))
                    Text("Your overall budget is tracked. Add limits for the categories you care about.", style = AppType.body, color = TextSecondary)
                    LinkText("+ Add a category budget") { onSetup(SetupMode.EDIT) }
                }
            } else {
                RowsCard(rows = st.categories.map { line -> { CategoryBudgetRow(line) { onOpenCategory(line.category!!) } } })
            }
        }

        if (st.unsortedCount > 0 || st.noBudgetPaise > 0) {
            item {
                Spacer(Modifier.height(Spacing.section + 4.dp))
                CapsLabel("Counted in the total only")
                val rows = buildList<@Composable () -> Unit> {
                    if (st.unsortedCount > 0) add {
                        TotalOnlyRow(
                            title = "Not sorted yet",
                            subtitle = "${st.unsortedCount} ${if (st.unsortedCount == 1) "payment" else "payments"} · sorting may change your budgets",
                            subtitleAccent = true,
                            amount = st.unsortedPaise,
                            onClick = onOpenReview,
                        )
                    }
                    if (st.noBudgetPaise > 0) add {
                        TotalOnlyRow("No budget set", st.noBudgetCategories.joinToString(", "), false, st.noBudgetPaise, null)
                    }
                }
                RowsCard(rows = rows)
            }
        }

        item {
            val note = when {
                st.overall.spentPaise == 0L -> "Payments appear here as soon as they're captured. If you've paid and nothing shows, check your sources."
                st.dailyPaise != null && st.dailyPaise > 0 && !st.lastDay ->
                    "“About ${Money.formatWhole(st.dailyPaise)} a day” is just ${Money.formatWhole(st.overall.leftPaise)} ÷ ${st.daysLeft} days, to help you plan. " +
                        "Unused budget doesn't carry into ${monthFull.format(next)}."
                else -> "Budgets reset on 1 ${monthShort.format(next)}. Unused budget doesn't carry over."
            }
            InfoNote(note, Modifier.padding(top = 6.dp))
        }
        if (s.hasPast) item {
            Spacer(Modifier.height(8.dp))
            LinkText("Past months") { onOpenPast(null) }
        }
    }
}

/** The overall hero: "Left this month" or "Over budget by", bar, days and daily amount. */
@Composable
private fun OverallHero(st: BudgetStatus, modifier: Modifier = Modifier) {
    val o = st.overall
    val over = o.level == BudgetLevel.OVER
    val nothingYet = o.spentPaise == 0L
    val month = monthFull.format(st.month)
    Column(modifier.fillMaxWidth()) {
        HeroLabel(if (over) "Over budget by · $month" else "Left this month · $month")
        Spacer(Modifier.height(6.dp))
        HeroAmount(Money.formatWhole(if (over) o.overPaise else o.leftPaise))
        Spacer(Modifier.height(4.dp))
        val sub = when {
            over -> "over your ${Money.formatWhole(o.limitPaise)} budget · ${Money.formatWhole(o.spentPaise)} spent"
            nothingYet -> "Nothing spent yet · ${daysWords(st.daysLeft)}"
            else -> "${Money.formatWhole(o.spentPaise)} spent of ${Money.formatWhole(o.limitPaise)}"
        }
        Text(sub, style = AppType.body.copy(fontSize = AppType.rowTitle.fontSize), color = TextSecondary)
        Spacer(Modifier.height(14.dp))
        BudgetBar(o.fraction, levelColor(o.level), track = Surface, height = 10.dp)
        if (!nothingYet) {
            Spacer(Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(daysLine(st), style = AppType.body, color = TextSecondary, modifier = Modifier.weight(1f))
                Spacer(Modifier.width(8.dp))
                Text("${o.percent}% used", style = AppType.body, color = TextSecondary)
            }
        }
    }
}

/** "18 days left · about ₹555 a day" · "Last day · ₹300 left today" · "Last 2 days · no daily amount when over". */
internal fun daysLine(st: BudgetStatus): String {
    val o = st.overall
    return when {
        st.daysLeft <= 0 -> "Month ended"
        o.level == BudgetLevel.OVER -> "${daysWords(st.daysLeft, short = true)} · no daily amount when over"
        st.lastDay -> "Last day · ${Money.formatWhole(o.leftPaise)} left today"
        st.dailyPaise != null && st.dailyPaise > 0 -> "${daysWords(st.daysLeft)} · about ${Money.formatWhole(st.dailyPaise)} a day"
        else -> daysWords(st.daysLeft)
    }
}

internal fun daysWords(days: Int, short: Boolean = false): String = when {
    days == 1 -> "Last day"
    short && days <= 3 -> "Last $days days"
    else -> "$days days left"
}

@Composable
internal fun HeroLabel(text: String) {
    Text(text, style = AppType.tab.copy(fontSize = AppType.rowTitle.fontSize.times(1.07f)), color = TextSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
}

@Composable
internal fun HeroAmount(text: String, color: androidx.compose.ui.graphics.Color = TextPrimary) {
    Text(text, style = AppType.hero, color = color, maxLines = 1, overflow = TextOverflow.Clip)
}

@Composable
private fun TotalOnlyRow(title: String, subtitle: String, subtitleAccent: Boolean, amount: Long, onClick: (() -> Unit)?) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(role = Role.Button, onClick = onClick) else Modifier)
            .padding(horizontal = Spacing.cardH, vertical = 16.dp),
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = AppType.rowTitle.copy(fontSize = AppType.tab.fontSize.times(1.15f)), color = TextPrimary)
            Spacer(Modifier.height(2.dp))
            Text(subtitle, style = if (subtitleAccent) AppType.chip.copy(fontSize = AppType.body.fontSize) else AppType.body, color = if (subtitleAccent) Accent else TextSecondary)
        }
        Spacer(Modifier.width(12.dp))
        Text(Money.formatWhole(amount), style = AppType.rowTitle, color = TextPrimary)
    }
}

/** bd11: the 1st of the month. Last month's amounts are already copied; Keep or Edit. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun NewMonthSheet(
    month: YearMonth,
    prev: NewMonthSummary,
    onKeep: () -> Unit,
    onEdit: () -> Unit,
    onSeePrevious: () -> Unit,
    onDismiss: () -> Unit,
) {
    val sheet = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    var saving by remember { mutableStateOf(false) }
    fun close(then: () -> Unit) = scope.launch { sheet.hide() }.invokeOnCompletion { then() }
    val m = monthFull.format(month)
    val p = monthFull.format(prev.month)
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheet, containerColor = Surface) {
        Column(Modifier.navigationBarsPadding().padding(start = Spacing.screen, end = Spacing.screen, bottom = 16.dp)) {
            Text("New month, fresh budgets", style = AppType.sectionTitle, color = TextPrimary, modifier = Modifier.semantics { heading() })
            Spacer(Modifier.height(8.dp))
            Text("$m starts at ₹0 spent. Unused $p money doesn't carry over.", style = AppType.body.copy(fontSize = AppType.rowTitle.fontSize), color = TextSecondary)
            Spacer(Modifier.height(18.dp))
            Column(Modifier.fillMaxWidth().clip(CardShape).background(SurfaceRaised).padding(horizontal = Spacing.cardH, vertical = 16.dp)) {
                Text("$p in short".uppercase(), style = AppType.chip, color = TextSecondary)
                Spacer(Modifier.height(10.dp))
                ShortRow("Spent", "${Money.formatWhole(prev.spentPaise)} of ${Money.formatWhole(prev.limitPaise)}")
                if (prev.categoryCount > 0) ShortRow("Within budget", "${prev.withinCount} of ${prev.categoryCount} categories")
                LinkText("See $p", modifier = Modifier.padding(top = 2.dp)) { close(onSeePrevious) }
            }
            Spacer(Modifier.height(18.dp))
            Text("We've kept $p's budgets for $m.", style = AppType.rowTitle, color = TextPrimary)
            Spacer(Modifier.height(14.dp))
            BudgetButton("Keep the same", loading = saving) { saving = true }
            Spacer(Modifier.height(12.dp))
            BudgetButton("Edit amounts", filled = false, enabled = !saving) { close(onEdit) }
        }
    }
    LaunchedEffect(saving) {
        if (!saving) return@LaunchedEffect
        delay(ACTION_DELAY_MS) // FEEDBACK.md §2a
        close(onKeep)
    }
}

@Composable
private fun ShortRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = AppType.body.copy(fontSize = AppType.rowTitle.fontSize), color = TextPrimary, modifier = Modifier.weight(1f))
        Text(value, style = AppType.rowTitle, color = TextPrimary)
    }
}
