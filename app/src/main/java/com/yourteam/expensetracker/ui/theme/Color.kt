package com.yourteam.expensetracker.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/**
 * DESIGN.md §1. The app follows the system theme: one palette for dark, one for light.
 * Depth comes from bg -> surface -> surfaceRaised; no borders or shadows in either.
 * Every text colour reaches 4.5:1 on bg, surface and surfaceRaised in its own palette.
 */
@Immutable
data class AppColors(
    val bg: Color,            // screen background
    val surface: Color,       // cards, list rows, search bar, inactive tabs, sheets
    val surfaceRaised: Color, // icon circles, chips on cards, dividers
    val textPrimary: Color,   // titles, amounts, row titles
    val textSecondary: Color, // subtitles, labels, timestamps, placeholders
    val accent: Color,        // the one accent: active tab, + button, links, "Needs a tag"
    val onAccent: Color,      // text and icons on accent
    val positive: Color,      // spent less, all matched (always with words)
    val negative: Color,      // spent more, money to check (always with words)
    val isDark: Boolean,
)

val DarkColors = AppColors(
    bg = Color(0xFF0B0B0D),
    surface = Color(0xFF1C1C1F),
    surfaceRaised = Color(0xFF2A2A2E),
    textPrimary = Color(0xFFF4F4F6),
    textSecondary = Color(0xFFA1A1A8),
    accent = Color(0xFF98BBF4),
    onAccent = Color(0xFF0B0B0D),
    positive = Color(0xFF3DD68C),
    negative = Color(0xFFFF6B6B),
    isDark = true,
)

/**
 * Light: cards are white on a soft grey background. The pale dark-theme blue/green/red are too
 * faint on white, so light uses deeper shades of the same hues (all ≥ 4.5:1 for text).
 */
val LightColors = AppColors(
    bg = Color(0xFFF4F4F6),
    surface = Color(0xFFFFFFFF),
    surfaceRaised = Color(0xFFE9E9ED),
    textPrimary = Color(0xFF0B0B0D),
    textSecondary = Color(0xFF5C5C64),
    accent = Color(0xFF2D5FC4),
    onAccent = Color(0xFFFFFFFF),
    positive = Color(0xFF0F7040),
    negative = Color(0xFFC62828),
    isDark = false,
)

/** Provided by [ExpenseTrackerTheme] from the system setting. */
val LocalAppColors = staticCompositionLocalOf { DarkColors }

// Token names used across the app. They read the current palette, so call them from UI code;
// in a draw block or plain function, read LocalAppColors.current first and pass it in.
val Bg: Color @Composable @ReadOnlyComposable get() = LocalAppColors.current.bg
val Surface: Color @Composable @ReadOnlyComposable get() = LocalAppColors.current.surface
val SurfaceRaised: Color @Composable @ReadOnlyComposable get() = LocalAppColors.current.surfaceRaised
val TextPrimary: Color @Composable @ReadOnlyComposable get() = LocalAppColors.current.textPrimary
val TextSecondary: Color @Composable @ReadOnlyComposable get() = LocalAppColors.current.textSecondary
val Accent: Color @Composable @ReadOnlyComposable get() = LocalAppColors.current.accent
val OnAccent: Color @Composable @ReadOnlyComposable get() = LocalAppColors.current.onAccent
val Positive: Color @Composable @ReadOnlyComposable get() = LocalAppColors.current.positive
val Negative: Color @Composable @ReadOnlyComposable get() = LocalAppColors.current.negative

// Older names, kept so screens not yet restyled still compile. Prefer the tokens above.
val SoftInk: Color @Composable @ReadOnlyComposable get() = TextSecondary
val Edge: Color @Composable @ReadOnlyComposable get() = SurfaceRaised
val Border: Color @Composable @ReadOnlyComposable get() = SurfaceRaised
val CardSurface: Color @Composable @ReadOnlyComposable get() = Surface
