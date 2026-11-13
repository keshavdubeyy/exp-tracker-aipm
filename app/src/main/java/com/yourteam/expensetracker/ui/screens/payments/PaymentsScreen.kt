package com.yourteam.expensetracker.ui.screens.payments

import com.yourteam.expensetracker.ui.feedback.PaymentActions
import android.app.Application
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.yourteam.expensetracker.data.PaymentRepository
import com.yourteam.expensetracker.data.PaymentSnapshot
import com.yourteam.expensetracker.ui.screens.CategorySheet
import com.yourteam.expensetracker.ui.screens.PaymentCard
import com.yourteam.expensetracker.ui.screens.PaymentSheet
import com.yourteam.expensetracker.ui.screens.NeedsCheckSheet
import com.yourteam.expensetracker.ui.screens.PaymentRowModel
import com.yourteam.expensetracker.ui.screens.SubScreenHeader
import com.yourteam.expensetracker.ui.screens.paymentRow
import com.yourteam.expensetracker.ui.screens.receivedRow
import com.yourteam.expensetracker.ui.theme.Accent
import com.yourteam.expensetracker.ui.theme.AppType
import com.yourteam.expensetracker.ui.theme.Bg
import com.yourteam.expensetracker.ui.theme.OnAccent
import com.yourteam.expensetracker.ui.theme.PillShape
import com.yourteam.expensetracker.ui.theme.Spacing
import com.yourteam.expensetracker.ui.theme.Surface
import com.yourteam.expensetracker.ui.theme.TextPrimary
import com.yourteam.expensetracker.ui.theme.TextSecondary
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

class PaymentsViewModel(app: Application) : AndroidViewModel(app) {
    private val repo = PaymentRepository.get(app).also { it.ensureLoaded() }
    /** Money out and money in, newest first. Money in is shown only; it's never added to spending. */
    private fun rows(s: PaymentSnapshot?) = s?.let { snap ->
        (snap.payments.map { paymentRow(it, snap) } + snap.received.map { receivedRow(it, snap) }).sortedByDescending { it.at }
    }.orEmpty()
    val rows: StateFlow<List<PaymentRowModel>> = repo.snapshot.map(::rows)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), rows(repo.snapshot.value))

    fun answer(id: Long, yes: Boolean) = PaymentActions.answer(repo, id, yes)

    fun setCategory(key: String, category: String, onlyThis: Boolean = false) {
        PaymentActions.categorise(repo, key, category, onlyThis)
    }
}

/** Quick filters. One per group can be on at a time. */
private enum class Filter(val label: String, val group: Int, val test: (PaymentRowModel) -> Boolean) {
    NeedsCheck("Needs check", 0, { it.askId != null || it.conflictId != null }),
    NoCategory("No category", 0, { it.debited && it.category == null }),
    Received("Received", 0, { !it.debited }),
    Under100("Under ₹100", 1, { it.paise < 100_00 }),
    Mid("₹100–₹1,000", 1, { it.paise in 100_00..1_000_00 }),
    Over1000("Over ₹1,000", 1, { it.paise > 1_000_00 }),
}

/** Every payment in and out, grouped by day, with search, filters and "Add categories to N payments". */
@Composable
fun PaymentsScreen(
    onBack: () -> Unit,
    onOpenPayment: (String) -> Unit,
    /** Opened from Home's received row: start with the "Received" filter on. */
    onlyReceived: Boolean = false,
    viewModel: PaymentsViewModel = viewModel(),
) {
    val rows by viewModel.rows.collectAsStateWithLifecycle()
    var query by rememberSaveable { mutableStateOf("") }
    var filters by rememberSaveable { mutableStateOf(if (onlyReceived) setOf(Filter.Received.name) else setOf()) }
    var category by rememberSaveable { mutableStateOf<String?>(null) }
    var tagging by remember { mutableStateOf<List<PaymentRowModel>?>(null) }
    var opened by rememberSaveable { mutableStateOf<String?>(null) }

    var checking by rememberSaveable { mutableStateOf<String?>(null) }
    rows.firstOrNull { it.key == checking }?.let { row ->
        NeedsCheckSheet(row, onAnswer = viewModel::answer, onDismiss = { checking = null })
    }

    rows.firstOrNull { it.key == opened }?.let { row ->
        PaymentSheet(
            row = row,
            onPickCategory = { c, onlyThis -> viewModel.setCategory(row.key, c, onlyThis) },
            onAnswer = viewModel::answer,
            onOpenDetails = { onOpenPayment(row.key) },
            onDismiss = { opened = null },
        )
    }

    tagging?.let { queue ->
        CategorySheet(queue, onPick = { r, c, onlyThis -> viewModel.setCategory(r.key, c, onlyThis) }, onDismiss = { tagging = null })
    }

    val active = Filter.entries.filter { it.name in filters }
    val shown = rows.filter { r ->
        matchesQuery(r, query) && active.all { it.test(r) } && (category == null || r.category == category)
    }
    val untagged = rows.filter { it.debited && it.category == null }
        .distinctBy { if (it.named) "payee:" + it.payee.lowercase() else it.key } // one pick covers a payee
    val categories = rows.mapNotNull { it.category }.distinct().sorted()

    Box(Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = Spacing.screen, end = Spacing.screen, bottom = 120.dp),
        ) {
            item { SubScreenHeader("Payments", onBack) }
            item {
                Spacer(Modifier.height(8.dp))
                SearchField(query, onChange = { query = it })
                Spacer(Modifier.height(10.dp))
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(Filter.entries, key = { it.name }) { f ->
                        FilterChip(f.label, selected = f.name in filters) {
                            filters = if (f.name in filters) filters - f.name
                            else filters.filterNot { n -> Filter.valueOf(n).group == f.group }.toSet() + f.name
                        }
                    }
                    items(categories, key = { "cat-$it" }) { c ->
                        FilterChip(c, selected = category == c) { category = if (category == c) null else c }
                    }
                }
            }
            if (shown.isEmpty()) {
                item {
                    Text(
                        if (rows.isEmpty()) "No payments yet." else "No payments match.",
                        style = AppType.body,
                        color = TextSecondary,
                        modifier = Modifier.padding(vertical = 24.dp),
                    )
                }
            }
            shown.groupBy { dayOf(it.at) }.forEach { (day, list) ->
                item(key = "day-$day") {
                    Text(
                        dayLabel(day),
                        style = AppType.tab,
                        color = TextSecondary,
                        modifier = Modifier.padding(top = Spacing.section, bottom = 10.dp),
                    )
                }
                items(list, key = { it.key }) { r ->
                    PaymentCard(
                        r,
                        onClick = { opened = r.key },
                        onCheck = { checking = r.key },
                        onAddCategory = { tagging = listOf(r) },
                        groupedByDay = true,
                    )
                    Spacer(Modifier.height(Spacing.cardGap))
                }
            }
            if (shown.any { it.aiPicked || it.fairlySure }) {
                item {
                    Text(
                        "✦ means AI picked it. Tap any payment to see why or change.",
                        style = AppType.chip,
                        color = TextSecondary,
                        modifier = Modifier.padding(top = Spacing.section, bottom = 8.dp),
                    )
                }
            }
        }

        if (untagged.isNotEmpty()) {
            Text(
                "Add categories to ${untagged.size} payment${if (untagged.size == 1) "" else "s"}",
                style = AppType.tab,
                color = OnAccent,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .background(Bg)
                    .padding(start = Spacing.screen, end = Spacing.screen, top = 8.dp, bottom = 16.dp)
                    .fillMaxWidth()
                    .heightIn(min = Spacing.button)
                    .clip(PillShape)
                    .background(Accent)
                    .clickable(role = Role.Button) { tagging = untagged }
                    .padding(vertical = 14.dp),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            )
        }
    }
}

/** Name, category, or amount ("420" finds ₹420 and ₹1,420). */
private fun matchesQuery(r: PaymentRowModel, query: String): Boolean {
    val q = query.trim().lowercase()
    if (q.isEmpty()) return true
    val digits = q.filter(Char::isDigit)
    return r.payee.lowercase().contains(q) ||
        r.category?.lowercase()?.contains(q) == true ||
        (digits.isNotEmpty() && digits == q.filterNot { it == '₹' || it == ',' || it == ' ' } && r.amount.filter(Char::isDigit).contains(digits))
}

@Composable
private fun SearchField(value: String, onChange: (String) -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).clip(PillShape).background(Surface).padding(horizontal = 16.dp),
    ) {
        Icon(com.yourteam.expensetracker.ui.theme.HugeIcons.Search, contentDescription = null, tint = TextSecondary, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(10.dp))
        Box(Modifier.weight(1f)) {
            if (value.isEmpty()) Text("Search name, category or amount", style = AppType.body, color = TextSecondary)
            BasicTextField(
                value = value,
                onValueChange = onChange,
                singleLine = true,
                textStyle = AppType.body.copy(color = TextPrimary),
                cursorBrush = SolidColor(Accent),
                modifier = Modifier.fillMaxWidth(),
            )
        }
        if (value.isNotEmpty()) {
            Icon(
                com.yourteam.expensetracker.ui.theme.HugeIcons.Close,
                contentDescription = "Clear search",
                tint = TextSecondary,
                modifier = Modifier.size(32.dp).clip(PillShape).clickable { onChange("") }.padding(6.dp),
            )
        }
    }
}

@Composable
private fun FilterChip(text: String, selected: Boolean, onClick: () -> Unit) {
    Text(
        text,
        style = AppType.chip,
        color = if (selected) OnAccent else TextPrimary,
        modifier = Modifier
            .heightIn(min = 36.dp)
            .clip(PillShape)
            .background(if (selected) Accent else Surface)
            .clickable(role = Role.Checkbox, onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 10.dp),
    )
}

private fun dayOf(at: Long): LocalDate = Instant.ofEpochMilli(at).atZone(ZoneId.systemDefault()).toLocalDate()

private val dayFormat = DateTimeFormatter.ofPattern("EEE d MMM")

private fun dayLabel(d: LocalDate): String {
    val today = LocalDate.now()
    return when (d) {
        today -> "Today"
        today.minusDays(1) -> "Yesterday"
        else -> dayFormat.format(d)
    }
}
