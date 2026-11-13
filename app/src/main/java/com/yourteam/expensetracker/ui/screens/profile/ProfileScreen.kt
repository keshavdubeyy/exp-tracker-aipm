package com.yourteam.expensetracker.ui.screens.profile

import android.app.Application
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.yourteam.expensetracker.data.PaymentRepository
import com.yourteam.expensetracker.data.PaymentSnapshot
import com.yourteam.expensetracker.parser.AccountParser
import com.yourteam.expensetracker.ui.screens.InitialsCircle
import com.yourteam.expensetracker.ui.screens.SubScreenHeader
import com.yourteam.expensetracker.ui.theme.AppType
import com.yourteam.expensetracker.ui.theme.CardShape
import com.yourteam.expensetracker.ui.theme.Spacing
import com.yourteam.expensetracker.ui.theme.Surface
import com.yourteam.expensetracker.ui.theme.TextPrimary
import com.yourteam.expensetracker.ui.theme.TextSecondary
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

/** A bank account seen in SMS: "SBI", "XX1234", 12 messages. */
data class Account(val bank: String, val last4: String?, val messages: Int)

class ProfileViewModel(app: Application) : AndroidViewModel(app) {
    private val repo = PaymentRepository.get(app).also { it.ensureLoaded() }

    private fun accounts(s: PaymentSnapshot?): List<Account> = s?.bankSms.orEmpty()
        .mapNotNull { m -> AccountParser.bank(m.address)?.let { it to AccountParser.last4(m.body) } }
        .groupingBy { it }.eachCount()
        .let { counts ->
            // Drop "bank, no number" when the same bank also has a numbered account.
            val numbered = counts.keys.filter { it.second != null }.map { it.first }.toSet()
            counts.filterKeys { it.second != null || it.first !in numbered }
        }
        .map { (k, n) -> Account(k.first, k.second, n) }
        .sortedByDescending { it.messages }

    val accounts: StateFlow<List<Account>> = repo.snapshot.map(::accounts)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), accounts(repo.snapshot.value))
}

/** Profile: accounts, settings, privacy. Opened from the avatar on the top tabs. */
@Composable
fun ProfileScreen(onBack: () -> Unit, onOpenSources: () -> Unit, onOpenCategories: () -> Unit = {}, onOpenDeveloper: () -> Unit = {}, onOpenDuplicates: () -> Unit = {}, viewModel: ProfileViewModel = viewModel()) {
    val accounts by viewModel.accounts.collectAsStateWithLifecycle()
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = Spacing.screen, end = Spacing.screen, bottom = 32.dp),
    ) {
        item { SubScreenHeader("Profile", onBack); Spacer(Modifier.height(Spacing.section)) }

        item { Label("Accounts") }
        if (accounts.isEmpty()) {
            item { Text("No bank accounts found in your SMS yet.", style = AppType.body, color = TextSecondary) }
        }
        items(accounts, key = { "${it.bank}-${it.last4}" }) { a ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth().clip(CardShape).background(Surface).padding(horizontal = 16.dp, vertical = Spacing.cardV),
            ) {
                InitialsCircle(a.bank)
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text(a.bank + (a.last4?.let { " · XX$it" } ?: ""), style = AppType.rowTitle, color = TextPrimary)
                    Text("Bank SMS · ${a.messages} messages", style = AppType.body, color = TextSecondary)
                }
            }
            Spacer(Modifier.height(Spacing.cardGap))
        }

        item {
            Spacer(Modifier.height(Spacing.section - Spacing.cardGap))
            Label("Settings")
            LinkRow("Sources", "Bank SMS, UPI alerts, GPay", onOpenSources)
            Spacer(Modifier.height(Spacing.cardGap))
            LinkRow("Categories", "Make, rename or delete your own", onOpenCategories)
            Spacer(Modifier.height(Spacing.cardGap))
            LinkRow("Duplicates", "Same person or category under different names", onOpenDuplicates)
            Spacer(Modifier.height(Spacing.section))
            Label("Privacy")
            Text(
                "Your SMS, payments and answers are read and stored only on this phone. " +
                    (if (com.yourteam.expensetracker.categoriser.AiCategoriser.enabled)
                        "To suggest categories, we send a payee's name, UPI ID and amount to our server, never your messages. "
                    else "Nothing is uploaded. ") +
                    "Account numbers show the last 4 digits only.",
                style = AppType.body,
                color = TextSecondary,
            )
            Spacer(Modifier.height(Spacing.section))
            // Tapping the version 7 times opens the Developer screen (MVP M24).
            var taps by androidx.compose.runtime.remember { androidx.compose.runtime.mutableIntStateOf(0) }
            Text(
                "Version ${com.yourteam.expensetracker.BuildConfig.VERSION_NAME}",
                style = AppType.body,
                color = TextSecondary,
                modifier = Modifier
                    .clickable(interactionSource = null, indication = null) {
                        taps++
                        if (taps >= 7) { taps = 0; onOpenDeveloper() }
                    }
                    .padding(vertical = 12.dp),
            )
        }
    }
}

@Composable
private fun Label(text: String) {
    Text(text, style = AppType.tab, color = TextSecondary, modifier = Modifier.padding(bottom = 10.dp))
}

@Composable
private fun LinkRow(title: String, subtitle: String, onClick: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clip(CardShape)
            .background(Surface)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = Spacing.cardH, vertical = 18.dp),
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = AppType.rowTitle, color = TextPrimary)
            Text(subtitle, style = AppType.body, color = TextSecondary)
        }
        Icon(com.yourteam.expensetracker.ui.theme.HugeIcons.ChevronRight, contentDescription = null, tint = TextSecondary)
    }
}
