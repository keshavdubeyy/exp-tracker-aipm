package com.yourteam.expensetracker.ui.screens.ask

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.yourteam.expensetracker.ui.screens.SubScreenHeader
import com.yourteam.expensetracker.ui.theme.AppType
import com.yourteam.expensetracker.ui.theme.Spacing
import com.yourteam.expensetracker.ui.theme.TextSecondary
import com.yourteam.expensetracker.ui.screens.SectionLabel
import com.yourteam.expensetracker.ui.theme.Border
import com.yourteam.expensetracker.ui.theme.CardShape
import com.yourteam.expensetracker.ui.theme.CardSurface
import com.yourteam.expensetracker.ui.theme.Edge
import com.yourteam.expensetracker.ui.theme.MediaShape
import com.yourteam.expensetracker.ui.theme.PillShape

/** Ask questions about spending. Answers are worked out on the phone (insights/Answers). */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AskScreen(modifier: Modifier = Modifier, onBack: () -> Unit = {}, viewModel: AskViewModel = viewModel()) {
    val exchanges by viewModel.exchanges.collectAsStateWithLifecycle()
    var text by rememberSaveable { mutableStateOf("") }
    val listState = rememberLazyListState()
    fun send(q: String) { viewModel.ask(q); text = "" }

    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { if (exchanges.isEmpty()) focus.requestFocus() }
    LaunchedEffect(exchanges.size) {
        if (exchanges.isNotEmpty()) listState.animateScrollToItem(listState.layoutInfo.totalItemsCount - 1)
    }

    Column(modifier.fillMaxSize().imePadding()) {
        LazyColumn(
            state = listState,
            modifier = Modifier.weight(1f).fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = Spacing.screen, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                SubScreenHeader("Ask about your spending", onBack)
                Spacer(Modifier.height(8.dp))
                Text(
                    "Answers are worked out on your phone from your payments. Nothing is sent anywhere.",
                    style = AppType.body,
                    color = TextSecondary,
                )
                Spacer(Modifier.height(Spacing.section))
            }
            item {
                SectionLabel(if (exchanges.isEmpty()) "Try asking" else "More to ask")
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    viewModel.suggestions.forEach { SuggestionPill(it) { send(it) } }
                }
                Spacer(Modifier.height(12.dp))
            }
            items(exchanges, key = { it.id }) { ex ->
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    QuestionBubble(ex.question)
                    AnswerShell(ex.answer.text, ex.answer.rows)
                }
            }
        }

        HorizontalDivider(thickness = 1.dp, color = Border)
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextField(
                value = text,
                onValueChange = { text = it },
                placeholder = { Text("Ask about your spending", style = MaterialTheme.typography.bodyMedium) },
                singleLine = true,
                shape = PillShape,
                textStyle = MaterialTheme.typography.bodyMedium,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                keyboardActions = KeyboardActions(onSend = { send(text) }),
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = CardSurface,
                    unfocusedContainerColor = CardSurface,
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent,
                ),
                modifier = Modifier.weight(1f).focusRequester(focus),
            )
            Spacer(Modifier.width(8.dp))
            IconButton(
                onClick = { send(text) },
                enabled = text.isNotBlank(),
                modifier = Modifier.size(52.dp),
                colors = IconButtonDefaults.iconButtonColors(
                    containerColor = MaterialTheme.colorScheme.onBackground,
                    contentColor = MaterialTheme.colorScheme.background,
                    disabledContainerColor = Border,
                    disabledContentColor = MaterialTheme.colorScheme.background,
                ),
            ) {
                Icon(com.yourteam.expensetracker.ui.theme.HugeIcons.ArrowForward, contentDescription = "Ask")
            }
        }
    }
}

@Composable
private fun SuggestionPill(text: String, onClick: () -> Unit) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onBackground,
        modifier = Modifier
            .border(1.dp, Edge, PillShape)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp),
    )
}

/** The user's question: near-black, right-aligned. */
@Composable
private fun QuestionBubble(text: String) {
    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterEnd) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.background,
            modifier = Modifier
                .widthIn(max = 300.dp)
                .background(MaterialTheme.colorScheme.onBackground, MediaShape)
                .padding(horizontal = 16.dp, vertical = 10.dp),
        )
    }
}

/** The answer: a pale-gray shell, with the numbers behind it as ruled rows. */
@Composable
private fun AnswerShell(text: String, rows: List<Pair<String, String>>) {
    Column(
        Modifier
            .fillMaxWidth()
            .background(CardSurface, CardShape)
            .padding(20.dp),
    ) {
        Text(text, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onBackground)
        if (rows.isNotEmpty()) {
            Spacer(Modifier.height(12.dp))
            rows.forEach { (label, value) ->
                HorizontalDivider(thickness = 1.dp, color = Border)
                Row(Modifier.fillMaxWidth().padding(vertical = 10.dp)) {
                    Text(label, style = MaterialTheme.typography.bodyMedium, color = TextSecondary, modifier = Modifier.weight(1f))
                    Text(value, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onBackground)
                }
            }
        }
    }
}
