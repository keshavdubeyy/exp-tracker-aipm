package com.yourteam.expensetracker.ui.screens.insights

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.yourteam.expensetracker.insights.Money
import com.yourteam.expensetracker.insights.PayeeTotal
import com.yourteam.expensetracker.insights.Spending
import com.yourteam.expensetracker.insights.SpendingSummary
import com.yourteam.expensetracker.ui.screens.PageHeader
import com.yourteam.expensetracker.ui.screens.SectionLabel
import com.yourteam.expensetracker.ui.theme.AppCard
import com.yourteam.expensetracker.ui.theme.Border
import com.yourteam.expensetracker.ui.theme.Heading
import com.yourteam.expensetracker.ui.theme.PillShape
import com.yourteam.expensetracker.ui.theme.SoftInk
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

@Composable
fun InsightsScreen(
    modifier: Modifier = Modifier,
    onOpenReview: () -> Unit = {},
    viewModel: InsightsViewModel = viewModel(),
) {
    val summary by viewModel.summary.collectAsStateWithLifecycle()
    val s = summary
    if (s == null) {
        Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        return
    }
    val monthName = s.month.month.getDisplayName(TextStyle.FULL, Locale.getDefault())

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 24.dp),
        verticalArrangement = Arrangement.spacedBy(7.dp),
    ) {
        item {
            PageHeader(
                first = "${Money.format(s.totalPaise)} spent",
                second = "in $monthName.",
                caption = "${s.paymentCount} payments · about ${Money.format(s.averagePerDayPaise)} a day",
            )
        }

        if (s.unconfirmedCount > 0) {
            item { UnconfirmedCard(s, onOpenReview) }
        }

        item {
            Spacer(Modifier.height(24.dp))
            SectionLabel("Last 7 days")
        }
        item { WeekBars(s) }

        item {
            Spacer(Modifier.height(24.dp))
            SectionLabel("Where it went this month")
        }
        if (s.topPayees.isEmpty()) {
            item {
                Text(
                    "No payments yet this month.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 16.dp),
                )
            }
        }
        items(s.topPayees, key = { it.payee }) { PayeeRow(it) }
        item {
            Text(
                "Totals count payments recorded by you, GPay or a bank SMS. Each payment is counted once.",
                style = MaterialTheme.typography.bodySmall,
                color = SoftInk,
                modifier = Modifier.padding(horizontal = 4.dp, vertical = 16.dp),
            )
        }
    }
}

/** Pale-gray shell: payments still waiting for "Yes / No", kept out of the total. */
@Composable
private fun UnconfirmedCard(s: SpendingSummary, onOpenReview: () -> Unit) {
    AppCard(Modifier.fillMaxWidth()) {
        Text("Not counted yet", style = MaterialTheme.typography.labelMedium)
        Spacer(Modifier.height(20.dp))
        Heading(Money.format(s.unconfirmedPaise), style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(8.dp))
        Text(
            "${s.unconfirmedCount} ${if (s.unconfirmedCount == 1) "payment is" else "payments are"} waiting for " +
                "your answer. Confirm them to include them in the total.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(20.dp))
        Button(onClick = onOpenReview, shape = PillShape, modifier = Modifier.height(40.dp)) {
            Text("Review", style = MaterialTheme.typography.labelLarge)
        }
    }
}

/** Seven near-black bars, today last. Heights come from [Spending.barFractions]. */
@Composable
private fun WeekBars(s: SpendingSummary) {
    val fractions = Spending.barFractions(s.lastSevenDays)
    val today = LocalDate.now()
    Column(Modifier.padding(horizontal = 4.dp, vertical = 8.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth().height(140.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.Bottom,
        ) {
            s.lastSevenDays.forEachIndexed { i, day ->
                Box(Modifier.weight(1f).fillMaxHeight(), contentAlignment = Alignment.BottomCenter) {
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .fillMaxHeight(fractions[i].coerceAtLeast(0.02f))
                            .background(
                                if (day.date == today) MaterialTheme.colorScheme.onBackground else Border,
                                RoundedCornerShape(7.dp),
                            ),
                    )
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            s.lastSevenDays.forEach { day ->
                Text(
                    day.date.dayOfWeek.getDisplayName(TextStyle.NARROW, Locale.getDefault()),
                    style = MaterialTheme.typography.labelSmall,
                    color = if (day.date == today) MaterialTheme.colorScheme.onBackground else SoftInk,
                    modifier = Modifier.weight(1f),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                )
            }
        }
        Spacer(Modifier.height(12.dp))
        val todayTotal = s.lastSevenDays.last().totalPaise
        Text(
            "Today ${Money.format(todayTotal)} · ${s.lastSevenDays.first().date.format(SHORT)} – ${today.format(SHORT)}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** Ruled row: payee and count on the left, amount on the right. */
@Composable
private fun PayeeRow(p: PayeeTotal) {
    Column {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(p.payee, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    "${p.count} ${if (p.count == 1) "payment" else "payments"}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(Money.format(p.totalPaise), style = MaterialTheme.typography.titleMedium)
        }
        HorizontalDivider(thickness = 1.dp, color = Border)
    }
}

private val SHORT = DateTimeFormatter.ofPattern("d MMM")
