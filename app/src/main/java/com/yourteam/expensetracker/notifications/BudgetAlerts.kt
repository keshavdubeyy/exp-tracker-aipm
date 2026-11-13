package com.yourteam.expensetracker.notifications

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.yourteam.expensetracker.MainActivity
import com.yourteam.expensetracker.R
import com.yourteam.expensetracker.data.AlertWay
import com.yourteam.expensetracker.data.BudgetStore
import com.yourteam.expensetracker.data.PaymentSnapshot
import com.yourteam.expensetracker.insights.AlertDue
import com.yourteam.expensetracker.insights.BudgetLine
import com.yourteam.expensetracker.insights.Budgets
import com.yourteam.expensetracker.insights.Money
import com.yourteam.expensetracker.insights.Threshold
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter

/**
 * Feature 3 alerts. 75%: recorded and shown in the app only. 90%, 100% and over: one
 * notification each, once per category per month; a payment crossing several sends only the
 * highest. The overall budget gets 90%, 100% and over too. Neutral words, never a warning.
 * Every number comes from [Budgets] (AGENTS.md rule 1). Real data only.
 */
object BudgetAlerts {
    private const val CHANNEL = "budget_alerts"
    /** MainActivity extra value: open Budgets ("budgets") or one category ("budget:<name>"). */
    const val OPEN_BUDGETS = "budgets"
    const val OPEN_CATEGORY_PREFIX = "budget:"

    /** A pass over this month's budget: what's due now, keyed by category (or [Budgets.OVERALL]). */
    class Pass internal constructor(
        private val context: Context,
        private val month: YearMonth,
        private val today: LocalDate,
        due: List<AlertDue>,
    ) {
        private val left = due.associateBy { it.key }.toMutableMap()

        /**
         * The line to add to the "saved under X" notification for a payment in [category], when
         * that category has an alert due (Feature 3: merge when it's the same payment). Consumes it.
         * 75% isn't a notification, so it's never merged.
         */
        fun takeMergedLine(category: String, paymentKey: String): String? {
            val d = left[category]?.takeIf { it.threshold != Threshold.P75 && paymentKey in it.line.paymentKeys } ?: return null
            left.remove(category)
            record(d, AlertWay.NOTIFIED)
            return mergedText(d)
        }

        /** Posts everything not merged, then records it. */
        fun finish() {
            val canNotify = PaymentCheck.canNotify(context)
            left.values.forEach { d ->
                when {
                    d.threshold == Threshold.P75 -> record(d, AlertWay.APP)
                    canNotify -> { post(context, d, month); record(d, AlertWay.NOTIFIED) }
                    else -> record(d, AlertWay.APP) // still shown on the budget screen
                }
            }
            left.clear()
        }

        /** Every threshold crossed is marked, so lower ones never fire later this month. */
        private fun record(d: AlertDue, way: AlertWay) {
            val store = BudgetStore.get(context)
            store.recordFired(month, d.key, setOf(d.threshold), way, today)
            store.recordFired(month, d.key, d.crossed - d.threshold, AlertWay.SILENT, today)
        }
    }

    /** Works out what's due for this month's budget. Null when there's no budget. */
    fun start(context: Context, snap: PaymentSnapshot, today: LocalDate = LocalDate.now()): Pass? {
        val store = BudgetStore.get(context)
        store.ensureMonth(today)
        val month = YearMonth.from(today)
        val budget = store.budgetFor(month) ?: return null
        val status = store.statusFor(snap, budget, today)
        val keys = listOf(Budgets.OVERALL) + budget.categories.map { it.category }
        val fired = keys.associateWith { k -> store.firedFor(month, k).map { it.threshold }.toSet() }
        return Pass(context, month, today, Budgets.due(status, fired))
    }

    private fun name(key: String, month: YearMonth) =
        if (key == Budgets.OVERALL) "${monthName.format(month)} budget" else "$key budget"

    /** "90% of your Food & Dining budget used · ₹500 left" (bd9, merged). */
    private fun mergedText(d: AlertDue): String {
        val l = d.line
        return when (d.threshold) {
            Threshold.P90 -> "${l.percent}% of your ${d.key} budget used · ${Money.formatWhole(l.leftPaise)} left"
            Threshold.P100 -> "${d.key} budget reached · ${Money.formatWhole(l.spentPaise)} of ${Money.formatWhole(l.limitPaise)}"
            Threshold.OVER -> "${Money.formatWhole(l.overPaise)} over your ${d.key} budget"
            Threshold.P75 -> ""
        }
    }

    private fun titleAndText(d: AlertDue, month: YearMonth): Pair<String, String> {
        val l: BudgetLine = d.line
        val what = name(d.key, month)
        val subject = if (d.key == Budgets.OVERALL) monthName.format(month) else d.key
        return when (d.threshold) {
            Threshold.P90 -> "${l.percent}% of your $what used" to
                "${Money.formatWhole(l.spentPaise)} of ${Money.formatWhole(l.limitPaise)} spent · ${Money.formatWhole(l.leftPaise)} left."
            Threshold.P100 -> "${what.replaceFirstChar(Char::uppercase)} reached" to
                "${Money.formatWhole(l.spentPaise)} of ${Money.formatWhole(l.limitPaise)} spent. Payments still go through as normal."
            Threshold.OVER -> "$subject is ${Money.formatWhole(l.overPaise)} over budget" to
                "${Money.formatWhole(l.spentPaise)} spent of ${Money.formatWhole(l.limitPaise)}."
            Threshold.P75 -> "" to ""
        }
    }

    private fun post(context: Context, d: AlertDue, month: YearMonth) {
        ensureChannel(context)
        val (title, text) = titleAndText(d, month)
        val id = 40_000 + ("$month|${d.key}".hashCode() and 0x7fffffff) % 9_000
        val open = if (d.key == Budgets.OVERALL) OPEN_BUDGETS else OPEN_CATEGORY_PREFIX + d.key
        val builder = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_stat_payment)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setContentIntent(openIntent(context, id, open))
            .setAutoCancel(true)
            .setCategory(NotificationCompat.CATEGORY_STATUS)
        if (d.threshold == Threshold.OVER) builder.addAction(0, "See budget", openIntent(context, id + 1, open))
        @Suppress("MissingPermission") // checked in canNotify()
        NotificationManagerCompat.from(context).notify(id, builder.build())
    }

    private fun openIntent(context: Context, requestCode: Int, open: String) = PendingIntent.getActivity(
        context, requestCode,
        Intent(context, MainActivity::class.java).putExtra(CategoryPrompt.EXTRA_OPEN, open)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    private fun ensureChannel(context: Context) {
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel(CHANNEL, "Budget alerts", NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = "Once when a budget reaches 90%, its limit, and goes over."
            },
        )
    }

    private val monthName = DateTimeFormatter.ofPattern("MMMM")
}
