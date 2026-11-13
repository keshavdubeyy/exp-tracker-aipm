package com.yourteam.expensetracker.ui.screens

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.yourteam.expensetracker.data.PaymentSnapshot
import com.yourteam.expensetracker.insights.Money
import com.yourteam.expensetracker.insights.Payment
import com.yourteam.expensetracker.insights.UserAnswer
import com.yourteam.expensetracker.ui.theme.Border
import com.yourteam.expensetracker.ui.theme.Edge
import com.yourteam.expensetracker.ui.theme.PillShape
import com.yourteam.expensetracker.ui.theme.SoftInk
import com.yourteam.expensetracker.ui.theme.TagShape
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** One row in a list: a payment (with its evidence), another bank SMS, or a UPI notification. */
data class FeedItem(
    val key: String,
    val source: String, // small label: "Payment", "SMS · XX-HDFCBK", "GPay · notification"
    val dateMillis: Long,
    val body: String,
    val title: String? = null, // "₹12.00 to Chai Point"
    val chips: List<String> = emptyList(), // what recorded it: "You confirmed", "GPay chat", "Bank SMS"
    val warning: String? = null,
    /** Started payment still waiting for "Yes, paid" / "No". */
    val askPendingId: Long? = null,
    /** User said "No" but the bank / GPay says it was paid: offer "It was paid". */
    val conflictPendingId: Long? = null,
    /** Payments open their Detail screen when tapped; other rows expand in place. */
    val openKey: String? = null,
)

/** Shared Payment -> row mapping, so every screen labels payments the same way. */
fun paymentItem(p: Payment, snapshot: PaymentSnapshot): FeedItem {
    val chips = buildList {
        if (p.started?.cash == true) add("Cash · added by you")
        else if (p.started?.manual == true) add("Added by you")
        else if (p.started?.answer == UserAnswer.YES) add("You confirmed")
        p.chat?.let { add(it.source) }
        if (p.sms != null) add("Bank SMS")
        if (p.needsAnswer) add("Waiting for you")
    }
    return FeedItem(
        key = p.key,
        source = if (p.sms != null) "Payment · ${p.sms.sender}" else "Payment",
        dateMillis = p.at,
        body = snapshot.smsFor(p)?.body.orEmpty(),
        title = paymentTitle(p),
        chips = chips,
        warning = if (p.conflict) "You said this didn't go through, but it was recorded as paid." else null,
        askPendingId = p.started?.id?.takeIf { p.needsAnswer },
        conflictPendingId = p.started?.id?.takeIf { p.conflict },
        openKey = p.key,
    )
}

fun paymentTitle(p: Payment): String =
    Money.format(p.paise) + (p.payee?.takeIf(String::isNotBlank)?.let { " to $it" } ?: " paid")

/** Two-line editorial statement: first line ink, second line soft. */
@Composable
fun PageHeader(first: String, second: String, caption: String? = null) {
    Column(Modifier.padding(start = 4.dp, end = 4.dp, top = 24.dp, bottom = 24.dp)) {
        Text(text = first, style = MaterialTheme.typography.displaySmall, color = MaterialTheme.colorScheme.onBackground)
        Text(text = second, style = MaterialTheme.typography.displaySmall, color = SoftInk)
        caption?.let {
            Spacer(Modifier.height(16.dp))
            Text(text = it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** Hairline above a small label, like an editorial rail. */
@Composable
fun SectionLabel(text: String) {
    Column {
        HorizontalDivider(thickness = 1.dp, color = Border)
        Text(
            text = text,
            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Normal),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 4.dp, vertical = 12.dp),
        )
    }
}

/** One payment as a ruled row: no card, just a hairline underneath. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun FeedRow(
    item: FeedItem,
    onConfirm: (Long) -> Unit,
    onDismiss: (Long) -> Unit,
    onOpen: (String) -> Unit = {},
) {
    var expanded by rememberSaveable(item.key) { mutableStateOf(false) }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { item.openKey?.let(onOpen) ?: run { expanded = !expanded } }
            .then(if (item.openKey == null) Modifier.animateContentSize() else Modifier),
    ) {
        Column(Modifier.padding(horizontal = 4.dp, vertical = 18.dp)) {
            item.title?.let {
                Text(text = it, style = MaterialTheme.typography.titleLarge)
                Spacer(Modifier.height(4.dp))
            }
            Text(
                text = "${item.source} · ${formatDate(item.dateMillis)}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (item.chips.isNotEmpty()) {
                Spacer(Modifier.height(10.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    item.chips.forEach { SourceChip(it) }
                }
            }
            item.warning?.let {
                Spacer(Modifier.height(8.dp))
                Text(text = it, style = MaterialTheme.typography.bodyMedium)
            }
            if (item.body.isNotBlank()) {
                Spacer(Modifier.height(8.dp))
                Text(
                    text = item.body,
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (item.title != null) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                    maxLines = if (expanded) Int.MAX_VALUE else 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            item.askPendingId?.let { id ->
                Spacer(Modifier.height(12.dp))
                Text(
                    text = "Did this payment go through?",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(8.dp))
                AnswerButtons(onNo = { onDismiss(id) }, onYes = { onConfirm(id) })
            }
            item.conflictPendingId?.let { id ->
                Spacer(Modifier.height(12.dp))
                Button(onClick = { onConfirm(id) }, shape = PillShape, modifier = Modifier.height(40.dp)) {
                    Text("It was paid", style = MaterialTheme.typography.labelLarge)
                }
            }
        }
        HorizontalDivider(thickness = 1.dp, color = Border)
    }
}

/** "No" (outlined, left) and "Yes, paid" (near-black, right). */
@Composable
fun AnswerButtons(onNo: () -> Unit, onYes: () -> Unit) {
    Row {
        OutlinedButton(
            onClick = onNo,
            shape = PillShape,
            border = BorderStroke(1.dp, Edge),
            modifier = Modifier.weight(1f).height(40.dp),
        ) {
            Text("No", style = MaterialTheme.typography.labelLarge)
        }
        Spacer(Modifier.width(8.dp))
        Button(onClick = onYes, shape = PillShape, modifier = Modifier.weight(1f).height(40.dp)) {
            Text("Yes, paid", style = MaterialTheme.typography.labelLarge)
        }
    }
}

/** Small outlined tag saying what recorded a payment. */
@Composable
fun SourceChip(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onBackground,
        modifier = Modifier
            .border(1.dp, Edge, TagShape)
            .background(MaterialTheme.colorScheme.background, TagShape)
            .padding(horizontal = 10.dp, vertical = 3.dp),
    )
}

private val dateFormatter = DateTimeFormatter.ofPattern("d MMM yyyy, h:mm a")

fun formatDate(millis: Long): String =
    dateFormatter.format(Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()))
