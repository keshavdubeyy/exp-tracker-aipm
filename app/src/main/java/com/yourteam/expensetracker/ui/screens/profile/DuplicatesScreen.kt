package com.yourteam.expensetracker.ui.screens.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.yourteam.expensetracker.categoriser.AiCategoriser
import com.yourteam.expensetracker.categoriser.Categories
import com.yourteam.expensetracker.categoriser.DuplicateGroup
import com.yourteam.expensetracker.categoriser.DuplicateKind
import com.yourteam.expensetracker.categoriser.Duplicates
import com.yourteam.expensetracker.categoriser.NameUse
import com.yourteam.expensetracker.data.PaymentRepository
import com.yourteam.expensetracker.ui.feedback.Feedback
import com.yourteam.expensetracker.ui.screens.SubScreenHeader
import com.yourteam.expensetracker.ui.theme.Accent
import com.yourteam.expensetracker.ui.theme.AppType
import com.yourteam.expensetracker.ui.theme.CardShape
import com.yourteam.expensetracker.ui.theme.OnAccent
import com.yourteam.expensetracker.ui.theme.PillShape
import com.yourteam.expensetracker.ui.theme.Spacing
import com.yourteam.expensetracker.ui.theme.Surface
import com.yourteam.expensetracker.ui.theme.SurfaceRaised
import com.yourteam.expensetracker.ui.theme.TextPrimary
import com.yourteam.expensetracker.ui.theme.TextSecondary
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** One suggestion: the group, and whether only the AI (not our rules) found it. */
private data class Suggestion(val group: DuplicateGroup, val byAi: Boolean)

private val builtIn = (Categories.expense + Categories.income).toSet()

/**
 * Duplicates: the same person or category under different names.
 *  1. We check everything (rules, then the AI) and say what we checked.
 *  2. People | Categories tabs. Each shows our suggestions first, then every name, so the user can
 *     also tick two or more themselves and merge them.
 * Nothing is merged until the user taps Merge; every merge and "Not the same" can be undone.
 */
@Composable
fun DuplicatesScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val repo = remember { PaymentRepository.get(context) }
    var reload by remember { mutableIntStateOf(0) }
    var people by remember { mutableStateOf<List<NameUse>>(emptyList()) }
    var cats by remember { mutableStateOf<List<String>>(emptyList()) }
    var uses by remember { mutableStateOf<Map<String, Int>>(emptyMap()) }
    var suggestions by remember { mutableStateOf<List<Suggestion>?>(null) }
    var aiGroups by remember { mutableStateOf<Pair<List<List<String>>, List<List<String>>>?>(null) }
    var aiStatus by remember { mutableStateOf(if (AiCategoriser.enabled) "AI check: running…" else "AI check: off") }
    var tab by remember { mutableIntStateOf(0) } // 0 people, 1 categories
    var selected by remember { mutableStateOf<Set<String>>(emptySet()) }
    var query by remember { mutableStateOf("") }
    var merging by remember { mutableStateOf<List<String>?>(null) }

    fun build(p: List<NameUse>, c: List<String>, u: Map<String, Int>, ai: Pair<List<List<String>>, List<List<String>>>?): List<Suggestion> {
        val rejected = repo.rejectedDuplicates()
        val byName = p.associateBy { it.name }
        val ruled = Duplicates.people(p, rejected) + Duplicates.categories(c, u, builtIn, rejected)
        val fromAi = ai?.let { (pg, cg) ->
            pg.mapNotNull { g -> g.mapNotNull { byName[it] }.takeIf { it.size > 1 }?.let { Duplicates.personGroup(it, "The AI thinks these are one person") } } +
                cg.mapNotNull { g -> g.filter { it in c }.takeIf { it.size > 1 }?.let { Duplicates.categoryGroup(it, u, builtIn, "The AI thinks these mean the same") } }
        }.orEmpty()
        val ruleNames = ruled.flatMap { it.members.map(String::lowercase) }.toSet()
        return Duplicates.mergeOverlapping(ruled + fromAi).filter { it.id !in rejected }
            .map { g -> Suggestion(g, byAi = g.members.none { it.lowercase() in ruleNames }) }
    }

    LaunchedEffect(reload) {
        val (p, c, u) = withContext(Dispatchers.Default) { Triple(repo.nameUses(), repo.allExpenseCategories(), repo.categoryUses()) }
        people = p; cats = c; uses = u
        suggestions = withContext(Dispatchers.Default) { build(p, c, u, aiGroups) }
        if (AiCategoriser.enabled && aiGroups == null && reload == 0) {
            val ai = withContext(Dispatchers.IO) { AiCategoriser.askDuplicates(p, c) }
            if (ai == null) aiStatus = "AI check: couldn't reach the AI, showing what our rules found"
            else {
                aiGroups = ai
                aiStatus = "AI check: done"
                suggestions = withContext(Dispatchers.Default) { build(p, c, u, ai) }
            }
        }
    }

    fun done(message: String, undo: () -> Unit) {
        selected = emptySet()
        reload++
        Feedback.success(message) { undo(); reload++ }
    }

    fun merge(kind: DuplicateKind, members: List<String>, into: String) {
        val undo = if (kind == DuplicateKind.PERSON) {
            val byName = people.associateBy { it.name }
            repo.mergePayees(members, members.flatMap { byName[it]?.vpas.orEmpty() }.toSet(), into)
        } else repo.mergeCategories(members, into)
        done("Merged into $into", undo)
    }

    val kind = if (tab == 0) DuplicateKind.PERSON else DuplicateKind.CATEGORY
    val all = suggestions.orEmpty()
    val shown = all.filter { it.group.kind == kind }
    val peopleCount = all.count { it.group.kind == DuplicateKind.PERSON }
    val catCount = all.count { it.group.kind == DuplicateKind.CATEGORY }
    val rows: List<Pair<String, String>> = if (tab == 0) {
        people.filter { query.isBlank() || it.name.contains(query.trim(), ignoreCase = true) }
            .sortedBy { it.name.lowercase() }
            .map { n -> n.name to listOfNotNull(plural(n.uses, "payment"), n.vpas.firstOrNull()).joinToString(" · ") }
    } else {
        cats.sortedBy { it.lowercase() }.map { c ->
            val type = when {
                c in builtIn -> "Built-in"
                repo.snapshot.value?.customCategories?.any { it.name == c } == true -> "Yours"
                else -> "Old name, still on payments"
            }
            c to listOfNotNull(type, uses[c]?.let { plural(it, "payment") }).joinToString(" · ")
        }
    }

    Box(Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = Spacing.screen, end = Spacing.screen, top = 8.dp, bottom = 120.dp),
            verticalArrangement = Arrangement.spacedBy(Spacing.cardGap),
        ) {
            item {
                SubScreenHeader("Duplicates", onBack)
                Spacer(Modifier.height(12.dp))
                Text(suggestions?.size?.toString() ?: "–", style = AppType.hero, color = TextPrimary)
                Spacer(Modifier.height(8.dp))
                Text(
                    when {
                        suggestions == null -> "Checking your people and categories…"
                        all.isEmpty() -> "look the same. You can still tick any two below and merge them."
                        else -> "look like the same thing under different names."
                    },
                    style = AppType.body,
                    color = TextSecondary,
                )
                Text(
                    "Checked ${plural(people.size, "person", "people")} and ${plural(cats.size, "category", "categories")} · $aiStatus",
                    style = AppType.chip,
                    color = TextSecondary,
                    modifier = Modifier.padding(top = 6.dp),
                )
                Spacer(Modifier.height(16.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TabPill("People · $peopleCount", tab == 0, Modifier.weight(1f)) { tab = 0; selected = emptySet() }
                    TabPill("Categories · $catCount", tab == 1, Modifier.weight(1f)) { tab = 1; selected = emptySet() }
                }
                Spacer(Modifier.height(Spacing.section - Spacing.cardGap))
                SectionLabel(if (shown.isEmpty()) "Suggested · none" else "Suggested · ${shown.size}")
                if (shown.isEmpty()) {
                    Text(
                        if (tab == 0) "No one looks like they have two names." else "No two categories look like the same thing.",
                        style = AppType.body,
                        color = TextSecondary,
                    )
                }
            }
            items(shown, key = { it.group.id }) { s ->
                SuggestionCard(
                    s = s,
                    countOf = { n -> if (s.group.kind == DuplicateKind.PERSON) people.firstOrNull { it.name == n }?.uses ?: 0 else uses[n] ?: 0 },
                    onMerge = { into -> merge(s.group.kind, s.group.members, into) },
                    onReject = { done("Kept apart", repo.rejectDuplicate(s.group.id)) },
                )
            }
            item {
                Spacer(Modifier.height(Spacing.section - Spacing.cardGap))
                SectionLabel(if (tab == 0) "All people · ${people.size}" else "All categories · ${cats.size}")
                Text("Spot two that are the same? Tick them, then tap Merge.", style = AppType.body, color = TextSecondary)
                if (tab == 0) {
                    Spacer(Modifier.height(10.dp))
                    Box(
                        Modifier.fillMaxWidth().height(48.dp).clip(PillShape).background(Surface).padding(horizontal = 16.dp),
                        contentAlignment = Alignment.CenterStart,
                    ) {
                        if (query.isEmpty()) Text("Search names", style = AppType.body, color = TextSecondary)
                        BasicTextField(
                            value = query,
                            onValueChange = { query = it.take(40) },
                            singleLine = true,
                            textStyle = AppType.body.copy(color = TextPrimary),
                            cursorBrush = SolidColor(Accent),
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            }
            items(rows, key = { "row-${it.first}" }) { (name, detail) ->
                val on = name in selected
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth().clip(CardShape).background(Surface)
                        .clickable(role = Role.Checkbox) { Feedback.tick(); selected = if (on) selected - name else selected + name }
                        .padding(horizontal = Spacing.cardH, vertical = 14.dp),
                ) {
                    Box(
                        Modifier.size(24.dp).clip(CircleShape).background(if (on) Accent else SurfaceRaised),
                        contentAlignment = Alignment.Center,
                    ) { if (on) Icon(com.yourteam.expensetracker.ui.theme.HugeIcons.Tick, contentDescription = null, tint = OnAccent, modifier = Modifier.size(16.dp)) }
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        Text(name, style = AppType.rowTitle, color = TextPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        if (detail.isNotEmpty()) Text(detail, style = AppType.chip, color = TextSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        }

        // Shows once two or more are ticked.
        if (selected.size >= 2) {
            Box(
                Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(Spacing.screen).fillMaxWidth()
                    .height(Spacing.button).clip(PillShape).background(Accent)
                    .clickable(role = Role.Button) { merging = selected.toList() },
                contentAlignment = Alignment.Center,
            ) { Text("Merge ${selected.size} selected", style = AppType.tab, color = OnAccent) }
        }
    }

    merging?.let { members ->
        MergeSheet(
            kind = kind,
            members = members,
            suggested = if (kind == DuplicateKind.PERSON) Duplicates.personGroup(people.filter { it.name in members }, "").suggested
            else Duplicates.categoryGroup(members, uses, builtIn, "")?.suggested,
            onDismiss = { merging = null },
            onMerge = { into -> merging = null; merge(kind, members, into) },
        )
    }
}

private fun plural(n: Int, one: String, many: String = one + "s") = "$n " + if (n == 1) one else many

@Composable
private fun TabPill(text: String, on: Boolean, modifier: Modifier, onClick: () -> Unit) {
    Box(
        modifier.height(44.dp).clip(PillShape).background(if (on) Accent else Surface).clickable(role = Role.Tab, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Text(text, style = AppType.tab, color = if (on) OnAccent else TextPrimary) }
}

@Composable
private fun SectionLabel(text: String) {
    Text(text, style = AppType.tab, color = TextSecondary, modifier = Modifier.padding(bottom = 4.dp))
}

@Composable
private fun SuggestionCard(s: Suggestion, countOf: (String) -> Int, onMerge: (String) -> Unit, onReject: () -> Unit) {
    val g = s.group
    var keep by remember(g.id) { mutableStateOf(g.suggested) }
    val person = g.kind == DuplicateKind.PERSON
    Column(Modifier.fillMaxWidth().clip(CardShape).background(Surface).padding(horizontal = Spacing.cardH, vertical = Spacing.cardV)) {
        Text(
            g.members.joinToString("  ·  "),
            style = AppType.rowTitle,
            color = TextPrimary,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            g.reason + " · " + g.members.sumOf(countOf).let { plural(it, "payment") },
            style = AppType.chip,
            color = TextSecondary,
        )
        Spacer(Modifier.height(12.dp))
        Text(if (person) "Show them as" else "Keep this category", style = AppType.chip, color = TextSecondary, modifier = Modifier.padding(bottom = 8.dp))
        KeepChips(listOf(g.suggested) + g.members.filter { it != g.suggested }, keep) { keep = it }
        Spacer(Modifier.height(14.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(
                Modifier.weight(1f).height(48.dp).clip(PillShape).background(SurfaceRaised).clickable(role = Role.Button, onClick = onReject),
                contentAlignment = Alignment.Center,
            ) { Text("Not the same", style = AppType.tab, color = TextPrimary) }
            Box(
                Modifier.weight(1f).height(48.dp).clip(PillShape).background(Accent).clickable(role = Role.Button) { onMerge(keep) },
                contentAlignment = Alignment.Center,
            ) { Text("Merge", style = AppType.tab, color = OnAccent) }
        }
    }
}

@Composable
private fun KeepChips(names: List<String>, keep: String?, onPick: (String) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.horizontalScroll(rememberScrollState())) {
        names.forEach { name ->
            val on = name == keep
            Box(
                Modifier.height(40.dp).clip(PillShape).background(if (on) Accent else SurfaceRaised)
                    .clickable(role = Role.Button) { Feedback.tick(); onPick(name) }
                    .padding(horizontal = 14.dp),
                contentAlignment = Alignment.Center,
            ) { Text(name, style = AppType.chip, color = if (on) OnAccent else TextPrimary, maxLines = 1) }
        }
    }
}

/** The user's own pick: which name to keep. Two built-in categories can't be merged. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MergeSheet(kind: DuplicateKind, members: List<String>, suggested: String?, onDismiss: () -> Unit, onMerge: (String) -> Unit) {
    val blocked = kind == DuplicateKind.CATEGORY && members.count { it in builtIn } > 1
    var keep by remember { mutableStateOf(suggested ?: members.first()) }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true), containerColor = Surface, dragHandle = null) {
        Column(Modifier.navigationBarsPadding().padding(horizontal = Spacing.screen, vertical = 20.dp)) {
            Text(if (kind == DuplicateKind.PERSON) "Merge into one person" else "Merge into one category", style = AppType.statValue, color = TextPrimary)
            Spacer(Modifier.height(6.dp))
            Text(
                if (blocked) "Two built-in categories can't be merged. Untick one of them."
                else if (kind == DuplicateKind.PERSON) "Their payments will show this name, and share one category."
                else "Payments in the others move here, and the others are removed from your list.",
                style = AppType.body,
                color = TextSecondary,
            )
            if (!blocked) {
                Spacer(Modifier.height(16.dp))
                Text(if (kind == DuplicateKind.PERSON) "Show them as" else "Keep this category", style = AppType.chip, color = TextSecondary, modifier = Modifier.padding(bottom = 8.dp))
                val ordered = (listOfNotNull(suggested) + members).distinct()
                    .let { if (kind == DuplicateKind.CATEGORY) it.filter { n -> members.none { m -> m in builtIn } || n in builtIn } else it }
                KeepChips(ordered, keep) { keep = it }
            }
            Spacer(Modifier.height(20.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(
                    Modifier.weight(1f).height(Spacing.button).clip(PillShape).background(SurfaceRaised).clickable(role = Role.Button, onClick = onDismiss),
                    contentAlignment = Alignment.Center,
                ) { Text("Cancel", style = AppType.tab, color = TextPrimary) }
                Box(
                    Modifier.weight(1f).height(Spacing.button).clip(PillShape).background(if (blocked) SurfaceRaised else Accent)
                        .clickable(enabled = !blocked, role = Role.Button) { onMerge(keep) },
                    contentAlignment = Alignment.Center,
                ) { Text("Merge", style = AppType.tab, color = if (blocked) TextSecondary else OnAccent) }
            }
        }
    }
}
