package com.yourteam.expensetracker.ui.screens.review

import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.clickable
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.yourteam.expensetracker.insights.Money
import com.yourteam.expensetracker.ui.screens.CategorySheet
import com.yourteam.expensetracker.ui.screens.NeedsCheckSheet
import com.yourteam.expensetracker.ui.screens.PaymentCard
import com.yourteam.expensetracker.ui.screens.PaymentRowModel
import com.yourteam.expensetracker.ui.screens.SectionHeader
import com.yourteam.expensetracker.ui.theme.AppCard
import com.yourteam.expensetracker.ui.theme.AppType
import com.yourteam.expensetracker.ui.theme.CardShape
import com.yourteam.expensetracker.ui.theme.ExpenseTrackerTheme
import com.yourteam.expensetracker.ui.theme.Negative
import com.yourteam.expensetracker.ui.theme.PillShape
import com.yourteam.expensetracker.ui.theme.Positive
import com.yourteam.expensetracker.ui.theme.SkeletonCircle
import com.yourteam.expensetracker.ui.theme.SkeletonLine
import com.yourteam.expensetracker.ui.theme.SkeletonPulse
import com.yourteam.expensetracker.ui.theme.Spacing
import com.yourteam.expensetracker.ui.theme.Surface
import com.yourteam.expensetracker.ui.theme.TextPrimary
import com.yourteam.expensetracker.ui.theme.TextSecondary
import com.yourteam.expensetracker.ui.theme.skeleton

/**
 * Review tab (MVP M13, reached from M14's tabs and the Home "need a check" banner).
 * Same shape as Home (DESIGN.md §5):
 *   To check
 *   ₹553
 *   4 payments · pending ones aren't in your totals yet
 *   Did these go through?   [payment cards]
 *   The bank disagrees      [payment cards]
 * Tapping a card or its "Needs check" opens the Needs check sheet. Every number is from insights/.
 */
@Composable
fun ReviewScreen(
    modifier: Modifier = Modifier,
    onOpenPayment: (String) -> Unit = {},
    viewModel: ReviewViewModel = viewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val loaded = state as? ReviewUiState.Loaded
    var checking by rememberSaveable { mutableStateOf<String?>(null) }
    var tagging by remember { mutableStateOf<List<PaymentRowModel>?>(null) }

    // Row is re-read from state, so once answered (and gone from Review) the sheet can't reopen.
    loaded?.rows?.firstOrNull { it.key == checking }?.let { row ->
        NeedsCheckSheet(row, onAnswer = viewModel::answer, onDismiss = { checking = null })
    }
    tagging?.let { queue ->
        CategorySheet(
            queue,
            onPick = { r, c, onlyThis -> viewModel.setCategory(r.key, c, onlyThis) },
            onDismiss = { tagging = null },
        )
    }

    when (val s = state) {
        ReviewUiState.Loading -> ReviewSkeleton(modifier)
        is ReviewUiState.Loaded -> LazyColumn(
            modifier = modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = Spacing.screen, end = Spacing.screen, top = 8.dp, bottom = 32.dp),
        ) {
            item {
                ReviewHero(s, Modifier.padding(bottom = Spacing.section))
            }
            if (s.rows.isEmpty() && s.untagged.isEmpty()) {
                item { CaughtUpCard() }
            }
            section(
                title = "Did these go through?",
                note = "You started these in GPay, but no bank SMS or GPay record has confirmed them yet.",
                rows = s.pending,
                onCheck = { checking = it.key },
                onTag = { tagging = listOf(it) },
            )
            section(
                title = "The bank disagrees",
                note = "You said these didn't go through, but the bank or GPay recorded them as paid. " +
                    "If the money comes back later, it shows up as a refund.",
                rows = s.conflicts,
                onCheck = { checking = it.key },
                onTag = { tagging = listOf(it) },
            )
            untaggedSection(
                rows = s.untagged,
                onOpen = { onOpenPayment(it.key) },
                onTag = { tagging = listOf(it) },
                onTagAll = { tagging = s.untagged },
            )
        }
    }
}

/**
 * "To check" label · hero amount · words line. At zero the hero stays ₹0 and the line says
 * "All caught up" in green, with a check icon (colour is never the only signal).
 */
@Composable
private fun ReviewHero(state: ReviewUiState.Loaded, modifier: Modifier = Modifier) {
    val count = state.toCheck.count
    val amount = Money.formatWhole(state.toCheck.paise)
    val payments = if (count == 1) "1 payment" else "$count payments"
    Column(
        modifier
            .fillMaxWidth()
            .semantics(mergeDescendants = true) {
                contentDescription = if (count == 0) "Nothing to check. All caught up." else "$amount to check across $payments"
            },
    ) {
        Text(
            "To check",
            style = AppType.tab,
            color = TextSecondary,
            modifier = Modifier.heightIn(min = Spacing.circle).padding(top = 13.dp),
        )
        Spacer(Modifier.height(4.dp))
        Text(amount, style = AppType.hero, color = TextPrimary, maxLines = 1, overflow = TextOverflow.Clip)
        Spacer(Modifier.height(10.dp))
        if (count == 0) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(com.yourteam.expensetracker.ui.theme.HugeIcons.Tick, contentDescription = null, tint = Positive, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("All caught up", style = AppType.rowTitle, color = Positive)
            }
        } else {
            val pendingOnly = state.conflicts.isEmpty()
            Text(
                buildAnnotatedString {
                    withStyle(SpanStyle(color = Negative, fontWeight = AppType.rowTitle.fontWeight)) {
                        append("$payments to check")
                    }
                    append(
                        when {
                            state.pending.isEmpty() -> " · already in your totals"
                            pendingOnly -> " · not in your totals yet"
                            else -> " · pending ones aren't in your totals yet"
                        },
                    )
                },
                style = AppType.body.copy(fontSize = AppType.rowTitle.fontSize),
                color = TextSecondary,
            )
        }
    }
}

/** Section title, one plain line on why these are here, then the cards. Skipped when empty. */
private fun LazyListScope.section(
    title: String,
    note: String,
    rows: List<PaymentRowModel>,
    onCheck: (PaymentRowModel) -> Unit,
    onTag: (PaymentRowModel) -> Unit,
) {
    if (rows.isEmpty()) return
    item(key = "header-$title") {
        Column(Modifier.padding(bottom = Spacing.cardGap)) {
            Box(Modifier.semantics { heading() }) { SectionHeader(title) }
            Text(note, style = AppType.body, color = TextSecondary)
            Spacer(Modifier.height(6.dp))
        }
    }
    items(rows, key = { it.key }) { row ->
        Column(Modifier.animateItem()) {
            PaymentCard(row, onClick = { onCheck(row) }, onCheck = { onCheck(row) }, onAddCategory = { onTag(row) })
            Spacer(Modifier.height(Spacing.cardGap))
        }
    }
    item(key = "gap-$title") { Spacer(Modifier.height(Spacing.section - Spacing.cardGap)) }
}

/** How many uncategorised payments Review lists before "Go through all N". */
private const val UNTAGGED_SHOWN = 10

/**
 * Needs Review (Feature 1 §3.8): payments we couldn't categorise with confidence. They're
 * already in every total; adding a category also teaches the payee's default.
 */
private fun LazyListScope.untaggedSection(
    rows: List<PaymentRowModel>,
    onOpen: (PaymentRowModel) -> Unit,
    onTag: (PaymentRowModel) -> Unit,
    onTagAll: () -> Unit,
) {
    if (rows.isEmpty()) return
    item(key = "header-untagged") {
        Column(Modifier.padding(bottom = Spacing.cardGap)) {
            Box(Modifier.semantics { heading() }) { SectionHeader(if (rows.size == 1) "1 payment needs a category" else "${rows.size} payments need a category") }
            Text("Already in your totals. Pick a category once and we'll remember it for that payee.", style = AppType.body, color = TextSecondary)
            Spacer(Modifier.height(10.dp))
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(Spacing.button)
                    .background(com.yourteam.expensetracker.ui.theme.Accent, PillShape)
                    .clip(PillShape)
                    .clickable(role = androidx.compose.ui.semantics.Role.Button, onClick = onTagAll),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    if (rows.size == 1) "Add a category" else "Go through all ${rows.size}",
                    style = AppType.tab,
                    color = com.yourteam.expensetracker.ui.theme.OnAccent,
                )
            }
            Spacer(Modifier.height(10.dp))
        }
    }
    items(rows.take(UNTAGGED_SHOWN), key = { "untagged-${it.key}" }) { row ->
        Column(Modifier.animateItem()) {
            PaymentCard(row, onClick = { onTag(row) }, onAddCategory = { onTag(row) })
            Spacer(Modifier.height(Spacing.cardGap))
        }
    }
    if (rows.size > UNTAGGED_SHOWN) item(key = "more-untagged") {
        Text("and ${rows.size - UNTAGGED_SHOWN} more", style = AppType.body, color = TextSecondary, modifier = Modifier.padding(bottom = Spacing.cardGap))
    }
}

/** Inline empty state (FEEDBACK.md §2, F3): says what would show up here. */
@Composable
private fun CaughtUpCard() {
    AppCard(Modifier.fillMaxWidth()) {
        Text("Nothing needs you", style = AppType.rowTitle, color = TextPrimary)
        Spacer(Modifier.height(6.dp))
        Text(
            "Payments show up here when GPay starts one but nothing confirms it, " +
                "or when the bank disagrees with your answer.",
            style = AppType.body,
            color = TextSecondary,
        )
    }
}

/** Loading: label · hero · words line, then three payment cards (same look as HomeSkeleton). */
@Composable
private fun ReviewSkeleton(modifier: Modifier = Modifier) {
    SkeletonPulse {
        Column(
            modifier
                .fillMaxSize()
                .clipToBounds()
                .padding(horizontal = Spacing.screen)
                .padding(top = 16.dp)
                .clearAndSetSemantics { contentDescription = "Loading payments to check" },
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                SkeletonLine(width = 80.dp, height = 16.dp, onCard = false)
                Box(Modifier.size(150.dp, 60.dp).skeleton(RoundedCornerShape(14.dp), onCard = false))
                SkeletonLine(width = 220.dp, height = 16.dp, onCard = false)
            }
            Spacer(Modifier.height(Spacing.section + 8.dp))
            SkeletonLine(width = 230.dp, height = 22.dp, onCard = false)
            Spacer(Modifier.height(Spacing.section))
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                RowSkeleton()
                RowSkeleton()
                RowSkeleton(Modifier.alpha(0.5f))
            }
        }
    }
}

@Composable
private fun RowSkeleton(modifier: Modifier = Modifier) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .fillMaxWidth()
            .background(Surface, CardShape)
            .padding(horizontal = 18.dp, vertical = Spacing.cardV),
    ) {
        SkeletonCircle()
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(Modifier.fillMaxWidth(0.6f).height(14.dp).skeleton(PillShape))
            Box(Modifier.fillMaxWidth(0.35f).height(12.dp).skeleton(PillShape))
        }
        Spacer(Modifier.width(14.dp))
        SkeletonLine(width = 48.dp, height = 14.dp)
    }
}

@Preview(widthDp = 412, heightDp = 915, showBackground = true, backgroundColor = 0xFF0B0B0D)
@Composable
private fun ReviewSkeletonPreview() {
    ExpenseTrackerTheme { ReviewSkeleton() }
}
