package com.yourteam.expensetracker.ui.screens.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.yourteam.expensetracker.ui.theme.AppType
import com.yourteam.expensetracker.ui.theme.CardShape
import com.yourteam.expensetracker.ui.theme.ExpenseTrackerTheme
import com.yourteam.expensetracker.ui.theme.SkeletonCircle
import com.yourteam.expensetracker.ui.theme.SkeletonLine
import com.yourteam.expensetracker.ui.theme.SkeletonPulse
import com.yourteam.expensetracker.ui.theme.Spacing
import com.yourteam.expensetracker.ui.theme.Surface
import com.yourteam.expensetracker.ui.theme.TextSecondary
import com.yourteam.expensetracker.ui.theme.skeleton

/**
 * Home while payments load: first import, or the moment after a new date range is applied
 * (design ref "v5 · Home loading"). Three bars for switcher · hero · delta, the real
 * "Payments ›" title in grey, and five payment cards, the last one fading out.
 * TalkBack hears "Loading your payments" once; the blocks themselves are hidden.
 */
@Composable
fun HomeSkeleton(modifier: Modifier = Modifier) {
    SkeletonPulse {
        Column(
            modifier
                .fillMaxSize()
                .clipToBounds()
                .padding(horizontal = Spacing.screen)
                .padding(top = 16.dp)
                .clearAndSetSemantics { contentDescription = "Loading your payments" },
        ) {
            // Switcher, hero amount, delta line: blocks straight on the background.
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                SkeletonLine(width = 170.dp, height = 16.dp, onCard = false)
                Box(Modifier.size(190.dp, 60.dp).skeleton(RoundedCornerShape(14.dp), onCard = false))
                SkeletonLine(width = 220.dp, height = 16.dp, onCard = false)
            }
            Spacer(Modifier.height(Spacing.section + 8.dp))

            // The section title is real text, greyed: it's the same on every load.
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.heightIn(min = Spacing.circle)) {
                Text("Payments", style = AppType.sectionTitle, color = TextSecondary)
                Spacer(Modifier.width(4.dp))
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = TextSecondary, modifier = Modifier.size(28.dp))
            }
            Spacer(Modifier.height(Spacing.cardGap))

            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                PaymentRowSkeleton(name = 0.70f, sub = 0.38f, amount = 44.dp)
                PaymentRowSkeleton(name = 0.45f, sub = 0.42f, amount = 56.dp)
                PaymentRowSkeleton(name = 0.62f, sub = 0.30f, amount = 50.dp)
                PaymentRowSkeleton(name = 0.55f, sub = 0.36f, amount = 40.dp)
                PaymentRowSkeleton(name = 0.66f, sub = 0.34f, amount = 46.dp, Modifier.alpha(0.5f)) // fades into the Ask bar
            }
        }
    }
}

/** §4.6 payment card: initials circle · payee + subtitle (widths as a share of the row) · amount. */
@Composable
private fun PaymentRowSkeleton(name: Float, sub: Float, amount: Dp, modifier: Modifier = Modifier) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .fillMaxWidth()
            .background(Surface, CardShape)
            .padding(horizontal = 18.dp, vertical = Spacing.cardV),
    ) {
        SkeletonCircle()
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(Modifier.fillMaxWidth(name).height(14.dp).skeleton())
            Box(Modifier.fillMaxWidth(sub).height(12.dp).skeleton())
        }
        Spacer(Modifier.width(14.dp))
        SkeletonLine(width = amount, height = 14.dp)
    }
}

/** Open this file in Android Studio's Split view to see the skeleton without a slow load. */
@Preview(widthDp = 412, heightDp = 915, showBackground = true, backgroundColor = 0xFF0B0B0D)
@Composable
private fun HomeSkeletonPreview() {
    ExpenseTrackerTheme { HomeSkeleton() }
}
