package com.yourteam.expensetracker.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

/** Outline icons drawn to DESIGN.md §4.10: 2dp stroke, rounded caps. Tinted by the Icon composable. */
object AppIcons {
    /** Scan frame: four corners and a line across the middle. */
    val Scan: ImageVector by lazy {
        ImageVector.Builder("Scan", 24.dp, 24.dp, 24f, 24f).path(
            stroke = SolidColor(Color.Black),
            strokeLineWidth = 2f,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round,
        ) {
            moveTo(4f, 8f); verticalLineTo(6f); arcTo(2f, 2f, 0f, false, true, 6f, 4f); horizontalLineTo(8f)
            moveTo(16f, 4f); horizontalLineTo(18f); arcTo(2f, 2f, 0f, false, true, 20f, 6f); verticalLineTo(8f)
            moveTo(20f, 16f); verticalLineTo(18f); arcTo(2f, 2f, 0f, false, true, 18f, 20f); horizontalLineTo(16f)
            moveTo(8f, 20f); horizontalLineTo(6f); arcTo(2f, 2f, 0f, false, true, 4f, 18f); verticalLineTo(16f)
            moveTo(7f, 12f); horizontalLineTo(17f)
        }.build()
    }

    /** Checkbox with a tick, for "To check". */
    val CheckBox: ImageVector by lazy {
        ImageVector.Builder("CheckBox", 24.dp, 24.dp, 24f, 24f).path(
            stroke = SolidColor(Color.Black),
            strokeLineWidth = 2f,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round,
        ) {
            moveTo(20f, 12f); verticalLineTo(18f); arcTo(2f, 2f, 0f, false, true, 18f, 20f); horizontalLineTo(6f)
            arcTo(2f, 2f, 0f, false, true, 4f, 18f); verticalLineTo(6f); arcTo(2f, 2f, 0f, false, true, 6f, 4f); horizontalLineTo(14f)
            moveTo(8f, 11f); lineTo(11f, 14f); lineTo(20f, 5f)
        }.build()
    }

    /** Plug, for a source that's switched off. */
    val Plug: ImageVector by lazy {
        ImageVector.Builder("Plug", 24.dp, 24.dp, 24f, 24f).path(
            stroke = SolidColor(Color.Black),
            strokeLineWidth = 2f,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round,
        ) {
            moveTo(9f, 3f); verticalLineTo(7f); moveTo(15f, 3f); verticalLineTo(7f)
            moveTo(6f, 7f); horizontalLineTo(18f); verticalLineTo(11f); arcTo(6f, 6f, 0f, false, true, 6f, 11f); close()
            moveTo(12f, 17f); verticalLineTo(21f)
        }.build()
    }
}
