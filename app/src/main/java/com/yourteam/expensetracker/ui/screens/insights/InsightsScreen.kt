package com.yourteam.expensetracker.ui.screens.insights

import androidx.compose.foundation.layout.offset
import com.yourteam.expensetracker.ui.theme.LinkButton
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
import com.yourteam.expensetracker.insights.CategorySlice
import com.yourteam.expensetracker.insights.HistoryState
import com.yourteam.expensetracker.insights.InsightCard
import com.yourteam.expensetracker.insights.InsightSummary
import com.yourteam.expensetracker.insights.Money
import com.yourteam.expensetracker.insights.PeriodKind
import com.yourteam.expensetracker.ui.theme.Accent
import com.yourteam.expensetracker.ui.theme.AppCard
import com.yourteam.expensetracker.ui.theme.AppIcons
import com.yourteam.expensetracker.ui.theme.AppType
import com.yourteam.expensetracker.ui.theme.CardShape
import com.yourteam.expensetracker.ui.theme.DeltaChipShape
import com.yourteam.expensetracker.ui.theme.Negative
import com.yourteam.expensetracker.ui.theme.PillShape
import com.yourteam.expensetracker.ui.theme.Positive
import com.yourteam.expensetracker.ui.theme.Spacing
import com.yourteam.expensetracker.ui.theme.Surface
import com.yourteam.expensetracker.ui.theme.SurfaceRaised
import com.yourteam.expensetracker.ui.theme.TextPrimary
import com.yourteam.expensetracker.ui.theme.TextSecondary
import java.time.LocalDate

/** How many categorised rows the "Where it went" card shows before "All categories". */
private const val BREAKDOWN_ROWS = 5

@Composable
fun InsightsScreen(
    modifier: Modifier = Modifier,
    onOpenReview: () -> Unit = {},
    onNavigate: (String) -> Unit = {},
    onOpenSources: () -> Unit = {},
    viewModel: InsightsViewModel = viewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val period by viewModel.selectedPeriod.collectAsStateWithLifecycle()
    val today = LocalDate.now()
    var showDates by remember { mutableStateOf(false) }
    val ctx = androidx.compose.ui.platform.LocalContext.current
    androidx.compose.runtime.LaunchedEffect(Unit) { com.yourteam.expensetracker.metrics.UsageLog.insightsOpened(ctx) } // M24: Insights opened this week

    Column(modifier.fillMaxSize()) {
        val loaded = state as? InsightsUiState.Loaded
        InsightsHeader(
            label = InsightsWords.label(loaded?.summary?.period ?: period, today),
            comparison = loaded?.summary?.comparisonLabel,
            refreshing = loaded?.refreshing == true,
            onPickDates = { showDates = true },
            onRefresh = viewModel::refresh,
        )
        PeriodTabs(selected = period.kind, onSelect = viewModel::setKind)
        when (val s = state) {
            is InsightsUiState.Loading ->
                InsightsSkeleton(label = InsightsWords.label(period, today))
            is InsightsUiState.Loaded -> LoadedContent(
                state = s,
                today = today,
                onPickDates = { showDates = true },
                onRefresh = viewModel::refresh,
                onRate = viewModel::rate,
                onOpenCategory = { cat -> onNavigate(InsightNav.categoryRoute(cat, s.summary.period)) },
                onOpenInsight = { id -> onNavigate(InsightNav.insightRoute(id, s.summary.period)) },
                onOpenReview = onOpenReview,
                onOpenSources = onOpenSources,
            )
        }
    }

    if (showDates) {
        InsightsDateSheet(
            period = period,
            today = today,
            onDismiss = { showDates = false },
            onStep = viewModel::step,
            onCustom = { a, b -> viewModel.setPeriod(com.yourteam.expensetracker.insights.InsightPeriod.custom(a, b)) },
        )
    }
}

@Composable
private fun LoadedContent(
    state: InsightsUiState.Loaded,
    today: LocalDate,
    onPickDates: () -> Unit,
    onRefresh: () -> Unit,
    onRate: (InsightCard, InsightFeedback) -> Unit,
    onOpenCategory: (String) -> Unit,
    onOpenInsight: (String) -> Unit,
    onOpenReview: () -> Unit,
    onOpenSources: () -> Unit,
) {
    val s = state.summary
    val cardDates = InsightsWords.span(s.period.start, minOf(s.period.end, today))
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = Spacing.screen, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        item { Spacer(Modifier.height(8.dp)); SummaryCard(s) }

        // Category-wise spending — shown whenever money was counted.
        if (s.totalPaise > 0) {
            item {
                Spacer(Modifier.height(12.dp))
                var expanded by remember(s.period) { mutableStateOf(false) }
                WhereItWent(
                    summary = s,
                    expanded = expanded,
                    onToggleExpand = { expanded = !expanded },
                    onCategory = onOpenCategory,
                    onSortThem = onOpenReview,
                )
            }
        }

        // The period-specific section below the totals.
        when (s.historyState) {
            HistoryState.SINGLE_DAY -> singleDay(s, onOpenSources)
            HistoryState.INSUFFICIENT -> {
                item { InsightsSectionHeader() }
                item { InsufficientCard(s, onImport = onRefresh) }
            }
            HistoryState.NO_PATTERN -> {
                item { InsightsSectionHeader() }
                item { NoPatternCard(s) }
            }
            HistoryState.OK, HistoryState.CUSTOM -> {
                item { InsightsSectionHeader() }
                if (state.cards.isEmpty()) {
                    item { NoPatternCard(s) }
                } else {
                    items(state.cards, key = { it.id }) { card ->
                        val ctx = androidx.compose.ui.platform.LocalContext.current
                        androidx.compose.runtime.LaunchedEffect(card.id) { com.yourteam.expensetracker.metrics.InsightLog.shown(ctx, card) } // M24
                        InsightCardView(
                            card,
                            dates = cardDates,
                            onOpen = { onOpenInsight(card.id) },
                            onRate = { onRate(card, it) },
                        )
                        Spacer(Modifier.height(12.dp))
                    }
                }
            }
        }

        item {
            Spacer(Modifier.height(8.dp))
            FootNote(
                if (s.historyState == HistoryState.SINGLE_DAY)
                    "No insight cards for a single quiet day."
                else "Insights explain your spending. They don't give financial advice or recommendations.",
            )
        }
    }
}

// ---- Period tabs (Month · Week · Day · Custom) ----

@Composable
private fun PeriodTabs(selected: PeriodKind, onSelect: (PeriodKind) -> Unit) {
    Row(
        Modifier
            .padding(horizontal = Spacing.screen, vertical = 4.dp)
            .fillMaxWidth()
            .clip(PillShape)
            .background(Surface)
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        PeriodKind.entries.forEach { kind ->
            val active = kind == selected
            Box(
                Modifier
                    .weight(1f)
                    .clip(PillShape)
                    .background(if (active) SurfaceRaised else androidx.compose.ui.graphics.Color.Transparent)
                    .clickable(role = Role.Tab) { onSelect(kind) }
                    .padding(vertical = 10.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    InsightsWords.tab(kind),
                    style = AppType.tab,
                    color = if (active) TextPrimary else TextSecondary,
                )
            }
        }
    }
}

// ---- Header: "Insights", "1–13 Nov 2026 · vs 1–13 Oct ⌄", refresh ----

@Composable
private fun InsightsHeader(label: String, comparison: String?, refreshing: Boolean, onPickDates: () -> Unit, onRefresh: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(start = Spacing.screen, end = Spacing.screen, top = 4.dp, bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text("Insights", style = AppType.sectionTitle, color = TextPrimary)
            Spacer(Modifier.height(2.dp))
            Row(
                Modifier.clip(PillShape).clickable(role = Role.Button, onClick = onPickDates),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    if (comparison != null) "$label · vs $comparison" else label,
                    style = AppType.body.copy(fontSize = AppType.rowTitle.fontSize),
                    color = TextSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                Icon(com.yourteam.expensetracker.ui.theme.HugeIcons.ChevronDown, contentDescription = "Change dates", tint = TextSecondary, modifier = Modifier.size(18.dp))
            }
        }
        Spacer(Modifier.width(12.dp))
        Box(
            Modifier.size(Spacing.circle).clip(PillShape).background(Surface).clickable(role = Role.Button, onClick = onRefresh),
            contentAlignment = Alignment.Center,
        ) {
            Icon(AppIcons.Refresh, contentDescription = if (refreshing) "Updating" else "Refresh", tint = TextPrimary, modifier = Modifier.size(20.dp))
        }
    }
}

// ---- Summary: expenses, income, net, transactions, highest category ----

@Composable
private fun SummaryCard(s: InsightSummary) {
    AppCard(Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth()) {
            Stat("Total expenses", Money.formatWhole(s.totalPaise), Modifier.weight(1f))
            Stat("Total income", Money.formatWhole(s.moneyInPaise), Modifier.weight(1f))
        }
        Spacer(Modifier.height(18.dp))
        Row(Modifier.fillMaxWidth()) {
            val net = s.netPaise
            Stat(
                "Net cash flow",
                when {
                    net > 0 -> "+${Money.formatWhole(net)}"
                    net < 0 -> "\u2212${Money.formatWhole(-net)}"
                    else -> Money.formatWhole(0)
                },
                Modifier.weight(1f),
                colour = when { net > 0 -> Positive; net < 0 -> Negative; else -> TextPrimary },
                // Green/red always comes with words (DESIGN.md).
                note = when { net > 0 -> "More came in"; net < 0 -> "More went out"; else -> null },
            )
            Stat("Transactions", "${s.transactionCount}", Modifier.weight(1f))
        }
        s.categories.firstOrNull()?.category?.let { top ->
            Spacer(Modifier.height(16.dp))
            ThinDivider()
            Spacer(Modifier.height(14.dp))
            Text(
                buildAnnotatedString {
                    append("Highest spending category: ")
                    withStyle(SpanStyle(color = TextPrimary, fontWeight = AppType.rowTitle.fontWeight)) { append(top) }
                },
                style = AppType.body.copy(fontSize = AppType.rowTitle.fontSize),
                color = TextSecondary,
            )
        }
    }
}

@Composable
private fun Stat(label: String, value: String, modifier: Modifier = Modifier, colour: androidx.compose.ui.graphics.Color = TextPrimary, note: String? = null) {
    Column(modifier) {
        Text(label, style = AppType.body.copy(fontSize = AppType.rowTitle.fontSize), color = TextSecondary)
        Spacer(Modifier.height(4.dp))
        Text(value, style = AppType.sectionTitle, color = colour, maxLines = 1, overflow = TextOverflow.Clip)
        note?.let { Text(it, style = AppType.chip, color = colour) }
    }
}

// ---- Where it went ----

@Composable
private fun WhereItWent(
    summary: InsightSummary,
    expanded: Boolean,
    onToggleExpand: () -> Unit,
    onCategory: (String) -> Unit,
    onSortThem: () -> Unit,
) {
    val shown = if (expanded) summary.categories else summary.categories.take(BREAKDOWN_ROWS)
    val maxPaise = summary.categories.maxOfOrNull { it.paise }?.takeIf { it > 0 } ?: 1L
    val hasMore = summary.categories.size > BREAKDOWN_ROWS
    AppCard(Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("CATEGORY-WISE SPENDING", style = AppType.chip, color = TextSecondary, modifier = Modifier.weight(1f))
            Text("Tap to see payments", style = AppType.body, color = TextSecondary)
        }
        Spacer(Modifier.height(4.dp))
        shown.forEach { slice ->
            CategoryBreakdownRow(slice, maxPaise, onClick = { slice.category?.let(onCategory) })
        }
        if (hasMore) {
            Box(Modifier.fillMaxWidth().clip(PillShape).clickable(onClick = onToggleExpand).padding(vertical = 12.dp), contentAlignment = Alignment.Center) {
                Text(if (expanded) "Show less" else "All categories", style = AppType.rowTitle, color = Accent)
            }
        }
        summary.uncategorised?.let { u ->
            Spacer(Modifier.height(8.dp))
            Text(
                "${Money.formatWhole(u.paise)} is waiting for a category in Review. It is included in total expenses but not in any category above.",
                style = AppType.body.copy(fontSize = AppType.rowTitle.fontSize, fontWeight = androidx.compose.ui.text.font.FontWeight.Normal),
                color = TextPrimary,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(SurfaceRaised)
                    .clickable(role = Role.Button, onClick = onSortThem)
                    .padding(16.dp),
            )
        }
    }
}

@Composable
private fun CategoryBreakdownRow(slice: CategorySlice, maxPaise: Long, onClick: () -> Unit) {
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable(onClick = onClick).padding(vertical = 12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(slice.category ?: "Not sorted yet", style = AppType.rowTitle, color = TextPrimary, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                buildAnnotatedString {
                    append(Money.formatWhole(slice.paise))
                    withStyle(SpanStyle(color = TextSecondary, fontWeight = androidx.compose.ui.text.font.FontWeight.Normal)) { append(" · ${slice.percent}%") }
                },
                style = AppType.rowTitle,
                color = TextPrimary,
            )
        }
        Spacer(Modifier.height(10.dp))
        Bar(fraction = slice.paise.toFloat() / maxPaise, modifier = Modifier.fillMaxWidth())
    }
}

@Composable
private fun Bar(fraction: Float, modifier: Modifier = Modifier) {
    Box(modifier.height(8.dp).clip(PillShape).background(SurfaceRaised)) {
        Box(Modifier.fillMaxWidth(fraction.coerceIn(0.03f, 1f)).height(8.dp).clip(PillShape).background(Accent))
    }
}

// ---- Insight card ----

/** Section title above the cards, with when they were worked out. */
@Composable
private fun InsightsSectionHeader() {
    Row(Modifier.fillMaxWidth().padding(top = 24.dp, bottom = 12.dp, start = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Text("Insights", style = AppType.sectionTitle, color = TextPrimary, modifier = Modifier.weight(1f))
        Text("Updated after your latest payment", style = AppType.body, color = TextSecondary)
    }
}

/** The kind chip: words carry the meaning, colour only backs them up. */
private fun kindOf(card: InsightCard): Pair<String, Int> = when (card.type) {
    com.yourteam.expensetracker.insights.InsightType.CATEGORY_CHANGE -> if (card.deltaUp == true) "Increased" to 1 else "Decreased" to 2
    com.yourteam.expensetracker.insights.InsightType.CATEGORY_SHARE -> "Big share" to 0
    com.yourteam.expensetracker.insights.InsightType.REPEAT_MERCHANT -> "Repeat" to 0
    com.yourteam.expensetracker.insights.InsightType.WEEKEND_WEEKDAY,
    com.yourteam.expensetracker.insights.InsightType.DAY_CONCENTRATION -> "Pattern" to 3
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun InsightCardView(card: InsightCard, dates: String, onOpen: () -> Unit, onRate: (InsightFeedback) -> Unit) {
    AppCard(Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            val (kind, tone) = kindOf(card)
            val ink = when (tone) { 1 -> com.yourteam.expensetracker.ui.theme.Warning; 2 -> Positive; 3 -> Accent; else -> TextPrimary }
            Text(kind, style = AppType.chip, color = ink, modifier = Modifier.clip(PillShape).background(SurfaceRaised).padding(horizontal = 12.dp, vertical = 5.dp))
            Spacer(Modifier.weight(1f))
            Text(dates, style = AppType.body, color = TextSecondary)
        }
        Spacer(Modifier.height(12.dp))
        Text(card.title, style = AppType.rowTitle.copy(fontSize = AppType.rowTitle.fontSize.times(1.12f)), color = TextPrimary)
        Spacer(Modifier.height(10.dp))
        FlowRow(verticalArrangement = Arrangement.Center, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(card.amountText, style = AppType.hero.copy(fontSize = AppType.sectionTitle.fontSize.times(1.3f), lineHeight = AppType.sectionTitle.lineHeight.times(1.3f)), color = TextPrimary)
            val vs = card.previousPaise?.let { prev -> card.comparisonLabel?.let { "vs ${Money.formatWhole(prev)} in $it" } }
            vs?.let { Text(it, style = AppType.body.copy(fontSize = AppType.rowTitle.fontSize), color = TextSecondary, modifier = Modifier.align(Alignment.CenterVertically)) }
        }
        Spacer(Modifier.height(10.dp))
        Text(card.detail, style = AppType.body.copy(fontSize = AppType.rowTitle.fontSize), color = TextPrimary)
        if (card.aiWritten) AiWordsNote()
        Spacer(Modifier.height(14.dp))
        Box(
            Modifier.fillMaxWidth().clip(PillShape).background(SurfaceRaised).clickable(role = Role.Button, onClick = onOpen).padding(vertical = 14.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text("View supporting transactions", style = AppType.rowTitle, color = TextPrimary)
        }
        Spacer(Modifier.height(12.dp))
        ThinDivider()
        Spacer(Modifier.height(12.dp))
        RateLinks(onRate)
    }
}

/** Helpful · Not relevant · Dismiss as link buttons: low priority, so no fill (DESIGN.md §4.11). */
@Composable
internal fun RateLinks(onRate: (InsightFeedback) -> Unit) {
    Row(Modifier.fillMaxWidth().offset(x = (-12).dp), verticalAlignment = Alignment.CenterVertically) {
        LinkButton("Helpful") { onRate(InsightFeedback.HELPFUL) }
        LinkButton("Not relevant") { onRate(InsightFeedback.NOT_RELEVANT) }
        Spacer(Modifier.weight(1f))
        LinkButton("Dismiss", Modifier.offset(x = 24.dp), color = TextSecondary) { onRate(InsightFeedback.DISMISSED) }
    }
}

@Composable
fun SparkleLabel(text: String = "Insight") {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(AppIcons.Sparkle, contentDescription = null, tint = Accent, modifier = Modifier.size(15.dp))
        Spacer(Modifier.width(6.dp))
        Text(text, style = AppType.tab, color = Accent)
    }
}

// ---- State cards ----

@Composable
private fun InsufficientCard(s: InsightSummary, onImport: () -> Unit) {
    AppCard(Modifier.fillMaxWidth()) {
        SparkleLabel("Getting to know your spending")
        Spacer(Modifier.height(10.dp))
        Text("We need more payment history before we can spot patterns or compare months.", style = AppType.rowTitle, color = TextPrimary)
        Spacer(Modifier.height(8.dp))
        Text("Your totals and categories above are already accurate.", style = AppType.body, color = TextSecondary)
        Spacer(Modifier.height(16.dp))
        Box(
            Modifier.fillMaxWidth().clip(PillShape).background(SurfaceRaised).clickable(onClick = onImport).padding(vertical = 14.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text("Import older payment SMS", style = AppType.rowTitle, color = TextPrimary)
        }
    }
}

@Composable
private fun NoPatternCard(s: InsightSummary) {
    AppCard(Modifier.fillMaxWidth()) {
        Text("Nothing stands out this period", style = AppType.rowTitle, color = TextPrimary)
        Spacer(Modifier.height(8.dp))
        val comp = s.comparisonLabel?.let { "much like the same days in ${monthWord(s)}" } ?: "steady"
        Text("Your spending looks $comp. We'll show something here when there's a real change.", style = AppType.body, color = TextSecondary)
    }
}

private fun monthWord(s: InsightSummary): String =
    if (s.period.kind == PeriodKind.MONTH) InsightsWords.previousMonthName(s.period) else "the period before"

// Day state (in10): pending list + "Check your sources".
private fun androidx.compose.foundation.lazy.LazyListScope.singleDay(s: InsightSummary, onOpenSources: () -> Unit) {
    if (s.pendingCount > 0) {
        item { Spacer(Modifier.height(24.dp)); SectionLabelSmall("NOT COUNTED YET") }
        item {
            AppCard(Modifier.fillMaxWidth()) {
                Text(
                    "${s.pendingCount} ${if (s.pendingCount == 1) "payment is" else "payments are"} still waiting to go through (${Money.formatWhole(s.pendingPaise)}).",
                    style = AppType.body, color = TextSecondary,
                )
            }
        }
        item { FootNote("Pending payments count once they go through. Failed ones never count.") }
    }
    item { Spacer(Modifier.height(24.dp)); SectionLabelSmall("PAID SOMETHING THAT'S MISSING?") }
    item {
        AppCard(Modifier.fillMaxWidth().clickable(onClick = onOpenSources)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Check your sources", style = AppType.rowTitle, color = TextPrimary)
                    Spacer(Modifier.height(2.dp))
                    Text("A payment app may not be connected", style = AppType.body, color = TextSecondary)
                }
                Icon(com.yourteam.expensetracker.ui.theme.HugeIcons.ChevronRight, contentDescription = null, tint = TextSecondary, modifier = Modifier.size(22.dp))
            }
        }
    }
}

// ---- small shared bits ----

@Composable
private fun SectionLabelSmall(text: String) {
    Text(text, style = AppType.chip, color = TextSecondary, modifier = Modifier.padding(start = 4.dp, bottom = 8.dp))
}

@Composable
private fun ThinDivider() {
    HorizontalDivider(thickness = 1.dp, color = SurfaceRaised)
}

@Composable
private fun FootNote(text: String) {
    Row(Modifier.padding(horizontal = 4.dp, vertical = 16.dp), verticalAlignment = Alignment.Top) {
        Text(text, style = AppType.body, color = TextSecondary)
    }
}

private fun updatedText(updatedAt: Long, refreshing: Boolean): String {
    if (refreshing) return "Updating…"
    val mins = (System.currentTimeMillis() - updatedAt) / 60_000
    return when {
        mins <= 0 -> "Updated now"
        mins < 60 -> "Updated $mins min ago"
        else -> "Updated ${mins / 60} h ago"
    }
}

// ---- Loading skeleton (in7) ----

@Composable
private fun InsightsSkeleton(label: String) {
    com.yourteam.expensetracker.ui.theme.SkeletonPulse {
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = Spacing.screen, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item {
                Row(Modifier.fillMaxWidth().padding(top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(label, style = AppType.rowTitle, color = TextPrimary)
                    Spacer(Modifier.weight(1f))
                    Text("Loading…", style = AppType.body, color = TextSecondary)
                }
            }
            item {
                Column {
                    Box(Modifier.width(60.dp).height(14.dp).clip(PillShape).background(SurfaceRaised))
                    Spacer(Modifier.height(10.dp))
                    Box(Modifier.width(220.dp).height(44.dp).clip(RoundedCornerShape(12.dp)).background(SurfaceRaised))
                }
            }
            item { Box(Modifier.fillMaxWidth().height(260.dp).clip(CardShape).background(Surface)) }
            item { Box(Modifier.fillMaxWidth().height(140.dp).clip(CardShape).background(Surface)) }
            item { FootNote("Totals load first (no AI needed). Insight cards follow, and only for work that's actually pending.") }
        }
    }
}

/** Under an AI-worded card (DESIGN.md §5a/§6): the words are the AI's, the numbers are the app's. */
@Composable
internal fun AiWordsNote() {
    Spacer(Modifier.height(6.dp))
    Text("Words by AI · numbers worked out by the app", style = AppType.body, color = TextSecondary)
}
