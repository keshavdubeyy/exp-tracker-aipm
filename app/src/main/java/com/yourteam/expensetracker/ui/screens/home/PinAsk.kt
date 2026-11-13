package com.yourteam.expensetracker.ui.screens.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.yourteam.expensetracker.categoriser.PaymentFacts
import com.yourteam.expensetracker.data.PaymentRepository
import com.yourteam.expensetracker.insights.Money
import com.yourteam.expensetracker.notifications.PinScreenWatcher
import com.yourteam.expensetracker.parser.Amounts
import com.yourteam.expensetracker.ui.feedback.Feedback
import com.yourteam.expensetracker.ui.feedback.PaymentActions
import com.yourteam.expensetracker.ui.theme.Accent
import com.yourteam.expensetracker.ui.theme.AppType
import com.yourteam.expensetracker.ui.theme.OnAccent
import com.yourteam.expensetracker.ui.theme.PillShape
import com.yourteam.expensetracker.ui.theme.Spacing
import com.yourteam.expensetracker.ui.theme.Surface
import com.yourteam.expensetracker.ui.theme.SurfaceRaised
import com.yourteam.expensetracker.ui.theme.TextPrimary
import com.yourteam.expensetracker.ui.theme.TextSecondary
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

private val amountInput = Regex("""^\d{0,7}(\.\d{0,2})?$""")

/**
 * Without accessibility: a UPI app showed its PIN screen, and no bank SMS, app alert or Scan & Pay
 * record followed ([PinScreenWatcher]). Whenever the user is in our app, ask once:
 * "Did your GPay payment go through?" → amount, who, category → saved like any payment.
 */
@Composable
fun PinAskHost() {
    val context = LocalContext.current
    val repo = remember { PaymentRepository.get(context) }
    val scope = rememberCoroutineScope()
    var attempt by remember { mutableStateOf<PinScreenWatcher.Attempt?>(null) }

    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME && attempt == null) scope.launch {
                attempt = withContext(Dispatchers.IO) { PinScreenWatcher.unanswered(context) }
            }
        }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }

    attempt?.let { a ->
        PinAskSheet(
            attempt = a,
            repo = repo,
            onNo = {
                PinScreenWatcher.resolve(context, a.openedAt)
                Feedback.success("Okay. Nothing was recorded")
                attempt = null
            },
            onSave = { amountText, payee, category ->
                PinScreenWatcher.resolve(context, a.openedAt)
                attempt = null
                scope.launch {
                    val name = payee.ifBlank { "Paid in ${a.app}" }
                    // Saved as a started payment the user confirmed: a late bank SMS with the same
                    // amount links to it instead of counting twice.
                    val id = repo.startScanPayment(name, "", amountText, category.takeIf { payee.isNotBlank() }, appName = a.app)
                    PaymentActions.answer(repo, id, yes = true) // message + Undo (FEEDBACK.md)
                    if (category != null && payee.isBlank()) {
                        val snap = withTimeoutOrNull(10_000) {
                            repo.snapshot.first { s -> s?.payments?.any { it.key == "started-$id" } == true }
                        }
                        snap?.payments?.firstOrNull { it.key == "started-$id" }?.let { repo.setCategory(it, category, onlyThis = true) }
                    }
                }
            },
            onLater = { attempt = null }, // asked again next time the app opens, until answered
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PinAskSheet(
    attempt: PinScreenWatcher.Attempt,
    repo: PaymentRepository,
    onNo: () -> Unit,
    onSave: (amountText: String, payee: String, category: String?) -> Unit,
    onLater: () -> Unit,
) {
    var amount by remember { mutableStateOf("") }
    var payee by remember { mutableStateOf("") }
    val paise = Amounts.toPaise(amount.trimEnd('.'))?.takeIf { it > 0 }
    val guess = remember(payee) { repo.guessForNew(PaymentFacts(payee.ifBlank { null }, method = "UPI")) }
    var category by remember(guess) { mutableStateOf(guess.category) }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }

    ModalBottomSheet(onDismissRequest = onLater, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true), containerColor = Surface, dragHandle = null) {
        Column(Modifier.navigationBarsPadding().imePadding().padding(top = 20.dp, bottom = 16.dp)) {
            Text(
                "Did your ${attempt.app} payment go through?",
                style = AppType.statValue,
                color = TextPrimary,
                modifier = Modifier.padding(horizontal = Spacing.screen),
            )
            Text(
                "You entered your UPI PIN at ${PinScreenWatcher.time(attempt.openedAt)}. Add the amount so we can save it.",
                style = AppType.body,
                color = TextSecondary,
                modifier = Modifier.padding(horizontal = Spacing.screen),
            )
            Spacer(Modifier.height(16.dp))
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = Spacing.screen)) {
                Text("₹", style = AppType.hero, color = if (amount.isEmpty()) TextSecondary else TextPrimary)
                Box {
                    if (amount.isEmpty()) Text("0", style = AppType.hero, color = TextSecondary)
                    BasicTextField(
                        value = amount,
                        onValueChange = { if (amountInput.matches(it)) amount = it },
                        textStyle = AppType.hero.copy(color = TextPrimary),
                        cursorBrush = SolidColor(Accent),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.fillMaxWidth().focusRequester(focus),
                    )
                }
            }
            Spacer(Modifier.height(12.dp))
            Box(
                Modifier.padding(horizontal = Spacing.screen).fillMaxWidth().height(48.dp).clip(PillShape).background(SurfaceRaised).padding(horizontal = 16.dp),
                contentAlignment = Alignment.CenterStart,
            ) {
                if (payee.isEmpty()) Text("Who did you pay? (optional)", style = AppType.body, color = TextSecondary)
                BasicTextField(
                    value = payee,
                    onValueChange = { if (it.length <= 40) payee = it },
                    textStyle = AppType.body.copy(color = TextPrimary),
                    cursorBrush = SolidColor(Accent),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            Spacer(Modifier.height(16.dp))
            Text("Category", style = AppType.chip, color = TextSecondary, modifier = Modifier.padding(horizontal = Spacing.screen).padding(bottom = 10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(horizontal = Spacing.screen)) {
                guess.suggestions.take(3).forEach { c ->
                    val selected = c == category
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier.weight(1f).height(44.dp).clip(PillShape)
                            .background(if (selected) Accent else SurfaceRaised)
                            .clickable(role = Role.Button) { Feedback.tick(); category = if (selected) null else c }
                            .padding(horizontal = 8.dp),
                    ) { Text(c, style = AppType.chip, color = if (selected) OnAccent else TextPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis) }
                }
            }
            Spacer(Modifier.height(20.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(horizontal = Spacing.screen)) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier.weight(1f).height(Spacing.button).clip(PillShape).background(SurfaceRaised).clickable(role = Role.Button, onClick = onNo),
                ) { Text("Not paid", style = AppType.tab, color = TextPrimary) }
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier.weight(1f).height(Spacing.button).clip(PillShape)
                        .background(if (paise != null) Accent else SurfaceRaised)
                        .clickable(enabled = paise != null, role = Role.Button) {
                            paise?.let { onSave(Money.plainAmount(it), payee.trim(), category) }
                        },
                ) {
                    Text(
                        paise?.let { "Save ${Money.formatAmount(it)}" } ?: "Enter amount",
                        style = AppType.tab,
                        color = if (paise != null) OnAccent else TextSecondary,
                    )
                }
            }
        }
    }
}
