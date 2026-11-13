package com.yourteam.expensetracker.ui.screens.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.withStyle
import com.yourteam.expensetracker.insights.Money
import com.yourteam.expensetracker.insights.DateRange
import com.yourteam.expensetracker.insights.PeriodSpend
import com.yourteam.expensetracker.ui.theme.Accent
import com.yourteam.expensetracker.ui.theme.AppType
import com.yourteam.expensetracker.ui.theme.DeltaChipShape
import com.yourteam.expensetracker.ui.theme.Negative
import com.yourteam.expensetracker.ui.theme.OnAccent
import com.yourteam.expensetracker.ui.theme.PillShape
import com.yourteam.expensetracker.ui.theme.Positive
import com.yourteam.expensetracker.ui.theme.Spacing
import com.yourteam.expensetracker.ui.theme.Surface
import com.yourteam.expensetracker.ui.theme.SurfaceRaised
import com.yourteam.expensetracker.ui.theme.TextPrimary
import com.yourteam.expensetracker.ui.theme.TextSecondary
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/**
 * Home hero (DESIGN.md §4.2, §4.3):
 *   Last 7 days · 3–9 Nov ⌄
 *   ₹3,897
 *   ₹412 less than the 7 days before
 *
 * [spend] null = still importing. Every number comes from insights/; this only lays it out.
 */
@Composable
fun SpendHero(
    spend: PeriodSpend?,
    range: DateRange,
    onPickPeriod: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier.fillMaxWidth()) {
        PeriodSwitcher(
            label = RangeWords.label(spend?.range ?: range),
            onClick = onPickPeriod,
        )
        Spacer(Modifier.height(4.dp))
        if (spend == null) {
            ImportingHero()
        } else {
            Text(
                text = Money.formatWhole(spend.totalPaise),
                style = AppType.hero,
                color = TextPrimary,
                maxLines = 1,
                overflow = TextOverflow.Clip,
            )
            Spacer(Modifier.height(10.dp))
            DeltaLine(spend)
        }
    }
}

/** "Last 7 days · 3–9 Nov ⌄": text button that opens the date sheet. */
@Composable
private fun PeriodSwitcher(label: String, onClick: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .heightIn(min = Spacing.circle)
            .clip(PillShape)
            .clickable(role = Role.Button, onClick = onClick)
            .semantics { contentDescription = "$label. Change period" },
    ) {
        Text(label, style = AppType.tab, color = TextSecondary)
        Spacer(Modifier.width(6.dp))
        Icon(
            imageVector = com.yourteam.expensetracker.ui.theme.HugeIcons.ChevronDown,
            contentDescription = null,
            tint = TextSecondary,
            modifier = Modifier.size(22.dp),
        )
    }
}

/** First open: no total yet, so say what's happening instead of showing ₹0. */
@Composable
private fun ImportingHero() {
    Text("Importing your payments…", style = AppType.sectionTitle, color = TextPrimary)
    Spacer(Modifier.height(14.dp))
    LinearProgressIndicator(
        modifier = Modifier.width(160.dp).height(4.dp).clip(PillShape),
        color = Accent,
        trackColor = Surface,
    )
}

/** Green/red always comes with words (DESIGN.md §1): "₹412 less" / "₹412 more". */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DeltaLine(spend: PeriodSpend) {
    val last = RangeWords.since(spend.range)
    when {
        spend.totalPaise == 0L && spend.previousPaise == 0L ->
            Text(RangeWords.nothingSpent(spend.range), style = AppType.body, color = TextSecondary)
        spend.changePaise == 0L ->
            Text("Same as $last", style = AppType.body, color = TextSecondary)
        else -> Text(
            // "₹412 less than last week": green/red always comes with the words (DESIGN.md §1).
            text = androidx.compose.ui.text.buildAnnotatedString {
                withStyle(androidx.compose.ui.text.SpanStyle(color = if (spend.spentLess) Positive else Negative, fontWeight = AppType.rowTitle.fontWeight)) {
                    append("${Money.formatWhole(spend.differencePaise)} ${if (spend.spentLess) "less" else "more"}")
                }
                append(" than $last")
            },
            style = AppType.body.copy(fontSize = AppType.rowTitle.fontSize),
            color = TextSecondary,
        )
    }
}
