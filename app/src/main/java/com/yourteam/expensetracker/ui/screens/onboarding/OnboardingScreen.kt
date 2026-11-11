package com.yourteam.expensetracker.ui.screens.onboarding

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.yourteam.expensetracker.ui.theme.AppCard
import com.yourteam.expensetracker.ui.theme.Heading
import com.yourteam.expensetracker.ui.theme.PillShape

/**
 * Explains why we need READ_SMS before asking for it.
 * [showSettingsHint] is true after the user has denied once, so they can grant it from Settings.
 */
@Composable
fun SmsPermissionScreen(
    onAllow: () -> Unit,
    onOpenSettings: () -> Unit,
    showSettingsHint: Boolean,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp, vertical = 24.dp),
    ) {
        Heading(text = "We only read bank payment messages")
        Spacer(Modifier.height(16.dp))
        AppCard(modifier = Modifier.fillMaxWidth()) {
            Bullet("We look for messages from bank and payment senders, like HDFCBK or SBIINB.")
            Bullet("We skip OTPs, promotions and your personal chats.")
            Bullet("Your messages stay on this phone. Nothing is uploaded.")
        }
        Spacer(Modifier.height(24.dp))
        Button(
            onClick = onAllow,
            shape = PillShape,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("Allow SMS access")
        }
        if (showSettingsHint) {
            Spacer(Modifier.height(12.dp))
            Text(
                text = "If the permission popup doesn't appear, turn on SMS in Settings → Permissions.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(12.dp))
            OutlinedButton(
                onClick = onOpenSettings,
                shape = PillShape,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Open settings")
            }
        }
    }
}

@Composable
private fun Bullet(text: String) {
    Text(
        text = "•  $text",
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(vertical = 4.dp),
    )
}
