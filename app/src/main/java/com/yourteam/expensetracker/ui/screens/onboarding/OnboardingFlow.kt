package com.yourteam.expensetracker.ui.screens.onboarding

import android.Manifest
import com.yourteam.expensetracker.ui.theme.Spacing
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.ui.semantics.Role
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.yourteam.expensetracker.data.PaymentRepository
import com.yourteam.expensetracker.insights.FirstLooks
import com.yourteam.expensetracker.insights.Money
import com.yourteam.expensetracker.notifications.GPayScreenReader
import com.yourteam.expensetracker.notifications.PaymentCheck
import com.yourteam.expensetracker.notifications.UpiApps
import com.yourteam.expensetracker.parser.AccountParser
import com.yourteam.expensetracker.ui.feedback.Feedback
import com.yourteam.expensetracker.ui.screens.InitialsCircle
import com.yourteam.expensetracker.ui.theme.Accent
import com.yourteam.expensetracker.ui.theme.AppType
import com.yourteam.expensetracker.ui.theme.Bg
import com.yourteam.expensetracker.ui.theme.CardShape
import com.yourteam.expensetracker.ui.theme.Negative
import com.yourteam.expensetracker.ui.theme.OnAccent
import com.yourteam.expensetracker.ui.theme.PillShape
import com.yourteam.expensetracker.ui.theme.Positive
import com.yourteam.expensetracker.ui.theme.Surface
import com.yourteam.expensetracker.ui.theme.SurfaceRaised
import com.yourteam.expensetracker.ui.theme.TextPrimary
import com.yourteam.expensetracker.ui.theme.TextSecondary
import kotlinx.coroutines.delay
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private enum class Ob { WELCOME, SMS, IMPORT, SORT, FIRST_LOOK, EXTRAS, SMS_DENIED }

/**
 * First-run flow (ONBOARDING.md, MVP M1 + M2 + M23). Starts at the first unfinished step.
 * [returning]: onboarding was finished before but SMS access is now off. Only the SMS steps
 * show, and granting it goes straight back to the app.
 */
@Composable
fun OnboardingFlow(onDone: () -> Unit, returning: Boolean = false) {
    val context = LocalContext.current
    val repo = remember { PaymentRepository.get(context) }
    val sample by repo.sampleMode.collectAsStateWithLifecycle()
    LaunchedEffect(sample) { if (sample) repo.setSampleMode(false) } // First look must show real numbers

    var screen by rememberSaveable {
        mutableStateOf(
            when {
                !Onboarding.hasSms(context) -> if (returning || Onboarding.isStarted(context)) Ob.SMS else Ob.WELCOME
                !Onboarding.isImportDone(context) -> Ob.IMPORT
                !Onboarding.isSortDone(context) -> Ob.SORT
                else -> Ob.FIRST_LOOK
            },
        )
    }
    var forward by remember { mutableStateOf(true) }
    fun go(to: Ob, ahead: Boolean = true) { forward = ahead; screen = to }
    fun smsGranted() {
        Feedback.success() // F28
        if (returning) onDone() else go(Ob.IMPORT)
    }
    fun finish() { Onboarding.markComplete(context); onDone() } // F34: no message

    when (screen) {
        Ob.SMS -> BackHandler(enabled = !returning) { go(Ob.WELCOME, ahead = false) }
        Ob.SMS_DENIED -> BackHandler { go(Ob.SMS, ahead = false) }
        else -> Unit
    }

    AnimatedContent(
        targetState = screen,
        transitionSpec = {
            val dir = if (forward) 1 else -1
            (slideInHorizontally(tween(280)) { it * dir / 4 } + fadeIn(tween(220)))
                .togetherWith(slideOutHorizontally(tween(280)) { -it * dir / 4 } + fadeOut(tween(160)))
        },
        label = "onboarding",
    ) { s ->
        when (s) {
            Ob.WELCOME -> Welcome(onStart = { Onboarding.markStarted(context); go(Ob.SMS) })
            Ob.SMS -> SmsExplainer(
                onBack = if (returning) null else ({ go(Ob.WELCOME, ahead = false) }),
                onGranted = ::smsGranted,
                onPermanentlyDenied = { go(Ob.SMS_DENIED) },
            )
            Ob.SMS_DENIED -> SmsDenied(onBack = { go(Ob.SMS, ahead = false) }, onGranted = ::smsGranted)
            Ob.IMPORT -> Importing(repo, onContinue = { Onboarding.markImportDone(context); go(Ob.SORT) })
            Ob.SORT -> SortPastScreen(repo, onContinue = { Onboarding.markSortDone(context); go(Ob.FIRST_LOOK) })
            Ob.FIRST_LOOK -> FirstLookScreen(repo, onContinue = { go(Ob.EXTRAS) })
            Ob.EXTRAS -> Extras(onFinish = ::finish)
        }
    }
}

// ob1 ─────────────────────────────────────────────────────────────────────────

@Composable
private fun Welcome(onStart: () -> Unit) {
    ObScaffold(step = null, onBack = null, bottom = {
        PrimaryButton("Get started", onClick = onStart)
        Footnote("No sign-up needed")
    }) {
        Spacer(Modifier.height(24.dp))
        IconTile(ObIcons.Chart, size = 64.dp, fill = Accent, tint = OnAccent, corner = 18.dp)
        Spacer(Modifier.height(28.dp))
        ObHeading("See where your money goes. Automatically.", style = ObTitle.copy(fontSize = 28.sp, lineHeight = 34.sp))
        Spacer(Modifier.height(14.dp))
        Text(
            "We read your bank payment messages and sort your spending for you. No typing.",
            style = AppType.body.copy(fontSize = 14.sp, lineHeight = 20.sp),
            color = TextSecondary,
        )
        Spacer(Modifier.height(28.dp))
        listOf(
            ObIcons.Card to "UPI, cards and net banking",
            ObIcons.Lock to "Your messages stay on your phone",
            ObIcons.Clock to "Set up in about a minute",
        ).forEach { (icon, text) ->
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 8.dp)) {
                IconTile(icon, size = 44.dp, corner = 22.dp)
                Spacer(Modifier.width(16.dp))
                Text(text, style = AppType.rowTitle, color = TextPrimary)
            }
        }
    }
}

// ob2 ─────────────────────────────────────────────────────────────────────────

@Composable
private fun SmsExplainer(onBack: (() -> Unit)?, onGranted: () -> Unit, onPermanentlyDenied: () -> Unit) {
    val context = LocalContext.current
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
        if (result[Manifest.permission.READ_SMS] == true) {
            onGranted() // RECEIVE_SMS denied too is fine: new payments appear when the app opens
        } else {
            Onboarding.countSmsDenied(context)
            val canAskAgain = context.findActivity()?.shouldShowRequestPermissionRationale(Manifest.permission.READ_SMS) == true
            if (!canAskAgain) onPermanentlyDenied() // F29 otherwise: stay here, no message
        }
    }
    ObScaffold(step = 1, onBack = onBack, bottom = {
        PrimaryButton("Allow SMS access") {
            launcher.launch(arrayOf(Manifest.permission.READ_SMS, Manifest.permission.RECEIVE_SMS))
        }
        Footnote("Android will ask you to confirm next")
    }) {
        SmsIllustration()
        Spacer(Modifier.height(28.dp))
        ObHeading("Let us read your bank messages")
        Spacer(Modifier.height(10.dp))
        Text("That's how payments show up here on their own.", style = AppType.body.copy(fontSize = 14.sp, lineHeight = 20.sp), color = TextSecondary)
        Spacer(Modifier.height(20.dp))
        CheckRow("Only messages from banks")
        CheckRow("We skip OTPs and personal chats")
        CheckRow("Messages never leave your phone")
    }
}

/** Decorative: a made-up bank SMS turning into a payment card. Hidden from TalkBack. */
@Composable
private fun SmsIllustration() {
    Column(
        Modifier.fillMaxWidth().clip(CardShape).background(Surface).padding(16.dp).clearAndSetSemantics { },
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            bolded("**HDFCBK** · Rs.86.00 debited from A/c XX1234 to SHREE GANESH KIRANA via UPI. Ref 4281…"),
            style = AppType.body,
            color = TextSecondary,
            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(SurfaceRaised).padding(16.dp),
        )
        Icon(ObIcons.ArrowDown, contentDescription = null, tint = Accent, modifier = Modifier.padding(vertical = 10.dp).size(20.dp))
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(Bg).padding(horizontal = 16.dp, vertical = 14.dp),
        ) {
            InitialsCircle("Shree Ganesh")
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text("Shree Ganesh Kirana", style = AppType.rowTitle, color = TextPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text("Groceries · now", style = AppType.body, color = TextSecondary)
            }
            Text("−₹86", style = AppType.rowTitle, color = TextPrimary)
        }
    }
}

// ob8 ─────────────────────────────────────────────────────────────────────────

@Composable
private fun SmsDenied(onBack: () -> Unit, onGranted: () -> Unit) {
    val context = LocalContext.current
    LifecycleResumeEffect(Unit) {
        if (Onboarding.hasSms(context)) onGranted() // came back from Settings with it on
        onPauseOrDispose { }
    }
    ObScaffold(step = null, onBack = onBack, bottom = {
        PrimaryButton("Open Settings") { context.startActivity(appDetailsIntent(context)) }
        // "Upload a bank statement instead" is parked (MVP.md §5) until statement upload exists.
    }) {
        IconTile(ObIcons.SmsOff, size = 56.dp, corner = 18.dp, tint = TextSecondary)
        Spacer(Modifier.height(24.dp))
        ObHeading("SMS access is off")
        Spacer(Modifier.height(10.dp))
        Text(
            "Without it we can't find your payments. Android won't ask again, so it has to be turned on in Settings.",
            style = AppType.body.copy(fontSize = 14.sp, lineHeight = 20.sp),
            color = TextSecondary,
        )
        Spacer(Modifier.height(22.dp))
        StepsBox(listOf("Tap **Permissions**", "Tap **SMS**", "Choose **Allow**, then come back"), fill = Surface)
    }
}

// ob3 ─────────────────────────────────────────────────────────────────────────

private val monthName = DateTimeFormatter.ofPattern("MMM")

@Composable
private fun Importing(repo: PaymentRepository, onContinue: () -> Unit) {
    val context = LocalContext.current
    LaunchedEffect(Unit) { repo.importRecent() }
    val progress by repo.importProgress.collectAsStateWithLifecycle()
    val p = progress
    val finished = p?.finished == true
    val failed = p?.failed == true
    val found = p?.found ?: 0
    LaunchedEffect(finished) { if (finished) Feedback.success() } // F30

    // TalkBack hears the count at most every 2 s, not on every step.
    var announced by remember { mutableIntStateOf(0) }
    LaunchedEffect(Unit) { while (true) { announced = repo.importProgress.value?.found ?: 0; delay(2_000) } }

    var showNotifCard by remember {
        mutableStateOf(Build.VERSION.SDK_INT >= 33 && !Onboarding.isNotifAsked(context) && !PaymentCheck.canNotify(context))
    }
    val notifLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        Onboarding.markNotifAsked(context)
        showNotifCard = false
    }

    ObScaffold(step = 2, onBack = null, bottom = {
        AnimatedVisibility(showNotifCard && !failed) {
            NotifCard(
                onAllow = { Feedback.tick(); notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS) }, // F31
                onNotNow = { Feedback.tick(); Onboarding.markNotifAsked(context); showNotifCard = false },
            )
        }
        when {
            failed -> PrimaryButton("Try again") { repo.importRecent() }
            finished && found == 0 -> PrimaryButton("Continue anyway", onClick = onContinue)
            finished -> PrimaryButton("Continue", onClick = onContinue)
            else -> PrimaryButton("Almost done…", enabled = false) {}
        }
    }) {
        Spacer(Modifier.height(24.dp))
        when {
            failed -> {
                ObHeading("Something went wrong reading messages")
                Spacer(Modifier.height(10.dp))
                Text("Nothing was saved. Try again in a moment.", style = AppType.body, color = TextSecondary)
            }
            finished && found == 0 -> {
                ObHeading("No bank payment messages found in the last 90 days")
                Spacer(Modifier.height(10.dp))
                Text(
                    "New payments will show up here as your bank texts you.",
                    style = AppType.body.copy(fontSize = 14.sp, lineHeight = 20.sp),
                    color = TextSecondary,
                )
            }
            else -> {
                Text(if (finished) "Read the last 90 days" else "Reading the last 90 days…", style = AppType.tab, color = TextSecondary)
                Spacer(Modifier.height(6.dp))
                Text(
                    "$found",
                    style = AppType.hero.copy(fontSize = 52.sp, lineHeight = 58.sp),
                    color = TextPrimary,
                    modifier = Modifier.clearAndSetSemantics {
                        contentDescription = "$announced payments found so far"
                        liveRegion = LiveRegionMode.Polite
                    },
                )
                Text(
                    if (finished) "payments found" else "payments found so far",
                    style = AppType.body.copy(fontSize = 14.sp, lineHeight = 20.sp),
                    color = TextSecondary,
                )
                Spacer(Modifier.height(24.dp))
                val months = p?.months.orEmpty()
                val fraction by animateFloatAsState(
                    if (months.isEmpty()) 0f else (p?.done?.size ?: 0).toFloat() / months.size,
                    tween(300),
                    label = "import",
                )
                Box(Modifier.fillMaxWidth().height(6.dp).background(SurfaceRaised, PillShape)) {
                    Box(Modifier.fillMaxWidth(fraction).fillMaxHeight().background(Accent, PillShape))
                }
                Spacer(Modifier.height(14.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    val current = months.firstOrNull { it !in (p?.done ?: emptySet()) }
                    months.forEach { m ->
                        val done = m in (p?.done ?: emptySet())
                        Text(
                            monthName.format(m) + when { done -> " ✓"; m == current -> "…"; else -> "" },
                            style = AppType.chip,
                            color = when { done -> TextPrimary; m == current -> Accent; else -> TextSecondary },
                            modifier = Modifier.clip(PillShape).background(Surface).padding(horizontal = 12.dp, vertical = 8.dp),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun NotifCard(onAllow: () -> Unit, onNotNow: () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(bottom = 16.dp).clip(CardShape).background(Surface).padding(18.dp)) {
        Row {
            IconTile(ObIcons.Bell, size = 40.dp, corner = 20.dp)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text("While you wait: want a nudge?", style = AppType.rowTitle, color = TextPrimary)
                Spacer(Modifier.height(4.dp))
                Text(
                    "Only when a payment needs you, like “Did your ₹40 to Raju go through?”. Never ads.",
                    style = AppType.body,
                    color = TextSecondary,
                )
            }
        }
        Spacer(Modifier.height(16.dp))
        Row {
            SecondaryButton("Not now", Modifier.weight(1f), onClick = onNotNow)
            Spacer(Modifier.width(8.dp))
            Box(
                Modifier.weight(1f).height(Spacing.button).clip(PillShape).background(Accent)
                    .clickable(role = Role.Button, onClick = onAllow),
                contentAlignment = Alignment.Center,
            ) { Text("Allow", style = AppType.tab, color = OnAccent) }
        }
    }
}

// ob4 ─────────────────────────────────────────────────────────────────────────

@Composable
private fun FirstLookScreen(repo: PaymentRepository, onContinue: () -> Unit) {
    LaunchedEffect(Unit) { repo.ensureLoaded() } // resumed after a restart: load the inbox again
    val snap by repo.snapshot.collectAsStateWithLifecycle()
    val today = remember { LocalDate.now() }
    val look = remember(snap) { snap?.let { FirstLooks.of(it.payments, today, it::categoryOf) } }
    val accounts = remember(snap) {
        val since = today.minusDays(FirstLooks.DAYS - 1).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
        snap?.bankSms?.filter { it.dateMillis >= since }?.mapNotNull { AccountParser.last4(it.body) }?.toSet()?.size ?: 0
    }

    ObScaffold(step = 2, onBack = null, bottom = { PrimaryButton("Continue", enabled = look != null, onClick = onContinue) }) {
        Spacer(Modifier.height(24.dp))
        Text("You spent online in the last 90 days", style = AppType.tab, color = TextSecondary)
        Spacer(Modifier.height(6.dp))
        if (look == null) {
            Text("Adding it up…", style = AppType.sectionTitle, color = TextSecondary)
            return@ObScaffold
        }
        Text(Money.formatWhole(look.totalPaise), style = AppType.hero, color = TextPrimary)
        Spacer(Modifier.height(6.dp))
        Text(
            buildString {
                append("across ${look.paymentCount} payment${if (look.paymentCount == 1) "" else "s"}")
                if (accounts > 0) append(" from $accounts bank account${if (accounts == 1) "" else "s"}")
            },
            style = AppType.body.copy(fontSize = 14.sp, lineHeight = 20.sp),
            color = TextSecondary,
        )
        if (look.top.isNotEmpty()) {
            Spacer(Modifier.height(24.dp))
            Column(Modifier.fillMaxWidth().clip(CardShape).background(Surface).padding(20.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
                Text("Where most of it went", style = AppType.rowTitle.copy(fontSize = 16.sp, lineHeight = 22.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold), color = TextPrimary)
                look.top.forEach { (cat, share) ->
                    Column {
                        Row {
                            Text(cat.category.orEmpty(), style = AppType.body.copy(fontSize = 14.sp), color = TextPrimary, modifier = Modifier.weight(1f))
                            Text(Money.formatWhole(cat.totalPaise), style = AppType.rowTitle, color = TextPrimary)
                        }
                        Spacer(Modifier.height(8.dp))
                        Box(Modifier.fillMaxWidth().height(6.dp).background(SurfaceRaised, PillShape)) {
                            Box(Modifier.fillMaxWidth(share.coerceIn(0.02f, 1f)).fillMaxHeight().background(Accent, PillShape))
                        }
                    }
                }
            }
        }
        if (look.uncategorised > 0) {
            Spacer(Modifier.height(24.dp))
            Row {
                Box(Modifier.padding(top = 6.dp).size(8.dp).background(Negative, PillShape))
                Spacer(Modifier.width(12.dp))
                Text(
                    bolded("**${look.uncategorised} payment${if (look.uncategorised == 1) "" else "s"}** need${if (look.uncategorised == 1) "s" else ""} a category. You can do that later, a few at a time."),
                    style = AppType.body.copy(fontSize = 14.sp, lineHeight = 20.sp),
                    color = TextSecondary,
                )
            }
        }
    }
}

// ob5–ob7 ─────────────────────────────────────────────────────────────────────

private enum class Sheet { GPAY, LISTENER, RESTRICTED }

@Composable
private fun Extras(onFinish: () -> Unit) {
    val context = LocalContext.current
    var tick by remember { mutableIntStateOf(0) }
    var sheet by rememberSaveable { mutableStateOf<Sheet?>(null) }
    /** Which source we sent the user to Settings for, so we can react when they come back. */
    var away by rememberSaveable { mutableStateOf<Sheet?>(null) }
    var restrictedFor by rememberSaveable { mutableStateOf(Sheet.GPAY) }

    val gpayOn = remember(tick) { GPayScreenReader.isOn(context) }
    val listenerOn = remember(tick) { UpiApps.isNotificationAccessOn(context) }

    LifecycleResumeEffect(Unit) {
        tick++
        val cameBackFrom = away
        away = null
        if (cameBackFrom != null) {
            val on = if (cameBackFrom == Sheet.GPAY) GPayScreenReader.isOn(context) else UpiApps.isNotificationAccessOn(context)
            if (on) {
                Feedback.success() // F32: the card flips to "✓ On"
                sheet = null
            } else if (Build.VERSION.SDK_INT >= 33) {
                restrictedFor = cameBackFrom // F33: likely greyed out on a sideloaded build
                sheet = Sheet.RESTRICTED
            }
        }
        onPauseOrDispose { }
    }

    fun openSettings(kind: Sheet) {
        Onboarding.markAttempted(context, if (kind == Sheet.GPAY) "gpay" else "listener")
        away = kind
        context.startActivity(if (kind == Sheet.GPAY) GPayScreenReader.settingsIntent(context) else UpiApps.notificationAccessIntent(context))
    }

    ObScaffold(step = 3, onBack = null, bottom = {
        PrimaryButton("Done", onClick = onFinish)
        TextLink("Skip for now", color = TextSecondary, onClick = onFinish)
    }) {
        Spacer(Modifier.height(16.dp))
        ObHeading("Catch the payments banks don't text about")
        Spacer(Modifier.height(10.dp))
        Text("Optional. You can turn these on later in Profile.", style = AppType.body.copy(fontSize = 14.sp, lineHeight = 20.sp), color = TextSecondary)
        Spacer(Modifier.height(22.dp))
        ExtraCard(
            badge = { InitialsCircle("G") },
            title = "GPay payments",
            recommended = true,
            body = "Catch small GPay payments and confirm them right after you pay.",
            on = gpayOn,
            onTurnOn = { sheet = Sheet.GPAY },
        )
        Spacer(Modifier.height(10.dp))
        ExtraCard(
            badge = { IconTile(ObIcons.Bell, corner = 22.dp) },
            title = "UPI app alerts",
            recommended = false,
            body = "Catch money you receive in GPay, PhonePe, Paytm, BHIM and CRED.",
            on = listenerOn,
            onTurnOn = { sheet = Sheet.LISTENER },
        )
    }

    when (sheet) {
        Sheet.GPAY -> ExplainerSheet(
            title = "Turn on the GPay reader",
            checks = listOf("We only look at the GPay app", "We never read your UPI PIN", "Nothing leaves your phone"),
            note = "In Settings, Android will show a strong warning. That's normal for this kind of setting.",
            steps = listOf("Tap **Downloaded apps › Expense Tracker**", "Switch it **on**", "Tap **Allow**, then come back here"),
            onOpenSettings = { openSettings(Sheet.GPAY) },
            onGreyedOut = { restrictedFor = Sheet.GPAY; sheet = Sheet.RESTRICTED },
            onDismiss = { sheet = null },
        )
        Sheet.LISTENER -> ExplainerSheet(
            title = "Turn on UPI app alerts",
            checks = listOf("We only read payment apps", "We ignore chats and other apps", "Nothing leaves your phone"),
            note = null,
            steps = listOf("Find **Expense Tracker**", "Switch it **on**", "Tap **Allow**, then come back here"),
            onOpenSettings = { openSettings(Sheet.LISTENER) },
            onGreyedOut = { restrictedFor = Sheet.LISTENER; sheet = Sheet.RESTRICTED },
            onDismiss = { sheet = null },
        )
        Sheet.RESTRICTED -> RestrictedSheet(
            onTryAgain = { sheet = restrictedFor },
            onOpenAppInfo = { context.startActivity(appDetailsIntent(context)) },
            onDismiss = { sheet = null },
        )
        null -> Unit
    }
}

@Composable
private fun ExtraCard(
    badge: @Composable () -> Unit,
    title: String,
    recommended: Boolean,
    body: String,
    on: Boolean,
    onTurnOn: () -> Unit,
) {
    Column(Modifier.fillMaxWidth().clip(CardShape).background(Surface).padding(18.dp)) {
        Row {
            badge()
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(title, style = AppType.rowTitle, color = TextPrimary)
                    if (recommended) {
                        Spacer(Modifier.width(8.dp))
                        Text(
                            "Recommended",
                            style = AppType.chip,
                            color = OnAccent,
                            modifier = Modifier.clip(PillShape).background(Accent).padding(horizontal = 8.dp, vertical = 2.dp),
                        )
                    }
                }
                Spacer(Modifier.height(4.dp))
                Text(body, style = AppType.body, color = TextSecondary)
            }
        }
        Spacer(Modifier.height(16.dp))
        if (on) {
            Row(
                Modifier.fillMaxWidth().height(48.dp).clip(PillShape).background(Positive.copy(alpha = 0.14f)),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(com.yourteam.expensetracker.ui.theme.HugeIcons.Tick, contentDescription = null, tint = Positive, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("On", style = AppType.tab, color = Positive)
            }
        } else {
            SecondaryButton("Turn on", Modifier.fillMaxWidth(), onClick = onTurnOn)
        }
    }
}

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
private fun ObSheet(title: String, onDismiss: () -> Unit, content: @Composable () -> Unit) {
    val state = androidx.compose.material3.rememberModalBottomSheetState(skipPartiallyExpanded = true)
    androidx.compose.material3.ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = state,
        containerColor = Surface,
        dragHandle = { androidx.compose.material3.BottomSheetDefaults.DragHandle(color = SurfaceRaised) },
    ) {
        Column(Modifier.padding(start = 20.dp, end = 20.dp, bottom = 20.dp).navigationBarsPadding()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.weight(1f)) { ObHeading(title, style = AppType.sectionTitle) }
                Box(
                    Modifier.size(44.dp).clip(PillShape).background(SurfaceRaised).clickable(role = Role.Button, onClick = onDismiss),
                    contentAlignment = Alignment.Center,
                ) { Icon(com.yourteam.expensetracker.ui.theme.HugeIcons.Close, contentDescription = "Close", tint = TextPrimary, modifier = Modifier.size(20.dp)) }
            }
            Spacer(Modifier.height(16.dp))
            content()
        }
    }
}

/** ob6 (and the UPI alerts version): promises, what Settings will show, then Open Settings. */
@Composable
private fun ExplainerSheet(
    title: String,
    checks: List<String>,
    note: String?,
    steps: List<String>,
    onOpenSettings: () -> Unit,
    onGreyedOut: () -> Unit,
    onDismiss: () -> Unit,
) {
    ObSheet(title, onDismiss) {
        checks.forEach { CheckRow(it) }
        Spacer(Modifier.height(16.dp))
        StepsBox(steps, note = note)
        Spacer(Modifier.height(20.dp))
        PrimaryButton("Open Settings", onClick = onOpenSettings)
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) { TextLink("Switch is greyed out?", onClick = onGreyedOut) }
    }
}

/** ob7: Android 13+ blocks these switches for sideloaded apps until "Allow restricted settings". */
@Composable
private fun RestrictedSheet(onTryAgain: () -> Unit, onOpenAppInfo: () -> Unit, onDismiss: () -> Unit) {
    ObSheet("Switch greyed out? One extra step", onDismiss) {
        Text(
            "Android locks this for apps installed outside the Play Store until you allow it once.",
            style = AppType.body.copy(fontSize = 14.sp, lineHeight = 20.sp),
            color = TextSecondary,
        )
        Spacer(Modifier.height(16.dp))
        StepsBox(
            listOf(
                "Open **App info** (button below)",
                "Tap **⋮** at the top right",
                "Tap **Allow restricted settings**",
                "Come back and tap **Try again**",
            ),
        )
        Spacer(Modifier.height(20.dp))
        Row {
            SecondaryButton("Try again", Modifier.weight(1f), onClick = onTryAgain)
            Spacer(Modifier.width(8.dp))
            Box(
                Modifier.weight(1f).height(Spacing.button).clip(PillShape).background(Accent).clickable(role = Role.Button, onClick = onOpenAppInfo),
                contentAlignment = Alignment.Center,
            ) { Text("Open App info", style = AppType.tab, color = OnAccent) }
        }
    }
}
