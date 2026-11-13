package com.yourteam.expensetracker.ui.theme

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.foundation.layout.Box
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp

/** Section heading: Inter bold (sectionTitle), sentence case (DESIGN.md §2). */
@Composable
fun Heading(
    text: String,
    modifier: Modifier = Modifier,
    style: TextStyle = MaterialTheme.typography.headlineMedium,
) {
    Text(
        text = text,
        modifier = modifier,
        style = style,
        color = MaterialTheme.colorScheme.onBackground,
    )
}

/** Card: Surface, 24dp corners, no border, no shadow (DESIGN.md §4). */
@Composable
fun AppCard(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Card(
        modifier = modifier,
        shape = CardShape,
        colors = CardDefaults.cardColors(containerColor = Surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Column(modifier = Modifier.padding(horizontal = Spacing.cardH, vertical = Spacing.cardV + 2.dp), content = content)
    }
}

/**
 * Link button: plain text, no fill or border (DESIGN.md §4.11). For low-priority actions
 * such as rating an insight. Keeps a 44 dp touch target.
 */
@Composable
fun LinkButton(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = Accent,
    onClick: () -> Unit,
) {
    Box(
        modifier
            .heightIn(min = Spacing.circle)
            .clip(PillShape)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, style = AppType.tab, color = color, maxLines = 1, softWrap = false)
    }
}
