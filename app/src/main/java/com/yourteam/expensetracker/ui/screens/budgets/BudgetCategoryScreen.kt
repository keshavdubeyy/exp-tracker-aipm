package com.yourteam.expensetracker.ui.screens.budgets

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.background
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.yourteam.expensetracker.data.AlertWay
import com.yourteam.expensetracker.data.FiredAlert
import com.yourteam.expensetracker.insights.BudgetLevel
import com.yourteam.expensetracker.insights.BudgetLine
import com.yourteam.expensetracker.insights.Budgets
import com.yourteam.expensetracker.insights.Money
import com.yourteam.expensetracker.insights.Threshold
import com.yourteam.expensetracker.ui.feedback.ACTION_DELAY_MS
import com.yourteam.expensetracker.ui.screens.PaymentCard
import com.yourteam.expensetracker.ui.screens.insights.BackRow
import com.yourteam.expensetracker.ui.screens.paymentRow
import com.yourteam.expensetracker.ui.theme.AppType
import com.yourteam.expensetracker.ui.theme.CardShape
import com.yourteam.expensetracker.ui.theme.Negative
import com.yourteam.expensetracker.ui.theme.Spacing
import com.yourteam.expensetracker.ui.theme.Surface
import com.yourteam.expensetracker.ui.theme.SurfaceRaised
import com.yourteam.expensetracker.ui.theme.TextPrimary
import com.yourteam.expensetracker.ui.theme.TextSecondary
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.format.DateTimeFormatter

private val dayMonth = DateTimeFormatter.ofPattern("d MMM")
private val monthFull = DateTimeFormatter.ofPattern("MMMM")

/** bd7: one category's month — what's left, alerts sent, payments counted, Edit budget (bd8). */
@Composable
fun BudgetCategoryScreen(
    category: String,
    onBack: () -> Unit,
    onOpenPayment: (String) -> Unit,
    viewModel: BudgetsViewModel = viewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snap by viewModel.snapshot.collectAsStateWithLifecycle()
    val active = state as? BudgetsUiState.Active
    val line = active?.status?.line(category)
    var editing by rememberSaveable { mutableStateOf(false) }
    var showAll by rememberSaveable { mutableStateOf(false) }
    // Removed (or not loaded): nothing to show here any more.
    LaunchedEffect(active != null, line == null) { if (active != null && line == null) onBack() }
    if (active == null || line == null) return
    val s = snap ?: return
    val st = active.status
    val payments = line.paymentKeys.mapNotNull { k -> s.payments.find { it.key == k } }
    val shown = if (showAll) payments else payments.take(3)

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = Spacing.screen, end = Spacing.screen, top = 8.dp, bottom = 40.dp),
    ) {
        item { BackRow("${monthFull.format(st.month)} budget", onBack) }
        item {
            Text(category, style = AppType.sectionTitle, color = TextPrimary, modifier = Modifier.padding(top = 8.dp).semantics { heading() })
            Spacer(Modifier.height(4.dp))
            val color = when (line.level) { BudgetLevel.UNDER -> TextPrimary; else -> levelColor(line.level) }
            HeroAmount(
                when (line.level) {
                    BudgetLevel.OVER -> "${Money.formatWhole(line.overPaise)} over"
                    BudgetLevel.REACHED -> "Limit reached"
                    else -> "${Money.formatWhole(line.leftPaise)} left"
                },
                color,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                "${Money.formatWhole(line.spentPaise)} spent of ${Money.formatWhole(line.limitPaise)} · ${daysWords(st.daysLeft)}",
                style = AppType.body.copy(fontSize = AppType.rowTitle.fontSize), color = TextSecondary,
            )
            Spacer(Modifier.height(14.dp))
            BudgetBar(line.fraction, levelColor(line.level), track = Surface, height = 10.dp)
            Spacer(Modifier.height(8.dp))
            Text("${line.percent}% used", style = AppType.body, color = TextSecondary, modifier = Modifier.fillMaxWidth(), textAlign = androidx.compose.ui.text.style.TextAlign.End)
            Spacer(Modifier.height(Spacing.section))
        }
        item {
            CapsLabel("Alerts this month")
            AlertsCard(active.fired[category].orEmpty())
            Spacer(Modifier.height(Spacing.section))
        }
        item {
            CapsLabel("${payments.size} ${if (payments.size == 1) "payment" else "payments"} counted")
        }
        items(shown, key = { it.key }) { p ->
            PaymentCard(paymentRow(p, s), onClick = { onOpenPayment(p.key) })
            Spacer(Modifier.height(Spacing.cardGap))
        }
        if (payments.size > 3 && !showAll) item {
            LinkText("See all ${payments.size}") { showAll = true }
        }
        item {
            Spacer(Modifier.height(16.dp))
            BudgetButton("Edit budget", filled = false) { editing = true }
        }
    }

    if (editing) {
        EditLimitSheet(
            line = line,
            monthName = monthFull.format(st.month),
            nextMonthName = monthFull.format(st.month.plusMonths(1)),
            fired = active.fired[category].orEmpty().map { it.threshold }.toSet(),
            onSave = { viewModel.setLimit(category, it) },
            onRemove = { viewModel.removeLimit(category) },
            onDismiss = { editing = false },
        )
    }
}

/** "75% · shown in the app — 9 Nov", "90% · notification sent — 12 Nov", "100% and over · not yet". */
@Composable
private fun AlertsCard(fired: List<FiredAlert>) {
    fun find(t: Threshold) = fired.firstOrNull { it.threshold == t }
    val rows = mutableListOf<@Composable () -> Unit>()
    fun add(label: String, f: FiredAlert?) {
        rows += {
            Row(Modifier.fillMaxWidth().padding(horizontal = Spacing.cardH, vertical = 18.dp), verticalAlignment = Alignment.CenterVertically) {
                val how = when (f?.way) {
                    null -> "not yet"
                    AlertWay.APP -> "shown in the app"
                    AlertWay.NOTIFIED -> "notification sent"
                    AlertWay.SILENT -> "already passed"
                }
                Text("$label · $how", style = AppType.body.copy(fontSize = AppType.rowTitle.fontSize), color = if (f == null) TextSecondary else TextPrimary, modifier = Modifier.weight(1f))
                if (f != null) Text(dayMonth.format(f.on), style = AppType.body.copy(fontSize = AppType.rowTitle.fontSize), color = TextSecondary)
            }
        }
    }
    add("75%", find(Threshold.P75))
    add("90%", find(Threshold.P90))
    if (find(Threshold.P100) == null && find(Threshold.OVER) == null) add("100% and over", null)
    else { add("100%", find(Threshold.P100)); add("Over", find(Threshold.OVER)) }
    RowsCard(rows = rows)
}

/** bd8: new limit with a preview, re-arm note, and Remove. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EditLimitSheet(
    line: BudgetLine,
    monthName: String,
    nextMonthName: String,
    fired: Set<Threshold>,
    onSave: (Long) -> Unit,
    onRemove: () -> Unit,
    onDismiss: () -> Unit,
) {
    val sheet = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    var amount by remember { mutableStateOf(paiseToRupeesText(line.limitPaise)) }
    var saving by remember { mutableStateOf(false) }
    val focus = remember { FocusRequester() }
    val newLimit = rupeesToPaise(amount)
    val category = line.category.orEmpty()
    fun close(then: () -> Unit) = scope.launch { sheet.hide() }.invokeOnCompletion { then(); onDismiss() }

    LaunchedEffect(Unit) { focus.requestFocus() }
    LaunchedEffect(saving) {
        if (!saving) return@LaunchedEffect
        delay(ACTION_DELAY_MS) // FEEDBACK.md §2a
        newLimit?.let { close { onSave(it) } }
    }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheet, containerColor = Surface) {
        Column(Modifier.navigationBarsPadding().imePadding().padding(horizontal = Spacing.screen).padding(bottom = 12.dp)) {
            Text("$category budget", style = AppType.sectionTitle, color = TextPrimary)
            Spacer(Modifier.height(16.dp))
            BigAmountField(amount, { amount = it }, "$category budget in rupees", focusRequester = focus, fill = SurfaceRaised)
            if (newLimit != null && newLimit != line.limitPaise) {
                val preview = line.copy(limitPaise = newLimit)
                val (kept, _) = Budgets.rearm(preview, fired)
                val rearmed = (fired - kept).filter { it == Threshold.P75 || it == Threshold.P90 || it == Threshold.P100 }.sorted()
                Spacer(Modifier.height(14.dp))
                Column(Modifier.fillMaxWidth().clip(CardShape).background(SurfaceRaised).padding(horizontal = 18.dp, vertical = 16.dp)) {
                    Text(
                        buildAnnotatedString {
                            append("You'll be at ")
                            withStyle(SpanStyle(fontWeight = AppType.rowTitle.fontWeight)) { append("${preview.percent}%") }
                            if (preview.leftPaise >= 0) {
                                append(" with ")
                                withStyle(SpanStyle(fontWeight = AppType.rowTitle.fontWeight)) { append("${Money.formatWhole(preview.leftPaise)} left") }
                            } else {
                                append(", ")
                                withStyle(SpanStyle(fontWeight = AppType.rowTitle.fontWeight)) { append("${Money.formatWhole(preview.overPaise)} over") }
                            }
                            append(".")
                        },
                        style = AppType.body.copy(fontSize = AppType.rowTitle.fontSize.times(1.07f)), color = TextPrimary,
                    )
                    if (rearmed.isNotEmpty()) {
                        Spacer(Modifier.height(6.dp))
                        val names = rearmed.joinToString(" and ") { it.label }
                        Text(
                            "The $names ${if (rearmed.size == 1) "alert" else "alerts"} can come again this month, since you're below ${if (rearmed.size == 1) "it" else "them"} now.",
                            style = AppType.body, color = TextSecondary,
                        )
                    }
                }
            }
            Spacer(Modifier.height(14.dp))
            Text("Only changes $monthName. $nextMonthName starts from this new amount.", style = AppType.body, color = TextSecondary)
            Spacer(Modifier.height(16.dp))
            BudgetButton("Save", loading = saving, enabled = newLimit != null) { saving = true }
            Spacer(Modifier.height(16.dp))
            HorizontalDivider(color = SurfaceRaised)
            LinkText("Remove $category budget", color = Negative, modifier = Modifier.padding(top = 6.dp)) {
                if (!saving) close(onRemove) // reversible: Undo in the banner (FEEDBACK.md §1.3)
            }
            Text("Payments stay in your totals and Insights. Only the limit and alerts go away.", style = AppType.body, color = TextSecondary)
        }
    }
}
