package com.yourteam.expensetracker.ui.screens.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.clickable
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.yourteam.expensetracker.BuildConfig
import com.yourteam.expensetracker.categoriser.AiCategoriser
import com.yourteam.expensetracker.categoriser.PaymentFacts
import com.yourteam.expensetracker.data.PaymentRepository
import com.yourteam.expensetracker.data.PendingPayment
import com.yourteam.expensetracker.notifications.CategoryPrompt
import com.yourteam.expensetracker.notifications.PaymentCheck
import com.yourteam.expensetracker.ui.feedback.Feedback
import com.yourteam.expensetracker.ui.screens.SubScreenHeader
import com.yourteam.expensetracker.ui.theme.AppType
import com.yourteam.expensetracker.ui.theme.CardShape
import com.yourteam.expensetracker.ui.theme.Spacing
import com.yourteam.expensetracker.ui.theme.Surface
import com.yourteam.expensetracker.ui.theme.TextPrimary
import com.yourteam.expensetracker.ui.theme.TextSecondary

/**
 * Developer screen (MVP M24, step 1): Feature 1 §7 success metrics for this phone, from counts
 * kept on the phone. Opened by tapping the app version in Profile 7 times. Counts only.
 */
@Composable
fun DeveloperScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val repo = PaymentRepository.get(context)
    val metrics by repo.metrics.collectAsStateWithLifecycle()
    val snapshot by repo.snapshot.collectAsStateWithLifecycle()
    val sampleOn by repo.sampleMode.collectAsStateWithLifecycle()
    val alerts = snapshot?.notifications.orEmpty().take(8)
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = Spacing.screen, end = Spacing.screen, bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(Spacing.cardGap),
    ) {
        item {
            SubScreenHeader("Developer", onBack)
            Text(
                "Feature 1 · Categorisation. This phone only. Payments seen while Sample data was on are not counted.",
                style = AppType.body,
                color = TextSecondary,
            )
            Spacer(Modifier.height(Spacing.section - Spacing.cardGap))
        }
        item { com.yourteam.expensetracker.metrics.MetricsUploadSection() } // MVP M24: all testers on the team dashboard
        item { com.yourteam.expensetracker.metrics.GoldenCoverageSection() } // MVP M20: real AI cases vs the golden dataset
        item { com.yourteam.expensetracker.metrics.InsightWordsSection() } // Feature 2: AI words on Insights cards
        items(metrics, key = { it.name }) { m ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth().clip(CardShape).background(Surface).padding(horizontal = Spacing.cardH, vertical = Spacing.cardV),
            ) {
                Column(Modifier.weight(1f)) {
                    Text(m.name, style = AppType.rowTitle, color = TextPrimary)
                    Text(m.meaning, style = AppType.body, color = TextSecondary)
                    Text("Target ${m.target} · from ${m.count} payments", style = AppType.chip, color = TextSecondary)
                }
                Spacer(Modifier.padding(start = 12.dp))
                Text(m.percent?.let { "$it%" } ?: "–", style = AppType.statValue, color = TextPrimary)
            }
        }
        item {
            Spacer(Modifier.height(Spacing.section - Spacing.cardGap))
            Text(
                "AI categoriser: " + (if (AiCategoriser.enabled) "on (${BuildConfig.AI_BASE_URL})" else "off (set ai.baseUrl in local.properties)") +
                    " · ${snapshot?.aiHints?.size ?: 0} AI answers saved on this phone",
                style = AppType.body,
                color = TextSecondary,
            )
            Spacer(Modifier.height(Spacing.section))
            Text("PIN screens seen (Usage access)", style = AppType.rowTitle, color = TextPrimary)
            Text(
                if (com.yourteam.expensetracker.notifications.PinScreenWatcher.hasAccess(context)) "On. Each time a UPI app's PIN screen opened and closed, and what happened."
                else "Off. Turn on in Profile › Sources › Payments with no SMS.",
                style = AppType.body,
                color = TextSecondary,
            )
            com.yourteam.expensetracker.notifications.PinScreenWatcher.recent(context).take(8).forEach { a ->
                val secs = (a.closedAt - a.openedAt) / 1000
                val what = when (a.state) {
                    com.yourteam.expensetracker.notifications.PinScreenWatcher.State.WAITING -> "waiting for SMS or alert"
                    com.yourteam.expensetracker.notifications.PinScreenWatcher.State.ASKED -> "nothing came: asked"
                    com.yourteam.expensetracker.notifications.PinScreenWatcher.State.DONE -> "recorded or answered"
                }
                Text("${a.app} · ${com.yourteam.expensetracker.notifications.PinScreenWatcher.time(a.openedAt)} · open ${secs}s · $what", style = AppType.body, color = TextPrimary, modifier = Modifier.padding(top = 6.dp))
            }
            Spacer(Modifier.height(Spacing.section))
            Text("PIN screen check (screen reader)", style = AppType.rowTitle, color = TextPrimary)
            Text(
                "The last keypad screen seen in each UPI app, and whether it was read as the PIN screen. " +
                    "All found = the \"Did it go through?\" pop-up can show. Never the screen's words.",
                style = AppType.body,
                color = TextSecondary,
            )
            val checks = com.yourteam.expensetracker.notifications.PinScreenCheck.all(context)
            if (checks.isEmpty()) Text("None yet. Is the screen reader on in Profile › Sources?", style = AppType.body, color = TextSecondary)
            checks.forEach { (app, at, result) ->
                val time = java.time.Instant.ofEpochMilli(at).atZone(java.time.ZoneId.systemDefault())
                    .format(java.time.format.DateTimeFormatter.ofPattern("d MMM, h:mm a"))
                Text("$app · $time", style = AppType.chip, color = TextSecondary, modifier = Modifier.padding(top = 8.dp))
                Text(result, style = AppType.body, color = TextPrimary)
            }
            Spacer(Modifier.height(Spacing.section))
            Text("Last payment app alerts", style = AppType.rowTitle, color = TextPrimary)
            Text(
                "What the app saw from GPay, PhonePe, Navi and other apps, and how it read each one. Shown on this phone only.",
                style = AppType.body,
                color = TextSecondary,
            )
            if (alerts.isEmpty()) Text("None yet. Check notification access in Profile › Sources.", style = AppType.body, color = TextSecondary)
        }
        items(alerts, key = { "n${it.id}" }) { n ->
            val time = java.time.Instant.ofEpochMilli(n.postedAt).atZone(java.time.ZoneId.systemDefault())
                .format(java.time.format.DateTimeFormatter.ofPattern("d MMM, h:mm a"))
            val readAs = com.yourteam.expensetracker.data.PaymentInputs.credit(n)?.let { c ->
                "Read as: received ${com.yourteam.expensetracker.insights.Money.format(c.paise)}" + (c.from?.let { " from $it" } ?: "")
            } ?: com.yourteam.expensetracker.data.PaymentInputs.paid(n)?.let { d ->
                "Read as: paid ${com.yourteam.expensetracker.insights.Money.format(d.paise)} to ${d.payee}"
            } ?: "Not read as a payment"
            Column(Modifier.fillMaxWidth().clip(CardShape).background(Surface).padding(horizontal = Spacing.cardH, vertical = Spacing.cardV)) {
                Text("${n.appName} · $time", style = AppType.chip, color = TextSecondary)
                Text(n.title, style = AppType.rowTitle, color = TextPrimary)
                if (n.text.isNotBlank()) Text(n.text, style = AppType.body, color = TextSecondary)
                Text(readAs, style = AppType.chip, color = TextPrimary)
            }
        }

        // Demo tools: fire the real notifications with made-up payments, so the capture flow can be
        // shown live without a payment app (which may block payments when accessibility is on).
        // Test builds only, so they never ship in release.
        if (BuildConfig.TEST_BUILD) {
            item {
                Spacer(Modifier.height(Spacing.section - Spacing.cardGap))
                TestSmsSection(repo, sampleOn = sampleOn)
            }
            item {
                Spacer(Modifier.height(Spacing.section - Spacing.cardGap))
                Text("Demo", style = AppType.rowTitle, color = TextPrimary)
                Text(
                    "Fire a real notification with a made-up payment. For the presentation; no payment app needed.",
                    style = AppType.body,
                    color = TextSecondary,
                )
                Spacer(Modifier.height(Spacing.cardGap))
            }
            item {
                DemoRow(
                    "Send “did it go through?”",
                    "The pending-payment check (no bank SMS found yet).",
                ) { fireDidItGoThrough(context) }
            }
            item {
                Spacer(Modifier.height(Spacing.cardGap))
                DemoRow(
                    "Send “what was it for?”",
                    "The category question with its best guesses.",
                ) { fireWhatWasItFor(context, repo) }
            }
        }
    }
}

/** A made-up payment each tap, so the demo can be fired repeatedly and each notification is fresh. */
private val DEMO_PAYEES = listOf(
    "Raju Tea Stall", "Blue Tokai Coffee", "Namma Metro", "Sharma Medicals", "Auto ride", "Big Bazaar",
)

private fun fireDidItGoThrough(context: android.content.Context) {
    if (!PaymentCheck.canNotify(context)) {
        Feedback.failure("Turn on notifications for this app first")
        return
    }
    val now = System.currentTimeMillis()
    val pending = PendingPayment(
        id = -now, // negative: a demo id that won't collide with a real pending payment
        dedupeKey = "demo|$now",
        appName = "GPay",
        amountText = (20..900).random().toString(),
        payee = DEMO_PAYEES.random(),
        detectedAt = now,
    )
    PaymentCheck.notifyUnconfirmed(context, pending)
    Feedback.success("Sent — check your notification shade")
}

private fun fireWhatWasItFor(context: android.content.Context, repo: PaymentRepository) {
    if (!PaymentCheck.canNotify(context)) {
        Feedback.failure("Turn on notifications for this app first")
        return
    }
    val now = System.currentTimeMillis()
    // A random amount keeps each question unique, so it's never suppressed as "already asked".
    val facts = PaymentFacts(DEMO_PAYEES.random(), paise = (20..900).random() * 100L, at = now, method = "UPI")
    val key = "demo-$now"
    CategoryPrompt.askToConfirm(context, key, facts, repo.guessForNew(facts, key))
    Feedback.success("Sent — check your notification shade")
}

@Composable
private fun DemoRow(title: String, subtitle: String, onClick: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().clip(CardShape).background(Surface)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = Spacing.cardH, vertical = Spacing.cardV),
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = AppType.rowTitle, color = TextPrimary)
            Text(subtitle, style = AppType.body, color = TextSecondary)
        }
    }
}
