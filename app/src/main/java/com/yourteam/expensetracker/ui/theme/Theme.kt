package com.yourteam.expensetracker.ui.theme

import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp

/** Material roles mapped onto our palette, so stock components (sheets, dialogs, pickers) match. */
private fun schemeFor(c: AppColors) = (if (c.isDark) darkColorScheme() else lightColorScheme()).copy(
    primary = c.accent,
    onPrimary = c.onAccent,
    primaryContainer = c.surface,
    onPrimaryContainer = c.textPrimary,
    inversePrimary = c.accent,
    secondary = c.accent,
    onSecondary = c.onAccent,
    secondaryContainer = c.surfaceRaised,
    onSecondaryContainer = c.textPrimary,
    tertiary = c.accent,
    onTertiary = c.onAccent,
    background = c.bg,
    onBackground = c.textPrimary,
    surface = c.bg,
    onSurface = c.textPrimary,
    surfaceVariant = c.surface,
    onSurfaceVariant = c.textSecondary,
    surfaceTint = c.accent,
    inverseSurface = c.textPrimary,
    inverseOnSurface = c.bg,
    error = c.negative,
    onError = c.bg,
    outline = c.surfaceRaised,
    outlineVariant = c.surfaceRaised,
    scrim = Color.Black,
    surfaceBright = c.surface,
    surfaceDim = c.bg,
    surfaceContainerLowest = c.bg,
    surfaceContainerLow = c.surface,
    surfaceContainer = c.surface,
    surfaceContainerHigh = c.surface,
    surfaceContainerHighest = c.surfaceRaised,
)

// DESIGN.md §3.
/** Cards: 24dp. */
val CardShape = RoundedCornerShape(24.dp)
/** Day-strip and small tiles: 16dp. */
val TileShape = RoundedCornerShape(16.dp)
/** Delta chip ("−9.6%"): 8dp. */
val DeltaChipShape = RoundedCornerShape(8.dp)
/** Tabs, chips, buttons, search bar: fully round. */
val PillShape = CircleShape

// Older names, kept so screens not yet restyled still compile.
val MediaShape = TileShape
val TagShape = PillShape

/** DESIGN.md §3 spacing, on a 4dp grid. */
object Spacing {
    val screen = 20.dp       // screen side padding
    val section = 22.dp      // gap between sections (DESIGN.md value, off-grid by design)
    val cardGap = 8.dp       // gap between cards in a list
    val cardH = 20.dp        // card inner padding, horizontal
    val cardV = 16.dp        // card inner padding, vertical
    val circle = 44.dp       // icon / avatar circle, minimum touch target
    val scanButton = 56.dp   // Scan & Pay
}

private val AppShapes = Shapes(
    extraSmall = PillShape,
    small = PillShape,
    medium = CardShape,
    large = CardShape,
    extraLarge = CardShape,
)

/** Follows the system light / dark setting (DESIGN.md §1). [dark] can be forced for previews. */
@Composable
fun ExpenseTrackerTheme(dark: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    val colors = if (dark) DarkColors else LightColors
    CompositionLocalProvider(LocalAppColors provides colors) {
        MaterialTheme(
            colorScheme = remember(colors) { schemeFor(colors) },
            typography = AppTypography,
            shapes = AppShapes,
            content = content,
        )
    }
}
