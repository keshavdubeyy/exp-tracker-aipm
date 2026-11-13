package com.yourteam.expensetracker.ui.theme

import androidx.compose.ui.graphics.vector.ImageVector

/** App icons (DESIGN.md §4.10): Hugeicons Stroke Rounded, see [HugeIcons]. Tinted by the Icon composable. */
object AppIcons {
    val Home: ImageVector get() = HugeIcons.Home
    val Chart: ImageVector get() = HugeIcons.Chart
    val Wallet: ImageVector get() = HugeIcons.Wallet
    val Scan: ImageVector get() = HugeIcons.Scan
    /** "To check" / Review. */
    val CheckBox: ImageVector get() = HugeIcons.TaskDone
    /** Marks a category the app or AI picked (Feature 1 F3). */
    val Sparkle: ImageVector get() = HugeIcons.Sparkles
    val Refresh: ImageVector get() = HugeIcons.Refresh
    val Plug: ImageVector get() = HugeIcons.Plug
}
