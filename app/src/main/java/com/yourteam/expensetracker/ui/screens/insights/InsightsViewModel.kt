package com.yourteam.expensetracker.ui.screens.insights

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.yourteam.expensetracker.categoriser.Categories
import com.yourteam.expensetracker.data.PaymentRepository
import com.yourteam.expensetracker.data.PaymentSnapshot
import com.yourteam.expensetracker.insights.InsightCard
import com.yourteam.expensetracker.insights.InsightPeriod
import com.yourteam.expensetracker.insights.InsightSummary
import com.yourteam.expensetracker.insights.Insights
import com.yourteam.expensetracker.insights.PeriodKind
import com.yourteam.expensetracker.ui.feedback.ACTION_DELAY_MS
import com.yourteam.expensetracker.ui.feedback.Feedback
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate

sealed interface InsightsUiState {
    data object Loading : InsightsUiState
    data class Loaded(
        val summary: InsightSummary,
        /** Cards left after the ones the user has rated are removed. */
        val cards: List<InsightCard>,
        val refreshing: Boolean,
        /** When these numbers were last worked out, for "Updated 2 min ago". */
        val updatedAt: Long,
    ) : InsightsUiState
}

class InsightsViewModel(app: Application) : AndroidViewModel(app) {

    private val repo = PaymentRepository.get(app).also { it.ensureLoaded() }
    private val feedbackStore = InsightFeedbackStore.get(app)
    /** Feature 2: AI words for the cards, applied when safe (the app's own words otherwise). */
    private val writer = com.yourteam.expensetracker.data.InsightWriter.get(app)

    /** The chosen period. Presets are re-worked out from today on every build. */
    private val period = MutableStateFlow(InsightPeriod.of(PeriodKind.MONTH, LocalDate.now()))
    val selectedPeriod: StateFlow<InsightPeriod> = period

    /** Ratings the user has given, so rated cards never return. */
    private val feedback = feedbackStore.state

    /** Shows the skeleton for a moment after the period changes (FEEDBACK.md §2a, in7). */
    private val switching = MutableStateFlow(false)
    private var switchJob: Job? = null

    /** When the visible numbers were produced. */
    private val updatedAt = MutableStateFlow(System.currentTimeMillis())

    val state: StateFlow<InsightsUiState> =
        combine(
            combine(repo.snapshot, repo.refreshing, period, switching, feedback) { snap, refreshing, p, busy, rated ->
                if (busy || snap == null) InsightsUiState.Loading else build(snap, refreshing, p, rated)
            },
            writer.version,
        ) { s, _ ->
            if (s is InsightsUiState.Loaded) {
                viewModelScope.launch { writer.fetch(s.cards) } // asks once per insight; a new answer bumps version
                s.copy(cards = s.cards.map(writer::apply))
            } else s
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), InsightsUiState.Loading)

    fun setKind(kind: PeriodKind) {
        if (kind == period.value.kind && kind != PeriodKind.CUSTOM) return
        setPeriod(InsightPeriod.of(kind, LocalDate.now()))
    }

    fun setPeriod(p: InsightPeriod) {
        if (p == period.value) return
        period.value = p
        updatedAt.value = System.currentTimeMillis()
        switchJob?.cancel()
        switchJob = viewModelScope.launch {
            switching.value = true
            delay(ACTION_DELAY_MS)
            switching.value = false
        }
    }

    /** Step to the previous/next period of the same kind (custom steps by its own length). */
    fun step(back: Boolean) {
        val p = period.value
        val next = when (p.kind) {
            PeriodKind.MONTH -> InsightPeriod.of(PeriodKind.MONTH, if (back) p.start.minusMonths(1) else p.start.plusMonths(1))
            PeriodKind.WEEK -> InsightPeriod.of(PeriodKind.WEEK, if (back) p.start.minusWeeks(1) else p.start.plusWeeks(1))
            PeriodKind.DAY -> InsightPeriod.of(PeriodKind.DAY, if (back) p.start.minusDays(1) else p.start.plusDays(1))
            PeriodKind.CUSTOM -> p // custom moves via the calendar
        }
        // Never step a current/day period into the future.
        if (!back && next.start.isAfter(LocalDate.now())) return
        setPeriod(next)
    }

    /** Helpful / Not relevant / Dismiss on a card: it leaves the list, with Undo. */
    fun rate(card: InsightCard, rating: InsightFeedback) = feedbackStore.rateAndConfirm(card, rating)

    fun refresh() {
        repo.refresh()
        updatedAt.value = System.currentTimeMillis()
    }

    /** Tag the payment behind [key] (from a drill-down). Insights recalculate on their own. */
    fun setCategory(key: String, category: String, onlyThis: Boolean = false) =
        com.yourteam.expensetracker.ui.feedback.PaymentActions.categorise(repo, key, category, onlyThis)

    private fun build(
        snap: PaymentSnapshot,
        refreshing: Boolean,
        chosen: InsightPeriod,
        rated: Map<String, InsightFeedback>,
    ): InsightsUiState.Loaded {
        val today = LocalDate.now()
        // A "current" month/week/day chosen earlier still means the current one today.
        val p = if (chosen.kind == PeriodKind.CUSTOM || !InsightsWords.containsToday(chosen, today)) chosen
        else InsightPeriod.of(chosen.kind, today)
        val summary = Insights.summarise(
            payments = snap.payments,
            received = snap.received,
            period = p,
            today = today,
            categoryOf = snap::categoryOf,
            parentOf = { Categories.parentOf(it, snap.customCategories) },
        )
        return InsightsUiState.Loaded(
            summary = summary,
            cards = summary.cards.filter { it.id !in rated },
            refreshing = refreshing,
            updatedAt = updatedAt.value,
        )
    }
}
