package com.yourteam.expensetracker.ui.screens.insights

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.yourteam.expensetracker.insights.InsightPeriod
import com.yourteam.expensetracker.insights.PeriodKind
import com.yourteam.expensetracker.ui.theme.Accent
import com.yourteam.expensetracker.ui.theme.AppType
import com.yourteam.expensetracker.ui.theme.Negative
import com.yourteam.expensetracker.ui.theme.OnAccent
import com.yourteam.expensetracker.ui.theme.PillShape
import com.yourteam.expensetracker.ui.theme.Surface
import com.yourteam.expensetracker.ui.theme.SurfaceRaised
import com.yourteam.expensetracker.ui.theme.TextPrimary
import com.yourteam.expensetracker.ui.theme.TextSecondary
import com.yourteam.expensetracker.ui.theme.TileShape
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

/**
 * The date sheet. Month/Week/Day step through earlier periods; Custom opens the calendar (in6),
 * with future days greyed and a clear error when the end is before the start.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InsightsDateSheet(
    period: InsightPeriod,
    today: LocalDate,
    onDismiss: () -> Unit,
    onStep: (back: Boolean) -> Unit,
    onCustom: (LocalDate, LocalDate) -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState, containerColor = Surface) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp).navigationBarsPadding().padding(bottom = 16.dp)) {
            if (period.kind == PeriodKind.CUSTOM) {
                CustomCalendar(today = today, onApply = { a, b -> onCustom(a, b); onDismiss() }, onClose = onDismiss)
            } else {
                PeriodStepper(period, today, onStep)
            }
        }
    }
}

@Composable
private fun PeriodStepper(period: InsightPeriod, today: LocalDate, onStep: (Boolean) -> Unit) {
    val atCurrent = !today.isBefore(period.start) && !today.isAfter(period.end)
    Text("Choose ${InsightsWords.unit(period.kind)}", style = AppType.sectionTitle, color = TextPrimary)
    Spacer(Modifier.height(16.dp))
    Text(InsightsWords.label(period, today), style = AppType.statValue, color = TextPrimary)
    Spacer(Modifier.height(20.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        StepButton("Previous", enabled = true, modifier = Modifier.weight(1f)) { onStep(true) }
        StepButton("Next", enabled = !atCurrent, modifier = Modifier.weight(1f)) { onStep(false) }
    }
    Spacer(Modifier.height(12.dp))
    Text("Tap Previous to look back. Next is off once you reach ${InsightsWords.unit(period.kind)} that includes today.", style = AppType.body, color = TextSecondary)
    Spacer(Modifier.height(8.dp))
}

@Composable
private fun StepButton(text: String, enabled: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Box(
        modifier
            .clip(PillShape)
            .background(if (enabled) SurfaceRaised else SurfaceRaised.copy(alpha = 0.4f))
            .then(if (enabled) Modifier.clickable(role = Role.Button, onClick = onClick) else Modifier)
            .padding(vertical = 14.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, style = AppType.rowTitle, color = if (enabled) TextPrimary else TextSecondary)
    }
}

// ---- Custom calendar (in6) ----

@Composable
private fun CustomCalendar(today: LocalDate, onApply: (LocalDate, LocalDate) -> Unit, onClose: () -> Unit) {
    var from by remember { mutableStateOf<LocalDate?>(null) }
    var to by remember { mutableStateOf<LocalDate?>(null) }
    var visible by remember { mutableStateOf(YearMonth.from(today)) }

    val endBeforeStart = from != null && to != null && to!!.isBefore(from!!)
    val valid = from != null && to != null && !endBeforeStart

    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text("Choose dates", style = AppType.sectionTitle, color = TextPrimary, modifier = Modifier.weight(1f))
        Box(Modifier.size(36.dp).clip(PillShape).background(SurfaceRaised).clickable(onClick = onClose), contentAlignment = Alignment.Center) {
            Icon(com.yourteam.expensetracker.ui.theme.HugeIcons.Close, contentDescription = "Close", tint = TextPrimary, modifier = Modifier.size(18.dp))
        }
    }
    Spacer(Modifier.height(16.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        DateField("From", from, error = false, modifier = Modifier.weight(1f))
        DateField("To", to, error = endBeforeStart, modifier = Modifier.weight(1f))
    }
    if (endBeforeStart) {
        Spacer(Modifier.height(10.dp))
        Text("End date is before the start date", style = AppType.body, color = Negative)
    }
    Spacer(Modifier.height(16.dp))

    // Month header with arrows.
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(visible.format(MONTH_YEAR), style = AppType.rowTitle, color = TextPrimary, modifier = Modifier.weight(1f))
        Icon(
            com.yourteam.expensetracker.ui.theme.HugeIcons.ChevronLeft, contentDescription = "Previous month", tint = TextPrimary,
            modifier = Modifier.size(28.dp).clip(PillShape).clickable { visible = visible.minusMonths(1) },
        )
        Spacer(Modifier.width(8.dp))
        val canGoNext = visible.isBefore(YearMonth.from(today))
        Icon(
            com.yourteam.expensetracker.ui.theme.HugeIcons.ChevronRight, contentDescription = "Next month",
            tint = if (canGoNext) TextPrimary else TextSecondary.copy(alpha = 0.4f),
            modifier = Modifier.size(28.dp).clip(PillShape).then(if (canGoNext) Modifier.clickable { visible = visible.plusMonths(1) } else Modifier),
        )
    }
    Spacer(Modifier.height(12.dp))
    WeekdayHeader()
    Spacer(Modifier.height(4.dp))
    MonthGrid(
        month = visible,
        today = today,
        from = from,
        to = to,
        onPick = { day ->
            when {
                from == null || to != null -> { from = day; to = null }   // start a new range
                day.isBefore(from) -> { to = from; from = day }           // tapped earlier → swap
                else -> to = day
            }
        },
    )
    Spacer(Modifier.height(16.dp))
    Text("Future dates are greyed out. Ranges can be up to 12 months.", style = AppType.body, color = TextSecondary)
    Spacer(Modifier.height(16.dp))
    Box(
        Modifier.fillMaxWidth().clip(PillShape)
            .background(if (valid) Accent else SurfaceRaised)
            .then(if (valid) Modifier.clickable { onApply(from!!, to!!) } else Modifier)
            .padding(vertical = 16.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text("Show this range", style = AppType.rowTitle, color = if (valid) OnAccent else TextSecondary)
    }
}

@Composable
private fun DateField(label: String, date: LocalDate?, error: Boolean, modifier: Modifier = Modifier) {
    Column(
        modifier
            .clip(TileShape)
            .background(SurfaceRaised)
            .then(if (error) Modifier.border(1.dp, Negative, TileShape) else Modifier)
            .padding(horizontal = 14.dp, vertical = 12.dp),
    ) {
        Text(label, style = AppType.body, color = TextSecondary)
        Spacer(Modifier.height(4.dp))
        Text(date?.format(DAY_MONTH) ?: "—", style = AppType.rowTitle, color = if (error) Negative else TextPrimary)
    }
}

@Composable
private fun WeekdayHeader() {
    Row(Modifier.fillMaxWidth()) {
        DayOfWeek.entries.forEach { d ->
            Text(
                d.getDisplayName(TextStyle.NARROW, Locale.getDefault()),
                style = AppType.chip, color = TextSecondary, textAlign = TextAlign.Center,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun MonthGrid(month: YearMonth, today: LocalDate, from: LocalDate?, to: LocalDate?, onPick: (LocalDate) -> Unit) {
    val first = month.atDay(1)
    val lead = (first.dayOfWeek.value + 6) % 7 // Monday = 0
    val days = month.lengthOfMonth()
    val cells = lead + days
    val rows = (cells + 6) / 7
    Column {
        for (r in 0 until rows) {
            Row(Modifier.fillMaxWidth()) {
                for (c in 0 until 7) {
                    val index = r * 7 + c
                    val dayNum = index - lead + 1
                    Box(Modifier.weight(1f).aspectRatio(1f), contentAlignment = Alignment.Center) {
                        if (dayNum in 1..days) {
                            val date = month.atDay(dayNum)
                            DayCell(date, today, from, to, onPick)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DayCell(date: LocalDate, today: LocalDate, from: LocalDate?, to: LocalDate?, onPick: (LocalDate) -> Unit) {
    val future = date.isAfter(today)
    val isEnd = date == from || date == to
    val inRange = from != null && to != null && !date.isBefore(minOf(from, to)) && !date.isAfter(maxOf(from, to))
    val bg = when {
        isEnd -> Accent
        inRange -> Accent.copy(alpha = 0.18f)
        else -> Color.Transparent
    }
    Box(
        Modifier.size(40.dp).clip(PillShape).background(bg)
            .then(if (!future) Modifier.clickable { onPick(date) } else Modifier),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            "${date.dayOfMonth}",
            style = AppType.body.copy(fontWeight = if (isEnd) FontWeight.SemiBold else FontWeight.Normal),
            color = when {
                isEnd -> OnAccent
                future -> TextSecondary.copy(alpha = 0.4f)
                else -> TextPrimary
            },
        )
    }
}

private val MONTH_YEAR = DateTimeFormatter.ofPattern("MMMM yyyy")
private val DAY_MONTH = DateTimeFormatter.ofPattern("d MMM")
