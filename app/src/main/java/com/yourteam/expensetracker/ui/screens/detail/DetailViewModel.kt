package com.yourteam.expensetracker.ui.screens.detail

import com.yourteam.expensetracker.ui.feedback.PaymentActions
import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.yourteam.expensetracker.data.PaymentRepository
import com.yourteam.expensetracker.data.PaymentSnapshot
import com.yourteam.expensetracker.insights.Payment
import com.yourteam.expensetracker.sms.SmsMessage
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

sealed interface DetailUiState {
    data object Loading : DetailUiState
    data object NotFound : DetailUiState
    data class Loaded(val payment: Payment, val sms: SmsMessage?) : DetailUiState
}

class DetailViewModel(app: Application, handle: SavedStateHandle) : AndroidViewModel(app) {

    private val key: String = checkNotNull(handle[ARG_KEY])
    private val repo = PaymentRepository.get(app).also { it.ensureLoaded() }

    val state: StateFlow<DetailUiState> = repo.snapshot.map(::build)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), build(repo.snapshot.value))

    private fun build(snap: PaymentSnapshot?): DetailUiState {
        if (snap == null) return DetailUiState.Loading
        val payment = snap.payments.firstOrNull { it.key == key } ?: return DetailUiState.NotFound
        return DetailUiState.Loaded(payment, snap.smsFor(payment))
    }

    fun answer(id: Long, yes: Boolean) = PaymentActions.answer(repo, id, yes)

    companion object {
        const val ARG_KEY = "key"
    }
}
