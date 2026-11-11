package com.yourteam.expensetracker.ui.screens.home

import com.yourteam.expensetracker.ui.feedback.Feedback
import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import com.yourteam.expensetracker.ui.theme.CardShape
import androidx.compose.foundation.layout.heightIn
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import android.os.Build
import com.yourteam.expensetracker.notifications.GPayScreenReader
import com.yourteam.expensetracker.notifications.PaymentCheck
import com.yourteam.expensetracker.notifications.UpiApps
import com.yourteam.expensetracker.ui.screens.FeedRow
import com.yourteam.expensetracker.ui.screens.SectionLabel
import com.yourteam.expensetracker.ui.screens.onboarding.SmsPermissionScreen
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.ui.layout.layout
import com.yourteam.expensetracker.insights.Money
import com.yourteam.expensetracker.ui.screens.PaymentCard
import com.yourteam.expensetracker.ui.screens.CategorySheet
import com.yourteam.expensetracker.ui.screens.PaymentSheet
import com.yourteam.expensetracker.ui.screens.NeedsCheckSheet
import com.yourteam.expensetracker.ui.screens.PaymentRowModel
import com.yourteam.expensetracker.ui.screens.SectionHeader
import com.yourteam.expensetracker.ui.screens.StatCard
import com.yourteam.expensetracker.ui.screens.SummaryRow
import com.yourteam.expensetracker.ui.screens.deltaWords
import com.yourteam.expensetracker.ui.theme.Accent
import com.yourteam.expensetracker.ui.theme.AppIcons
import com.yourteam.expensetracker.ui.theme.AppType
import com.yourteam.expensetracker.ui.theme.TextSecondary
import com.yourteam.expensetracker.ui.theme.TextPrimary
import com.yourteam.expensetracker.ui.theme.AppCard
import com.yourteam.expensetracker.ui.theme.Bg
import com.yourteam.expensetracker.ui.screens.AskAndAddBar
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import com.yourteam.expensetracker.ui.theme.Border
import com.yourteam.expensetracker.ui.theme.Edge
import com.yourteam.expensetracker.ui.theme.Heading
import com.yourteam.expensetracker.ui.theme.PillShape
import com.yourteam.expensetracker.ui.theme.SoftInk
import com.yourteam.expensetracker.ui.theme.Spacing
import com.yourteam.expensetracker.ui.theme.TagShape
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    modifier: Modifier = Modifier,
    onOpenPayment: (String) -> Unit = {},
    onOpenPayments: () -> Unit = {},
    onOpenReview: () -> Unit = {},
    onOpenSources: () -> Unit = {},
    onAsk: () -> Unit = {},
    viewModel: HomeViewModel = viewModel(),
) {
    val context = LocalContext.current
    var granted by remember { mutableStateOf(hasReadSms(context)) }
    var deniedOnce by rememberSaveable { mutableStateOf(false) }
    var notificationAccess by remember { mutableStateOf(UpiApps.isNotificationAccessOn(context)) }
    var screenReaderOn by remember { mutableStateOf(GPayScreenReader.isOn(context)) }
    var canNotify by remember { mutableStateOf(PaymentCheck.canNotify(context)) }
    val notifyLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        canNotify = PaymentCheck.canNotify(context)
    }

    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        granted = ok
        if (!ok) deniedOnce = true
    }

    // Re-check when coming back from Settings.
    LifecycleResumeEffect(Unit) {
        granted = hasReadSms(context)
        notificationAccess = UpiApps.isNotificationAccessOn(context)
        screenReaderOn = GPayScreenReader.isOn(context)
        canNotify = PaymentCheck.canNotify(context)
        onPauseOrDispose { }
    }

    if (!granted) {
        SmsPermissionScreen(
            onAllow = { launcher.launch(Manifest.permission.READ_SMS) },
            onOpenSettings = { context.startActivity(appSettingsIntent(context)) },
            showSettingsHint = deniedOnce,
            modifier = modifier,
        )
        return
    }

    LaunchedEffect(Unit) { viewModel.load() }
    val state by viewModel.state.collectAsStateWithLifecycle()
    val chosenRange by viewModel.selectedRange.collectAsStateWithLifecycle()
    // Loaded state holds the range worked out for today; before that, the chosen one.
    val range = (state as? HomeUiState.Loaded)?.spend?.range ?: chosenRange
    var showPeriodSheet by rememberSaveable { mutableStateOf(false) }
    var adding by rememberSaveable { mutableStateOf(false) }

    if (adding) {
        AddPaymentSheet(onSave = viewModel::addPayment, onDismiss = { adding = false })
    }
    var tagging by remember { mutableStateOf<PaymentRowModel?>(null) }
    var opened by rememberSaveable { mutableStateOf<String?>(null) }
    var checking by rememberSaveable { mutableStateOf<String?>(null) }

    if (showPeriodSheet) {
        DateRangeSheet(
            current = range,
            today = java.time.LocalDate.now(),
            onApply = viewModel::setRange, // ticks fire inside the sheet; no message on Apply
            onDismiss = { showPeriodSheet = false },
        )
    }

    // Tapping a payment: details + "Change category" in a sheet. Row is re-read so edits show at once.
    // "Needs check": the Pending sheet (MVP M7/M13).
    (state as? HomeUiState.Loaded)?.recent?.firstOrNull { it.key == checking }?.let { row ->
        NeedsCheckSheet(
            row = row,
            onAnswer = { id, yes -> if (yes) viewModel.confirmPending(id) else viewModel.dismissPending(id) },
            onDismiss = { checking = null },
        )
    }

    (state as? HomeUiState.Loaded)?.recent?.firstOrNull { it.key == opened }?.let { row ->
        PaymentSheet(
            row = row,
            onPickCategory = { viewModel.setCategory(row.key, it) },
            onAnswer = { id, yes -> if (yes) viewModel.confirmPending(id) else viewModel.dismissPending(id) },
            onOpenDetails = { onOpenPayment(row.key) },
            onDismiss = { opened = null },
            extras = (state as? HomeUiState.Loaded)?.categories?.mapNotNull { it.category }.orEmpty(),
        )
    }

    tagging?.let { row ->
        CategorySheet(listOf(row), onPick = { r, c -> viewModel.setCategory(r.key, c) }, onDismiss = { tagging = null }, extras = (state as? HomeUiState.Loaded)?.categories?.mapNotNull { it.category }.orEmpty())
    }

    Box(modifier.fillMaxSize()) {
    when (val s = state) {
        HomeUiState.Loading -> HomeSkeleton()
        is HomeUiState.Loaded -> PullDownRefresh(
            refreshing = s.refreshing,
            itemKeys = s.items.filter { it.openKey != null }.map { it.key }, // payments only
            onRefresh = viewModel::refresh,
            modifier = Modifier.fillMaxSize(),
        ) { listState ->
            FeedList(
                state = s,
                listState = listState,
                onPickPeriod = { showPeriodSheet = true },
                onOpenReview = onOpenReview,
                onOpenPayment = onOpenPayment,
                onOpenPayments = onOpenPayments,
                onCheck = { checking = it.key },
                onTag = { tagging = it },
                onOpenSheet = { opened = it },
            )
        }
    }
        // Pinned Ask bar + the + button, on a fade so list content slides under it cleanly.
        AskAndAddBar(
            onAsk = onAsk,
            onAdd = { adding = true },
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .background(Brush.verticalGradient(0f to Color.Transparent, 0.35f to Bg))
                .padding(start = Spacing.screen, end = Spacing.screen, top = 28.dp, bottom = 16.dp),
        )
    }
}

private enum class RefreshPhase { Idle, Loading, Result }

/** Light ticks on the way to "Release to refresh"; the last step is the firmer threshold tick. */
private const val PULL_STEPS = 10

/** Height of the gap that opens above the list while refreshing. */
private val RefreshGap = 72.dp

/**
 * Pull-down refresh that pushes the list down instead of drawing over it.
 * Pull → spinner fills in. Release → spinner spins. Done → short result line,
 * then the gap closes and the list scrolls back to the top.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PullDownRefresh(
    refreshing: Boolean,
    itemKeys: List<String>,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable (LazyListState) -> Unit,
) {
    val pullState = rememberPullToRefreshState()
    val listState = rememberLazyListState()
    val density = LocalDensity.current
    var phase by remember { mutableStateOf(RefreshPhase.Idle) }
    var message by remember { mutableStateOf("") }
    var keysBefore by remember { mutableStateOf(emptySet<String>()) }
    val isRefreshing by rememberUpdatedState(refreshing)
    val currentKeys by rememberUpdatedState(itemKeys)

    LaunchedEffect(phase) {
        when (phase) {
            RefreshPhase.Loading -> {
                delay(800) // keep the spinner visible even if the reload is instant
                snapshotFlow { isRefreshing }.first { !it }
                val added = currentKeys.count { it !in keysBefore }
                message = when (added) {
                    0 -> "Up to date" // F12: words only, no haptic (background result)
                    1 -> "1 new payment"
                    else -> "$added new payments"
                }
                phase = RefreshPhase.Result
            }
            RefreshPhase.Result -> {
                delay(1400)
                listState.animateScrollToItem(0)
                phase = RefreshPhase.Idle
            }
            RefreshPhase.Idle -> Unit
        }
    }

    // F12a: light ticks follow the finger as it pulls down (a step every 1/PULL_STEPS of the way),
    // then a firmer tick when it reaches "Release to refresh". Only on the way down, so the
    // list springing back on its own stays silent; nothing fires while the refresh runs.
    LaunchedEffect(pullState) {
        var previous = 0
        snapshotFlow {
            if (phase != RefreshPhase.Idle) -1
            else (pullState.distanceFraction.coerceIn(0f, 1f) * PULL_STEPS).toInt()
        }
            .distinctUntilChanged()
            .collect { step ->
                if (previous >= 0 && step > previous) {
                    if (step == PULL_STEPS) Feedback.tick() else Feedback.pull()
                }
                previous = step
            }
    }

    PullToRefreshBox(
        isRefreshing = phase != RefreshPhase.Idle,
        onRefresh = {
            Feedback.release() // F12b: let go past the line → refresh starts
            keysBefore = currentKeys.toSet()
            phase = RefreshPhase.Loading
            onRefresh()
        },
        state = pullState,
        modifier = modifier,
        indicator = {}, // drawn below, in the gap, instead of on top of the list
    ) {
        val fraction = pullState.distanceFraction
        val gapPx = with(density) { RefreshGap.toPx() }

        // The gap above the list, clipped so nothing overlaps the content.
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(with(density) { (fraction * gapPx).toDp() })
                .clipToBounds(),
            contentAlignment = Alignment.Center,
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.graphicsLayer { alpha = fraction.coerceIn(0f, 1f) },
            ) {
                val ink = MaterialTheme.colorScheme.onBackground
                when (phase) {
                    RefreshPhase.Idle -> CircularProgressIndicator(
                        progress = { fraction.coerceIn(0f, 1f) },
                        modifier = Modifier.size(18.dp),
                        color = ink,
                        trackColor = Border,
                        strokeWidth = 2.dp,
                    )
                    RefreshPhase.Loading -> CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        color = ink,
                        trackColor = Border,
                        strokeWidth = 2.dp,
                    )
                    RefreshPhase.Result -> Unit // just the words: "Up to date" / "2 new payments"
                }
                if (phase != RefreshPhase.Result) Spacer(Modifier.width(10.dp))
                Text(
                    text = when (phase) {
                        RefreshPhase.Idle -> if (fraction >= 1f) "Release to refresh" else "Pull to refresh"
                        RefreshPhase.Loading -> "Checking messages…"
                        RefreshPhase.Result -> message
                    },
                    style = MaterialTheme.typography.labelLarge,
                    color = if (phase == RefreshPhase.Result) ink else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        // The list itself slides down by the same amount.
        Box(Modifier.fillMaxSize().graphicsLayer { translationY = fraction * gapPx }) {
            content(listState)
        }
    }
}

/** Home, DESIGN.md §5: hero, "To check", Payments ›, Categories ›, then the pinned Ask bar. */
@Composable
private fun FeedList(
    state: HomeUiState.Loaded,
    listState: LazyListState,
    onPickPeriod: () -> Unit,
    onOpenReview: () -> Unit,
    onOpenPayment: (String) -> Unit,
    onOpenPayments: () -> Unit,
    onCheck: (PaymentRowModel) -> Unit,
    onTag: (PaymentRowModel) -> Unit,
    onOpenSheet: (String) -> Unit,
) {
    val since = RangeWords.since(state.spend.range)
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        state = listState,
        contentPadding = PaddingValues(start = Spacing.screen, end = Spacing.screen, top = 8.dp, bottom = 120.dp),
    ) {
        item {
            SpendHero(
                spend = state.spend,
                range = state.spend.range,
                onPickPeriod = onPickPeriod,
                modifier = Modifier.padding(bottom = Spacing.section),
            )
        }
        if (state.toCheck.count > 0) {
            item {
                CheckBanner(state.toCheck.count, onClick = onOpenReview)
                Spacer(Modifier.height(Spacing.cardGap))
            }
        }
        item {
            Spacer(Modifier.height(Spacing.section - Spacing.cardGap))
            SectionHeader("Payments", onClick = onOpenPayments)
            Spacer(Modifier.height(Spacing.cardGap))
        }
        if (state.recent.isEmpty()) {
            item {
                EmptyRangeCard(state.spend.range, onChangeDates = onPickPeriod)
                Spacer(Modifier.height(Spacing.cardGap))
            }
        }
        items(state.recent, key = { it.key }) {
            PaymentCard(it, onClick = { onOpenSheet(it.key) }, onCheck = { onCheck(it) }, onAddCategory = { onTag(it) })
            Spacer(Modifier.height(Spacing.cardGap))
        }
        if (state.categories.isNotEmpty()) {
            item {
                Spacer(Modifier.height(Spacing.section - Spacing.cardGap))
                SectionHeader("Categories")
                Spacer(Modifier.height(Spacing.cardGap))
                // Full-bleed carousel: the next card peeks at the edge.
                LazyRow(
                    modifier = Modifier.fullBleed(),
                    contentPadding = PaddingValues(horizontal = Spacing.screen),
                    horizontalArrangement = Arrangement.spacedBy(Spacing.cardGap),
                ) {
                    items(state.categories, key = { it.category ?: "untagged" }) { c ->
                        val (delta, color) = deltaWords(c.changePaise, c.previousPaise, since)
                        StatCard(
                            label = c.category ?: "Needs a tag",
                            labelColor = if (c.category == null) Accent else TextPrimary,
                            value = Money.formatWhole(c.totalPaise),
                            delta = delta,
                            deltaColor = color,
                        )
                    }
                }
                Spacer(Modifier.height(Spacing.cardGap))
            }
        }
    }
}

private fun hasReadSms(context: Context) =
    ContextCompat.checkSelfPermission(context, Manifest.permission.READ_SMS) ==
        PackageManager.PERMISSION_GRANTED

private fun appSettingsIntent(context: Context) =
    Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null))
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

/** Lets a carousel run edge to edge past the screen padding, so the next card peeks in. */
private fun Modifier.fullBleed(): Modifier = layout { m, c ->
    val extra = (Spacing.screen * 2).roundToPx()
    val p = m.measure(c.copy(minWidth = c.maxWidth + extra, maxWidth = c.maxWidth + extra))
    layout(c.maxWidth, p.height) { p.place(-extra / 2, 0) }
}

/**
 * Payments section when the chosen dates have none (inline empty state, FEEDBACK.md §2).
 * Ranges ending today point to the + button; past ranges offer other dates.
 */
@Composable
private fun EmptyRangeCard(range: com.yourteam.expensetracker.insights.DateRange, onChangeDates: () -> Unit) {
    val live = RangeWords.includesToday(range, java.time.LocalDate.now())
    AppCard(Modifier.fillMaxWidth()) {
        Text("No payments to show", style = AppType.rowTitle, color = TextPrimary)
        Spacer(Modifier.height(6.dp))
        Text(
            if (live) "Payments you make will show up here. Tap + below to add one yourself."
            else "Nothing was recorded on these dates. Try other dates.",
            style = AppType.body,
            color = TextSecondary,
        )
        Spacer(Modifier.height(14.dp))
        Text(
            "Change dates",
            style = AppType.tab,
            color = TextPrimary,
            modifier = Modifier
                .heightIn(min = Spacing.circle)
                .clip(PillShape)
                .background(com.yourteam.expensetracker.ui.theme.SurfaceRaised)
                .clickable(role = androidx.compose.ui.semantics.Role.Button, onClick = onChangeDates)
                .padding(horizontal = 18.dp, vertical = 12.dp),
        )
    }
}

/** "● 4 payments need a check   Review ›": whole banner opens Review. Hidden at 0 (MVP M14). */
@Composable
private fun CheckBanner(count: Int, onClick: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clip(CardShape)
            .background(com.yourteam.expensetracker.ui.theme.Surface)
            .clickable(role = androidx.compose.ui.semantics.Role.Button, onClick = onClick)
            .heightIn(min = 56.dp)
            .padding(start = 18.dp, end = 12.dp, top = 16.dp, bottom = 16.dp),
    ) {
        Box(Modifier.size(8.dp).background(com.yourteam.expensetracker.ui.theme.Negative, CircleShape))
        Spacer(Modifier.width(12.dp))
        Text(
            if (count == 1) "1 payment needs a check" else "$count payments need a check",
            style = AppType.rowTitle,
            color = TextPrimary,
            maxLines = 1,
            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        Spacer(Modifier.width(8.dp))
        Text("Review", style = AppType.tab, color = Accent)
        androidx.compose.material3.Icon(
            androidx.compose.material.icons.Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = Accent,
            modifier = Modifier.size(20.dp),
        )
    }
}
