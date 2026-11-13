package com.yourteam.expensetracker.ui.screens.home

import com.yourteam.expensetracker.ui.feedback.PaymentActions
import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.yourteam.expensetracker.data.PaymentInputs
import com.yourteam.expensetracker.data.PaymentRepository
import com.yourteam.expensetracker.data.PaymentSnapshot
import com.yourteam.expensetracker.insights.CategorySpend
import com.yourteam.expensetracker.insights.DateRange
import com.yourteam.expensetracker.insights.RangePreset
import com.yourteam.expensetracker.insights.ToCheck
import com.yourteam.expensetracker.ui.screens.PaymentRowModel
import com.yourteam.expensetracker.ui.screens.paymentRow
import com.yourteam.expensetracker.ui.screens.receivedRow
import com.yourteam.expensetracker.insights.Received
import com.yourteam.expensetracker.insights.ReceivedTotal
import com.yourteam.expensetracker.insights.PeriodSpend
import com.yourteam.expensetracker.insights.Spending
import com.yourteam.expensetracker.ui.screens.FeedItem
import com.yourteam.expensetracker.ui.screens.paymentItem
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import com.yourteam.expensetracker.ui.feedback.ACTION_DELAY_MS
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import java.time.LocalDate

sealed interface HomeUiState {
    data object Loading : HomeUiState
    data class Loaded(
        /** Hero: spent this week/month vs the same stretch last time. Computed in insights/. */
        val spend: PeriodSpend,
        /** "To check · 4 · ₹553". Hidden when count is 0. */
        val toCheck: ToCheck,
        /** "Received today · 2 · +₹400" for the same days as the hero. Never part of [spend]. */
        val received: ReceivedTotal,
        /** Latest 10 transactions (out and in) for Home's Transactions; the full list is one tap away. */
        val recent: List<PaymentRowModel>,
        /** Category cards for "Categories ›", biggest first. */
        val categories: List<CategorySpend>,
        val items: List<FeedItem>,
        val paymentCount: Int,
        val needsAnswerCount: Int,
        val otherCount: Int,
        val refreshing: Boolean = false,
        /** Feature 3 (bd10): this month's budget, when one is set. Home then leads with "Left this month". */
        val budget: com.yourteam.expensetracker.insights.BudgetStatus? = null,
        /** With a budget: the latest payments this month (the hero covers the month, not the chosen dates). */
        val monthRecent: List<PaymentRowModel> = emptyList(),
    ) : HomeUiState
}

class HomeViewModel(app: Application) : AndroidViewModel(app) {

    private val repo = PaymentRepository.get(app)
    private val budgets = com.yourteam.expensetracker.data.BudgetStore.get(app).also { it.ensureMonth(LocalDate.now()) }

    /** Chosen on the date sheet. Presets are re-worked out from today on every build. */
    private val range = MutableStateFlow(DateRange.of(RangePreset.THIS_WEEK, LocalDate.now()))

    /** True for a moment after a new date range is applied, so Home shows its skeleton. */
    private val switching = MutableStateFlow(false)
    private var switchJob: Job? = null

    val state: StateFlow<HomeUiState> =
        combine(repo.snapshot, repo.refreshing, range, switching, budgets.budgets) { snap, refreshing, r, busy, _ ->
            if (busy) HomeUiState.Loading else snap?.let { build(it, refreshing, r) } ?: HomeUiState.Loading
        }.stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5_000),
            repo.snapshot.value?.let { build(it, repo.refreshing.value, range.value) } ?: HomeUiState.Loading,
        )

    val selectedRange: StateFlow<DateRange> = range

    /** Apply on the date sheet. */
    fun setRange(r: DateRange) {
        if (r == range.value) return
        range.value = r
        switchJob?.cancel()
        switchJob = viewModelScope.launch {
            switching.value = true
            delay(ACTION_DELAY_MS) // skeleton, then the new numbers (FEEDBACK.md §2a)
            switching.value = false
        }
    }

    /** Call once READ_SMS is granted. */
    fun load() = repo.ensureLoaded()

    /** Pull-to-refresh: re-read the inbox, keeping the current list visible meanwhile. */
    fun refresh() = repo.refresh()

    fun confirmPending(id: Long) = PaymentActions.answer(repo, id, yes = true)

    fun dismissPending(id: Long) = PaymentActions.answer(repo, id, yes = false)

    /** The + button: money out or in typed in by hand, from a bank account or cash. */
    fun addPayment(paise: Long, payee: String, at: Long, category: String?, moneyIn: Boolean, cash: Boolean) =
        PaymentActions.addManual(repo, paise, payee, at, category, moneyIn, cash)

    /** Tag the payment behind [key] (and others to the same payee). */
    fun setCategory(key: String, category: String, onlyThis: Boolean = false) {
        PaymentActions.categorise(repo, key, category, onlyThis)
    }

    private fun build(snap: PaymentSnapshot, refreshing: Boolean, chosen: DateRange): HomeUiState.Loaded {
        val today = LocalDate.now()
        // "Last 7 days" picked yesterday still means the last 7 days today.
        val r = if (chosen.preset == RangePreset.CUSTOM) chosen else DateRange.of(chosen.preset, today)
        val paymentItems = snap.payments.map { paymentItem(it, snap) }

        // Bank SMS that aren't payments (credits, alerts) and money-received notifications.
        val debitIds = snap.bankSms.mapNotNull(PaymentInputs::debit).map { it.smsId }.toSet()
        val otherSms = snap.bankSms.filter { it.id !in debitIds }.map {
            FeedItem("sms-${it.id}", "SMS · ${it.address}", it.dateMillis, it.body)
        }
        val received = snap.notifications.map {
            val body = listOf(it.title, it.text).filter(String::isNotBlank).joinToString("\n")
            FeedItem("upi-${it.id}", "${it.appName} · notification", it.postedAt, body)
        }

        return HomeUiState.Loaded(
            spend = Spending.periodSpend(snap.payments, r, today),
            toCheck = Spending.toCheck(snap.payments),
            received = Received.total(snap.received, r, today),
            // Same dates as the totals. Money received is listed (+₹, "Received") but never in the hero total.
            recent = (Spending.paymentsIn(snap.payments, r, today).map { paymentRow(it, snap) } +
                Received.inRange(snap.received, r, today).map { receivedRow(it, snap) })
                .sortedByDescending { it.at }.take(10),
            categories = Spending.byCategory(snap.payments, r, today, snap::categoryOf),
            items = (paymentItems + otherSms + received).sortedByDescending { it.dateMillis },
            paymentCount = snap.payments.size,
            needsAnswerCount = snap.payments.count { it.needsAnswer },
            otherCount = otherSms.size + received.size,
            refreshing = refreshing,
            budget = budgets.budgetFor(java.time.YearMonth.from(today))?.let { budgets.statusFor(snap, it, today) },
            // Money out and money in this month, newest first (Home's Transactions).
            monthRecent = DateRange.of(RangePreset.THIS_MONTH, today).let { m ->
                (Spending.paymentsIn(snap.payments, m, today).map { paymentRow(it, snap) } +
                    Received.inRange(snap.received, m, today).map { receivedRow(it, snap) })
                    .sortedByDescending { it.at }.take(10)
            },
        )
    }
}
