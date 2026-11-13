package com.yourteam.expensetracker.ui.screens.onboarding

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yourteam.expensetracker.ui.theme.Accent
import com.yourteam.expensetracker.ui.theme.AppType
import com.yourteam.expensetracker.ui.theme.Bg
import com.yourteam.expensetracker.ui.theme.OnAccent
import com.yourteam.expensetracker.ui.theme.PillShape
import com.yourteam.expensetracker.ui.theme.Positive
import com.yourteam.expensetracker.ui.theme.Spacing
import com.yourteam.expensetracker.ui.theme.SurfaceRaised
import com.yourteam.expensetracker.ui.theme.TextPrimary
import com.yourteam.expensetracker.ui.theme.TextSecondary

/** Onboarding headline (DESIGN.md §2 "onboardingTitle"): 28/34, 600. */
internal val ObTitle = AppType.sectionTitle.copy(fontSize = 24.sp, lineHeight = 30.sp)

/**
 * One onboarding screen: optional back + step dots on top, scrolling content, and a pinned
 * bottom area for the primary button (last in focus order, ONBOARDING.md §6).
 */
@Composable
internal fun ObScaffold(
    step: Int?,
    onBack: (() -> Unit)?,
    bottom: @Composable ColumnScope.() -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(Modifier.fillMaxSize().padding(horizontal = Spacing.screen)) {
        Row(Modifier.fillMaxWidth().height(Spacing.circle + 16.dp).padding(top = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            if (onBack != null) {
                Box(
                    Modifier.size(Spacing.circle).clip(PillShape).background(SurfaceRaised).clickable(role = Role.Button, onClick = onBack),
                    contentAlignment = Alignment.Center,
                ) { Icon(com.yourteam.expensetracker.ui.theme.HugeIcons.ChevronLeft, contentDescription = "Back", tint = TextPrimary, modifier = Modifier.size(22.dp)) }
            }
            Spacer(Modifier.weight(1f))
            if (step != null) StepDots(step)
        }
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(top = 24.dp, bottom = 16.dp), content = content)
        Column(Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 20.dp), horizontalAlignment = Alignment.CenterHorizontally, content = bottom)
    }
}

/** Three short bars; done and current ones in accent. Read as "Step 2 of 3". */
@Composable
private fun StepDots(step: Int) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        modifier = Modifier.clearAndSetSemantics { contentDescription = "Step $step of 3" },
    ) {
        repeat(3) { i ->
            Box(Modifier.size(width = 24.dp, height = 4.dp).background(if (i < step) Accent else SurfaceRaised, PillShape))
        }
    }
}

@Composable
internal fun ObHeading(text: String, style: androidx.compose.ui.text.TextStyle = ObTitle, color: Color = TextPrimary) {
    Text(text, style = style, color = color, modifier = Modifier.semantics { heading() })
}

@Composable
internal fun PrimaryButton(text: String, enabled: Boolean = true, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Box(
        modifier
            .fillMaxWidth()
            .height(Spacing.button)
            .clip(PillShape)
            .background(if (enabled) Accent else SurfaceRaised)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Text(text, style = AppType.tab.copy(fontSize = 15.sp), color = if (enabled) OnAccent else TextSecondary) }
}

@Composable
internal fun SecondaryButton(text: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Box(
        modifier
            .height(Spacing.button)
            .clip(PillShape)
            .background(SurfaceRaised)
            .clickable(role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Text(text, style = AppType.tab, color = TextPrimary) }
}

@Composable
internal fun TextLink(text: String, color: Color = Accent, onClick: () -> Unit) {
    Text(
        text,
        style = AppType.tab,
        color = color,
        modifier = Modifier
            .heightIn(min = Spacing.circle)
            .clip(PillShape)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
    )
}

@Composable
internal fun Footnote(text: String) {
    Text(text, style = AppType.body, color = TextSecondary, modifier = Modifier.padding(top = 14.dp))
}

/** Green tick + promise. Green is paired with words, as DESIGN.md asks. */
@Composable
internal fun CheckRow(text: String) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 6.dp)) {
        Icon(com.yourteam.expensetracker.ui.theme.HugeIcons.Tick, contentDescription = null, tint = Positive, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(14.dp))
        Text(text, style = AppType.body.copy(fontSize = 14.sp, lineHeight = 20.sp), color = TextPrimary)
    }
}

/** Numbered how-to steps in an inset box. Words between ** are set in 600. */
@Composable
internal fun StepsBox(steps: List<String>, note: String? = null, fill: Color = Bg) {
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(fill).padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        note?.let { Text(it, style = AppType.body, color = TextSecondary) }
        steps.forEachIndexed { i, s ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(28.dp).background(SurfaceRaised, PillShape), contentAlignment = Alignment.Center) {
                    Text("${i + 1}", style = AppType.chip, color = TextPrimary)
                }
                Spacer(Modifier.width(14.dp))
                Text(bolded(s), style = AppType.body.copy(fontSize = 14.sp, lineHeight = 20.sp), color = TextPrimary)
            }
        }
    }
}

/** "Tap **Allow**, then come back" → "Allow" in semi-bold. */
internal fun bolded(s: String): AnnotatedString = buildAnnotatedString {
    s.split("**").forEachIndexed { i, part ->
        if (i % 2 == 1) withStyle(SpanStyle(fontWeight = AppType.rowTitle.fontWeight)) { append(part) } else append(part)
    }
}

/** Icon in a rounded tile (ob1 rows, ob8 header). */
@Composable
internal fun IconTile(icon: ImageVector, size: Dp = Spacing.circle, fill: Color = SurfaceRaised, tint: Color = TextPrimary, corner: Dp = 16.dp) {
    Box(Modifier.size(size).clip(RoundedCornerShape(corner)).background(fill), contentAlignment = Alignment.Center) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(size * 0.46f))
    }
}

/** Outline icons for onboarding, drawn to DESIGN.md §4.10 (2 dp stroke, round caps). */
internal object ObIcons {
    private fun icon(name: String, block: androidx.compose.ui.graphics.vector.PathBuilder.() -> Unit) =
        ImageVector.Builder(name, 24.dp, 24.dp, 24f, 24f).path(
            stroke = SolidColor(Color.Black),
            strokeLineWidth = 2f,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round,
            pathBuilder = block,
        ).build()

    /** Bar chart on a baseline (app mark on Welcome). */
    val Chart by lazy {
        icon("Chart") {
            moveTo(4f, 20f); horizontalLineTo(20f)
            moveTo(7f, 16f); verticalLineTo(11f)
            moveTo(11f, 16f); verticalLineTo(5f)
            moveTo(15f, 16f); verticalLineTo(9f)
            moveTo(18f, 16f); verticalLineTo(13f)
        }
    }
    val Card by lazy {
        icon("Card") {
            moveTo(5f, 5f); horizontalLineTo(19f); arcTo(2f, 2f, 0f, false, true, 21f, 7f); verticalLineTo(17f)
            arcTo(2f, 2f, 0f, false, true, 19f, 19f); horizontalLineTo(5f); arcTo(2f, 2f, 0f, false, true, 3f, 17f)
            verticalLineTo(7f); arcTo(2f, 2f, 0f, false, true, 5f, 5f); close()
            moveTo(3f, 10f); horizontalLineTo(21f)
        }
    }
    val Lock by lazy {
        icon("Lock") {
            moveTo(6f, 11f); horizontalLineTo(18f); verticalLineTo(20f); horizontalLineTo(6f); close()
            moveTo(8f, 11f); verticalLineTo(8f); arcTo(4f, 4f, 0f, false, true, 16f, 8f); verticalLineTo(11f)
        }
    }
    val Clock by lazy {
        icon("Clock") {
            moveTo(21f, 12f); arcTo(9f, 9f, 0f, true, true, 3f, 12f); arcTo(9f, 9f, 0f, true, true, 21f, 12f); close()
            moveTo(12f, 7f); verticalLineTo(12f); lineTo(15f, 14f)
        }
    }
    val Bell by lazy {
        icon("Bell") {
            moveTo(6f, 16f); verticalLineTo(11f); arcTo(6f, 6f, 0f, false, true, 18f, 11f); verticalLineTo(16f)
            lineTo(19.5f, 18f); horizontalLineTo(4.5f); close()
            moveTo(10f, 21f); horizontalLineTo(14f)
        }
    }
    /** Speech bubble with a slash: SMS off. */
    val SmsOff by lazy {
        icon("SmsOff") {
            moveTo(20f, 15f); verticalLineTo(6f); arcTo(2f, 2f, 0f, false, false, 18f, 4f); horizontalLineTo(9f)
            moveTo(5f, 4.5f); arcTo(2f, 2f, 0f, false, false, 4f, 6f); verticalLineTo(20f); lineTo(8f, 16f); horizontalLineTo(16f)
            moveTo(3f, 3f); lineTo(21f, 21f)
        }
    }
    val ArrowDown by lazy { icon("ArrowDown") { moveTo(12f, 5f); verticalLineTo(19f); moveTo(6f, 13f); lineTo(12f, 19f); lineTo(18f, 13f) } }
}
