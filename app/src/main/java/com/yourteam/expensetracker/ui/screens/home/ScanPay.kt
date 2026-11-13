package com.yourteam.expensetracker.ui.screens.home

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.codescanner.GmsBarcodeScannerOptions
import com.google.mlkit.vision.codescanner.GmsBarcodeScanning
import com.yourteam.expensetracker.categoriser.PaymentFacts
import com.yourteam.expensetracker.data.PaymentRepository
import com.yourteam.expensetracker.data.ScanPayState
import com.yourteam.expensetracker.insights.Money
import com.yourteam.expensetracker.notifications.UpiApps
import com.yourteam.expensetracker.parser.Amounts
import com.yourteam.expensetracker.parser.UpiQr
import com.yourteam.expensetracker.parser.UpiResponse
import com.yourteam.expensetracker.parser.UpiResult
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
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private val amountInput = Regex("""^\d{0,7}(\.\d{0,2})?$""")

/** A UPI app on this phone that can take a upi://pay link. */
private class UpiAppChoice(val pkg: String, val label: String, val icon: ImageBitmap?)

/** Installed UPI apps: last used first, then the well-known ones, then the rest by name. */
private fun installedUpiApps(context: Context): List<UpiAppChoice> {
    val pm = context.packageManager
    val last = ScanPayState.lastApp(context)
    return pm.queryIntentActivities(Intent(Intent.ACTION_VIEW, Uri.parse("upi://pay")), 0)
        .distinctBy { it.activityInfo.packageName }
        .filter { it.activityInfo.packageName != context.packageName }
        .map { ri ->
            val pkg = ri.activityInfo.packageName
            UpiAppChoice(
                pkg = pkg,
                label = UpiApps.byPackage[pkg] ?: ri.loadLabel(pm).toString(),
                icon = runCatching { ri.loadIcon(pm).toBitmap(96, 96).asImageBitmap() }.getOrNull(),
            )
        }
        .sortedWith(compareBy<UpiAppChoice>({ it.pkg != last }, { it.pkg !in UpiApps.byPackage }, { it.label }))
}

/** What the one result modal shows when the user is back from the UPI app. */
private sealed interface PayOutcome {
    val current: ScanPayState.Current
    data class Asking(override val current: ScanPayState.Current) : PayOutcome
    data class Saved(override val current: ScanPayState.Current) : PayOutcome
    data class Failed(override val current: ScanPayState.Current) : PayOutcome
}

/**
 * Scan & Pay (MVP M7). No screen reading. We scan the QR in our app; in one sheet the user picks
 * the UPI app, amount and category; we open that app straight on its payment screen, where they
 * check the amount and enter the PIN. When they come back (from the UPI app, Home or any other
 * app), one modal settles it:
 *  - The UPI app said SUCCESS, or a bank SMS / app alert already confirmed it: "Payment saved".
 *  - It said FAILURE: "Payment didn't go through", nothing counted.
 *  - Otherwise: "Did this payment go through?" Yes / Not paid.
 * The payment card shows under Payments as soon as the UPI app opens ("Needs check" until settled).
 * Bump [startKey] to open the scanner.
 */
@Composable
fun ScanPayHost(startKey: Int) {
    val context = LocalContext.current
    val repo = remember { PaymentRepository.get(context) }
    val scope = rememberCoroutineScope()
    var scanned by remember { mutableStateOf<UpiQr?>(null) }
    var outcome by remember { mutableStateOf<PayOutcome?>(null) }

    fun settle(current: ScanPayState.Current, result: UpiResult) {
        ScanPayState.clear(context) // settled once only, whatever happens next
        when (result) {
            UpiResult.SUCCESS -> { repo.answer(current.id, yes = true, via = com.yourteam.expensetracker.metrics.CaptureLog.Via.SCAN); outcome = PayOutcome.Saved(current) }
            UpiResult.FAILURE -> { repo.answer(current.id, yes = false); outcome = PayOutcome.Failed(current) }
            UpiResult.UNKNOWN -> scope.launch {
                // A bank SMS or app alert may already have confirmed it: then there's nothing to ask.
                outcome = if (repo.isSettled(current.id)) PayOutcome.Saved(current) else PayOutcome.Asking(current)
            }
        }
    }

    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { res ->
        val current = ScanPayState.current(context) ?: return@rememberLauncherForActivityResult
        val data = res.data
        settle(current, UpiResponse.parse(data?.getStringExtra("response"), data?.getStringExtra("Status")))
    }

    // Back in our app without a result (went Home from the UPI app, switched apps, opened us later).
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, event ->
            if (event != Lifecycle.Event.ON_RESUME) return@LifecycleEventObserver
            scope.launch {
                delay(700) // a result from the UPI app, if any, arrives first and clears the state
                if (outcome != null) return@launch
                val current = ScanPayState.current(context) ?: return@launch
                if (System.currentTimeMillis() - current.startedAt < 3_000) return@launch
                settle(current, UpiResult.UNKNOWN)
            }
        }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(startKey) {
        if (startKey <= 0) return@LaunchedEffect
        val options = GmsBarcodeScannerOptions.Builder().setBarcodeFormats(Barcode.FORMAT_QR_CODE).build()
        GmsBarcodeScanning.getClient(context, options).startScan()
            .addOnSuccessListener { code ->
                val qr = UpiQr.parse(code.rawValue)
                if (qr == null) Feedback.failure("That isn't a UPI payment QR") else { Feedback.tick(); scanned = qr }
            }
            .addOnFailureListener { Feedback.failure("Couldn't open the scanner. Update Google Play services.") }
        // Cancelled: nothing to do.
    }

    scanned?.let { qr ->
        PaySheet(
            qr = qr,
            repo = repo,
            onDismiss = { scanned = null },
            onPay = { app, amountText, category ->
                // Straight to the chosen app's payment screen: no "Open with" chooser.
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(qr.payUri(amountText))).setPackage(app.pkg)
                scanned = null
                ScanPayState.setLastApp(context, app.pkg)
                scope.launch {
                    val id = repo.startScanPayment(qr.displayName, qr.vpa, amountText, category, appName = app.label)
                    ScanPayState.set(
                        context,
                        ScanPayState.Current(id, qr.displayName, amountText, System.currentTimeMillis(), app.label, category),
                    )
                    runCatching { launcher.launch(intent) }.onFailure {
                        repo.answer(id, yes = false)
                        ScanPayState.clear(context)
                        Feedback.failure("Couldn't open ${app.label}")
                    }
                }
            },
        )
    }

    outcome?.let { o ->
        PayResultSheet(
            outcome = o,
            onAnswer = { yes ->
                PaymentActions.answer(repo, o.current.id, yes) // message + Undo (FEEDBACK.md)
                outcome = null
            },
            onDone = { outcome = null },
        )
    }
}

/** UPI app, amount (fixed if the QR has one) and our top 3 categories, then hand off. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PaySheet(
    qr: UpiQr,
    repo: PaymentRepository,
    onDismiss: () -> Unit,
    onPay: (app: UpiAppChoice, amountText: String, category: String?) -> Unit,
) {
    val context = LocalContext.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val apps = remember { installedUpiApps(context) }
    var app by remember { mutableStateOf(apps.firstOrNull()) }
    val fixed = qr.amountText
    var amount by remember { mutableStateOf(fixed?.removeSuffix(".00") ?: "") }
    val paise = Amounts.toPaise(amount.trimEnd('.'))?.takeIf { it > 0 }
    // Same categoriser as everywhere: saved choice → merchant → shop words → AI.
    val guess = remember(qr) { repo.guessForNew(PaymentFacts(qr.displayName, qr.vpa, method = "UPI")) }
    var category by remember(qr) { mutableStateOf(guess.category) }
    val amountFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) { if (fixed == null) amountFocus.requestFocus() }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState, containerColor = Surface, dragHandle = null) {
        Column(Modifier.navigationBarsPadding().imePadding().padding(top = 20.dp, bottom = 16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(start = Spacing.screen, end = Spacing.screen - 4.dp)) {
                Column(Modifier.weight(1f)) {
                    Text("Pay ${qr.displayName}", style = AppType.statValue, color = TextPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(qr.vpa, style = AppType.body, color = TextSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                IconButton(onClick = onDismiss, modifier = Modifier.size(Spacing.circle).clip(PillShape).background(SurfaceRaised)) {
                    Icon(com.yourteam.expensetracker.ui.theme.HugeIcons.Close, contentDescription = "Close", tint = TextPrimary)
                }
            }
            Spacer(Modifier.height(20.dp))
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = Spacing.screen)) {
                Text("₹", style = AppType.hero, color = if (amount.isEmpty()) TextSecondary else TextPrimary)
                Box {
                    if (amount.isEmpty()) Text("0", style = AppType.hero, color = TextSecondary)
                    BasicTextField(
                        value = amount,
                        onValueChange = { if (fixed == null && amountInput.matches(it)) amount = it },
                        readOnly = fixed != null,
                        textStyle = AppType.hero.copy(color = TextPrimary),
                        cursorBrush = SolidColor(Accent),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.fillMaxWidth().focusRequester(amountFocus),
                    )
                }
            }
            if (fixed != null) Text("Amount set by the QR code", style = AppType.chip, color = TextSecondary, modifier = Modifier.padding(horizontal = Spacing.screen))

            Spacer(Modifier.height(20.dp))
            Text("Pay with", style = AppType.chip, color = TextSecondary, modifier = Modifier.padding(horizontal = Spacing.screen).padding(bottom = 10.dp))
            if (apps.isEmpty()) {
                Text(
                    "No UPI app found. Install GPay, PhonePe or another UPI app.",
                    style = AppType.body,
                    color = TextSecondary,
                    modifier = Modifier.padding(horizontal = Spacing.screen),
                )
            } else {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = Spacing.screen),
                ) {
                    apps.forEach { a ->
                        val selected = a.pkg == app?.pkg
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .height(44.dp)
                                .clip(PillShape)
                                .background(if (selected) Accent else SurfaceRaised)
                                .clickable(role = Role.Button) { Feedback.tick(); app = a }
                                .padding(start = 6.dp, end = 14.dp),
                        ) {
                            a.icon?.let { Image(it, contentDescription = null, modifier = Modifier.size(32.dp).clip(CircleShape)) }
                            Spacer(Modifier.width(8.dp))
                            Text(a.label, style = AppType.chip, color = if (selected) OnAccent else TextPrimary, maxLines = 1)
                        }
                    }
                }
            }

            Spacer(Modifier.height(20.dp))
            Text(
                if (guess.category != null) "Category" else "What's it for? We'll remember it for ${qr.displayName}.",
                style = AppType.chip,
                color = TextSecondary,
                modifier = Modifier.padding(horizontal = Spacing.screen).padding(bottom = 10.dp),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(horizontal = Spacing.screen)) {
                guess.suggestions.take(3).forEach { c ->
                    val selected = c == category
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .weight(1f)
                            .height(44.dp)
                            .clip(PillShape)
                            .background(if (selected) Accent else SurfaceRaised)
                            .clickable(role = Role.Button) { Feedback.tick(); category = if (selected) null else c }
                            .padding(horizontal = 8.dp),
                    ) {
                        Text(c, style = AppType.chip, color = if (selected) OnAccent else TextPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
            Spacer(Modifier.height(24.dp))
            val ready = paise != null && app != null
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .padding(horizontal = Spacing.screen)
                    .fillMaxWidth()
                    .height(Spacing.button)
                    .clip(PillShape)
                    .background(if (ready) Accent else SurfaceRaised)
                    .clickable(enabled = ready, role = Role.Button) {
                        val p = paise ?: return@clickable
                        val a = app ?: return@clickable
                        onPay(a, Money.plainAmount(p), category)
                    },
            ) {
                Text(
                    when {
                        paise == null -> "Enter an amount"
                        app == null -> "No UPI app"
                        else -> "Pay ${Money.formatAmount(paise)} with ${app!!.label}"
                    },
                    style = AppType.tab,
                    color = if (ready) OnAccent else TextSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(Modifier.height(10.dp))
            Text(
                "You'll check the amount and enter your PIN in ${app?.label ?: "your UPI app"}. We never see it.",
                style = AppType.chip,
                color = TextSecondary,
                modifier = Modifier.padding(horizontal = Spacing.screen),
            )
        }
    }
}

/**
 * The one modal after paying. Asks "Did this payment go through?" only when neither the UPI app
 * nor a bank SMS / app alert told us; otherwise shows what happened.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PayResultSheet(outcome: PayOutcome, onAnswer: (Boolean) -> Unit, onDone: () -> Unit) {
    val c = outcome.current
    val amount = "₹${c.amountText.removeSuffix(".00")}"
    val via = c.app.ifEmpty { "your UPI app" }
    LaunchedEffect(outcome) {
        when (outcome) {
            is PayOutcome.Saved -> { Feedback.success(null); delay(3_000); onDone() } // haptic only; the sheet says it
            is PayOutcome.Failed -> Feedback.failure(null)
            is PayOutcome.Asking -> Unit
        }
    }
    ModalBottomSheet(onDismissRequest = onDone, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true), containerColor = Surface, dragHandle = null) {
        Column(Modifier.navigationBarsPadding().padding(horizontal = Spacing.screen, vertical = 20.dp)) {
            Text(
                when (outcome) {
                    is PayOutcome.Asking -> "Did this payment go through?"
                    is PayOutcome.Saved -> "Payment saved"
                    is PayOutcome.Failed -> "Payment didn't go through"
                },
                style = AppType.statValue,
                color = TextPrimary,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                when (outcome) {
                    is PayOutcome.Asking -> "$amount to ${c.payee} in $via"
                    is PayOutcome.Saved -> "$amount to ${c.payee} · paid in $via" + (c.category?.let { " · $it" } ?: "")
                    is PayOutcome.Failed -> "$amount to ${c.payee}. Nothing was counted."
                },
                style = AppType.body,
                color = TextSecondary,
            )
            Spacer(Modifier.height(20.dp))
            if (outcome is PayOutcome.Asking) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(false to "Not paid", true to "Yes, paid").forEach { (yes, label) ->
                        SheetButton(label, primary = yes, modifier = Modifier.weight(1f)) { onAnswer(yes) }
                    }
                }
            } else {
                SheetButton("Done", primary = outcome is PayOutcome.Saved, modifier = Modifier.fillMaxWidth(), onClick = onDone)
            }
        }
    }
}

@Composable
private fun SheetButton(label: String, primary: Boolean, modifier: Modifier, onClick: () -> Unit) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .height(Spacing.button)
            .clip(PillShape)
            .background(if (primary) Accent else SurfaceRaised)
            .clickable(role = Role.Button, onClick = onClick),
    ) { Text(label, style = AppType.tab, color = if (primary) OnAccent else TextPrimary) }
}
