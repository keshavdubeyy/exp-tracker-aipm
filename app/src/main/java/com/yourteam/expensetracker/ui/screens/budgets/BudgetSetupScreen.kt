package com.yourteam.expensetracker.ui.screens.budgets

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.yourteam.expensetracker.categoriser.Categories
import com.yourteam.expensetracker.insights.BudgetLine
import com.yourteam.expensetracker.insights.Budgets
import com.yourteam.expensetracker.insights.CategoryLimit
import com.yourteam.expensetracker.insights.Money
import com.yourteam.expensetracker.insights.MonthBudget
import com.yourteam.expensetracker.ui.feedback.ACTION_DELAY_MS
import com.yourteam.expensetracker.ui.feedback.Feedback
import com.yourteam.expensetracker.ui.theme.Accent
import com.yourteam.expensetracker.ui.theme.AppType
import com.yourteam.expensetracker.ui.theme.CardShape
import com.yourteam.expensetracker.ui.theme.Negative
import com.yourteam.expensetracker.ui.theme.PillShape
import com.yourteam.expensetracker.ui.theme.Positive
import com.yourteam.expensetracker.ui.theme.Spacing
import com.yourteam.expensetracker.ui.theme.Surface
import com.yourteam.expensetracker.ui.theme.SurfaceRaised
import com.yourteam.expensetracker.ui.theme.TextPrimary
import com.yourteam.expensetracker.ui.theme.TextSecondary
import com.yourteam.expensetracker.ui.theme.Warning
import kotlinx.coroutines.delay
import java.time.format.DateTimeFormatter

/** One category row on step 3: name and the typed rupees ("" = no budget for it). */
private class SplitRow(val category: String, amount: String) {
    var amount by mutableStateOf(amount)
}

private val monthFull = DateTimeFormatter.ofPattern("MMMM")

/**
 * bd2–bd5: income (optional) → overall → categories. Categories that add up to more than the
 * overall warn but never block. Everything typed is whole rupees; every sum comes from [Budgets].
 */
@Composable
fun BudgetSetupScreen(mode: String, onDone: () -> Unit, viewModel: BudgetsViewModel = viewModel()) {
    val data by viewModel.setup.collectAsStateWithLifecycle()
    val d = data ?: return
    val startStep = if (mode == SetupMode.EDIT && d.existing != null) 3 else 1
    var step by rememberSaveable { mutableStateOf(startStep) }
    var income by rememberSaveable { mutableStateOf("") }
    var overall by rememberSaveable { mutableStateOf("") }
    val rows = remember { mutableStateListOf<SplitRow>() }
    var filled by rememberSaveable { mutableStateOf(false) }
    var saving by remember { mutableStateOf(false) }
    var picking by remember { mutableStateOf(false) }
    val lastName = monthFull.format(d.month.minusMonths(1))
    val monthName = monthFull.format(d.month)

    // Fill the fields once, from what's there or from last month.
    LaunchedEffect(Unit) {
        if (rows.isNotEmpty()) return@LaunchedEffect
        val existing = d.existing
        val lastCats = d.lastMonth.byCategory.filter { it.first in Categories.expense }
        when {
            existing != null && mode != SetupMode.NEW && mode != SetupMode.FROM_LAST -> {
                if (!filled) { income = paiseToRupeesText(existing.incomePaise); overall = paiseToRupeesText(existing.overallPaise) }
                existing.categories.forEach { rows += SplitRow(it.category, paiseToRupeesText(it.limitPaise)) }
            }
            mode == SetupMode.FROM_LAST && lastCats.isNotEmpty() -> {
                if (!filled) overall = paiseToRupeesText(Budgets.wholeRupeesUp(d.lastMonth.totalPaise))
                lastCats.forEach { (c, p) -> rows += SplitRow(c, paiseToRupeesText(Budgets.wholeRupeesUp(p))) }
            }
            else -> {
                if (!filled) existing?.let { income = paiseToRupeesText(it.incomePaise) }
                val start = lastCats.map { it.first }.take(5).ifEmpty {
                    listOf(Categories.FOOD, Categories.SHOPPING, Categories.TRAVEL, Categories.GROCERIES, Categories.ENTERTAINMENT)
                }
                start.forEach { rows += SplitRow(it, "") }
            }
        }
        filled = true
    }

    fun back() { if (step > startStep) step-- else onDone() }
    BackHandler { back() }

    val overallPaise = rupeesToPaise(overall)
    val split = Budgets.split(overallPaise ?: 0L, rows.mapNotNull { rupeesToPaise(it.amount) })

    fun budget(raiseTo: Long? = null) = MonthBudget(
        month = d.month,
        overallPaise = raiseTo ?: overallPaise ?: 0L,
        categories = rows.mapNotNull { r -> rupeesToPaise(r.amount)?.let { CategoryLimit(r.category, it) } },
        incomePaise = rupeesToPaise(income),
    )
    var pendingSave by remember { mutableStateOf<MonthBudget?>(null) }
    LaunchedEffect(pendingSave) {
        val b = pendingSave ?: return@LaunchedEffect
        saving = true
        delay(ACTION_DELAY_MS) // FEEDBACK.md §2a
        viewModel.save(b, editing = d.existing != null)
        onDone()
    }

    Column(Modifier.fillMaxSize().imePadding()) {
        StepHeader(step, onBack = ::back)
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = Spacing.screen)) {
            when (step) {
                1 -> {
                    StepTitle("How much do you expect to earn this month?")
                    StepSubtitle("Optional. It only helps show what's left for savings. It isn't shared and doesn't change your budget.")
                    Spacer(Modifier.height(24.dp))
                    val focus = remember { FocusRequester() }
                    BigAmountField(income, { income = it }, "Expected income in rupees", focusRequester = focus)
                    LaunchedEffect(Unit) { focus.requestFocus() }
                }
                2 -> {
                    StepTitle("How much do you want to spend in $monthName?")
                    StepSubtitle("Your overall limit, across everything.")
                    Spacer(Modifier.height(24.dp))
                    val focus = remember { FocusRequester() }
                    BigAmountField(overall, { overall = it }, "Overall budget in rupees", focusRequester = focus)
                    LaunchedEffect(Unit) { focus.requestFocus() }
                    val incomePaise = rupeesToPaise(income)
                    if (incomePaise != null && overallPaise != null) {
                        Spacer(Modifier.height(16.dp))
                        val savings = Budgets.savingsLeft(incomePaise, overallPaise)
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth().clip(PillShape).background(Surface).padding(horizontal = 22.dp, vertical = 18.dp),
                        ) {
                            if (savings >= 0) {
                                Text("Left for savings and other plans", style = AppType.body.copy(fontSize = AppType.rowTitle.fontSize), color = TextSecondary, modifier = Modifier.weight(1f))
                                Text(Money.formatWhole(savings), style = AppType.rowTitle, color = Positive)
                            } else {
                                Text("More than you expect to earn", style = AppType.body.copy(fontSize = AppType.rowTitle.fontSize), color = TextSecondary, modifier = Modifier.weight(1f))
                                Text("${Money.formatWhole(-savings)} above", style = AppType.rowTitle, color = Negative)
                            }
                        }
                    }
                    if (d.spentThisMonthPaise > 0) {
                        Spacer(Modifier.height(12.dp))
                        val left = overallPaise?.let { BudgetLine(null, it, d.spentThisMonthPaise) }
                        Column(Modifier.fillMaxWidth().clip(CardShape).background(Surface).padding(horizontal = 22.dp, vertical = 18.dp)) {
                            Text("${Money.formatWhole(d.spentThisMonthPaise)} already spent this month counts", style = AppType.rowTitle, color = TextPrimary)
                            Spacer(Modifier.height(6.dp))
                            Text(
                                "Budgets cover the whole month, so payments since 1 ${DateTimeFormatter.ofPattern("MMM").format(d.month)} are included." +
                                    when {
                                        left == null -> ""
                                        left.leftPaise >= 0 -> " You'd have ${Money.formatWhole(left.leftPaise)} left."
                                        else -> " You'd already be ${Money.formatWhole(left.overPaise)} over."
                                    },
                                style = AppType.body.copy(fontSize = AppType.tab.fontSize, lineHeight = AppType.rowTitle.lineHeight),
                                color = TextSecondary,
                            )
                        }
                    }
                }
                else -> {
                    StepTitle("Split it across categories")
                    StepSubtitle("Only the ones you care about. Leave the rest.")
                    Spacer(Modifier.height(20.dp))
                    val cardRows: List<@Composable () -> Unit> = rows.map { r ->
                        val row: @Composable () -> Unit = {
                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(start = Spacing.cardH, end = 12.dp, top = 12.dp, bottom = 12.dp)) {
                                Column(Modifier.weight(1f)) {
                                    Text(r.category, style = AppType.rowTitle.copy(fontSize = AppType.tab.fontSize.times(1.15f)), color = TextPrimary)
                                    d.lastMonth.of(r.category)?.let {
                                        Text("$lastName: ${Money.formatWhole(it)}", style = AppType.body, color = TextSecondary)
                                    }
                                }
                                RowAmountField(r.amount, { r.amount = it }, "${r.category} budget in rupees")
                            }
                        }
                        row
                    }
                    val addRow: @Composable () -> Unit = {
                        LinkText("+ Add a category budget", modifier = Modifier.padding(horizontal = Spacing.cardH - 4.dp, vertical = 4.dp)) { picking = true }
                    }
                    val allRows = if (rows.size < Categories.expense.size) cardRows + listOf(addRow) else cardRows
                    RowsCard(rows = allRows)
                    Spacer(Modifier.height(16.dp))
                }
            }
        }

        // Pinned bottom: buttons (and on step 3, the split summary).
        Column(Modifier.navigationBarsPadding().padding(start = Spacing.screen, end = Spacing.screen, top = 8.dp, bottom = 12.dp)) {
            when (step) {
                1 -> {
                    BudgetButton("Next") { step = 2 }
                    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                        LinkText("Skip", color = TextSecondary) { income = ""; step = 2 }
                    }
                }
                2 -> BudgetButton("Next", enabled = overallPaise != null) { step = 3 }
                else -> {
                    val total = overallPaise ?: 0L
                    if (split.overByPaise > 0) {
                        Column(
                            Modifier.fillMaxWidth().clip(CardShape).background(Surface).border(2.dp, Warning, CardShape)
                                .padding(horizontal = 22.dp, vertical = 16.dp),
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("${Money.formatWhole(split.allocatedPaise)} of ${Money.formatWhole(total)} split", style = AppType.body.copy(fontSize = AppType.rowTitle.fontSize), color = TextPrimary, modifier = Modifier.weight(1f))
                                Text("${Money.formatWhole(split.overByPaise)} over", style = AppType.rowTitle, color = Warning)
                            }
                            Spacer(Modifier.height(4.dp))
                            Text("Your categories add up to more than your overall budget.", style = AppType.body, color = TextSecondary)
                        }
                        Spacer(Modifier.height(12.dp))
                        BudgetButton("Raise overall to ${Money.formatWhole(split.allocatedPaise)}", loading = saving) {
                            overall = paiseToRupeesText(split.allocatedPaise)
                            pendingSave = budget(raiseTo = split.allocatedPaise)
                        }
                        Spacer(Modifier.height(12.dp))
                        BudgetButton("Keep ${Money.formatWhole(total)} and save", filled = false, enabled = !saving) { pendingSave = budget() }
                    } else {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth().clip(PillShape).background(Surface).padding(horizontal = 22.dp, vertical = 16.dp),
                        ) {
                            Text("${Money.formatWhole(split.allocatedPaise)} of ${Money.formatWhole(total)} split", style = AppType.body.copy(fontSize = AppType.rowTitle.fontSize), color = TextPrimary, modifier = Modifier.weight(1f))
                            Text("${Money.formatWhole(split.unallocatedPaise)} unallocated", style = AppType.rowTitle, color = TextPrimary)
                        }
                        InfoNote("Unallocated money still counts toward your overall ${Money.formatWhole(total)}. Custom categories count inside their parent.", Modifier.padding(bottom = 12.dp))
                        BudgetButton("Save budgets", loading = saving, enabled = overallPaise != null) { pendingSave = budget() }
                    }
                }
            }
        }
    }

    if (picking) {
        AddCategorySheet(
            choices = Categories.expense.filter { c -> rows.none { it.category == c } },
            onPick = { c ->
                Feedback.tick() // F14
                rows += SplitRow(c, "")
                picking = false
            },
            onDismiss = { picking = false },
        )
    }
}

/** Back button, "Step 1 of 3" and three progress segments. */
@Composable
private fun StepHeader(step: Int, onBack: () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(start = Spacing.screen, end = Spacing.screen, top = 12.dp, bottom = 20.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(Spacing.circle).clip(PillShape).background(Surface).clickable(role = Role.Button, onClick = onBack),
                contentAlignment = Alignment.Center,
            ) { Icon(com.yourteam.expensetracker.ui.theme.HugeIcons.ChevronLeft, contentDescription = "Back", tint = TextPrimary, modifier = Modifier.size(24.dp)) }
            Spacer(Modifier.width(14.dp))
            Text("Step $step of 3", style = AppType.body.copy(fontSize = AppType.rowTitle.fontSize), color = TextSecondary)
        }
        Spacer(Modifier.height(16.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            repeat(3) { i ->
                Box(Modifier.weight(1f).height(4.dp).clip(PillShape).background(if (i < step) Accent else SurfaceRaised))
            }
        }
    }
}

@Composable
private fun StepTitle(text: String) {
    Text(text, style = AppType.sectionTitle.copy(fontSize = AppType.sectionTitle.fontSize.times(1.15f), lineHeight = AppType.sectionTitle.lineHeight.times(1.15f)), color = TextPrimary, modifier = Modifier.semantics { heading() })
}

@Composable
private fun StepSubtitle(text: String) {
    Spacer(Modifier.height(6.dp))
    Text(text, style = AppType.body.copy(fontSize = AppType.rowTitle.fontSize, lineHeight = AppType.statValue.lineHeight), color = TextSecondary)
}

/** "+ Add a category budget": top-level categories only (custom ones roll into their parent). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddCategorySheet(choices: List<String>, onPick: (String) -> Unit, onDismiss: () -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true), containerColor = Surface) {
        Column(Modifier.navigationBarsPadding().padding(horizontal = Spacing.screen).padding(bottom = 16.dp)) {
            Text("Add a category budget", style = AppType.statValue, color = TextPrimary)
            Spacer(Modifier.height(4.dp))
            Text(
                buildAnnotatedString {
                    append("Budgets go on main categories. ")
                    withStyle(SpanStyle(color = TextSecondary)) { append("Your own categories count inside their parent.") }
                },
                style = AppType.body, color = TextSecondary,
            )
            Spacer(Modifier.height(12.dp))
            choices.forEach { c ->
                Text(
                    c,
                    style = AppType.rowTitle,
                    color = TextPrimary,
                    modifier = Modifier.fillMaxWidth().heightIn(min = Spacing.button).clip(PillShape)
                        .clickable(role = Role.Button) { onPick(c) }.padding(horizontal = 8.dp, vertical = 16.dp),
                )
            }
        }
    }
}
