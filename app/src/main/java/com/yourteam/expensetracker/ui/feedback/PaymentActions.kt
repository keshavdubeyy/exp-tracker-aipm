package com.yourteam.expensetracker.ui.feedback

import com.yourteam.expensetracker.data.PaymentRepository
import com.yourteam.expensetracker.insights.Money

/**
 * User actions on payments, each with its FEEDBACK.md message, Confirm haptic and Undo.
 * Screens call these instead of the repository, so every screen says the same thing.
 */
object PaymentActions {

    /**
     * F1 / F2 (Feature 1 §3.5). By default the choice becomes the payee's default and the message
     * says so; [onlyThis] changes just this payment. Unnamed payments only change themselves.
     */
    fun categorise(repo: PaymentRepository, paymentKey: String, category: String, onlyThis: Boolean = false) {
        val snap = repo.snapshot.value ?: return
        val payment = snap.payments.firstOrNull { it.key == paymentKey }
        if (payment == null) {
            // Money received uses income categories.
            val credit = snap.received.firstOrNull { it.key == paymentKey } ?: return
            val undo = repo.setIncomeCategory(credit, category, onlyThis)
            val from = credit.from?.trim()?.takeIf(String::isNotEmpty)
            Feedback.success(
                message = if (from != null && !onlyThis) "Saved. Money from $from will be $category" else "Saved as $category",
                undo = undo,
            )
            return
        }
        val undo = repo.setCategory(payment, category, onlyThis)
        val payee = payment.payee?.trim()?.takeIf(String::isNotEmpty)
        Feedback.success(
            message = when {
                payee == null -> "Saved as $category"
                onlyThis -> "Saved as $category for this payment only"
                else -> "Saved. Future payments to $payee will be $category"
            },
            undo = undo,
        )
    }

    /** A category the user made (Feature 1 §3.6). Null name = refused (empty or already used). */
    fun addCustomCategory(repo: PaymentRepository, name: String, parent: String?): Boolean {
        val undo = repo.addCustomCategory(name, parent) ?: run { Feedback.failure("That name is already used"); return false }
        Feedback.success("Added ${name.trim()}", undo)
        return true
    }

    fun editCustomCategory(repo: PaymentRepository, old: String, name: String, parent: String?): Boolean {
        val undo = repo.editCustomCategory(old, name, parent) ?: run { Feedback.failure("That name is already used"); return false }
        Feedback.success("Saved ${name.trim()}", undo)
        return true
    }

    fun deleteCustomCategory(repo: PaymentRepository, name: String) {
        val undo = repo.deleteCustomCategory(name)
        Feedback.success("Deleted $name", undo)
    }

    /** F24: money out or in added by hand from the + button (bank account or cash). Undo removes it again. */
    fun addManual(repo: PaymentRepository, paise: Long, payee: String, at: Long, category: String?, moneyIn: Boolean = false, cash: Boolean = false) {
        val undo = repo.addManual(paise, payee, at, category, moneyIn, cash)
        val name = payee.trim()
        val amount = Money.formatWhole(paise)
        Feedback.success(
            message = when {
                moneyIn && name.isEmpty() -> "Added $amount received"
                moneyIn -> "Added $amount from $name"
                name.isEmpty() -> "Added $amount"
                else -> "Added $amount to $name"
            },
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
