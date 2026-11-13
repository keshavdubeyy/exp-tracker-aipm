package com.yourteam.expensetracker.ui.screens.budgets

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.yourteam.expensetracker.data.BudgetStore
import com.yourteam.expensetracker.data.FiredAlert
import com.yourteam.expensetracker.data.PaymentRepository
import com.yourteam.expensetracker.data.PaymentSnapshot
import com.yourteam.expensetracker.insights.BudgetLevel
import com.yourteam.expensetracker.insights.BudgetStatus
import com.yourteam.expensetracker.insights.Money
import com.yourteam.expensetracker.insights.MonthBudget
import com.yourteam.expensetracker.insights.MonthSpend
import com.yourteam.expensetracker.ui.feedback.Feedback
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter

/** Last month in short, for the "New month, fresh budgets" sheet (bd11). */
data class NewMonthSummary(
    val month: YearMonth,
    val spentPaise: Long,
    val limitPaise: Long,
    val withinCount: Int,
    val categoryCount: Int,
)

sealed interface BudgetsUiState {
    data object Loading : BudgetsUiState

    /** bd1: no budget for this month yet. */
    data class NoBudget(
        val month: YearMonth,
        val spentPaise: Long,
        val today: LocalDate,
        /** "Start from October's spending" shows only when last month had spending. */
        val lastMonthSpend: MonthSpend,
        val hasPast: Boolean,
    ) : BudgetsUiState

    /** bd6 / bd13 / bd14, with the bd11 sheet when [newMonth] is set. */
    data class Active(
        val status: BudgetStatus,
        val today: LocalDate,
        val fired: Map<String, List<FiredAlert>>,
        val newMonth: NewMonthSummary?,
        val hasPast: Boolean,
    ) : BudgetsUiState
}

/** What the setup steps (bd2–bd5) need. */
data class SetupData(
    val month: YearMonth,
    val spentThisMonthPaise: Long,
    val lastMonth: MonthSpend,
    val existing: MonthBudget?,
)

class BudgetsViewModel(app: Application) : AndroidViewModel(app) {

    private val repo = PaymentRepository.get(app)
    private val store = BudgetStore.get(app)

    init { store.ensureMonth(LocalDate.now()) }

    val state: StateFlow<BudgetsUiState> = combine(repo.snapshot, store.budgets, store.alerts) { snap, budgets, alerts ->
        snap ?: return@combine BudgetsUiState.Loading
        build(snap, budgets, alerts)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), BudgetsUiState.Loading)

    val setup: StateFlow<SetupData?> = combine(repo.snapshot, store.budgets) { snap, budgets ->
        snap ?: return@combine null
        val month = YearMonth.now()
        SetupData(
            month = month,
            spentThisMonthPaise = store.monthSpend(snap, month).totalPaise,
            lastMonth = store.monthSpend(snap, month.minusMonths(1)),
            existing = budgets[month],
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val snapshot: StateFlow<PaymentSnapshot?> = repo.snapshot

    /** Status of any month with a budget (past months for bd12). */
    fun statusOf(month: YearMonth): BudgetStatus? {
        val snap = repo.snapshot.value ?: return null
        val b = store.budgets.value[month] ?: return null
        return store.statusFor(snap, b, LocalDate.now())
    }

    fun pastMonths(): List<YearMonth> = store.pastMonths(LocalDate.now())

    /** Setup / edit saved (bd4–bd5). The button's 1.2 s step runs in the UI first. */
    fun save(budget: MonthBudget, editing: Boolean) {
        val undo = store.save(budget.copy(confirmed = true))
        Feedback.success(if (editing) "Budgets updated for ${monthName(budget.month)}" else "Budget set for ${monthName(budget.month)}", undo)
    }

    /** "Keep the same" (bd11). */
    fun keepSame(month: YearMonth) {
        store.confirm(month)
        Feedback.success("${monthName(month)} budgets kept")
    }

    /** bd8 Save. */
    fun setLimit(category: String, paise: Long) {
        val undo = store.setCategoryLimit(YearMonth.now(), category, paise)
        Feedback.success("$category budget is now ${Money.formatWhole(paise)}", undo)
    }

    /** bd8 Remove: reversible, so Undo instead of a dialog (FEEDBACK.md §1.3). */
    fun removeLimit(category: String) {
        val undo = store.setCategoryLimit(YearMonth.now(), category, null)
        Feedback.success("$category budget removed", undo)
    }

    fun firedFor(category: String): List<FiredAlert> = store.firedFor(YearMonth.now(), category)

    private fun build(snap: PaymentSnapshot, budgets: Map<YearMonth, MonthBudget>, alerts: Map<String, List<FiredAlert>>): BudgetsUiState {
        val today = LocalDate.now()
        val month = YearMonth.from(today)
        val hasPast = budgets.keys.any { it.isBefore(month) }
        val budget = budgets[month] ?: return BudgetsUiState.NoBudget(
            month = month,
            spentPaise = store.monthSpend(snap, month).totalPaise,
            today = today,
            lastMonthSpend = store.monthSpend(snap, month.minusMonths(1)),
            hasPast = hasPast,
        )
        val newMonth = if (budget.confirmed) null else budgets[month.minusMonths(1)]?.let { prev ->
            val s = store.statusFor(snap, prev, today)
            NewMonthSummary(
                month = prev.month,
                spentPaise = s.overall.spentPaise,
                limitPaise = s.overall.limitPaise,
                withinCount = s.categories.count { it.level != BudgetLevel.OVER },
                categoryCount = s.categories.size,
            )
        }
        return BudgetsUiState.Active(
            status = store.statusFor(snap, budget, today),
            today = today,
            fired = alerts.filterKeys { it.startsWith("$month|") }.mapKeys { it.key.substringAfter('|') },
            newMonth = newMonth,
            hasPast = hasPast,
        )
    }

    companion object {
        private val full = DateTimeFormatter.ofPattern("MMMM")
        fun monthName(m: YearMonth): String = full.format(m)
    }
}
