package com.yourteam.expensetracker.ui.screens.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.yourteam.expensetracker.insights.DateRange
import com.yourteam.expensetracker.insights.RangePreset
import com.yourteam.expensetracker.ui.feedback.Feedback
import com.yourteam.expensetracker.ui.theme.Accent
import com.yourteam.expensetracker.ui.theme.AppType
import com.yourteam.expensetracker.ui.theme.OnAccent
import com.yourteam.expensetracker.ui.theme.PillShape
import com.yourteam.expensetracker.ui.theme.Spacing
import com.yourteam.expensetracker.ui.theme.Surface
import com.yourteam.expensetracker.ui.theme.SurfaceRaised
import com.yourteam.expensetracker.ui.theme.TextPrimary
import com.yourteam.expensetracker.ui.theme.TextSecondary
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter

/** Soft blue band between the start and end days. */
private val Band: Color @Composable @androidx.compose.runtime.ReadOnlyComposable get() = Accent.copy(alpha = 0.22f)
/** Days from the previous month, and the disabled forward arrow. */
private val Faded: Color @Composable @androidx.compose.runtime.ReadOnlyComposable get() = TextSecondary.copy(alpha = 0.4f)
private val CellHeight = 48.dp
private val DotSize = 44.dp

private val Quick = listOf(
    RangePreset.TODAY, RangePreset.THIS_WEEK, RangePreset.LAST_7_DAYS, RangePreset.THIS_MONTH, RangePreset.LAST_MONTH,
)
private val monthTitle = DateTimeFormatter.ofPattern("MMMM yyyy")
private val spokenDay = DateTimeFormatter.ofPattern("EEEE d MMMM")

/**
 * Date range picker for Home (canvas v5). Quick-pick chips fill in the calendar; tapping a day
 * starts a new range and a second tap ends it (that makes it a custom range). Future days are
 * not shown, so they can't be picked. Nothing changes
 * on Home until Apply. Haptics per FEEDBACK.md: Tick on each chip / day tap, nothing on Apply
 * because Home updating is the confirmation.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DateRangeSheet(
    current: DateRange,
    today: LocalDate,
    onApply: (DateRange) -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    fun close(then: () -> Unit = {}) = scope.launch { sheetState.hide() }.invokeOnCompletion { then(); onDismiss() }

    var start by remember { mutableStateOf(current.start) }
    var end by remember { mutableStateOf<LocalDate?>(current.end) }
    var preset by remember { mutableStateOf(current.preset) }
    var month by remember { mutableStateOf(YearMonth.from(minOf(current.end, today))) }
    val thisMonth = YearMonth.from(today)

    val draft = DateRange(start, end ?: start, if (end == null) RangePreset.CUSTOM else preset)

    fun pickPreset(p: RangePreset) {
        Feedback.tick()
        val r = DateRange.of(p, today)
        start = r.start
        end = r.end
        preset = p
        month = YearMonth.from(minOf(r.end, today))
    }

    fun pickDay(d: LocalDate) {
        Feedback.tick()
        if (end != null) {
            start = d // first tap: a new range starts here
            end = null
        } else {
            val r = DateRange.custom(start, d) // second tap ends it, in either direction
            start = r.start
            end = r.end
        }
        preset = RangePreset.CUSTOM
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = Surface,
        dragHandle = { BottomSheetDefaults.DragHandle(color = SurfaceRaised) },
    ) {
        Column(Modifier.navigationBarsPadding()) {
            // Title + close
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(start = Spacing.screen, end = Spacing.screen - 4.dp),
            ) {
                Text("Select dates", style = AppType.statValue, color = TextPrimary, modifier = Modifier.weight(1f))
                IconButton(
                    onClick = { close() },
                    modifier = Modifier.size(Spacing.circle).clip(PillShape).background(SurfaceRaised),
                ) {
                    Icon(com.yourteam.expensetracker.ui.theme.HugeIcons.Close, contentDescription = "Close", tint = TextPrimary)
                }
            }
            Spacer(Modifier.height(16.dp))

            // Quick picks
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = Spacing.screen),
            ) {
                Quick.forEach { p ->
                    val selected = end != null && preset == p
                    Text(
                        text = RangeWords.chip(p),
                        style = AppType.tab,
                        color = if (selected) OnAccent else TextPrimary,
                        modifier = Modifier
                            .heightIn(min = Spacing.circle)
                            .clip(PillShape)
                            .background(if (selected) Accent else SurfaceRaised)
                            .clickable(role = Role.Button) { pickPreset(p) }
                            .semantics { this.selected = selected }
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                    )
                }
            }
            Spacer(Modifier.height(20.dp))

            Column(Modifier.padding(horizontal = Spacing.screen)) {
                // ‹ November 2026 ›
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { month = month.minusMonths(1) }) {
                        Icon(com.yourteam.expensetracker.ui.theme.HugeIcons.ChevronLeft, contentDescription = "Previous month", tint = TextPrimary)
                    }
                    Text(
                        text = monthTitle.format(month),
                        style = AppType.tab.copy(fontWeight = FontWeight.Bold),
                        color = TextPrimary,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.weight(1f),
                    )
                    val canGoForward = month < thisMonth // no future spending to show
                    IconButton(onClick = { month = month.plusMonths(1) }, enabled = canGoForward) {
                        Icon(
                            com.yourteam.expensetracker.ui.theme.HugeIcons.ChevronRight,
                            contentDescription = "Next month",
                            tint = if (canGoForward) TextPrimary else Faded,
                        )
                    }
                }
                Spacer(Modifier.height(8.dp))

                // Weekday header, Monday first
                Row {
                    listOf("Mo", "Tu", "We", "Th", "Fr", "Sa", "Su").forEach {
                        Text(
                            it,
                            style = AppType.chip,
                            color = TextSecondary,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
                Spacer(Modifier.height(8.dp))

                MonthGrid(month, today, start, end, onDay = ::pickDay)
            }

            Spacer(Modifier.height(12.dp))
            HorizontalDivider(color = SurfaceRaised)
            Spacer(Modifier.height(16.dp))

            // "Tue 3 Nov – Mon 9 Nov · 7 days"
            Text(
                text = RangeWords.summary(draft),
                style = AppType.body.copy(fontWeight = FontWeight.SemiBold),
                color = TextPrimary,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.screen),
            )
            if (end == null) {
                Text(
                    "Tap another day to end the range",
                    style = AppType.body,
                    color = TextSecondary,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                )
            }
            Spacer(Modifier.height(16.dp))

            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.padding(horizontal = Spacing.screen).padding(bottom = 16.dp),
            ) {
                SheetPill("Cancel", fill = SurfaceRaised, text = TextPrimary, Modifier.weight(1f)) { close() }
                SheetPill("Apply", fill = Accent, text = OnAccent, Modifier.weight(1f)) {
                    close { onApply(draft) } // no message: Home updating is the confirmation
                }
            }
        }
    }
}

@Composable
private fun MonthGrid(
    month: YearMonth,
    today: LocalDate,
    start: LocalDate,
    end: LocalDate?,
    onDay: (LocalDate) -> Unit,
) {
    val first = month.atDay(1)
    val lead = first.dayOfWeek.value - 1 // Monday = 1
    val cells = (lead downTo 1).map { first.minusDays(it.toLong()) } + (1..month.lengthOfMonth()).map { month.atDay(it) }
    val last = end ?: start

    Column {
        cells.chunked(7).forEach { week ->
            Row {
                week.forEachIndexed { col, day ->
                    val inMonth = YearMonth.from(day) == month
                    val future = day.isAfter(today)
                    val isEdge = inMonth && (day == start || day == end)
                    val inRange = inMonth && end != null && !day.isBefore(start) && !day.isAfter(last)
                    if (future) {
                        Spacer(Modifier.weight(1f)) // future days aren't shown: no spending there yet
                        return@forEachIndexed
                    }
                    DayCell(
                        day = day,
                        col = col,
                        isEdge = isEdge,
                        bandLeft = inRange && day.isAfter(start),
                        bandRight = inRange && day.isBefore(last),
                        textColor = when {
                            isEdge -> OnAccent
                            !inMonth -> Faded
                            else -> TextPrimary
                        },
                        onClick = if (inMonth) ({ onDay(day) }) else null,
                        modifier = Modifier.weight(1f),
                    )
                }
                repeat(7 - week.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

/**
 * One day. The band is drawn in two halves so it joins the neighbouring days; it gets a
 * rounded end at the edge of a row, so each week's stretch reads as one pill.
 */
@Composable
private fun DayCell(
    day: LocalDate,
    col: Int,
    isEdge: Boolean,
    bandLeft: Boolean,
    bandRight: Boolean,
    textColor: Color,
    onClick: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    val round = DotSize / 2
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .height(CellHeight)
            .then(
                if (onClick != null) {
                    Modifier
                        .clickable(role = Role.Button, onClick = onClick)
                        .semantics {
                            contentDescription = spokenDay.format(day)
                            selected = isEdge || bandLeft || bandRight
                        }
                } else Modifier,
            ),
    ) {
        Row(Modifier.fillMaxWidth().height(DotSize)) {
            Box(
                Modifier.weight(1f).fillMaxHeight().background(
                    if (bandLeft) Band else Color.Transparent,
                    if (col == 0) RoundedCornerShape(topStart = round, bottomStart = round) else RoundedCornerShape(0.dp),
                ),
            )
            Box(
                Modifier.weight(1f).fillMaxHeight().background(
                    if (bandRight) Band else Color.Transparent,
                    if (col == 6) RoundedCornerShape(topEnd = round, bottomEnd = round) else RoundedCornerShape(0.dp),
                ),
            )
        }
        if (isEdge) Box(Modifier.size(DotSize).background(Accent, PillShape))
        Text(
            text = day.dayOfMonth.toString(),
            style = AppType.body.copy(fontWeight = FontWeight.SemiBold),
            color = textColor,
        )
    }
}

@Composable
private fun SheetPill(label: String, fill: Color, text: Color, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .height(Spacing.button)
            .clip(PillShape)
            .background(fill)
            .clickable(role = Role.Button, onClick = onClick),
    ) {
        Text(label, style = AppType.tab, color = text)
    }
}
