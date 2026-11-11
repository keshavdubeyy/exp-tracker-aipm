package com.yourteam.expensetracker.ui.screens.insights

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.yourteam.expensetracker.data.PaymentRepository
import com.yourteam.expensetracker.data.PaymentSnapshot
import com.yourteam.expensetracker.insights.Spending
import com.yourteam.expensetracker.insights.SpendingSummary
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import java.time.LocalDate

class InsightsViewModel(app: Application) : AndroidViewModel(app) {

    private val repo = PaymentRepository.get(app).also { it.ensureLoaded() }

    /** Null while loading. All numbers come from [Spending] (insights/), never from the UI. */
    val summary: StateFlow<SpendingSummary?> = repo.snapshot
        .map(::summarise)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), summarise(repo.snapshot.value))

    private fun summarise(snap: PaymentSnapshot?) = snap?.let { Spending.summarise(it.payments, LocalDate.now()) }
}
