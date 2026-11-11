package com.yourteam.expensetracker.ui.screens.ask

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import com.yourteam.expensetracker.data.PaymentRepository
import com.yourteam.expensetracker.insights.Answer
import com.yourteam.expensetracker.insights.Answers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.time.LocalDate

data class Exchange(val id: Int, val question: String, val answer: Answer)

class AskViewModel(app: Application) : AndroidViewModel(app) {

    private val repo = PaymentRepository.get(app).also { it.ensureLoaded() }

    private val _exchanges = MutableStateFlow<List<Exchange>>(emptyList())
    /** Oldest first. Kept only while the app is open. */
    val exchanges: StateFlow<List<Exchange>> = _exchanges.asStateFlow()

    val suggestions = Answers.suggestions

    fun ask(question: String) {
        val q = question.trim()
        if (q.isEmpty()) return
        val payments = repo.snapshot.value?.payments.orEmpty()
        val answer = Answers.answer(q, payments, LocalDate.now()) // all numbers from insights/
        _exchanges.update { it + Exchange(it.size, q, answer) }
    }

    fun clear() { _exchanges.value = emptyList() }
}
