package com.yourteam.expensetracker.ui.screens.review

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.yourteam.expensetracker.data.PaymentRepository
import com.yourteam.expensetracker.data.PaymentSnapshot
import com.yourteam.expensetracker.insights.Spending
import com.yourteam.expensetracker.insights.ToCheck
import com.yourteam.expensetracker.ui.feedback.PaymentActions
import com.yourteam.expensetracker.ui.screens.PaymentRowModel
import com.yourteam.expensetracker.ui.screens.paymentRow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

sealed interface ReviewUiState {
    /** For the Review tab badge. 0 while loading. */
    val count: Int get() = (this as? Loaded)?.toCheck?.count ?: 0

    data object Loading : ReviewUiState
    data class Loaded(
        /** Hero: count and ₹ still to check. Computed in insights/, same as the Home banner. */
        val toCheck: ToCheck,
        /** Started payments with no answer and nothing else recorded them. Newest first. */
        val pending: List<PaymentRowModel>,
        /** "Not paid", but the bank / GPay recorded it as paid. Newest first. */
        val conflicts: List<PaymentRowModel>,
        /** Category names already in use, for the Add category sheet. */
        val categories: List<String>,
    ) : ReviewUiState {
        val rows get() = pending + conflicts
    }
}

class ReviewViewModel(app: Application) : AndroidViewModel(app) {

    private val repo = PaymentRepository.get(app).also { it.ensureLoaded() }

    val state: StateFlow<ReviewUiState> = repo.snapshot.map(::build)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), build(repo.snapshot.value))

    private fun build(snap: PaymentSnapshot?): ReviewUiState {
        if (snap == null) return ReviewUiState.Loading
        return ReviewUiState.Loaded(
            toCheck = Spending.toCheck(snap.payments),
            pending = snap.payments.filter { it.needsAnswer }.map { paymentRow(it, snap) },
            conflicts = snap.payments.filter { it.conflict }.map { paymentRow(it, snap) },
            categories = snap.payments.mapNotNull(snap::categoryOf).distinct(),
        )
    }

    fun answer(id: Long, yes: Boolean) = PaymentActions.answer(repo, id, yes)

    fun setCategory(key: String, category: String) = PaymentActions.categorise(repo, key, category)
}
