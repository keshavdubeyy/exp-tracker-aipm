package com.yourteam.expensetracker.ui.feedback

import com.yourteam.expensetracker.data.PaymentRepository
import com.yourteam.expensetracker.insights.Money

/**
 * User actions on payments, each with its FEEDBACK.md message, Confirm haptic and Undo.
 * Screens call these instead of the repository, so every screen says the same thing.
 */
object PaymentActions {

    /**
     * F1 / F2. A named payee's choice applies to all their payments, so say so;
     * an unnamed payment only changes itself.
     */
    fun categorise(repo: PaymentRepository, paymentKey: String, category: String) {
        val payment = repo.snapshot.value?.payments?.firstOrNull { it.key == paymentKey } ?: return
        val undo = repo.setCategory(payment, category)
        val payee = payment.payee?.trim()?.takeIf(String::isNotEmpty)
        Feedback.success(
            message = if (payee != null) "Saved. Future payments to $payee will be $category" else "Saved as $category",
            undo = undo,
        )
    }

    /** F24: a payment added by hand from the + button. Undo removes it again. */
    fun addManual(repo: PaymentRepository, paise: Long, payee: String, at: Long, category: String?) {
        val undo = repo.addManual(paise, payee, at, category)
        val name = payee.trim()
        Feedback.success(
            message = if (name.isEmpty()) "Added ${Money.formatWhole(paise)}" else "Added ${Money.formatWhole(paise)} to $name",
            undo = undo,
        )
    }

    /** F10a "Yes, paid" / "It was paid", F10b "Not paid". */
    fun answer(repo: PaymentRepository, pendingId: Long, yes: Boolean) {
        val payment = repo.snapshot.value?.payments?.firstOrNull { it.started?.id == pendingId }
        val undo = repo.answer(pendingId, yes)
        val message = if (!yes) {
            "Removed. Nothing was recorded"
        } else {
            val amount = payment?.let { Money.formatWhole(it.paise) }
            val payee = payment?.payee?.trim()?.takeIf(String::isNotEmpty)
            when {
                amount == null -> "Recorded"
                payee == null -> "Recorded $amount"
                else -> "Recorded $amount to $payee"
            }
        }
        Feedback.success(message, undo)
    }
}
