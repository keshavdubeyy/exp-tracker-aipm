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

private val dmSans = GoogleFont("DM Sans")

/** DM Sans for everything: 400, 500 and 600 (DESIGN.md §2). No bold. */
val DmSansFamily = FontFamily(
    Font(googleFont = dmSans, fontProvider = fontProvider, weight = FontWeight.Normal),
    Font(googleFont = dmSans, fontProvider = fontProvider, weight = FontWeight.Medium),
    Font(googleFont = dmSans, fontProvider = fontProvider, weight = FontWeight.SemiBold),
)

private fun style(size: Int, line: Int, weight: FontWeight, trackingSp: Double = 0.0) = TextStyle(
    fontFamily = DmSansFamily,
    fontWeight = weight,
    fontSize = size.sp,
    lineHeight = line.sp,
    letterSpacing = trackingSp.sp,
)

/**
 * The named styles from DESIGN.md §2. Use these in new UI.
 * Hierarchy comes from weight as much as size: 600 only for the hero, titles and amounts,
 * 500 for row names and controls, 400 for everything you read.
 */
object AppType {
    /** The one big amount per screen. Never two on a screen. */
    val hero = style(40, 46, FontWeight.SemiBold, -0.8)
    /** Section headers and sheet / screen titles. */
    val sectionTitle = style(18, 24, FontWeight.SemiBold)
    /** Amount on category / stat cards. */
    val statValue = style(17, 22, FontWeight.SemiBold)
    /** Payee name, card title, row amount. */
    val rowTitle = style(15, 20, FontWeight.Medium)
    /** Pill tabs, period switcher, button labels. */
    val tab = style(14, 18, FontWeight.Medium)
    /** Row subtitles, explanations. */
    val body = style(13, 18, FontWeight.Normal)
    /** Badges, delta chips, source labels. Also the minimum size. */
    val chip = style(12, 16, FontWeight.Medium)
}

/** Material roles mapped onto the DESIGN.md scale, so stock components match. Min 12sp, no bold. */
val AppTypography = Typography(
    displayLarge = AppType.hero,
    displayMedium = style(32, 38, FontWeight.SemiBold, -0.5),
    displaySmall = style(24, 30, FontWeight.SemiBold, -0.3),
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
    labelSmall = style(12, 16, FontWeight.Medium),
)
