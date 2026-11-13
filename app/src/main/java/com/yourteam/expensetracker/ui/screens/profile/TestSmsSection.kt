package com.yourteam.expensetracker.ui.screens.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.yourteam.expensetracker.data.PaymentInputs
import com.yourteam.expensetracker.data.PaymentRepository
import com.yourteam.expensetracker.insights.Money
import com.yourteam.expensetracker.parser.CreditKind
import com.yourteam.expensetracker.sms.SenderFilter
import com.yourteam.expensetracker.sms.SmsMessage
import com.yourteam.expensetracker.ui.feedback.Feedback
import com.yourteam.expensetracker.ui.theme.Accent
import com.yourteam.expensetracker.ui.theme.AppType
import com.yourteam.expensetracker.ui.theme.CardShape
import com.yourteam.expensetracker.ui.theme.Negative
import com.yourteam.expensetracker.ui.theme.OnAccent
import com.yourteam.expensetracker.ui.theme.PillShape
import com.yourteam.expensetracker.ui.theme.Spacing
import com.yourteam.expensetracker.ui.theme.Surface
import com.yourteam.expensetracker.ui.theme.SurfaceRaised
import com.yourteam.expensetracker.ui.theme.TextPrimary
import com.yourteam.expensetracker.ui.theme.TextSecondary

private const val EXAMPLE_SENDER = "AX-HDFCBK"
private const val EXAMPLE_BODY =
    "Sent Rs.250.00 From HDFC Bank A/C x1234 To TEST SHOP On 13/11/26 Ref 600012345678 Not You? Call 18002586161"

/**
 * Developer › Test an SMS (test builds only): type a made-up bank SMS and see exactly what the
 * app reads from it, using the same filter and parser as real SMS. "Add payment" saves it in
 * this app (TestSmsStore) and mixes it into the real payments as if it just arrived; each one can be
 * deleted again. Never writes to the phone's SMS inbox and never uploads anything.
 */
@Composable
fun TestSmsSection(repo: PaymentRepository, sampleOn: Boolean) {
    var sender by rememberSaveable { mutableStateOf(EXAMPLE_SENDER) }
    var body by rememberSaveable { mutableStateOf(EXAMPLE_BODY) }
    var read by rememberSaveable { mutableStateOf(false) }
    val saved by repo.testSms.messages.collectAsStateWithLifecycle()

    Column(Modifier.fillMaxWidth().clip(CardShape).background(Surface).padding(horizontal = Spacing.cardH, vertical = Spacing.cardV)) {
        Text("Test an SMS", style = AppType.rowTitle, color = TextPrimary)
        Text(
            "Type a made-up bank SMS to see what the app reads. Don't paste real SMS you'll share in screenshots.",
            style = AppType.body,
            color = TextSecondary,
        )
        Spacer(Modifier.height(12.dp))
        Field("Sender", sender, singleLine = true) { sender = it; read = false }
        Spacer(Modifier.height(8.dp))
        Field("SMS text", body, singleLine = false) { body = it; read = false }
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button("Read it", accent = true, modifier = Modifier.weight(1f)) {
                read = true
                Feedback.tick()
            }
            Button("Add payment", accent = false, modifier = Modifier.weight(1f)) {
                val result = readSms(sender, body)
                when {
                    result.skipReason != null -> Feedback.failure("The app would skip this SMS: ${result.skipReason}")
                    result.kind == null -> Feedback.failure("Not a payment the app can read")
                    else -> {
                        val m = repo.testSms.add(sender, body)
                        Feedback.success(
                            if (sampleOn) "Added. Turn Sample off to see it with your payments"
                            else "Added. Find it on Home under Transactions",
                        ) { repo.testSms.delete(m.id) }
                    }
                }
            }
        }
        if (read) {
            Spacer(Modifier.height(14.dp))
            ResultCard(readSms(sender, body))
        }
        if (saved.isNotEmpty()) {
            Spacer(Modifier.height(18.dp))
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("Added test SMS · ${saved.size}", style = AppType.rowTitle, color = TextPrimary, modifier = Modifier.weight(1f))
                Text(
                    "Delete all",
                    style = AppType.tab,
                    color = Negative,
                    modifier = Modifier
                        .heightIn(min = Spacing.circle)
                        .clip(PillShape)
                        .clickable(role = Role.Button) {
                            val removed = saved
                            repo.testSms.deleteAll()
                            Feedback.success("Deleted ${removed.size} test SMS") { removed.forEach(repo.testSms::restore) }
                        }
                        .padding(horizontal = 12.dp, vertical = 12.dp),
                )
            }
            saved.forEach { m ->
                Spacer(Modifier.height(8.dp))
                SavedRow(m) {
                    repo.testSms.delete(m.id)
                    Feedback.success("Test SMS deleted") { repo.testSms.restore(m) }
                }
            }
        }
    }
}

/** One added test SMS: sender, time, the text, and Delete. */
@Composable
private fun SavedRow(m: SmsMessage, onDelete: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(SurfaceRaised).padding(start = 14.dp, top = 10.dp, bottom = 10.dp, end = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                "${m.address} · ${java.time.format.DateTimeFormatter.ofPattern("d MMM, h:mm a").format(java.time.Instant.ofEpochMilli(m.dateMillis).atZone(java.time.ZoneId.systemDefault()))}",
                style = AppType.chip,
                color = TextSecondary,
            )
            Text(m.body, style = AppType.body, color = TextPrimary, maxLines = 2, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
        }
        Text(
            "Delete",
            style = AppType.tab,
            color = Negative,
            modifier = Modifier.heightIn(min = Spacing.circle).clip(PillShape).clickable(role = Role.Button, onClick = onDelete).padding(horizontal = 12.dp, vertical = 12.dp),
        )
    }
}

/** What the app makes of one SMS, worked out by the real filter and parser. */
private data class SmsReading(
    val skipReason: String?,
    /** "Money out", "Money in", or null when it isn't a payment. */
    val kind: String?,
    val lines: List<Pair<String, String>>,
)

private fun readSms(sender: String, body: String): SmsReading {
    val m = SmsMessage(0, sender.trim(), body.trim(), System.currentTimeMillis())
    val skip = when {
        !SenderFilter.isBankSender(m.address) -> "the sender isn't a bank ID like AX-HDFCBK"
        SenderFilter.isOtpOrPromo(m.body) -> "it reads like an OTP or an offer"
        else -> null
    }
    PaymentInputs.debit(m)?.let { d ->
        return SmsReading(skip, "Money out", listOfNotNull(
            "Amount" to Money.formatAmount(d.paise),
            d.payee?.let { "Paid to" to it },
            d.vpa?.let { "UPI ID" to it },
            d.method?.let { "Method" to it },
        ))
    }
    PaymentInputs.credit(m)?.let { c ->
        return SmsReading(skip, "Money in", listOfNotNull(
            "Amount" to Money.formatAmount(c.paise),
            c.from?.let { "From" to it },
            c.kind?.let { "Kind" to kindWords(it) },
            if (c.refund) "Refund" to "Yes" else null,
        ))
    }
    return SmsReading(skip, null, emptyList())
}

private fun kindWords(k: CreditKind) = when (k) {
    CreditKind.SALARY -> "Salary"
    CreditKind.REFUND -> "Refund"
    CreditKind.INTEREST -> "Interest"
    CreditKind.CASHBACK -> "Cashback"
}

@Composable
private fun ResultCard(r: SmsReading) {
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(SurfaceRaised).padding(16.dp)) {
        Text(r.kind ?: "Not a payment", style = AppType.rowTitle, color = TextPrimary)
        r.lines.forEach { (label, value) ->
            Row(Modifier.fillMaxWidth().padding(top = 6.dp)) {
                Text(label, style = AppType.body, color = TextSecondary, modifier = Modifier.width(80.dp))
                Text(value, style = AppType.body, color = TextPrimary)
            }
        }
        if (r.kind == null) {
            Text("No debit or credit amount found in this text.", style = AppType.body, color = TextSecondary, modifier = Modifier.padding(top = 6.dp))
        }
        if (r.skipReason != null) {
            Text("Skipped: ${r.skipReason}.", style = AppType.body, color = Negative, modifier = Modifier.padding(top = 8.dp))
        } else if (r.kind != null) {
            Text("Kept: counted like a real bank SMS.", style = AppType.body, color = TextSecondary, modifier = Modifier.padding(top = 8.dp))
        }
    }
}

@Composable
private fun Field(label: String, value: String, singleLine: Boolean, onChange: (String) -> Unit) {
    Column {
        Text(label, style = AppType.chip, color = TextSecondary)
        Spacer(Modifier.height(4.dp))
        BasicTextField(
            value = value,
            onValueChange = onChange,
            singleLine = singleLine,
            minLines = if (singleLine) 1 else 3,
            textStyle = AppType.body.copy(color = TextPrimary),
            cursorBrush = SolidColor(Accent),
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(SurfaceRaised)
                .padding(horizontal = 14.dp, vertical = 12.dp),
        )
    }
}

@Composable
private fun Button(text: String, accent: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Box(
        modifier
            .heightIn(min = Spacing.circle)
            .clip(PillShape)
            .background(if (accent) Accent else SurfaceRaised)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, style = AppType.tab, color = if (accent) OnAccent else TextPrimary, maxLines = 1)
    }
}
