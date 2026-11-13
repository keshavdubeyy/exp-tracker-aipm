package com.yourteam.expensetracker.ui.screens.detail

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.yourteam.expensetracker.insights.Money
import com.yourteam.expensetracker.insights.Payment
import com.yourteam.expensetracker.insights.UserAnswer
import com.yourteam.expensetracker.sms.SmsMessage
import com.yourteam.expensetracker.ui.screens.AnswerButtons
import com.yourteam.expensetracker.ui.screens.PageHeader
import com.yourteam.expensetracker.ui.screens.SectionLabel
import com.yourteam.expensetracker.ui.screens.formatDate
import com.yourteam.expensetracker.ui.theme.AppCard
import com.yourteam.expensetracker.ui.theme.Border
import com.yourteam.expensetracker.ui.theme.Heading
import com.yourteam.expensetracker.ui.theme.PillShape
import com.yourteam.expensetracker.ui.theme.SoftInk

/** One payment: amount, payee, status, and the trail of what recorded it. */
@Composable
fun DetailScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: DetailViewModel = viewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(7.dp),
    ) {
        item {
            TextButton(onClick = onBack, shape = PillShape) {
                Icon(com.yourteam.expensetracker.ui.theme.HugeIcons.ArrowBack, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("Back", style = MaterialTheme.typography.labelLarge)
            }
        }
        when (val s = state) {
            DetailUiState.Loading -> item {
                Box(Modifier.fillMaxWidth().padding(48.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            }
            DetailUiState.NotFound -> item {
                PageHeader(first = "Not found.", second = "It may have been merged with another record.")
            }
            is DetailUiState.Loaded -> {
                val p = s.payment
                item {
                    PageHeader(
                        first = Money.format(p.paise),
                        second = p.payee?.takeIf(String::isNotBlank)?.let { "to $it." } ?: "paid.",
                        caption = "${formatDate(p.at)} · ${statusText(p)}",
                    )
                }
                if (p.needsAnswer) item {
                    AppCard(Modifier.fillMaxWidth()) {
                        Text("Needs you", style = MaterialTheme.typography.labelMedium)
                        Spacer(Modifier.height(20.dp))
                        Heading("Did this payment go through?", style = MaterialTheme.typography.headlineSmall)
                        Spacer(Modifier.height(20.dp))
                        val id = p.started!!.id
                        AnswerButtons(onNo = { viewModel.answer(id, false) }, onYes = { viewModel.answer(id, true) })
                    }
                }
                if (p.conflict) item {
                    AppCard(Modifier.fillMaxWidth()) {
                        Text("The bank disagrees", style = MaterialTheme.typography.labelMedium)
                        Spacer(Modifier.height(20.dp))
                        Text(
                            "You said this didn't go through, but it was recorded as paid. " +
                                "If the money comes back later, it's a refund.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.height(20.dp))
                        Button(onClick = { viewModel.answer(p.started!!.id, true) }, shape = PillShape, modifier = Modifier.height(40.dp)) {
                            Text("It was paid", style = MaterialTheme.typography.labelLarge)
                        }
                    }
                }
                item {
                    Spacer(Modifier.height(16.dp))
                    SectionLabel("How we know")
                }
                evidence(p, s.sms).forEach { e -> item { EvidenceRow(e) } }
                item {
                    Text(
                        "Everything here stays on your phone.",
                        style = MaterialTheme.typography.bodySmall,
                        color = SoftInk,
                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 16.dp),
                    )
                }
            }
        }
    }
}

private data class Evidence(val title: String, val time: Long?, val detail: String? = null)

private fun evidence(p: Payment, sms: SmsMessage?): List<Evidence> = buildList {
    p.started?.let {
        add(Evidence("You started it in ${it.app}", it.at, "${Money.format(it.paise)} to ${it.payee}."))
        when (it.answer) {
            UserAnswer.YES -> add(Evidence("You confirmed it went through", null))
            UserAnswer.NO -> add(Evidence("You said it didn't go through", null))
            UserAnswer.NONE -> Unit
        }
    }
    p.chat?.let { add(Evidence("${it.source} shows it as paid", it.at, "Payment to ${it.payee} · ${Money.format(it.paise)}")) }
    p.sms?.let { add(Evidence("Bank SMS from ${it.sender}", it.at, sms?.body)) }
}

private fun statusText(p: Payment): String = when {
    p.needsAnswer -> "waiting for you"
    p.conflict -> "check this"
    else -> "recorded"
}

/** Timeline row: dot, title, time, optional detail, hairline underneath. */
@Composable
private fun EvidenceRow(e: Evidence) {
    Column {
        Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 16.dp)) {
            Box(
                Modifier
                    .padding(top = 7.dp)
                    .size(6.dp)
                    .background(MaterialTheme.colorScheme.onBackground, CircleShape),
            )
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(e.title, style = MaterialTheme.typography.titleSmall)
                e.time?.let {
                    Text(formatDate(it), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                e.detail?.takeIf(String::isNotBlank)?.let {
                    Spacer(Modifier.height(8.dp))
                    Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        HorizontalDivider(thickness = 1.dp, color = Border)
    }
}
