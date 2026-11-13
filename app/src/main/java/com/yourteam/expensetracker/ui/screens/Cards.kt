package com.yourteam.expensetracker.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import com.yourteam.expensetracker.ui.feedback.ACTION_DELAY_MS
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.animation.togetherWith
import androidx.compose.ui.focus.focusRequester
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.yourteam.expensetracker.data.PaymentSnapshot
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.yourteam.expensetracker.categoriser.Basis
import com.yourteam.expensetracker.categoriser.Categories
import com.yourteam.expensetracker.categoriser.Confidence
import com.yourteam.expensetracker.insights.BankCredit
import com.yourteam.expensetracker.insights.Money
import com.yourteam.expensetracker.insights.Payment
import com.yourteam.expensetracker.insights.Received
import com.yourteam.expensetracker.insights.UserAnswer
import com.yourteam.expensetracker.ui.theme.Accent
import com.yourteam.expensetracker.ui.theme.AppIcons
import com.yourteam.expensetracker.ui.theme.AppType
import com.yourteam.expensetracker.ui.theme.CardShape
import com.yourteam.expensetracker.ui.theme.Bg
import com.yourteam.expensetracker.ui.theme.Negative
import com.yourteam.expensetracker.ui.theme.OnAccent
import com.yourteam.expensetracker.ui.theme.PillShape
import com.yourteam.expensetracker.ui.theme.Positive
import com.yourteam.expensetracker.ui.theme.Spacing
import com.yourteam.expensetracker.ui.theme.Surface
import com.yourteam.expensetracker.ui.theme.SurfaceRaised
import com.yourteam.expensetracker.ui.theme.TextPrimary
import com.yourteam.expensetracker.ui.theme.TextSecondary
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

/** What a payment row shows. Text only; amounts are formatted from paise. */
data class PaymentRowModel(
    val key: String,
    val payee: String,
    /** False when the name is a placeholder ("Bank payment"): categories then apply to this payment only. */
    val named: Boolean,
    /** "₹40", no sign: [debited] says which way the money went. */
    val amount: String,
    /** For filters only (amount ranges); never summed here. */
    val paise: Long,
    /** True = money left the account; false = money received (bank credit SMS). */
    val debited: Boolean,
    val at: Long,
    /** Waiting for "Did it go through?": the started payment's id. */
    val askId: Long? = null,
    /** User said "No" but the bank recorded it: the started payment's id. */
    val conflictId: Long? = null,
    /** Kept for the detail sheet and later use; not shown on the row. */
    val category: String? = null,
    val source: String = "",
    /** Top 3 categories to offer when asking (Feature 1 §3.4), most likely first. */
    val suggestions: List<String> = emptyList(),
    /** How sure the categoriser is; null for rows that don't need one. */
    val confidence: Confidence? = null,
    /** Why we picked it; drives the ✦ marker (shown when the app/AI chose, not the user). */
    val basis: Basis? = null,
) {
    /** True when the category was chosen automatically (rules or AI), so the card shows ✦ (Feature 1 F3). */
    val aiPicked: Boolean get() = basis == Basis.KNOWN_MERCHANT || basis == Basis.SHOP_WORDS || basis == Basis.AI
    /** "Fairly sure": not filed, but we have a best guess to confirm (F5). */
    val fairlySure: Boolean get() = debited && category == null && confidence == Confidence.MEDIUM && suggestions.isNotEmpty()
    val bestGuess: String? get() = suggestions.firstOrNull()
}

/** Money received: "+₹500 · Received", never counted as spending. Income category from [snapshot] (Feature 1 §3.7). */
fun receivedRow(c: BankCredit, snapshot: PaymentSnapshot? = null): PaymentRowModel = (snapshot?.incomeGuessFor(c)).let { g -> PaymentRowModel(
    key = c.key,
    payee = Received.senderOf(c),
    named = c.from != null,
    amount = Money.formatAmount(c.paise),
    paise = c.paise,
    debited = false,
    at = c.at,
    source = c.alsoIn?.let { "${c.source} + $it" } ?: c.source,
    category = g?.category,
    suggestions = g?.suggestions.orEmpty(),
    confidence = g?.confidence,
    basis = g?.basis,
) }

fun paymentRow(p: Payment, snapshot: PaymentSnapshot): PaymentRowModel = snapshot.guessFor(p).let { g -> PaymentRowModel(
    key = p.key,
    payee = p.payee?.takeIf(String::isNotBlank) ?: p.sms?.let { "Bank payment" } ?: "Payment",
    named = !p.payee.isNullOrBlank(),
    amount = Money.formatAmount(p.paise),
    paise = p.paise,
    debited = true, // every Payment today is money going out
    at = p.at,
    askId = p.started?.id?.takeIf { p.needsAnswer },
    conflictId = p.started?.id?.takeIf { p.conflict },
    category = g.category,
    suggestions = g.suggestions,
    confidence = g.confidence,
    basis = g.basis,
    source = when {
        p.sms != null -> "Bank SMS"
        p.chat != null -> p.chat.source
        p.started?.cash == true -> "Cash · added by you"
        p.started?.manual == true -> "Added by you"
        p.started?.answer == UserAnswer.YES -> "You confirmed"
        else -> p.started?.app ?: "GPay"
    },
) }

/**
 * Payment row card: initials · payee over "Category · time" · signed amount.
 * Unsure category shows "Add category" in accent; credits say "Received".
 * Tapping the card (or "Needs check") opens [PaymentSheet], centred, with the question on top when needed.
 */
@Composable
fun PaymentCard(
    row: PaymentRowModel,
    onClick: () -> Unit,
    onCheck: () -> Unit = {},
    onAddCategory: () -> Unit = onClick,
    /** In a list grouped by day, show the clock time instead of "Yesterday". */
    groupedByDay: Boolean = false,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clip(CardShape)
            .background(Surface)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = Spacing.cardV),
    ) {
        InitialsCircle(row.payee)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(row.payee, style = AppType.rowTitle, color = TextPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Row(verticalAlignment = Alignment.CenterVertically) {
                if ((row.debited && row.category != null && row.aiPicked) || row.fairlySure) SparkleMark()
                when {
                    !row.debited -> Text(row.category?.let { "Received · $it" } ?: "Received", style = AppType.body, color = TextSecondary, maxLines = 1)
                    // Sure: filed. ✦ (added above) when the app/AI chose it, plain when the user did.
                    row.category != null -> Text(row.category, style = AppType.body, color = TextSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                    // Fairly sure: a best guess to confirm (F5), ✦ + "Groceries? Confirm".
                    row.fairlySure -> Text(
                        "${row.bestGuess}? Confirm",
                        style = AppType.body.copy(fontWeight = AppType.rowTitle.fontWeight),
                        color = Accent,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false).clip(PillShape).clickable(role = Role.Button, onClick = onAddCategory),
                    )
                    // Not sure: no guess.
                    else -> Text(
                        "Add category",
                        style = AppType.body.copy(fontWeight = AppType.rowTitle.fontWeight),
                        color = Accent,
                        modifier = Modifier.clip(PillShape).clickable(role = Role.Button, onClick = onAddCategory),
                    )
                }
                Text(" · ${relativeTime(row.at, groupedByDay = groupedByDay)}", style = AppType.body, color = TextSecondary, maxLines = 1)
            }
        }
        Spacer(Modifier.width(12.dp))
        Column(horizontalAlignment = Alignment.End) {
            Text(
                if (row.debited) "\u2212${row.amount}" else "+${row.amount}",
                style = AppType.rowTitle,
                color = TextPrimary,
            )
            if (row.askId != null || row.conflictId != null) {
                Text(
                    "Needs check",
                    style = AppType.chip,
                    color = Negative,
                    modifier = Modifier
                        .clip(PillShape)
                        .clickable(role = Role.Button, onClick = onCheck)
                        .padding(vertical = 2.dp),
                )
            }
        }
    }
}

/** The ✦ marker shown before an app/AI-chosen category (Feature 1 F3). Accent, sized to the label. */
@Composable
private fun SparkleMark() {
    Icon(AppIcons.Sparkle, contentDescription = "Picked for you", tint = Accent, modifier = Modifier.size(14.dp))
    Spacer(Modifier.width(5.dp))
}

/** Card labels ("Food & dining", payee names): 13sp semi-bold. */
val CardLabel = AppType.rowTitle.copy(fontSize = AppType.body.fontSize, lineHeight = AppType.body.lineHeight)

@Composable
private fun SheetButton(
    text: String,
    filled: Boolean,
    modifier: Modifier,
    loading: Boolean = false,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    val ink = if (filled) OnAccent else TextPrimary
    Box(
        modifier
            .height(Spacing.button)
            .clip(PillShape)
            .background(if (filled) Accent else SurfaceRaised)
            .clickable(enabled = enabled && !loading, role = Role.Button, onClick = onClick)
            .semantics { if (loading) stateDescription = "Saving" },
        contentAlignment = Alignment.Center,
    ) {
        if (loading) ButtonSpinner(ink) else Text(text, style = AppType.tab, color = ink)
    }
}

/** 20dp spinner shown inside a pill button while its action runs (FEEDBACK.md §2a). */
@Composable
private fun ButtonSpinner(color: Color) {
    androidx.compose.material3.CircularProgressIndicator(color = color, strokeWidth = 2.dp, modifier = Modifier.size(20.dp))
}

/** 44dp circle with two initials: "Chai Point" -> "CP", "Papa" -> "PA". */
@Composable
fun InitialsCircle(name: String, fill: Color = SurfaceRaised, ink: Color = TextPrimary, size: androidx.compose.ui.unit.Dp = Spacing.circle) {
    val words = name.split(' ', '-', '.').filter { it.firstOrNull()?.isLetterOrDigit() == true }
    // "Chai Point" -> "CP", "Papa" -> "PA", "Swiggy" -> "SW".
    val initials = (if (words.size == 1) words[0].take(2) else words.take(2).joinToString("") { it.take(1) })
        .uppercase().ifEmpty { "₹" }
    Box(Modifier.size(size).background(fill, PillShape), contentAlignment = Alignment.Center) {
        Text(initials, style = if (size < Spacing.circle) AppType.chip else AppType.rowTitle, color = ink)
    }
}

/** Icon in a 44dp raised circle. */
@Composable
fun IconCircle(icon: ImageVector, contentDescription: String? = null) {
    Box(Modifier.size(Spacing.circle).background(SurfaceRaised, PillShape), contentAlignment = Alignment.Center) {
        Icon(icon, contentDescription, tint = TextPrimary, modifier = Modifier.size(22.dp))
    }
}

/** "Payments ›": tap opens the full list (DESIGN.md §4.5). */
@Composable
fun SectionHeader(title: String, onClick: (() -> Unit)? = null) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .heightIn(min = Spacing.circle)
            .clip(PillShape)
            .then(if (onClick != null) Modifier.clickable(role = Role.Button, onClick = onClick) else Modifier),
    ) {
        Text(title, style = AppType.sectionTitle, color = TextPrimary)
        if (onClick != null) {
            Spacer(Modifier.width(4.dp))
            Icon(com.yourteam.expensetracker.ui.theme.HugeIcons.ChevronRight, contentDescription = null, tint = TextPrimary, modifier = Modifier.size(28.dp))
        }
    }
}

/** Full-width card: icon · label · value, whole card tappable (DESIGN.md §4.4). */
@Composable
fun SummaryRow(icon: ImageVector, label: String, value: String, onClick: () -> Unit, valueColor: Color = TextPrimary) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clip(CardShape)
            .background(Surface)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 18.dp),
    ) {
        IconCircle(icon)
        Spacer(Modifier.width(14.dp))
        Text(label, style = AppType.rowTitle, color = TextPrimary, modifier = Modifier.weight(1f))
        Text(value, style = AppType.rowTitle, color = valueColor)
    }
}

/** 170dp stat card for the carousel: label, value, delta in words (DESIGN.md §4.7). */
@Composable
fun StatCard(label: String, value: String, delta: String, deltaColor: Color, labelColor: Color = TextPrimary, onClick: () -> Unit = {}) {
    Column(
        Modifier
            .width(140.dp)
            .clip(CardShape)
            .background(Surface)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(16.dp),
    ) {
        Text(label, style = CardLabel, color = labelColor, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Spacer(Modifier.height(6.dp))
        Text(value, style = AppType.statValue, color = TextPrimary, maxLines = 1)
        Spacer(Modifier.height(4.dp))
        Text(delta, style = AppType.chip, color = deltaColor, maxLines = 1)
    }
}

/** Words for a change against last period. Green = spent less, red = spent more (DESIGN.md §1). */
@Composable
@androidx.compose.runtime.ReadOnlyComposable
fun deltaWords(changePaise: Long, previousPaise: Long, since: String): Pair<String, Color> = when {
    previousPaise == 0L && changePaise > 0 -> "New" to TextSecondary
    changePaise == 0L -> "Same as $since" to TextSecondary
    changePaise < 0 -> "${Money.formatWhole(-changePaise)} less" to Positive
    else -> "${Money.formatWhole(changePaise)} more" to Negative
}

/** Neutral chip: raised fill. Accent chip: accent fill (DESIGN.md §4.9). */
@Composable
fun Chip(text: String, accent: Boolean = false) {
    Text(
        text = text,
        style = AppType.chip,
        color = if (accent) OnAccent else TextPrimary,
        modifier = Modifier
            .background(if (accent) Accent else SurfaceRaised, PillShape)
            .padding(horizontal = 10.dp, vertical = 3.dp),
    )
}

/**
 * Home's floating buttons, bottom right: the round + to add a payment by hand and (when [onScan]
 * is set) the accent Scan & Pay button (DESIGN.md §4.8, MVP M7).
 */
@Composable
fun AddAndScanButtons(onAdd: () -> Unit, modifier: Modifier = Modifier, onScan: (() -> Unit)? = null) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier
                .size(Spacing.scanButton)
                .clip(PillShape)
                .background(if (onScan != null) Surface else Accent)
                .clickable(role = Role.Button, onClick = onAdd),
            contentAlignment = Alignment.Center,
        ) {
            Icon(com.yourteam.expensetracker.ui.theme.HugeIcons.Add, contentDescription = "Add a payment", tint = if (onScan != null) TextPrimary else OnAccent, modifier = Modifier.size(28.dp))
        }
        if (onScan != null) {
            Spacer(Modifier.width(12.dp))
            Box(
                Modifier
                    .size(Spacing.scanButton)
                    .clip(PillShape)
                    .background(Accent)
                    .clickable(role = Role.Button, onClick = onScan),
                contentAlignment = Alignment.Center,
            ) {
                Icon(com.yourteam.expensetracker.ui.theme.AppIcons.Scan, contentDescription = "Scan and pay", tint = OnAccent, modifier = Modifier.size(26.dp))
            }
        }
    }
}

private val dayFormat = DateTimeFormatter.ofPattern("EEE d MMM")
private val timeFormat = DateTimeFormatter.ofPattern("h:mm a")

/** "now", "12m", "3h" today; then "Yesterday" / "Mon 9 Nov", or "8:10 pm" in a day-grouped list (DESIGN.md §6). */
fun relativeTime(
    at: Long,
    now: Long = System.currentTimeMillis(),
    zone: ZoneId = ZoneId.systemDefault(),
    groupedByDay: Boolean = false,
): String {
    val then = Instant.ofEpochMilli(at).atZone(zone)
    val today = LocalDate.now(zone)
    val mins = ChronoUnit.MINUTES.between(Instant.ofEpochMilli(at), Instant.ofEpochMilli(now))
    return when {
        then.toLocalDate() == today && mins < 1 -> "now"
        then.toLocalDate() == today && mins < 60 -> "${mins}m"
        then.toLocalDate() == today && mins < 12 * 60 -> "${mins / 60}h"
        then.toLocalDate() == today || groupedByDay -> timeFormat.format(then).lowercase()
        then.toLocalDate() == today.minusDays(1) -> "Yesterday"
        else -> dayFormat.format(then)
    }
}

/** Round back button with the title beside it, for screens opened from a tab (Payments, Ask, Profile). */
@Composable
fun SubScreenHeader(title: String, onBack: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(top = 12.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier.size(Spacing.circle).clip(PillShape).background(Surface).clickable(role = Role.Button, onClick = onBack),
            contentAlignment = Alignment.Center,
        ) {
            Icon(com.yourteam.expensetracker.ui.theme.HugeIcons.ChevronLeft, contentDescription = "Back", tint = TextPrimary, modifier = Modifier.size(24.dp))
        }
        Spacer(Modifier.width(14.dp))
        Text(title, style = AppType.sectionTitle, color = TextPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/**
 * "What was this payment for?" sheet (Feature 1 §3.4). Works through [queue] one payment at a
 * time: one tap on a suggestion saves it (and learns it for the payee), or Skip.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CategorySheet(
    queue: List<PaymentRowModel>,
    onPick: (row: PaymentRowModel, category: String, onlyThis: Boolean) -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    var index by remember { androidx.compose.runtime.mutableIntStateOf(0) }
    /** True while the chooser is on a sub-page (All categories / New), which shows its own title. */
    var chooserSub by remember { androidx.compose.runtime.mutableStateOf(false) }
    fun close() = scope.launch { sheetState.hide() }.invokeOnCompletion { onDismiss() }
    val row = queue.getOrNull(index)
    if (row == null) { androidx.compose.runtime.LaunchedEffect(Unit) { onDismiss() }; return }
    fun next() { if (index + 1 < queue.size) index++ else close() }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState, containerColor = Surface, dragHandle = null) {
        Column(Modifier.padding(horizontal = Spacing.screen, vertical = 20.dp).navigationBarsPadding()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                // On a chooser sub-page the inner header carries the title; keep only the close button here.
                if (!chooserSub) {
                    Column(Modifier.weight(1f)) {
                        if (queue.size > 1) Text("${index + 1} of ${queue.size}", style = AppType.chip, color = TextSecondary)
                        Text(if (row.debited) "What was this payment for?" else "What was this money for?", style = AppType.sectionTitle, color = TextPrimary)
                    }
                } else {
                    Spacer(Modifier.weight(1f))
                }
                Box(
                    Modifier.size(Spacing.circle).clip(PillShape).background(SurfaceRaised).clickable(role = Role.Button) { close() },
                    contentAlignment = Alignment.Center,
                ) { Icon(com.yourteam.expensetracker.ui.theme.HugeIcons.Close, contentDescription = "Close", tint = TextPrimary, modifier = Modifier.size(20.dp)) }
            }
            if (!chooserSub) {
                Spacer(Modifier.height(16.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    InitialsCircle(row.payee)
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        Text(if (row.debited) "Paid to ${row.payee}" else "From ${row.payee}", style = AppType.rowTitle, color = TextPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(relativeTime(row.at), style = AppType.body, color = TextSecondary)
                    }
                    Text(if (row.debited) "\u2212${row.amount}" else "+${row.amount}", style = AppType.rowTitle, color = TextPrimary)
                }
            }
            Spacer(Modifier.height(if (chooserSub) 12.dp else 20.dp))
            androidx.compose.runtime.key(row.key) {
                CategoryChooser(row, onSubPageChange = { chooserSub = it }) { c, onlyThis -> onPick(row, c, onlyThis); next() }
            }
            if (!chooserSub && queue.size > 1) {
                Spacer(Modifier.height(12.dp))
                Text(
                    "Skip",
                    style = AppType.tab,
                    color = Accent,
                    modifier = Modifier.heightIn(min = Spacing.circle).clip(PillShape).clickable(role = Role.Button) { next() }.padding(horizontal = 4.dp, vertical = 12.dp),
                )
            }
        }
    }
}

private val fullWhen = DateTimeFormatter.ofPattern("EEE d MMM, h:mm a")

/**
 * Tapping a payment opens this sheet: the payment centred, a secondary "Change category" button
 * that swaps the same sheet to the category pills, and "See how we know" for the full details.
 * [row] is looked up fresh by the caller, so a new category shows straight away.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PaymentSheet(
    row: PaymentRowModel,
    onPickCategory: (category: String, onlyThis: Boolean) -> Unit,
    onAnswer: (id: Long, yes: Boolean) -> Unit,
    onOpenDetails: () -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    var picking by remember { androidx.compose.runtime.mutableStateOf(false) }
    /** True while the chooser is on a sub-page (All categories / New), which shows its own title. */
    var chooserSub by remember { androidx.compose.runtime.mutableStateOf(false) }
    /** Which answer button is loading, if any. */
    var busy by remember { androidx.compose.runtime.mutableStateOf<String?>(null) }
    fun close(then: () -> Unit = {}) = scope.launch { sheetState.hide() }.invokeOnCompletion { then(); onDismiss() }
    /** Spinner on the tapped button, then the sheet closes and the change applies (top message confirms). */
    fun answer(label: String, id: Long, yes: Boolean) {
        if (busy != null) return
        busy = label
        scope.launch { delay(ACTION_DELAY_MS); close { onAnswer(id, yes) } }
    }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState, containerColor = Surface, dragHandle = null) {
        Column(Modifier.padding(horizontal = Spacing.screen, vertical = 20.dp).navigationBarsPadding()) {
            // Top row: back (while picking) and close.
            Row(verticalAlignment = Alignment.CenterVertically) {
                // On a chooser sub-page the inner header carries the title and back, so collapse this one.
                if (picking && !chooserSub) {
                    Box(
                        Modifier.size(Spacing.circle).clip(PillShape).background(SurfaceRaised).clickable(role = Role.Button) { picking = false },
                        contentAlignment = Alignment.Center,
                    ) { Icon(com.yourteam.expensetracker.ui.theme.HugeIcons.ChevronLeft, contentDescription = "Back", tint = TextPrimary, modifier = Modifier.size(22.dp)) }
                    Spacer(Modifier.width(14.dp))
                    Text(if (row.category == null) "Add category" else "Change category", style = AppType.sectionTitle, color = TextPrimary, modifier = Modifier.weight(1f))
                } else {
                    Spacer(Modifier.weight(1f))
                }
                Box(
                    Modifier.size(Spacing.circle).clip(PillShape).background(SurfaceRaised).clickable(role = Role.Button) { close() },
                    contentAlignment = Alignment.Center,
                ) { Icon(com.yourteam.expensetracker.ui.theme.HugeIcons.Close, contentDescription = "Close", tint = TextPrimary, modifier = Modifier.size(20.dp)) }
            }

            androidx.compose.animation.AnimatedContent(
                targetState = picking,
                transitionSpec = {
                    val dir = if (targetState) 1 else -1
                    (androidx.compose.animation.slideInHorizontally { it * dir / 4 } + androidx.compose.animation.fadeIn())
                        .togetherWith(androidx.compose.animation.slideOutHorizontally { -it * dir / 4 } + androidx.compose.animation.fadeOut())
                        .using(androidx.compose.animation.SizeTransform(clip = false))
                },
                label = "payment-sheet",
            ) { isPicking ->
                if (isPicking) {
                    Column(Modifier.fillMaxWidth().padding(top = 16.dp)) {
                        CategoryChooser(row, onSubPageChange = { chooserSub = it }) { c, onlyThis -> close { onPickCategory(c, onlyThis) } } // the chooser already showed its loading step
                    }
                } else {
                    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                        InitialsCircle(row.payee, size = 64.dp)
                        Spacer(Modifier.height(12.dp))
                        Text(row.payee, style = AppType.rowTitle, color = TextPrimary, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                        Spacer(Modifier.height(4.dp))
                        Text(
                            if (row.debited) "\u2212${row.amount}" else "+${row.amount}",
                            style = AppType.hero,
                            color = TextPrimary,
                        )
                        Text(
                            "${if (row.debited) "Debited" else "Credited"} · ${fullWhen.format(Instant.ofEpochMilli(row.at).atZone(ZoneId.systemDefault())).replace("AM", "am").replace("PM", "pm")}",
                            style = AppType.body,
                            color = TextSecondary,
                        )
                        Spacer(Modifier.height(12.dp))
                        Row(horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp)) {
                            Chip(if (!row.debited) row.category?.let { "Received · $it" } ?: "Received" else row.category ?: "No category", accent = false)
                            if (row.source.isNotEmpty()) Chip(row.source)
                        }
                        Spacer(Modifier.height(Spacing.section))
                        row.askId?.let { id ->
                            Text("Did it go through?", style = AppType.sectionTitle, color = TextPrimary)
                            Spacer(Modifier.height(4.dp))
                            Text(
                                "You started this in ${row.source.ifEmpty { "your UPI app" }}, but no bank SMS or app record has confirmed it yet.",
                                style = AppType.body,
                                color = TextSecondary,
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                            )
                            Spacer(Modifier.height(14.dp))
                            Row {
                                SheetButton("Not paid", filled = false, Modifier.weight(1f), loading = busy == "no", enabled = busy == null) { answer("no", id, false) }
                                Spacer(Modifier.width(Spacing.cardGap))
                                SheetButton("Yes, paid", filled = true, Modifier.weight(1f), loading = busy == "yes", enabled = busy == null) { answer("yes", id, true) }
                            }
                            Spacer(Modifier.height(Spacing.cardGap))
                        }
                        row.conflictId?.let { id ->
                            Text("The bank disagrees", style = AppType.sectionTitle, color = TextPrimary)
                            Spacer(Modifier.height(4.dp))
                            Text(
                                "You said this didn't go through, but a ${row.source.lowercase()} shows the money left your account.",
                                style = AppType.body,
                                color = TextSecondary,
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                            )
                            Spacer(Modifier.height(10.dp))
                            SheetButton("It was paid", filled = true, Modifier.fillMaxWidth(), loading = busy == "paid", enabled = busy == null) { answer("paid", id, true) }
                            Spacer(Modifier.height(Spacing.cardGap))
                        }
                        // Secondary, not primary: raised fill, normal text. Money received gets an income category.
                        SheetButton(if (row.category == null) "Add category" else "Change category", filled = false, Modifier.fillMaxWidth(), enabled = busy == null) {
                            picking = true
                        }
                        Spacer(Modifier.height(4.dp))
                        if (row.debited) Text(
                            "See how we know",
                            style = AppType.tab,
                            color = Accent,
                            modifier = Modifier
                                .heightIn(min = Spacing.circle)
                                .clip(PillShape)
                                .clickable(role = Role.Button) { close(onOpenDetails) }
                                .padding(horizontal = 16.dp, vertical = 12.dp),
                        )
                    }
                }
            }
        }
    }
}

/**
 * "Needs check" sheet (MVP M7 / M13, FEEDBACK F10): drag handle, question, the payment in an
 * inset card marked Pending, one line on what happens next, then Not paid | Yes, paid.
 * Closing it leaves the payment Pending; a matching bank SMS later confirms it on its own.
 * For a disagreement ("you said Not paid, the bank says paid") it offers Not now | It was paid.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NeedsCheckSheet(row: PaymentRowModel, onAnswer: (id: Long, yes: Boolean) -> Unit, onDismiss: () -> Unit) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    fun close(then: () -> Unit = {}) = scope.launch { sheetState.hide() }.invokeOnCompletion { then(); onDismiss() }
    val pending = row.askId != null
    val id = row.askId ?: row.conflictId ?: return
    var busy by remember { androidx.compose.runtime.mutableStateOf<Boolean?>(null) }
    /** Spinner on the tapped button, then the sheet closes and the change applies (top message confirms). */
    fun answer(yes: Boolean) {
        if (busy != null) return
        busy = yes
        scope.launch { delay(ACTION_DELAY_MS); close { onAnswer(id, yes) } }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = Surface,
        dragHandle = { androidx.compose.material3.BottomSheetDefaults.DragHandle(color = SurfaceRaised) },
    ) {
        Column(Modifier.padding(start = Spacing.screen, end = Spacing.screen, top = 8.dp, bottom = 20.dp).navigationBarsPadding()) {
            Text(
                if (pending) "Did this payment go through?" else "The bank says this was paid",
                style = AppType.sectionTitle,
                color = TextPrimary,
            )
            Spacer(Modifier.height(16.dp))
            // The payment, inset on the darker background.
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth().clip(CardShape).background(Bg).padding(horizontal = 16.dp, vertical = Spacing.cardV),
            ) {
                InitialsCircle(row.payee)
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text(row.payee, style = AppType.rowTitle, color = TextPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text("${row.source} · ${relativeTime(row.at)}", style = AppType.body, color = TextSecondary, maxLines = 1)
                }
                Spacer(Modifier.width(12.dp))
                Column(horizontalAlignment = Alignment.End) {
                    Text(row.amount, style = AppType.rowTitle, color = TextPrimary)
                    Text(if (pending) "Pending" else "Debited", style = AppType.chip, color = TextSecondary)
                }
            }
            Spacer(Modifier.height(16.dp))
            Text(
                if (pending) "Your bank hasn't confirmed it yet. If it does, we'll update this on our own."
                else "You said it didn't go through, but a ${row.source.lowercase()} shows the money left your account. If it comes back as a refund, you'll see a credit.",
                style = AppType.body,
                color = TextSecondary,
            )
            Spacer(Modifier.height(Spacing.section))
            Row {
                // "Not now" only closes, so it needs no loading step.
                SheetButton(if (pending) "Not paid" else "Not now", filled = false, Modifier.weight(1f), loading = busy == false, enabled = busy == null) {
                    if (pending) answer(false) else close()
                }
                Spacer(Modifier.width(Spacing.cardGap))
                SheetButton(if (pending) "Yes, paid" else "It was paid", filled = true, Modifier.weight(1f), loading = busy == true, enabled = busy == null) {
                    answer(true)
                }
            }
        }
    }
}
