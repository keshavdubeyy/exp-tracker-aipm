package com.yourteam.expensetracker.ui.screens.budgets

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.yourteam.expensetracker.insights.BudgetLevel
import com.yourteam.expensetracker.insights.BudgetLine
import com.yourteam.expensetracker.insights.Money
import com.yourteam.expensetracker.ui.theme.Accent
import com.yourteam.expensetracker.ui.theme.AppType
import com.yourteam.expensetracker.ui.theme.CardShape
import com.yourteam.expensetracker.ui.theme.Negative
import com.yourteam.expensetracker.ui.theme.OnAccent
import com.yourteam.expensetracker.ui.theme.PillShape
import com.yourteam.expensetracker.ui.theme.Spacing
import com.yourteam.expensetracker.ui.theme.Surface
import com.yourteam.expensetracker.ui.theme.SurfaceRaised
import com.yourteam.expensetracker.ui.theme.TextPrimary
import com.yourteam.expensetracker.ui.theme.TextSecondary
import com.yourteam.expensetracker.ui.theme.TileShape
import com.yourteam.expensetracker.ui.theme.Warning

/** Bar colour: under 90% blue · 90–99% amber · 100%+ red (always paired with words). */
@Composable
fun levelColor(level: BudgetLevel): Color = when (level) {
    BudgetLevel.UNDER -> Accent
    BudgetLevel.NEAR -> Warning
    BudgetLevel.REACHED, BudgetLevel.OVER -> Negative
}

/** "₹1,000 left" · "₹300 left" (amber) · "Limit reached" · "₹500 over" — the words carry the meaning. */
@Composable
fun leftWords(line: BudgetLine): Pair<String, Color> = when (line.level) {
    BudgetLevel.UNDER -> "${Money.formatWhole(line.leftPaise)} left" to TextSecondary
    BudgetLevel.NEAR -> "${Money.formatWhole(line.leftPaise)} left" to Warning
    BudgetLevel.REACHED -> "Limit reached" to Negative
    BudgetLevel.OVER -> "${Money.formatWhole(line.overPaise)} over" to Negative
}

/** Rounded progress bar. No gradient, no border. */
@Composable
fun BudgetBar(fraction: Float, color: Color, track: Color, height: Dp = 6.dp, modifier: Modifier = Modifier) {
    Box(modifier.fillMaxWidth().height(height).clip(PillShape).background(track)) {
        if (fraction > 0f) Box(Modifier.fillMaxWidth(fraction.coerceIn(0f, 1f)).height(height).clip(PillShape).background(color))
    }
}

/** One category in a card: name · "₹4,000 of ₹5,000", bar, "₹1,000 left" · "80% used". */
@Composable
fun CategoryBudgetRow(line: BudgetLine, onClick: (() -> Unit)? = null) {
    val (words, wordsColor) = leftWords(line)
    Column(
        Modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(role = Role.Button, onClick = onClick) else Modifier)
            .padding(horizontal = Spacing.cardH, vertical = 16.dp)
            .semantics(mergeDescendants = true) {},
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(line.category.orEmpty(), style = AppType.rowTitle.copy(fontSize = AppType.tab.fontSize.times(1.15f)), color = TextPrimary, modifier = Modifier.weight(1f))
            Text(spentOf(line), style = AppType.body.copy(fontSize = AppType.rowTitle.fontSize), color = TextSecondary)
        }
        Spacer(Modifier.height(10.dp))
        BudgetBar(line.fraction, levelColor(line.level), track = SurfaceRaised)
        Spacer(Modifier.height(10.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(words, style = if (line.level == BudgetLevel.UNDER) AppType.body else AppType.chip.copy(fontSize = AppType.body.fontSize), color = wordsColor, modifier = Modifier.weight(1f))
            Text("${line.percent}% used", style = AppType.body, color = TextSecondary)
        }
    }
}

/** "**₹4,000** of ₹5,000": the spent amount stands out, the limit stays quiet. */
@Composable
fun spentOf(line: BudgetLine): AnnotatedString = buildAnnotatedString {
    withStyle(SpanStyle(color = TextPrimary, fontWeight = AppType.rowTitle.fontWeight)) { append(Money.formatWhole(line.spentPaise)) }
    append(" of ${Money.formatWhole(line.limitPaise)}")
}

/** A surface card holding rows separated by thin raised dividers. */
@Composable
fun RowsCard(modifier: Modifier = Modifier, rows: List<@Composable () -> Unit>) {
    Column(modifier.fillMaxWidth().clip(CardShape).background(Surface)) {
        rows.forEachIndexed { i, row ->
            if (i > 0) HorizontalDivider(color = SurfaceRaised, thickness = 1.dp)
            row()
        }
    }
}

/** ⓘ footnote under a section. */
@Composable
fun InfoNote(text: String, modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth().padding(top = 14.dp)) {
        Icon(com.yourteam.expensetracker.ui.theme.HugeIcons.Info, contentDescription = null, tint = TextSecondary, modifier = Modifier.padding(top = 1.dp).size(18.dp))
        Spacer(Modifier.width(10.dp))
        Text(text, style = AppType.body, color = TextSecondary)
    }
}

/** Small caps label above a card ("ALERTS THIS MONTH"). */
@Composable
fun CapsLabel(text: String, modifier: Modifier = Modifier) {
    Text(text.uppercase(), style = AppType.chip, color = TextSecondary, modifier = modifier.padding(start = 4.dp, bottom = 10.dp))
}

/** Full-width pill button. [loading] shows the 20 dp spinner (FEEDBACK.md §2a) and blocks taps. */
@Composable
fun BudgetButton(text: String, filled: Boolean = true, loading: Boolean = false, enabled: Boolean = true, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val ink = when {
        !enabled -> TextSecondary
        filled -> OnAccent
        else -> TextPrimary
    }
    Box(
        modifier
            .fillMaxWidth()
            .height(Spacing.button)
            .clip(PillShape)
            .background(if (filled && enabled) Accent else SurfaceRaised)
            .clickable(enabled = enabled && !loading, role = Role.Button, onClick = onClick)
            .semantics { if (loading) stateDescription = "Saving" },
        contentAlignment = Alignment.Center,
    ) {
        if (loading) CircularProgressIndicator(color = ink, strokeWidth = 2.dp, modifier = Modifier.size(20.dp))
        else Text(text, style = AppType.tab.copy(fontSize = AppType.rowTitle.fontSize.times(1.07f)), color = ink)
    }
}

/** A link-style text button in the accent colour, 44 dp tall. */
@Composable
fun LinkText(text: String, color: Color = Accent, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Text(
        text,
        style = AppType.tab,
        color = color,
        modifier = modifier
            .heightIn(min = Spacing.circle)
            .clip(PillShape)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 4.dp, vertical = 12.dp),
    )
}

// ---- Amount input ----

/** Whole rupees only, up to ₹9,99,99,999. */
private val rupeesInput = Regex("""^\d{0,8}$""")

/** Rupees typed → paise; null when empty or zero. */
fun rupeesToPaise(text: String): Long? = text.toLongOrNull()?.takeIf { it > 0 }?.times(100)

/** Paise → the text an amount field holds (whole rupees, no grouping). */
fun paiseToRupeesText(paise: Long?): String = paise?.takeIf { it > 0 }?.let { (it / 100).toString() }.orEmpty()

/** Shows "2500000" as "25,00,000" while typing (Indian grouping), keeping the cursor right. */
private object IndianGrouping : VisualTransformation {
    override fun filter(text: AnnotatedString): TransformedText {
        val digits = text.text
        val grouped = Money.groupDigits(digits)
        val map = object : OffsetMapping {
            override fun originalToTransformed(offset: Int): Int {
                var seen = 0
                var i = 0
                while (i < grouped.length && seen < offset) { if (grouped[i] != ',') seen++; i++ }
                return i
            }
            override fun transformedToOriginal(offset: Int): Int = grouped.take(offset).count { it != ',' }
        }
        return TransformedText(AnnotatedString(grouped), map)
    }
}

/** Big "₹ 40,000" field in a card; accent outline while focused (bd2, bd3, bd8). */
@Composable
fun BigAmountField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    focusRequester: FocusRequester = remember { FocusRequester() },
    fill: Color = Surface,
) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .fillMaxWidth()
            .clip(CardShape)
            .background(fill)
            .border(2.dp, if (focused) Accent else Color.Transparent, CardShape)
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { focusRequester.requestFocus() }
            .padding(horizontal = 18.dp, vertical = 18.dp),
    ) {
        Text("₹", style = AppType.hero.copy(fontSize = AppType.hero.fontSize.times(0.8f)), color = TextSecondary)
        Spacer(Modifier.width(8.dp))
        Box {
            if (value.isEmpty()) Text("0", style = AppType.hero, color = TextSecondary)
            BasicTextField(
                value = value,
                onValueChange = { if (rupeesInput.matches(it)) onValueChange(it.trimStart('0')) },
                textStyle = AppType.hero.copy(color = TextPrimary),
                cursorBrush = SolidColor(Accent),
                singleLine = true,
                interactionSource = interaction,
                visualTransformation = IndianGrouping,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
                modifier = Modifier.fillMaxWidth().focusRequester(focusRequester).semantics { contentDescription = label },
            )
        }
    }
}

/** Small right-aligned "₹5,000" field on a category row (bd4); outline while focused. */
@Composable
fun RowAmountField(value: String, onValueChange: (String) -> Unit, label: String) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    Box(
        contentAlignment = Alignment.CenterEnd,
        modifier = Modifier
            .widthIn(min = 112.dp, max = 140.dp)
            .heightIn(min = Spacing.circle)
            .clip(TileShape)
            .border(2.dp, if (focused) Accent else Color.Transparent, TileShape)
            .padding(horizontal = 14.dp, vertical = 10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.End) {
            Text("₹", style = AppType.rowTitle.copy(fontSize = AppType.statValue.fontSize.times(0.9f)), color = if (value.isEmpty()) TextSecondary else TextPrimary)
            Box(contentAlignment = Alignment.CenterEnd) {
                if (value.isEmpty()) Text("0", style = AppType.rowTitle.copy(fontSize = AppType.statValue.fontSize.times(0.9f)), color = TextSecondary)
                BasicTextField(
                    value = value,
                    onValueChange = { if (rupeesInput.matches(it)) onValueChange(it.trimStart('0')) },
                    textStyle = AppType.rowTitle.copy(fontSize = AppType.statValue.fontSize.times(0.9f), color = TextPrimary, textAlign = TextAlign.End),
                    cursorBrush = SolidColor(Accent),
                    singleLine = true,
                    interactionSource = interaction,
                    visualTransformation = IndianGrouping,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Next),
                    modifier = Modifier.widthIn(min = 24.dp, max = 110.dp).semantics { contentDescription = label },
                )
            }
        }
    }
}
