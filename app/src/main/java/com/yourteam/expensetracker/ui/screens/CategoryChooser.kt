package com.yourteam.expensetracker.ui.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.vector.ImageVector
import com.yourteam.expensetracker.categoriser.Categories
import com.yourteam.expensetracker.data.PaymentRepository
import com.yourteam.expensetracker.ui.feedback.ACTION_DELAY_MS
import com.yourteam.expensetracker.ui.feedback.Feedback
import com.yourteam.expensetracker.ui.theme.Accent
import com.yourteam.expensetracker.ui.theme.AppType
import com.yourteam.expensetracker.ui.theme.CategoryIcons
import com.yourteam.expensetracker.ui.theme.Negative
import com.yourteam.expensetracker.ui.theme.OnAccent
import com.yourteam.expensetracker.ui.theme.PillShape
import com.yourteam.expensetracker.ui.theme.Spacing
import com.yourteam.expensetracker.ui.theme.SurfaceRaised
import com.yourteam.expensetracker.ui.theme.TextPrimary
import com.yourteam.expensetracker.ui.theme.TextSecondary
import com.yourteam.expensetracker.ui.theme.TileShape
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private enum class ChooserPage { MAIN, ALL, NEW }

/**
 * "What was this payment for?" (Feature 1 §3.4): the top 3 suggestions as one-tap rows, then
 * View all categories (with search) and Add a new custom category. When a named payment already
 * has a category, "All payments to X" (default, §3.5) or "Just this one" picks what changes.
 * Money received offers income categories only (§3.7).
 *
 * Tapping a category shows the 1.2 s loading step (FEEDBACK.md §2a), then calls [onSave].
 */
@Composable
fun CategoryChooser(
    row: PaymentRowModel,
    onSubPageChange: (Boolean) -> Unit = {},
    onSave: (category: String, onlyThis: Boolean) -> Unit,
) {
    val repo = PaymentRepository.get(LocalContext.current)
    val snapshot by repo.snapshot.collectAsState()
    val custom = snapshot?.customCategories.orEmpty()
    val income = !row.debited
    val all = if (income) Categories.income else snapshot?.expenseChoices ?: Categories.expense
    val suggestions = row.suggestions.ifEmpty { all.take(3) }.take(3)

    var page by remember(row.key) { mutableStateOf(ChooserPage.MAIN) }
    var onlyThis by remember(row.key) { mutableStateOf(false) }
    var saving by remember(row.key) { mutableStateOf<String?>(null) }
    var newName by remember(row.key) { mutableStateOf("") }
    val scope = rememberCoroutineScope()

    fun save(category: String) {
        if (saving != null) return
        saving = category
        scope.launch { delay(ACTION_DELAY_MS); onSave(category, onlyThis) }
    }

    // Let the host sheet drop its own title while a sub-page shows its own header, so there's one title.
    LaunchedEffect(page) { onSubPageChange(page != ChooserPage.MAIN) }

    AnimatedContent(
        targetState = page,
        transitionSpec = {
            val dir = if (targetState.ordinal > initialState.ordinal) 1 else -1
            (slideInHorizontally { it * dir / 4 } + fadeIn()).togetherWith(slideOutHorizontally { -it * dir / 4 } + fadeOut())
                .using(SizeTransform(clip = false))
        },
        label = "category-chooser",
    ) { current ->
        Column(Modifier.fillMaxWidth()) {
            when (current) {
                ChooserPage.MAIN -> {
                    if (row.named && row.category != null) {
                        ScopePills(row.payee, onlyThis) { onlyThis = it; Feedback.tick() }
                        Spacer(Modifier.height(16.dp))
                    }
                    Label("Suggested categories")
                    suggestions.forEachIndexed { i, c ->
                        ChoiceRow(c, icon = CategoryIcons.forCategory(c), current = c == row.category, loading = saving == c, enabled = saving == null) { save(c) }
                        Spacer(Modifier.height(Spacing.cardGap))
                    }
                    Spacer(Modifier.height(8.dp))
                    Label("More options")
                    LinkRow("View all categories", com.yourteam.expensetracker.ui.theme.HugeIcons.ChevronRight, enabled = saving == null) { page = ChooserPage.ALL }
                    if (!income) LinkRow("Add a new custom category", com.yourteam.expensetracker.ui.theme.HugeIcons.Add, enabled = saving == null) { newName = ""; page = ChooserPage.NEW }
                    if (row.named && !income && !onlyThis) {
                        Spacer(Modifier.height(8.dp))
                        Text("We'll use it for future payments to ${row.payee} too.", style = AppType.body, color = TextSecondary)
                    }
                }

                ChooserPage.ALL -> {
                    PageHeader("All categories") { page = ChooserPage.MAIN }
                    var query by remember { mutableStateOf("") }
                    SearchField(query, placeholder = "Search categories") { query = it }
                    Spacer(Modifier.height(12.dp))
                    val q = query.trim()
                    val mine = if (income) emptyList() else custom.map { it.name }.filter { it.contains(q, ignoreCase = true) }
                    val predefined = all.filter { it !in mine && it !in custom.map { c -> c.name } && it.contains(q, ignoreCase = true) }
                    LazyColumn(Modifier.heightIn(max = 420.dp), verticalArrangement = Arrangement.spacedBy(Spacing.cardGap)) {
                        if (mine.isNotEmpty()) {
                            item { Label("Your categories") }
                            items(mine, key = { "mine-$it" }) { c ->
                                val parent = custom.firstOrNull { it.name == c }?.parent
                                val icon = CategoryIcons.forCategory(parent ?: c)
                                ChoiceRow(c, icon = icon, subtitle = parent?.let { "In $it" }, current = c == row.category, loading = saving == c, enabled = saving == null) { save(c) }
                            }
                        }
                        if (predefined.isNotEmpty()) {
                            item { Label(if (income) "Income categories" else "Categories") }
                            items(predefined, key = { "all-$it" }) { c ->
                                ChoiceRow(c, icon = CategoryIcons.forCategory(c), current = c == row.category, loading = saving == c, enabled = saving == null) { save(c) }
                            }
                        }
                        if (mine.isEmpty() && predefined.isEmpty()) item {
                            Text("No category called “$q”.", style = AppType.body, color = TextSecondary, modifier = Modifier.padding(vertical = 8.dp))
                        }
                        if (!income && q.isNotEmpty() && (custom.map { it.name } + all).none { it.equals(q, ignoreCase = true) }) item {
                            LinkRow("Add “${Categories.cleanName(q)}” as a new category", com.yourteam.expensetracker.ui.theme.HugeIcons.Add, enabled = saving == null) {
                                newName = q; page = ChooserPage.NEW
                            }
                        }
                    }
                }

                ChooserPage.NEW -> NewCategoryForm(
                    initial = newName,
                    busy = saving != null,
                    onBack = { page = ChooserPage.MAIN },
                    onCreate = { name, parent ->
                        // Inline error if the name is taken (FEEDBACK.md principle 4); otherwise use it straight away.
                        val clean = Categories.cleanName(name)
                        if (clean != null && repo.addCustomCategory(clean, parent) != null) { save(clean); true } else false
                    },
                )
            }
        }
    }
}

/** "All payments to X" | "Just this one" (Feature 1 §3.5). */
@Composable
private fun ScopePills(payee: String, onlyThis: Boolean, onChange: (Boolean) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        listOf(false to "All payments to $payee", true to "Just this one").forEach { (value, label) ->
            val on = onlyThis == value
            Text(
                label,
                style = AppType.tab,
                color = if (on) OnAccent else TextPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .weight(1f, fill = false)
                    .heightIn(min = Spacing.circle)
                    .clip(PillShape)
                    .background(if (on) Accent else SurfaceRaised)
                    .clickable(role = Role.RadioButton) { if (!on) onChange(value) }
                    .padding(horizontal = 16.dp, vertical = 12.dp),
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun NewCategoryForm(
    initial: String,
    busy: Boolean,
    onBack: () -> Unit,
    onCreate: (name: String, parent: String?) -> Boolean,
) {
    var name by remember { mutableStateOf(initial) }
    var parent by remember { mutableStateOf<String?>(null) }
    var taken by remember { mutableStateOf(false) }
    val canSave = Categories.cleanName(name) != null && !busy
    fun submit() { if (canSave) taken = !onCreate(name, parent) }

    PageHeader("New category", onBack)
    Label("Name")
    val focus = remember { FocusRequester() }
    InputBox {
        if (name.isEmpty()) Text("e.g. Coffee & Snacks, Gym", style = AppType.body, color = TextSecondary)
        BasicTextField(
            value = name,
            onValueChange = { name = it.take(30); taken = false },
            singleLine = true,
            textStyle = AppType.body.copy(color = TextPrimary),
            cursorBrush = SolidColor(Accent),
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { submit() }),
            modifier = Modifier.fillMaxWidth().focusRequester(focus),
        )
    }
    LaunchedEffect(Unit) { focus.requestFocus() }
    if (taken) {
        Spacer(Modifier.height(6.dp))
        Text("That name is already used. Try another.", style = AppType.body, color = Negative)
    }
    Spacer(Modifier.height(16.dp))
    Label("Group under (optional)")
    Text("Its spending also counts towards this category in totals.", style = AppType.body, color = TextSecondary)
    Spacer(Modifier.height(8.dp))
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        (Categories.expense - Categories.OTHERS).forEach { c ->
            val on = parent == c
            Text(
                c,
                style = AppType.tab,
                color = if (on) OnAccent else TextPrimary,
                modifier = Modifier
                    .heightIn(min = Spacing.circle)
                    .clip(PillShape)
                    .background(if (on) Accent else SurfaceRaised)
                    .clickable(role = Role.RadioButton) { parent = if (on) null else c; Feedback.tick() }
                    .padding(horizontal = 16.dp, vertical = 12.dp),
            )
        }
    }
    Spacer(Modifier.height(20.dp))
    Box(
        Modifier
            .fillMaxWidth()
            .height(Spacing.button)
            .clip(PillShape)
            .background(if (canSave || busy) Accent else SurfaceRaised)
            .clickable(enabled = canSave, role = Role.Button) { submit() }
            .semantics { if (busy) stateDescription = "Saving" },
        contentAlignment = Alignment.Center,
    ) {
        if (busy) CircularProgressIndicator(color = OnAccent, strokeWidth = 2.dp, modifier = Modifier.size(20.dp))
        else Text("Save and use", style = AppType.tab, color = if (canSave) OnAccent else TextSecondary)
    }
}

@Composable
internal fun PageHeader(title: String, onBack: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(bottom = 12.dp)) {
        Box(
            Modifier.size(Spacing.circle).clip(PillShape).background(SurfaceRaised).clickable(role = Role.Button, onClick = onBack),
            contentAlignment = Alignment.Center,
        ) { Icon(com.yourteam.expensetracker.ui.theme.HugeIcons.ChevronLeft, contentDescription = "Back", tint = TextPrimary, modifier = Modifier.size(22.dp)) }
        Spacer(Modifier.width(14.dp))
        Text(title, style = AppType.sectionTitle, color = TextPrimary)
    }
}

@Composable
internal fun Label(text: String) {
    Text(text, style = AppType.chip, color = TextSecondary, modifier = Modifier.padding(bottom = 8.dp))
}

@Composable
internal fun InputBox(content: @Composable () -> Unit) {
    Box(
        Modifier.fillMaxWidth().heightIn(min = Spacing.button).clip(PillShape).background(SurfaceRaised).padding(horizontal = 18.dp),
        contentAlignment = Alignment.CenterStart,
    ) { content() }
}

@Composable
internal fun SearchField(value: String, placeholder: String, onChange: (String) -> Unit) {
    InputBox {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(com.yourteam.expensetracker.ui.theme.HugeIcons.Search, contentDescription = null, tint = TextSecondary, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(10.dp))
            Box(contentAlignment = Alignment.CenterStart) {
                if (value.isEmpty()) Text(placeholder, style = AppType.body, color = TextSecondary)
                BasicTextField(
                    value = value,
                    onValueChange = onChange,
                    singleLine = true,
                    textStyle = AppType.body.copy(color = TextPrimary),
                    cursorBrush = SolidColor(Accent),
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Search),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

/** One category as a tappable row; the trailing spot shows "Current" or the loading spinner. */
@Composable
internal fun ChoiceRow(
    text: String,
    subtitle: String? = null,
    icon: ImageVector? = null,
    current: Boolean = false,
    loading: Boolean = false,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = Spacing.button)
            .clip(TileShape)
            .background(SurfaceRaised)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .semantics { if (loading) stateDescription = "Saving" }
            .padding(horizontal = 18.dp, vertical = 12.dp),
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, tint = TextPrimary, modifier = Modifier.size(24.dp))
            Spacer(Modifier.width(14.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(text, style = AppType.rowTitle, color = TextPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
            subtitle?.let { Text(it, style = AppType.body, color = TextSecondary) }
        }
        when {
            loading -> CircularProgressIndicator(color = Accent, strokeWidth = 2.dp, modifier = Modifier.size(20.dp))
            current -> Text("Current", style = AppType.chip, color = TextSecondary)
        }
    }
}

@Composable
private fun LinkRow(text: String, icon: androidx.compose.ui.graphics.vector.ImageVector, enabled: Boolean, onClick: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = Spacing.circle)
            .clip(TileShape)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(horizontal = 4.dp, vertical = 10.dp),
    ) {
        Text(text, style = AppType.tab, color = Accent, modifier = Modifier.weight(1f))
        Icon(icon, contentDescription = null, tint = Accent, modifier = Modifier.size(20.dp))
    }
}
