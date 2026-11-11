package com.yourteam.expensetracker.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.googlefonts.Font
import androidx.compose.ui.text.googlefonts.GoogleFont
import androidx.compose.ui.unit.sp
import com.yourteam.expensetracker.R

private val fontProvider = GoogleFont.Provider(
    providerAuthority = "com.google.android.gms.fonts",
    providerPackage = "com.google.android.gms",
    certificates = R.array.com_google_android_gms_fonts_certs,
)

private val inter = GoogleFont("Inter")

/** Inter for everything: 400 and 600 (DESIGN.md §2). No bold. */
val InterFamily = FontFamily(
    Font(googleFont = inter, fontProvider = fontProvider, weight = FontWeight.Normal),
    Font(googleFont = inter, fontProvider = fontProvider, weight = FontWeight.SemiBold),
)

private fun style(size: Int, line: Int, weight: FontWeight, trackingSp: Double = 0.0) = TextStyle(
    fontFamily = InterFamily,
    fontWeight = weight,
    fontSize = size.sp,
    lineHeight = line.sp,
    letterSpacing = trackingSp.sp,
)

/** The named styles from DESIGN.md §2. Use these in new UI. */
object AppType {
    /** The one big amount per screen. Never two on a screen. */
    val hero = style(48, 52, FontWeight.SemiBold, -1.0)
    /** "Payments ›", "Categories ›", screen titles. */
    val sectionTitle = style(24, 30, FontWeight.SemiBold)
    /** Amount on category / stat cards. */
    val statValue = style(20, 26, FontWeight.SemiBold)
    /** Payee name, card title, row amount. */
    val rowTitle = style(15, 20, FontWeight.SemiBold)
    /** Pill tabs, period switcher. */
    val tab = style(14, 18, FontWeight.SemiBold)
    /** Row subtitles, explanations. */
    val body = style(13, 18, FontWeight.Normal)
    /** Badges, delta chips, source labels. Also the minimum size. */
    val chip = style(12, 16, FontWeight.SemiBold)
}

/** Material roles mapped onto the DESIGN.md scale, so stock components match. Min 12sp, no bold. */
val AppTypography = Typography(
    displayLarge = AppType.hero,
    displayMedium = style(36, 40, FontWeight.SemiBold, -0.5),
    displaySmall = style(28, 34, FontWeight.SemiBold, -0.3),
    headlineLarge = AppType.sectionTitle,
    headlineMedium = AppType.sectionTitle,
    headlineSmall = AppType.statValue,
    titleLarge = AppType.rowTitle,
    titleMedium = AppType.rowTitle,
    titleSmall = AppType.tab,
    bodyLarge = style(14, 20, FontWeight.Normal),
    bodyMedium = AppType.body,
    bodySmall = style(12, 16, FontWeight.Normal),
    labelLarge = AppType.tab,
    labelMedium = AppType.chip,
    labelSmall = style(12, 16, FontWeight.SemiBold),
)
