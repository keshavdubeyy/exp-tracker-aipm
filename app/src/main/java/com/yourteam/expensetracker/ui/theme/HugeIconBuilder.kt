package com.yourteam.expensetracker.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.unit.dp

/** One SVG element of a Hugeicon, already turned into path data. */
internal class HugePart(
    val d: String,
    val stroke: Boolean,
    val fill: Boolean,
    val width: Float,
    val round: Boolean,
    val evenOdd: Boolean = false,
)

/** Builds a 24 × 24 Hugeicon. Black is a placeholder: the Icon composable tints it. */
internal fun hugeIcon(name: String, vararg parts: HugePart): ImageVector =
    ImageVector.Builder(name, 24.dp, 24.dp, 24f, 24f).apply {
        parts.forEach { p ->
            addPath(
                pathData = addPathNodes(p.d),
                pathFillType = if (p.evenOdd) PathFillType.EvenOdd else PathFillType.NonZero,
                fill = if (p.fill) SolidColor(Color.Black) else null,
                stroke = if (p.stroke) SolidColor(Color.Black) else null,
                strokeLineWidth = p.width,
                strokeLineCap = if (p.round) StrokeCap.Round else StrokeCap.Butt,
                strokeLineJoin = if (p.round) StrokeJoin.Round else StrokeJoin.Miter,
            )
        }
    }.build()
