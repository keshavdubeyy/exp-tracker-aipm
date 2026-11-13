package com.yourteam.expensetracker.ui.screens.insights

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import com.yourteam.expensetracker.data.PaymentRepository
import com.yourteam.expensetracker.data.PaymentSnapshot
import com.yourteam.expensetracker.insights.InsightCard
import com.yourteam.expensetracker.ui.feedback.Feedback
import com.yourteam.expensetracker.ui.feedback.PaymentActions
import kotlinx.coroutines.flow.StateFlow

/** Backs both drill-downs (category in2, insight in3/F4). Reads the same snapshot every screen uses. */
class InsightsDetailViewModel(app: Application) : AndroidViewModel(app) {

    private val repo = PaymentRepository.get(app).also { it.ensureLoaded() }
    private val store = InsightFeedbackStore.get(app)

    val snapshot: StateFlow<PaymentSnapshot?> = repo.snapshot

    fun setCategory(key: String, category: String, onlyThis: Boolean = false) =
        PaymentActions.categorise(repo, key, category, onlyThis)

    /** Rate an insight (design §5): record it, confirm, and offer Undo. The card won't return. */
    fun rate(card: InsightCard, rating: InsightFeedback) = store.rateAndConfirm(card, rating)
}
