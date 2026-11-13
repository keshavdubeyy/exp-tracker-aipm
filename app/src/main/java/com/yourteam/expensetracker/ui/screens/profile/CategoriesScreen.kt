package com.yourteam.expensetracker.ui.screens.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
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
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.yourteam.expensetracker.categoriser.Categories
import com.yourteam.expensetracker.categoriser.CustomCategory
import com.yourteam.expensetracker.data.PaymentRepository
import com.yourteam.expensetracker.ui.feedback.Feedback
import com.yourteam.expensetracker.ui.feedback.PaymentActions
import com.yourteam.expensetracker.ui.screens.InputBox
import com.yourteam.expensetracker.ui.screens.SubScreenHeader
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
import kotlinx.coroutines.launch

/**
 * Profile › Categories (Feature 1 §3.6): the user's own categories (add, rename, change the
 * group, delete with Undo) and the built-in expense and income lists for reference.
 */
@Composable
fun CategoriesScreen(onBack: () -> Unit) {
    val repo = PaymentRepository.get(LocalContext.current)
    val snapshot by repo.snapshot.collectAsStateWithLifecycle()
    val custom = snapshot?.customCategories.orEmpty()
    var editing by remember { mutableStateOf<CustomCategory?>(null) }
    var adding by remember { mutableStateOf(false) }

    if (adding) CategoryEditSheet(null, onDismiss = { adding = false }) { name, parent ->
        PaymentActions.addCustomCategory(repo, name, parent)
    }
    editing?.let { c ->
        CategoryEditSheet(c, onDismiss = { editing = null }, onDelete = { PaymentActions.deleteCustomCategory(repo, c.name) }) { name, parent ->
            PaymentActions.editCustomCategory(repo, c.name, name, parent)
        }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = Spacing.screen, end = Spacing.screen, bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(Spacing.cardGap),
    ) {
        item { SubScreenHeader("Categories", onBack); Spacer(Modifier.height(Spacing.section - Spacing.cardGap)) }
        item { SectionLabel("Your categories") }
        if (custom.isEmpty()) item {
            Text("None yet. Make one for things like Coffee & Snacks or Gym.", style = AppType.body, color = TextSecondary)
        }
        items(custom, key = { "c-${it.name}" }) { c ->
            CategoryRow(c.name, c.parent?.let { "In $it" } ?: "No group") { editing = c }
        }
        item {
            Text(
                "Add a new category",
                style = AppType.tab,
                color = Accent,
                modifier = Modifier.heightIn(min = Spacing.circle).clip(PillShape).clickable(role = Role.Button) { adding = true }.padding(vertical = 12.dp),
            )
            Spacer(Modifier.height(Spacing.section - Spacing.cardGap))
            SectionLabel("Built-in")
            Text("Spending: " + Categories.expense.joinToString(" · "), style = AppType.body, color = TextSecondary)
            Spacer(Modifier.height(8.dp))
            Text("Money received: " + Categories.income.joinToString(" · "), style = AppType.body, color = TextSecondary)
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(text, style = AppType.tab, color = TextSecondary, modifier = Modifier.padding(bottom = 2.dp))
}

@Composable
private fun CategoryRow(title: String, subtitle: String, onClick: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clip(CardShape)
            .background(Surface)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = Spacing.cardH, vertical = Spacing.cardV),
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = AppType.rowTitle, color = TextPrimary)
            Text(subtitle, style = AppType.body, color = TextSecondary)
        }
        Icon(com.yourteam.expensetracker.ui.theme.HugeIcons.ChevronRight, contentDescription = "Edit $title", tint = TextSecondary)
    }
}

/** Add ([existing] null) or edit a category. [onSave] returns false when the name is taken. */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun CategoryEditSheet(
    existing: CustomCategory?,
    onDismiss: () -> Unit,
    onDelete: (() -> Unit)? = null,
    onSave: (name: String, parent: String?) -> Boolean,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    fun close(then: () -> Unit = {}) = scope.launch { sheetState.hide() }.invokeOnCompletion { then(); onDismiss() }
    var name by remember { mutableStateOf(existing?.name.orEmpty()) }
    var parent by remember { mutableStateOf(existing?.parent) }
    var taken by remember { mutableStateOf(false) }
    val canSave = Categories.cleanName(name) != null && (existing == null || Categories.cleanName(name) != existing.name || parent != existing.parent)

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState, containerColor = Surface) {
        Column(Modifier.padding(horizontal = Spacing.screen).padding(bottom = 20.dp).navigationBarsPadding()) {
            Text(if (existing == null) "New category" else "Edit category", style = AppType.sectionTitle, color = TextPrimary)
            Spacer(Modifier.height(16.dp))
            InputBox {
                if (name.isEmpty()) Text("Name, e.g. Coffee & Snacks", style = AppType.body, color = TextSecondary)
                BasicTextField(
                    value = name,
                    onValueChange = { name = it.take(30); taken = false },
                    singleLine = true,
                    textStyle = AppType.body.copy(color = TextPrimary),
                    cursorBrush = SolidColor(Accent),
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            if (taken) Text("That name is already used. Try another.", style = AppType.body, color = Negative, modifier = Modifier.padding(top = 6.dp))
            Spacer(Modifier.height(16.dp))
            Text("Group under (optional)", style = AppType.chip, color = TextSecondary)
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
                    .background(if (canSave) Accent else SurfaceRaised)
                    .clickable(enabled = canSave, role = Role.Button) { if (onSave(name, parent)) close() else taken = true },
                contentAlignment = Alignment.Center,
            ) { Text("Save", style = AppType.tab, color = if (canSave) OnAccent else TextSecondary) }
            onDelete?.let { delete ->
                Spacer(Modifier.height(8.dp))
                Text(
                    "Delete category",
                    style = AppType.tab,
                    color = Negative,
                    modifier = Modifier.fillMaxWidth().heightIn(min = Spacing.circle).clip(PillShape)
                        .clickable(role = Role.Button) { close(delete) }.padding(vertical = 12.dp),
                )
                Text(
                    existing?.parent?.let { "Payments in it move to $it. You can undo." } ?: "Payments in it go back to “Add category”. You can undo.",
                    style = AppType.body,
                    color = TextSecondary,
                )
            }
        }
    }
}
