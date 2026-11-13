package com.yourteam.expensetracker.ui.screens.onboarding

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.clickable
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.yourteam.expensetracker.categoriser.AiCategoriser
import com.yourteam.expensetracker.categoriser.Basis
import com.yourteam.expensetracker.categoriser.CategoryMemory
import com.yourteam.expensetracker.data.PaymentRepository
import com.yourteam.expensetracker.insights.Money
import com.yourteam.expensetracker.insights.PastSort
import com.yourteam.expensetracker.insights.PayeeGroup
import com.yourteam.expensetracker.ui.feedback.ACTION_DELAY_MS
import com.yourteam.expensetracker.ui.feedback.Feedback
import com.yourteam.expensetracker.ui.feedback.PaymentActions
import com.yourteam.expensetracker.ui.screens.ChoiceRow
import com.yourteam.expensetracker.ui.screens.InitialsCircle
import com.yourteam.expensetracker.ui.theme.Accent
import com.yourteam.expensetracker.ui.theme.AppType
import com.yourteam.expensetracker.ui.theme.CardShape
import com.yourteam.expensetracker.ui.theme.CategoryIcons
import com.yourteam.expensetracker.ui.theme.HugeIcons
import com.yourteam.expensetracker.ui.theme.Negative
import com.yourteam.expensetracker.ui.theme.PillShape
import com.yourteam.expensetracker.ui.theme.Spacing
import com.yourteam.expensetracker.ui.theme.Surface
import com.yourteam.expensetracker.ui.theme.SurfaceRaised
import com.yourteam.expensetracker.ui.theme.TextPrimary
import com.yourteam.expensetracker.ui.theme.TextSecondary
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private enum class SortPhase { WORKING, RESULT, ASK }

/**
 * ob3b · Sort past payments (ONBOARDING.md). The inbox found at setup is sorted the same way as new payments:
 * rules straight away, then the AI for every payee the rules couldn't place. Only "Sure" answers are filed.
 * The user sees what was sorted, then can pick the rest one payee at a time from the AI's suggestions, or skip.
 * Counts come from [PastSort] (AGENTS.md rule 1); the AI only picks categories (rule 2).
 */
@Composable
internal fun SortPastScreen(repo: PaymentRepository, onContinue: () -> Unit) {
    LaunchedEffect(Unit) { repo.ensureLoaded() }
    val snap by repo.snapshot.collectAsStateWithLifecycle()
    val plan = remember(snap) { snap?.let { s -> PastSort.of(s.payments, s::guessFor) } }

    var phase by rememberSaveable { mutableStateOf(if (AiCategoriser.enabled) SortPhase.WORKING else SortPhase.RESULT) }
    var asked by remember { mutableIntStateOf(0) }
    var total by remember { mutableIntStateOf(0) }

    LaunchedEffect(plan?.total) { if (plan != null && plan.total == 0) onContinue() } // nothing imported
    LaunchedEffect(phase, snap != null) {
        if (phase == SortPhase.WORKING && snap != null) {
            repo.askAiForAllPast { a, t -> asked = a; total = t }
            Feedback.success() // like F30: the count stops
            phase = SortPhase.RESULT
        }
    }

    AnimatedContent(
        targetState = phase,
        transitionSpec = {
            (slideInHorizontally(tween(280)) { it / 4 } + fadeIn(tween(220)))
                .togetherWith(slideOutHorizontally(tween(280)) { -it / 4 } + fadeOut(tween(160)))
        },
        label = "sort-past",
    ) { p ->
        when (p) {
            SortPhase.WORKING -> Working(asked, total, onSkip = { Feedback.tick(); phase = SortPhase.RESULT })
            SortPhase.RESULT -> plan?.let {
                Result(
                    plan = it,
                    onSortRest = { Feedback.tick(); phase = SortPhase.ASK },
                    onSkip = { Feedback.tick(); onContinue() },
                    onContinue = onContinue,
                )
            }
            SortPhase.ASK -> plan?.let { AskEach(repo, it.toAsk, onDone = onContinue) }
        }
    }
}

@Composable
private fun Working(asked: Int, total: Int, onSkip: () -> Unit) {
    ObScaffold(step = 2, onBack = null, bottom = {
        PrimaryButton("Sorting…", enabled = false) {}
        TextLink("Skip this step", onClick = onSkip)
    }) {
        Spacer(Modifier.height(24.dp))
        Text("Sorting your payments", style = AppType.tab, color = TextSecondary)
        Spacer(Modifier.height(6.dp))
        Text(
            if (total == 0) "…" else "$asked of $total",
            style = AppType.hero.copy(fontSize = 52.sp, lineHeight = 58.sp),
            color = TextPrimary,
            modifier = Modifier.clearAndSetSemantics { contentDescription = "Sorting payees" },
        )
        Text("payees checked by AI", style = AppType.body.copy(fontSize = 14.sp, lineHeight = 20.sp), color = TextSecondary)
        Spacer(Modifier.height(24.dp))
        val fraction by animateFloatAsState(if (total == 0) 0f else asked.toFloat() / total, tween(300), label = "sort")
        Box(Modifier.fillMaxWidth().height(6.dp).background(SurfaceRaised, PillShape)) {
            Box(Modifier.fillMaxWidth(fraction).fillMaxHeight().background(Accent, PillShape))
        }
        Spacer(Modifier.height(20.dp))
        CheckRow("Known shops are sorted straight away")
        CheckRow("AI looks at the rest. It sends only the payee's name and amount, never the message")
        CheckRow("We only file it when the AI is sure")
    }
}

private fun basisLabel(b: Basis): String = when (b) {
    Basis.KNOWN_MERCHANT -> "Known shop"
    Basis.SHOP_WORDS -> "From its name"
    Basis.AI -> "AI"
    Basis.THIS_PAYMENT, Basis.YOUR_DEFAULT -> "You"
    Basis.NONE -> ""
}

private fun payments(n: Int) = "$n payment${if (n == 1) "" else "s"}"

@Composable
private fun Result(plan: com.yourteam.expensetracker.insights.PastSortPlan, onSortRest: () -> Unit, onSkip: () -> Unit, onContinue: () -> Unit) {
    val rest = plan.toAsk.size
    ObScaffold(step = 2, onBack = null, bottom = {
        if (rest > 0) {
            PrimaryButton("Sort the rest now", onClick = onSortRest)
            TextLink("Skip, I'll do it later", onClick = onSkip)
        } else {
            PrimaryButton("Continue", onClick = onContinue)
        }
    }) {
        Spacer(Modifier.height(24.dp))
        Text("Sorted on their own", style = AppType.tab, color = TextSecondary)
        Spacer(Modifier.height(6.dp))
        Text("${plan.sorted} of ${plan.total}", style = AppType.hero, color = TextPrimary)
        Spacer(Modifier.height(6.dp))
        Text(
            buildString {
                append("past payments have a category")
                if (plan.sortedByAi > 0) append(", ${plan.sortedByAi} of them by AI")
                append(". You can change any of them later.")
            },
            style = AppType.body.copy(fontSize = 14.sp, lineHeight = 20.sp),
            color = TextSecondary,
        )
        if (plan.filed.isNotEmpty()) {
            Spacer(Modifier.height(24.dp))
            Column(Modifier.fillMaxWidth().clip(CardShape).background(Surface).padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                plan.filed.take(6).forEach { g -> FiledRow(g) }
                val more = plan.filed.size - 6
                if (more > 0) Text("and $more more payee${if (more == 1) "" else "s"}", style = AppType.body, color = TextSecondary)
            }
        }
        if (rest > 0) {
            Spacer(Modifier.height(24.dp))
            Row {
                Box(Modifier.padding(top = 6.dp).size(8.dp).background(Negative, PillShape))
                Spacer(Modifier.width(12.dp))
                Text(
                    bolded("**${payments(plan.unsorted)}** to $rest payee${if (rest == 1) "" else "s"} need you. Pick from our suggestions, one payee at a time, or do it later from Review."),
                    style = AppType.body.copy(fontSize = 14.sp, lineHeight = 20.sp),
                    color = TextSecondary,
                )
            }
        }
    }
}

@Composable
private fun FiledRow(g: PayeeGroup) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        InitialsCircle(g.payee ?: "?")
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(g.payee ?: "Unnamed payment", style = AppType.rowTitle, color = TextPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                listOf(g.guess.category.orEmpty(), payments(g.payments.size), basisLabel(g.guess.basis)).filter(String::isNotEmpty).joinToString(" · "),
                style = AppType.body,
                color = TextSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

private val shortDate = java.time.format.DateTimeFormatter.ofPattern("d MMM")

/** One payee at a time: its payments, the AI's top 3, all categories, or skip. Picking saves it for every payment to them. */
@Composable
private fun AskEach(repo: PaymentRepository, groups: List<PayeeGroup>, onDone: () -> Unit) {
    val queue = remember { groups } // fixed when this phase opens, so the list doesn't jump as picks land
    var index by rememberSaveable { mutableIntStateOf(0) }
    var saving by remember { mutableStateOf<String?>(null) }
    var showAll by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val snap by repo.snapshot.collectAsStateWithLifecycle()

    LaunchedEffect(index) { if (index >= queue.size) onDone() }
    val g = queue.getOrNull(index) ?: return

    fun next() { saving = null; showAll = false; index++ }
    fun pick(category: String) {
        if (saving != null) return
        saving = category
        scope.launch {
            delay(ACTION_DELAY_MS) // FEEDBACK.md §2a loading step
            val first = g.payments.first()
            val named = CategoryMemory.payeeKeys(first.payee, first.sms?.vpa).isNotEmpty()
            // A named payee: saved as their default, so all their past and future payments use it. No name: just this one.
            PaymentActions.categorise(repo, first.key, category, onlyThis = !named)
            next()
        }
    }

    ObScaffold(step = 2, onBack = null, bottom = {
        Row(Modifier.fillMaxWidth()) {
            SecondaryButton("Skip this one", Modifier.weight(1f)) { Feedback.tick(); next() }
        }
        TextLink("Skip the rest", onClick = { Feedback.tick(); onDone() })
    }) {
        Text("${index + 1} of ${queue.size}", style = AppType.tab, color = TextSecondary)
        Spacer(Modifier.height(16.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            InitialsCircle(g.payee ?: "?")
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                ObHeading(g.payee ?: "Unnamed payment")
                Text(
                    "${payments(g.payments.size)} · ${Money.formatWhole(g.totalPaise)} · last on ${shortDate.format(java.time.Instant.ofEpochMilli(g.payments.maxOf { it.at }).atZone(java.time.ZoneId.systemDefault()))}",
                    style = AppType.body.copy(fontSize = 14.sp, lineHeight = 20.sp),
                    color = TextSecondary,
                )
            }
        }
        Spacer(Modifier.height(24.dp))
        Text(if (g.guess.basis == Basis.AI) "AI suggests" else "Suggested", style = AppType.tab, color = TextSecondary)
        Spacer(Modifier.height(10.dp))
        val all = snap?.expenseChoices.orEmpty()
        val suggestions = g.guess.suggestions.ifEmpty { all.take(3) }.take(3)
        suggestions.forEach { c ->
            ChoiceRow(c, icon = CategoryIcons.forCategory(c), loading = saving == c, enabled = saving == null) { pick(c) }
            Spacer(Modifier.height(Spacing.cardGap))
        }
        Spacer(Modifier.height(8.dp))
        if (!showAll) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth().clip(PillShape).clickable(role = Role.Button, enabled = saving == null) { Feedback.tick(); showAll = true }
                    .padding(vertical = 12.dp),
            ) {
                Text("Something else", style = AppType.rowTitle, color = Accent, modifier = Modifier.weight(1f))
                Icon(HugeIcons.ChevronRight, contentDescription = null, tint = Accent, modifier = Modifier.size(20.dp))
            }
        } else {
            Text("All categories", style = AppType.tab, color = TextSecondary)
            Spacer(Modifier.height(10.dp))
            all.filter { it !in suggestions }.forEach { c ->
                ChoiceRow(c, icon = CategoryIcons.forCategory(c), loading = saving == c, enabled = saving == null) { pick(c) }
                Spacer(Modifier.height(Spacing.cardGap))
            }
        }
        if (g.payee != null) {
            Spacer(Modifier.height(8.dp))
            Text("We'll use it for all ${payments(g.payments.size)} to ${g.payee}, and future ones.", style = AppType.body, color = TextSecondary)
        }
    }
}
