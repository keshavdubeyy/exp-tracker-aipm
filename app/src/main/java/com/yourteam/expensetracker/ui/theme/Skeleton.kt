package com.yourteam.expensetracker.ui.theme

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.State
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/*
 * Skeleton placeholders for loading states (design ref: "v5 · Home loading").
 * Each block is a flat fill with a soft light band sweeping left to right every 1.4 s.
 * DESIGN.md §1 allows this one gradient: loading placeholders only, never on real content.
 *
 *   SkeletonPulse {                          // once, around the whole loading screen
 *       Box(Modifier.size(120.dp, 16.dp).skeleton(onCard = false))   // on the bg
 *       SkeletonCircle()                     // inside a surface card
 *   }
 */

private val LocalShimmer = compositionLocalOf<State<Float>?> { null }

/** The sweeping band: 240 dp wide, white at 6% in dark, black at 4% in light. */
private val BandWidth = 240.dp
private val DarkBand = Color.White.copy(alpha = 0.06f)
private val LightBand = Color.Black.copy(alpha = 0.04f)

/** Starts the shared shimmer for everything inside. Wrap the whole loading layout once. */
@Composable
fun SkeletonPulse(content: @Composable () -> Unit) {
    val transition = rememberInfiniteTransition(label = "skeleton")
    val progress = transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            tween(1_400, easing = CubicBezierEasing(0.42f, 0f, 0.58f, 1f)), // CSS ease-in-out
            RepeatMode.Restart,
        ),
        label = "shimmer",
    )
    CompositionLocalProvider(LocalShimmer provides progress, content = content)
}

/**
 * Paints this element as a placeholder. [onCard]: `surfaceRaised` fill for blocks inside a
 * `surface` card, otherwise `surface` fill on the screen background. Outside [SkeletonPulse]
 * it stays still. The band is read in the draw phase only, so it never recomposes.
 */
@Composable
fun Modifier.skeleton(shape: Shape = PillShape, onCard: Boolean = true): Modifier {
    val shimmer = LocalShimmer.current
    val base = if (onCard) SurfaceRaised else Surface
    val bandColor = if (LocalAppColors.current.isDark) DarkBand else LightBand
    return this.drawWithCache {
        val outline = shape.createOutline(size, layoutDirection, this)
        val band = BandWidth.toPx()
        onDrawBehind {
            drawOutline(outline, base)
            val p = shimmer?.value ?: return@onDrawBehind
            val start = -band + 2 * band * p // background-position −240 → +240
            drawOutline(
                outline,
                Brush.horizontalGradient(
                    0f to Color.Transparent, 0.5f to bandColor, 1f to Color.Transparent,
                    startX = start, endX = start + band,
                ),
            )
        }
    }
}

/** Placeholder for a 44dp icon or initials circle. */
@Composable
fun SkeletonCircle(size: Dp = Spacing.circle, onCard: Boolean = true) {
    Box(Modifier.size(size).skeleton(PillShape, onCard))
}

/** Placeholder for one line of text: [height] about the text's cap height, fully round. */
@Composable
fun SkeletonLine(width: Dp, height: Dp = 12.dp, modifier: Modifier = Modifier, onCard: Boolean = true) {
    Box(modifier.size(width, height).skeleton(PillShape, onCard))
}
