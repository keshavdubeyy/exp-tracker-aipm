package com.yourteam.expensetracker.ui.screens.sources

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.yourteam.expensetracker.notifications.PinScreenWatcher
import com.yourteam.expensetracker.notifications.PaymentCheck
import com.yourteam.expensetracker.notifications.UpiApps
import com.yourteam.expensetracker.ui.screens.Chip
import com.yourteam.expensetracker.ui.screens.SubScreenHeader
import com.yourteam.expensetracker.ui.theme.Accent
import com.yourteam.expensetracker.ui.theme.AppType
import com.yourteam.expensetracker.ui.theme.CardShape
import com.yourteam.expensetracker.ui.theme.OnAccent
import com.yourteam.expensetracker.ui.theme.PillShape
import com.yourteam.expensetracker.ui.theme.Positive
import com.yourteam.expensetracker.ui.theme.Spacing
import com.yourteam.expensetracker.ui.theme.Surface
import com.yourteam.expensetracker.ui.theme.TextPrimary
import com.yourteam.expensetracker.ui.theme.TextSecondary

private data class Source(val title: String, val body: String, val on: Boolean, val action: String, val onTurnOn: () -> Unit)

/** Where payments come from, and switches for the ones that are off. */
@Composable
fun SourcesScreen(modifier: Modifier = Modifier, onBack: () -> Unit = {}) {
    val context = LocalContext.current
    var tick by remember { mutableIntStateOf(0) } // bump to re-read every status
    LifecycleResumeEffect(Unit) { tick++; onPauseOrDispose { } }
    val smsLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { tick++ }
    val notifyLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { tick++ }

    val sources = remember(tick) {
        listOf(
            Source(
                "Bank SMS",
                "Reads payment messages from your bank. OTPs and offers are skipped.",
                com.yourteam.expensetracker.ui.screens.onboarding.Onboarding.hasSms(context),
                "Allow SMS",
            ) { smsLauncher.launch(com.yourteam.expensetracker.ui.screens.onboarding.Onboarding.SMS_PERMISSIONS) },
            Source(
                "UPI app alerts",
                "Saves payment notifications from GPay, PhonePe, Paytm, BHIM and CRED. Other apps are ignored.",
                UpiApps.isNotificationAccessOn(context),
                "Turn on",
            ) { context.startActivity(UpiApps.notificationAccessIntent(context)) },
            Source(
                "Payments with no SMS",
                "Sees only when a UPI app (GPay, PhonePe, Navi, Paytm…) opens its PIN screen. Never the screen, " +
                    "your PIN or the amount. If no bank SMS or alert follows, we ask once. In Usage access, pick Expense Tracker.",
                PinScreenWatcher.hasAccess(context),
                "Turn on",
            ) { context.startActivity(PinScreenWatcher.settingsIntent()) },
            Source(
                "\"Did it go through?\"",
                "One notification when a payment you started isn't recorded anywhere else.",
                PaymentCheck.canNotify(context),
                "Allow",
            ) {
                if (Build.VERSION.SDK_INT >= 33) notifyLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                else context.startActivity(appSettings(context))
            },
        )
    }
    val onCount = sources.count { it.on }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = Spacing.screen, end = Spacing.screen, top = 8.dp, bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(Spacing.cardGap),
    ) {
        item {
            SubScreenHeader("Sources", onBack)
            Spacer(Modifier.height(12.dp))
            Text("$onCount of ${sources.size}", style = AppType.hero, color = TextPrimary)
            Spacer(Modifier.height(10.dp))
            Text(
                if (onCount == sources.size) "All on. Every payment can be recorded." else "switched on. Turn the rest on to miss fewer payments.",
                style = AppType.body,
                color = if (onCount == sources.size) Positive else TextSecondary,
            )
            Spacer(Modifier.height(Spacing.section - Spacing.cardGap))
        }
        items(sources, key = { it.title }) { SourceCard(it) }
        item {
            Spacer(Modifier.height(12.dp))
            Text("Everything is read and kept on your phone.", style = AppType.body, color = TextSecondary)
        }
    }
}

@Composable
private fun SourceCard(s: Source) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(CardShape)
            .background(Surface)
            .padding(horizontal = Spacing.cardH, vertical = 18.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(s.title, style = AppType.rowTitle, color = TextPrimary, modifier = Modifier.weight(1f))
            Spacer(Modifier.width(8.dp))
            if (s.on) Chip("On") else Chip("Off")
        }
        Spacer(Modifier.height(6.dp))
        Text(s.body, style = AppType.body, color = TextSecondary)
        if (!s.on) {
            Spacer(Modifier.height(14.dp))
            Text(
                s.action,
                style = AppType.tab,
                color = OnAccent,
                modifier = Modifier
                    .heightIn(min = Spacing.circle)
                    .clip(PillShape)
                    .background(Accent)
                    .clickable(role = Role.Button, onClick = s.onTurnOn)
                    .padding(horizontal = 20.dp, vertical = 12.dp),
            )
        }
    }
}

private fun appSettings(context: Context) =
    Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null))
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
