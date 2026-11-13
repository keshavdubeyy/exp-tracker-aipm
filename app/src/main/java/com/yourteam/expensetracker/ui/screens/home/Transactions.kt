package com.yourteam.expensetracker.ui.screens.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.yourteam.expensetracker.ui.feedback.Feedback
import com.yourteam.expensetracker.ui.screens.PaymentRowModel
import com.yourteam.expensetracker.ui.screens.budgets.CapsLabel
import com.yourteam.expensetracker.ui.screens.budgets.RowsCard
import com.yourteam.expensetracker.ui.theme.Accent
import com.yourteam.expensetracker.ui.theme.AppType
import com.yourteam.expensetracker.ui.theme.Negative
import com.yourteam.expensetracker.ui.theme.PillShape
import com.yourteam.expensetracker.ui.theme.CategoryIcons
import com.yourteam.expensetracker.ui.theme.Spacing
import com.yourteam.expensetracker.ui.theme.Surface
import com.yourteam.expensetracker.ui.theme.SurfaceRaised
import com.yourteam.expensetracker.ui.theme.TextPrimary
import com.yourteam.expensetracker.ui.theme.TextSecondary
import com.yourteam.expensetracker.ui.theme.Warning
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private const val NEEDS_REVIEW = "Needs review"

/**
 * Home's "Transactions" section: money out and money in, newest first, in one card.
 * "All" lists them; "By category" groups the same rows under each category (no totals here:
 * totals live in insights/). Taps open the payment sheet; chips open the check / category sheets.
 */
@Composable
internal fun TransactionsSection(
    rows: List<PaymentRowModel>,
    onOpen: (PaymentRowModel) -> Unit,
    onCheck: (PaymentRowModel) -> Unit,
    onTag: (PaymentRowModel) -> Unit,
    onSeeAll: () -> Unit,
    modifier: Modifier = Modifier,
    empty: @Composable () -> Unit = {},
) {
    var byCategory by rememberSaveable { mutableStateOf(false) }
    Column(modifier.fillMaxWidth()) {
        Text(
            "Transactions",
            style = AppType.sectionTitle,
            color = TextPrimary,
            modifier = Modifier.clip(PillShape).clickable(role = Role.Button, onClick = onSeeAll),
        )
        Spacer(Modifier.height(Spacing.cardGap))
        ViewToggle(byCategory) {
            if (it != byCategory) Feedback.tick()
            byCategory = it
        }
        Spacer(Modifier.height(Spacing.cardGap + 4.dp))
        when {
            rows.isEmpty() -> empty()
            !byCategory -> RowsCard(rows = rows.map { r -> { TransactionRow(r, onOpen, onCheck, onTag) } })
            else -> rows.groupBy(::groupOf).toList()
                .sortedBy { (g, _) -> if (g == NEEDS_REVIEW) 0 else 1 }
                .forEachIndexed { i, (group, list) ->
                    if (i > 0) Spacer(Modifier.height(16.dp))
                    CapsLabel("$group · ${list.size}")
                    RowsCard(rows = list.map { r -> { TransactionRow(r, onOpen, onCheck, onTag) } })
                }
        }
        if (rows.isNotEmpty()) {
            Box(Modifier.fillMaxWidth().padding(top = 8.dp), contentAlignment = Alignment.Center) {
                Text(
                    "See all transactions",
                    style = AppType.tab,
                    color = Accent,
                    modifier = Modifier
                        .heightIn(min = Spacing.circle)
                        .clip(PillShape)
                        .clickable(role = Role.Button, onClick = onSeeAll)
                        .padding(horizontal = 16.dp, vertical = 13.dp),
                )
            }
        }
    }
}

private fun groupOf(r: PaymentRowModel): String = r.category ?: NEEDS_REVIEW

/** Full-width "All transactions | By category" segmented pill, two equal halves. */
@Composable
private fun ViewToggle(byCategory: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().clip(PillShape).background(Surface).padding(4.dp)) {
        listOf(false to "All transactions", true to "By category").forEach { (value, label) ->
            val selected = value == byCategory
            Box(
                Modifier
                    .weight(1f)
                    .heightIn(min = 44.dp)
                    .clip(PillShape)
                    .background(if (selected) SurfaceRaised else Surface)
                    .selectable(selected = selected, role = Role.Tab, onClick = { onChange(value) }),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    label,
                    style = AppType.tab,
                    color = if (selected) TextPrimary else TextSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(horizontal = 12.dp),
                )
            }
        }
    }
}

private val dayFormat = DateTimeFormatter.ofPattern("EEE, d MMM")
private val timeFormat = DateTimeFormatter.ofPattern("h:mm a")

/** "Fri, 13 Nov · Just now" / "Thu, 12 Nov · 5:50 PM". */
private fun whenWords(at: Long, now: Long = System.currentTimeMillis()): String {
    val t = Instant.ofEpochMilli(at).atZone(ZoneId.systemDefault())
    val mins = (now - at) / 60_000
    val time = when {
        t.toLocalDate() == LocalDate.now() && mins < 1 -> "Just now"
        t.toLocalDate() == LocalDate.now() && mins < 60 -> "${mins}m ago"
        else -> timeFormat.format(t)
    }
    return "${dayFormat.format(t)} · $time"
}

@Composable
private fun TransactionRow(
    row: PaymentRowModel,
    onOpen: (PaymentRowModel) -> Unit,
    onCheck: (PaymentRowModel) -> Unit,
    onTag: (PaymentRowModel) -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(role = Role.Button, onClick = { onOpen(row) })
            .padding(horizontal = 16.dp, vertical = 14.dp),
    ) {
        Box(
            Modifier.size(Spacing.circle).background(SurfaceRaised, RoundedCornerShape(14.dp)),
            contentAlignment = Alignment.Center,
        ) {
            // The category's outline icon; the tag icon while it still needs a category.
            Icon(
                row.category?.let(CategoryIcons::forCategory) ?: CategoryIcons.tag,
                contentDescription = null,
                tint = if (row.category != null) TextPrimary else TextSecondary,
                modifier = Modifier.size(22.dp),
            )
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(row.payee, style = AppType.rowTitle, color = TextPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(2.dp))
            Text(whenWords(row.at), style = AppType.body, color = TextSecondary, maxLines = 1)
        }
        Spacer(Modifier.width(12.dp))
        Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                if (row.debited) "−${row.amount}" else "+${row.amount}",
                style = AppType.rowTitle,
                color = TextPrimary,
            )
            when {
                row.askId != null || row.conflictId != null -> RowChip("Needs check", Negative) { onCheck(row) }
                row.category == null -> RowChip(NEEDS_REVIEW, Warning) { onTag(row) }
                // ✦ = the AI picked it (not the user or a known merchant); tap the row to change it.
                else -> RowChip(if (row.debited && row.basis == com.yourteam.expensetracker.categoriser.Basis.AI) "✦ ${row.category}" else row.category, TextPrimary, onClick = null)
            }
        }
    }
}

@Composable
private fun RowChip(text: String, ink: Color, onClick: (() -> Unit)?) {
    Text(
        text,
        style = AppType.chip,
        color = ink,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier
            .widthIn(max = 104.dp)
            .clip(PillShape)
            .background(SurfaceRaised)
            .then(if (onClick != null) Modifier.clickable(role = Role.Button, onClick = onClick) else Modifier)
            .padding(horizontal = 10.dp, vertical = 4.dp),
    )
}
