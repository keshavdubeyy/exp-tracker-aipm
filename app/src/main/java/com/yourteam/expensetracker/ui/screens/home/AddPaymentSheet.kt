package com.yourteam.expensetracker.ui.screens.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.yourteam.expensetracker.categoriser.Categories
import com.yourteam.expensetracker.parser.Amounts
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
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

/** Up to ₹99,99,999.99, at most 2 decimals. Anything else is ignored as it's typed. */
private val amountInput = Regex("""^\d{0,7}(\.\d{0,2})?$""")
private val pickedDay = DateTimeFormatter.ofPattern("EEE d MMM")

/**
 * The + button on Home: add money out or money in by hand, from a bank account or in cash.
 * Amount is required; payee, category and day are optional (day defaults to today).
 * Future days can't be picked. [onSave] gets paise, payee/sender, time in millis, category,
 * whether it's money in, and whether it was cash.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddPaymentSheet(
    onSave: (paise: Long, payee: String, at: Long, category: String?, moneyIn: Boolean, cash: Boolean) -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    fun close(then: () -> Unit = {}) = scope.launch { sheetState.hide() }.invokeOnCompletion { then(); onDismiss() }

    val today = remember { LocalDate.now() }
    var amount by remember { mutableStateOf("") }
    var payee by remember { mutableStateOf("") }
    var category by remember { mutableStateOf<String?>(null) }
    var moneyIn by remember { mutableStateOf(false) }
    var cash by remember { mutableStateOf(false) }
    var day by remember { mutableStateOf(today) }
    var pickingDate by remember { mutableStateOf(false) }

    val paise = Amounts.toPaise(amount.trimEnd('.'))?.takeIf { it > 0 }
    val amountFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) { amountFocus.requestFocus() } // keypad opens straight away

    fun save() {
        val p = paise ?: return
        // Today: now. An earlier day: midday, so it sorts sensibly within that day.
        val time = if (day == today) LocalTime.now() else LocalTime.NOON
        val at = day.atTime(time).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        close { onSave(p, payee, at, category, moneyIn, cash) }
    }

    if (pickingDate) {
        val zoneToday = today
        val state = rememberDatePickerState(
            initialSelectedDateMillis = day.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
            selectableDates = object : SelectableDates {
                // No future days: there's no spending there yet.
                override fun isSelectableDate(utcTimeMillis: Long) =
                    !Instant.ofEpochMilli(utcTimeMillis).atZone(ZoneOffset.UTC).toLocalDate().isAfter(zoneToday)
                override fun isSelectableYear(year: Int) = year <= zoneToday.year
            },
        )
        DatePickerDialog(
            onDismissRequest = { pickingDate = false },
            confirmButton = {
                TextButton(onClick = {
                    state.selectedDateMillis?.let {
                        day = Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate()
                        Feedback.tick()
                    }
                    pickingDate = false
                }) { Text("OK", color = Accent) }
            },
            dismissButton = { TextButton(onClick = { pickingDate = false }) { Text("Cancel", color = TextSecondary) } },
        ) { DatePicker(state) }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = Surface,
        dragHandle = null,
    ) {
        Column(
            Modifier
                .navigationBarsPadding()
                .imePadding()
                .verticalScroll(rememberScrollState()) // scrolls instead of squashing when the keypad is open
                .padding(top = 20.dp, bottom = 16.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(start = Spacing.screen, end = Spacing.screen - 4.dp),
            ) {
                Text(if (moneyIn) "Add money in" else "Add a payment", style = AppType.statValue, color = TextPrimary, modifier = Modifier.weight(1f))
                IconButton(
                    onClick = { close() },
                    modifier = Modifier.size(Spacing.circle).clip(PillShape).background(SurfaceRaised),
                ) { Icon(com.yourteam.expensetracker.ui.theme.HugeIcons.Close, contentDescription = "Close", tint = TextPrimary) }
            }
            Spacer(Modifier.height(16.dp))

            // Money out | Money in
            Row(
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                modifier = Modifier
                    .padding(horizontal = Spacing.screen)
                    .fillMaxWidth()
                    .clip(PillShape)
                    .background(SurfaceRaised)
                    .padding(4.dp),
            ) {
                listOf(false to "Money out", true to "Money in").forEach { (isIn, label) ->
                    val on = moneyIn == isIn
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = Spacing.circle - 4.dp)
                            .clip(PillShape)
                            .background(if (on) Accent else SurfaceRaised)
                            .clickable(role = Role.Tab) {
                                if (!on) {
                                    Feedback.tick()
                                    moneyIn = isIn
                                    category = null // the category lists differ
                                }
                            }
                            .semantics { selected = on },
                    ) { Text(label, style = AppType.tab, color = if (on) OnAccent else TextPrimary) }
                }
            }
            Spacer(Modifier.height(20.dp))

            // ₹ amount, hero-sized
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = Spacing.screen)) {
                Text("₹", style = AppType.hero, color = if (amount.isEmpty()) TextSecondary else TextPrimary)
                Box {
                    if (amount.isEmpty()) Text("0", style = AppType.hero, color = TextSecondary)
                    BasicTextField(
                        value = amount,
                        onValueChange = { if (amountInput.matches(it)) amount = it },
                        textStyle = AppType.hero.copy(color = TextPrimary),
                        cursorBrush = SolidColor(Accent),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Next),
                        modifier = Modifier
                            .fillMaxWidth()
                            .focusRequester(amountFocus)
                            .semantics { contentDescription = "Amount in rupees" },
                    )
                }
            }
            Spacer(Modifier.height(16.dp))

            // Paid to
            Box(
                contentAlignment = Alignment.CenterStart,
                modifier = Modifier
                    .padding(horizontal = Spacing.screen)
                    .fillMaxWidth()
                    .heightIn(min = Spacing.button)
                    .clip(PillShape)
                    .background(SurfaceRaised)
                    .padding(horizontal = 20.dp),
            ) {
                if (payee.isEmpty()) Text(if (moneyIn) "Received from (e.g. Priya)" else "Paid to (e.g. Raju Tea Stall)", style = AppType.body, color = TextSecondary)
                BasicTextField(
                    value = payee,
                    onValueChange = { payee = it.take(60) },
                    textStyle = AppType.body.copy(color = TextPrimary),
                    cursorBrush = SolidColor(Accent),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { save() }),
                    modifier = Modifier.fillMaxWidth().semantics { contentDescription = if (moneyIn) "Received from" else "Paid to" },
                )
            }
            Spacer(Modifier.height(20.dp))

            SheetLabel(if (moneyIn) "Received in" else "Paid with")
            ChipRow {
                SheetChip("Bank account", selected = !cash) { Feedback.tick(); cash = false }
                SheetChip("Cash", selected = cash) { Feedback.tick(); cash = true }
            }
            Spacer(Modifier.height(20.dp))

            SheetLabel("Category")
            ChipRow {
                (if (moneyIn) Categories.income else Categories.expense).forEach { c ->
                    SheetChip(c, selected = category == c) {
                        Feedback.tick() // F14
                        category = if (category == c) null else c // tap again to clear
                    }
                }
            }
            Spacer(Modifier.height(20.dp))

            SheetLabel("When")
            ChipRow {
                val yesterday = today.minusDays(1)
                SheetChip("Today", selected = day == today) { Feedback.tick(); day = today }
                SheetChip("Yesterday", selected = day == yesterday) { Feedback.tick(); day = yesterday }
                val other = day != today && day != yesterday
                SheetChip(if (other) pickedDay.format(day) else "Pick a date", selected = other) { pickingDate = true }
            }
            Spacer(Modifier.height(24.dp))

            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .padding(horizontal = Spacing.screen)
                    .fillMaxWidth()
                    .height(Spacing.button)
                    .clip(PillShape)
                    .background(if (paise != null) Accent else SurfaceRaised)
                    .clickable(enabled = paise != null, role = Role.Button) { save() },
            ) {
                Text(if (moneyIn) "Add money in" else "Add payment", style = AppType.tab, color = if (paise != null) OnAccent else TextSecondary)
            }
        }
    }
}

@Composable
private fun SheetLabel(text: String) {
    Text(
        text,
        style = AppType.chip,
        color = TextSecondary,
        modifier = Modifier.padding(horizontal = Spacing.screen).padding(bottom = 10.dp),
    )
}

@Composable
private fun ChipRow(content: @Composable () -> Unit) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = Spacing.screen),
    ) { content() }
}

@Composable
private fun SheetChip(text: String, selected: Boolean, onClick: () -> Unit) {
    Text(
        text = text,
        style = AppType.tab,
        color = if (selected) OnAccent else TextPrimary,
        modifier = Modifier
            .heightIn(min = Spacing.circle)
            .clip(PillShape)
            .background(if (selected) Accent else SurfaceRaised)
            .clickable(role = Role.Button, onClick = onClick)
            .semantics { this.selected = selected }
            .padding(horizontal = 16.dp, vertical = 12.dp),
    )
}
