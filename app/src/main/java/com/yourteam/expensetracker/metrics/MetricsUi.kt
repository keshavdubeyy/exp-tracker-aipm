package com.yourteam.expensetracker.metrics

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.foundation.layout.Row
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.yourteam.expensetracker.BuildConfig
import com.yourteam.expensetracker.ui.feedback.Feedback
import com.yourteam.expensetracker.ui.theme.Accent
import com.yourteam.expensetracker.ui.theme.AppType
import com.yourteam.expensetracker.ui.theme.CardShape
import com.yourteam.expensetracker.ui.theme.OnAccent
import com.yourteam.expensetracker.ui.theme.PillShape
import com.yourteam.expensetracker.ui.theme.Spacing
import com.yourteam.expensetracker.ui.theme.Surface
import com.yourteam.expensetracker.ui.theme.SurfaceRaised
import com.yourteam.expensetracker.ui.theme.TextPrimary
import com.yourteam.expensetracker.ui.theme.TextSecondary
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Asked once, test builds only, before anything is sent (MVP M24 / M21). */
@Composable
fun MetricsConsentDialog(onAnswer: (Boolean) -> Unit) {
    AlertDialog(
        onDismissRequest = { onAnswer(false) },
        containerColor = Surface,
        title = { Text("Help us test the app?", style = AppType.statValue, color = TextPrimary) },
        text = {
            Text(
                "Once a day this test version can send the team anonymous counts, like how many payments were sorted " +
                    "automatically and how many days you opened the app, plus one number: the total you've tracked so far. " +
                    "Never your messages, names or single payments. The team only sees all testers' totals added together. " +
                    "You can turn it off in Profile › Developer.",
                style = AppType.body, color = TextSecondary,
            )
        },
        confirmButton = { TextButton(onClick = { onAnswer(true) }) { Text("Share counts", style = AppType.tab, color = Accent) } },
        dismissButton = { TextButton(onClick = { onAnswer(false) }) { Text("Not now", style = AppType.tab, color = TextSecondary) } },
    )
}

/** Developer screen section: what this phone sends to the team dashboard, and when it last did. */
@Composable
fun MetricsUploadSection() {
    val context = LocalContext.current
    var on by remember { mutableStateOf(UsageLog.consent(context) == true) }
    var refresh by remember { mutableIntStateOf(0) }
    val counts = remember(refresh) { MetricsUpload.buildCounts(context) }
    val last = UsageLog.lastUpload(context).let { if (it == 0L) "never" else SimpleDateFormat("d MMM, h:mm a", Locale.getDefault()).format(Date(it)) }

    Column(Modifier.fillMaxWidth().clip(CardShape).background(Surface).padding(horizontal = Spacing.cardH, vertical = Spacing.cardV)) {
        Text("Team dashboard upload", style = AppType.rowTitle, color = TextPrimary)
        Text(
            if (!MetricsUpload.available) "Off in this build (set metrics.baseUrl or ai.baseUrl in local.properties)."
            else "Server ${BuildConfig.METRICS_BASE_URL} · once a day · last sent $last · ${UsageLog.lastResult(context)}",
            style = AppType.body, color = TextSecondary,
        )
        Text("Tester ID T-${UsageLog.installId(context).take(6)} (scrambled, not linked to you)", style = AppType.chip, color = TextSecondary, modifier = Modifier.padding(top = 4.dp))
        if (MetricsUpload.available) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 8.dp)) {
                Text("Share anonymous counts and total tracked", style = AppType.body, color = TextPrimary, modifier = Modifier.weight(1f))
                Switch(
                    checked = on,
                    onCheckedChange = {
                        on = it
                        Feedback.toggle(it) // F16
                        UsageLog.setConsent(context, it)
                        MetricsUpload.schedule(context)
                    },
                    colors = SwitchDefaults.colors(checkedTrackColor = Accent, checkedThumbColor = OnAccent),
                )
            }
            if (on) {
                Text(
                    "Send now",
                    style = AppType.tab,
                    color = OnAccent,
                    modifier = Modifier
                        .padding(top = 6.dp)
                        .heightIn(min = Spacing.circle)
                        .clip(PillShape)
                        .background(Accent)
                        .clickable(role = Role.Button) {
                            MetricsUpload.sendNow(context)
                            Feedback.success("Sending today's counts")
                            refresh++
                        }
                        .padding(horizontal = 18.dp, vertical = 12.dp),
                )
            }
        }
        Spacer(Modifier.height(10.dp))
        Text("What's sent today", style = AppType.chip, color = TextSecondary)
        Column(Modifier.fillMaxWidth().padding(top = 4.dp).clip(CardShape).background(SurfaceRaised).padding(12.dp)) {
            counts.entries.sortedBy { it.key }.forEach { (k, v) -> Text("$k  $v", style = AppType.body, color = TextPrimary) }
        }
    }
}

/**
 * Developer screen: this phone's real AI cases by golden-dataset scenario (MVP M20). The payee names of
 * uncovered ones are shown here only, so the team can write v2 cases from them; they are never uploaded.
 */
@Composable
fun GoldenCoverageSection() {
    val context = LocalContext.current
    val repo = com.yourteam.expensetracker.data.PaymentRepository.get(context)
    val result = remember {
        repo.realSnapshotNow()?.let { GoldenCoverage.compute(it, repo::categoryRecord) }
    }
    Column(Modifier.fillMaxWidth().clip(CardShape).background(Surface).padding(horizontal = Spacing.cardH, vertical = Spacing.cardV)) {
        Text("Golden dataset coverage", style = AppType.rowTitle, color = TextPrimary)
        Text(
            "Payments the AI answered for on this phone, by golden-dataset scenario. Only the counts go to the team dashboard.",
            style = AppType.body, color = TextSecondary,
        )
        if (result == null || result.byScenario.isEmpty()) {
            Text("None yet. The AI is asked only about payees the app couldn't place.", style = AppType.body, color = TextSecondary, modifier = Modifier.padding(top = 6.dp))
        } else {
            result.byScenario.entries.sortedByDescending { it.value.asked }.forEach { (s, c) ->
                val label = s.key.replace('_', ' ').replaceFirstChar { it.uppercase() } + if (s.covered) "" else " · not in dataset"
                Text(
                    "$label · ${c.asked} payments · ${c.picked} picked · ${c.top3} in AI top 3",
                    style = AppType.body, color = if (s.covered) TextSecondary else TextPrimary, modifier = Modifier.padding(top = 6.dp),
                )
            }
            if (result.uncoveredExamples.isNotEmpty()) {
                Spacer(Modifier.height(10.dp))
                Text("Not in the dataset yet (v2 candidates, this phone only)", style = AppType.rowTitle, color = TextPrimary)
                result.uncoveredExamples.forEach { (s, name) ->
                    Text("$name · ${s.key.replace('_', ' ')}", style = AppType.body, color = TextSecondary, modifier = Modifier.padding(top = 4.dp))
                }
            }
        }
    }
}

/** Developer switch for Feature 2: AI words on Insights cards (on by default), to compare with the app's own words. */
@Composable
fun InsightWordsSection() {
    val context = LocalContext.current
    val writer = remember { com.yourteam.expensetracker.data.InsightWriter.get(context) }
    var on by remember { mutableStateOf(writer.isOn()) }
    Column(Modifier.fillMaxWidth().clip(CardShape).background(Surface).padding(horizontal = Spacing.cardH, vertical = Spacing.cardV)) {
        Text("AI words on Insights cards", style = AppType.rowTitle, color = TextPrimary)
        Text(
            if (!writer.available) "Off in this build (set ai.baseUrl in local.properties)."
            else "The app works out every number; the AI only words the card. Off = the app's own words.",
            style = AppType.body, color = TextSecondary,
        )
        if (writer.available) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 8.dp)) {
                Text("Use AI words", style = AppType.body, color = TextPrimary, modifier = Modifier.weight(1f))
                Switch(
                    checked = on,
                    onCheckedChange = {
                        on = it
                        Feedback.toggle(it) // F16
                        writer.setOn(it)
                    },
                    colors = SwitchDefaults.colors(checkedTrackColor = Accent, checkedThumbColor = OnAccent),
                )
            }
        }
    }
}
