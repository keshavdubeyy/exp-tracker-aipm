package com.yourteam.expensetracker.ui.navigation

import androidx.compose.foundation.layout.navigationBarsPadding
import com.yourteam.expensetracker.ui.feedback.Feedback
import com.yourteam.expensetracker.ui.feedback.FeedbackHost
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import com.yourteam.expensetracker.ui.screens.onboarding.Onboarding
import com.yourteam.expensetracker.ui.screens.onboarding.OnboardingFlow
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.layout
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.yourteam.expensetracker.BuildConfig
import com.yourteam.expensetracker.data.PaymentRepository
import com.yourteam.expensetracker.notifications.GPayScreenReader
import com.yourteam.expensetracker.ui.screens.ask.AskScreen
import com.yourteam.expensetracker.ui.screens.detail.DetailScreen
import com.yourteam.expensetracker.ui.screens.detail.DetailViewModel
import com.yourteam.expensetracker.ui.screens.home.HomeScreen
import com.yourteam.expensetracker.ui.screens.insights.InsightsScreen
import com.yourteam.expensetracker.ui.screens.payments.PaymentsScreen
import com.yourteam.expensetracker.ui.screens.profile.ProfileScreen
import com.yourteam.expensetracker.ui.screens.review.ReviewScreen
import com.yourteam.expensetracker.ui.screens.review.ReviewViewModel
import com.yourteam.expensetracker.ui.screens.sources.SourcesScreen
import com.yourteam.expensetracker.ui.theme.Accent
import com.yourteam.expensetracker.ui.theme.AppType
import com.yourteam.expensetracker.ui.theme.Bg
import com.yourteam.expensetracker.ui.theme.OnAccent
import com.yourteam.expensetracker.ui.theme.PillShape
import com.yourteam.expensetracker.ui.theme.Spacing
import com.yourteam.expensetracker.ui.theme.Surface
import com.yourteam.expensetracker.ui.theme.SurfaceRaised
import com.yourteam.expensetracker.ui.theme.TextPrimary
import kotlin.math.roundToInt

private const val TAB_FADE_MS = 140

/**
 * First run (or SMS access turned off later): onboarding (ONBOARDING.md, MVP M1/M2/M23), no tabs.
 * Otherwise the app itself.
 */
@Composable
fun AppRoot() {
    val context = LocalContext.current
    var setupNeeded by remember { androidx.compose.runtime.mutableStateOf(!Onboarding.isComplete(context) || !Onboarding.hasSms(context)) }
    androidx.lifecycle.compose.LifecycleResumeEffect(Unit) {
        if (!Onboarding.hasSms(context)) setupNeeded = true
        onPauseOrDispose { }
    }
    if (setupNeeded) {
        Box(Modifier.fillMaxSize().background(Bg).systemBarsPadding()) {
            // ONBOARDING.md. If it was finished before and only SMS is off now, just the SMS steps.
            OnboardingFlow(onDone = { setupNeeded = false }, returning = Onboarding.isComplete(context))
        }
        FeedbackHost(Modifier.statusBarsPadding().padding(start = 16.dp, end = 16.dp, top = 8.dp)) // haptics on setup
        return
    }
    MainApp()
}

@Composable
private fun MainApp() {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = backStackEntry?.destination
    // Activity-scoped, only for the count on the Review tab.
    val reviewState by viewModel<ReviewViewModel>().state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val repo = PaymentRepository.get(context)
    val sampleMode by repo.sampleMode.collectAsStateWithLifecycle()

    fun openTab(route: String) = navController.navigate(route) {
        popUpTo(navController.graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
    val openPayment = { key: String -> navController.navigate("${Routes.PAYMENT}/${Uri.encode(key)}") }
    val back: () -> Unit = { navController.popBackStack() }
    val onTab = TopLevelDestination.entries.firstOrNull { d -> currentDestination?.hierarchy?.any { it.route == d.route } == true }

    // Top bar hides while content scrolls down, and comes back as soon as it scrolls up.
    val barHeight = remember { IntArray(1) }
    var barHidden by remember { mutableFloatStateOf(0f) }
    val hideOnScroll = remember {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                val before = barHidden
                barHidden = (barHidden - available.y).coerceIn(0f, barHeight[0].toFloat())
                return Offset(0f, before - barHidden) // the bar moves first, then the list
            }
        }
    }
    LaunchedEffect(currentDestination?.route) { barHidden = 0f } // always show it on a new screen

    Box(Modifier.fillMaxSize().background(Bg)) {
        Column(Modifier.fillMaxSize().systemBarsPadding().nestedScroll(hideOnScroll)) {
            // Tabs only on the four main screens; sub-screens have their own Back.
            if (onTab != null || currentDestination == null) {
                TopTabs(
                    modifier = Modifier.clipToBounds().layout { m, c ->
                        val p = m.measure(c)
                        barHeight[0] = p.height
                        val hidden = barHidden.roundToInt().coerceAtMost(p.height)
                        layout(p.width, p.height - hidden) { p.place(0, -hidden) }
                    },
                    current = onTab ?: TopLevelDestination.Home,
                    reviewCount = (reviewState as? com.yourteam.expensetracker.ui.screens.review.ReviewUiState.Loaded)?.toCheck?.count ?: 0,
                    onTab = {
                        if (it != onTab) Feedback.tick() // F15
                        openTab(it.route)
                    },
                    onProfile = { navController.navigate(Routes.PROFILE) },
                )
            }
            NavHost(
                navController = navController,
                startDestination = TopLevelDestination.Home.route,
                modifier = Modifier.weight(1f),
                enterTransition = { fadeIn(tween(TAB_FADE_MS, delayMillis = TAB_FADE_MS / 2)) },
                exitTransition = { fadeOut(tween(TAB_FADE_MS / 2)) },
                popEnterTransition = { fadeIn(tween(TAB_FADE_MS, delayMillis = TAB_FADE_MS / 2)) },
                popExitTransition = { fadeOut(tween(TAB_FADE_MS / 2)) },
            ) {
                composable(TopLevelDestination.Home.route) {
                    HomeScreen(
                        onOpenPayment = openPayment,
                        onOpenPayments = { navController.navigate(Routes.PAYMENTS) },
                        onOpenReview = { openTab(TopLevelDestination.Review.route) },
                        onAsk = { navController.navigate(Routes.ASK) },
                    )
                }
                composable(TopLevelDestination.Review.route) { ReviewScreen(onOpenPayment = openPayment) }
                composable(TopLevelDestination.Insights.route) {
                    InsightsScreen(onOpenReview = { openTab(TopLevelDestination.Review.route) })
                }
                subScreen(Routes.SOURCES) { SourcesScreen(onBack = back) }
                subScreen(Routes.ASK) { AskScreen(onBack = back) }
                subScreen(Routes.PAYMENTS) { PaymentsScreen(onBack = back, onOpenPayment = openPayment) }
                subScreen(Routes.PROFILE) {
                    ProfileScreen(onBack = back, onOpenSources = { navController.navigate(Routes.SOURCES) })
                }
                composable(
                    route = "${Routes.PAYMENT}/{${DetailViewModel.ARG_KEY}}",
                    arguments = listOf(navArgument(DetailViewModel.ARG_KEY) { type = NavType.StringType }),
                    enterTransition = { slideIn() },
                    popExitTransition = { slideOut() },
                ) {
                    DetailScreen(onBack = back)
                }
            }
        }

        // Test builds only: a floating switch for made-up data. Drag it out of the way.
        if (BuildConfig.TEST_BUILD) {
            SampleDataBubble(on = sampleMode, onToggle = {
                Feedback.toggle(!sampleMode) // F16
                repo.setSampleMode(!sampleMode)
            })
        }

        // Confirmations slide down from the top (FEEDBACK.md §2), clear of the Ask bar and bottom CTAs.
        FeedbackHost(
            Modifier
                .align(Alignment.TopCenter)
                .statusBarsPadding()
                .padding(start = 16.dp, end = 16.dp, top = 8.dp),
        )
    }
}

/** Sub-screens slide in from the right like a page on top, and back out. */
private fun androidx.navigation.NavGraphBuilder.subScreen(route: String, content: @Composable () -> Unit) =
    composable(route, enterTransition = { slideIn() }, popExitTransition = { slideOut() }) { content() }

private fun AnimatedContentTransitionScope<*>.slideIn() =
    slideIntoContainer(AnimatedContentTransitionScope.SlideDirection.Start, tween(280, easing = FastOutSlowInEasing)) + fadeIn(tween(200))

private fun AnimatedContentTransitionScope<*>.slideOut() =
    slideOutOfContainer(AnimatedContentTransitionScope.SlideDirection.End, tween(240, easing = FastOutSlowInEasing)) + fadeOut(tween(200))

/** Avatar + pill tabs, scrolling sideways if they overflow (DESIGN.md §4.1). */
@Composable
private fun TopTabs(
    modifier: Modifier = Modifier,
    current: TopLevelDestination,
    reviewCount: Int,
    onTab: (TopLevelDestination) -> Unit,
    onProfile: () -> Unit,
) {
    Row(
        modifier
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = Spacing.screen, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(Spacing.circle)
                .clip(PillShape)
                .background(Accent)
                .clickable(role = Role.Button, onClick = onProfile),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Filled.Person, contentDescription = "Profile, accounts and settings", tint = OnAccent, modifier = Modifier.size(24.dp))
        }
        TopLevelDestination.entries.forEach { d ->
            val selected = d == current
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .heightIn(min = Spacing.circle)
                    .clip(PillShape)
                    .background(if (selected) Accent else Surface)
                    .clickable(role = Role.Tab, onClick = { onTab(d) })
                    .padding(horizontal = 20.dp, vertical = 12.dp),
            ) {
                Text(d.label, style = AppType.tab, color = if (selected) OnAccent else TextPrimary)
                if (d == TopLevelDestination.Review && reviewCount > 0) {
                    Spacer(Modifier.width(8.dp))
                    Text(
                        "$reviewCount",
                        style = AppType.chip,
                        color = if (selected) Accent else OnAccent,
                        modifier = Modifier
                            .background(if (selected) OnAccent else Accent, PillShape)
                            .padding(horizontal = 7.dp, vertical = 1.dp),
                    )
                }
            }
        }
    }
}

/** Small draggable bubble: "Sample" switch for test builds. Accent when on. */
@Composable
private fun SampleDataBubble(on: Boolean, onToggle: () -> Unit) {
    var x by rememberSaveable { mutableFloatStateOf(0f) }
    var y by rememberSaveable { mutableFloatStateOf(0f) }
    Box(Modifier.fillMaxSize().systemBarsPadding().padding(end = 12.dp, bottom = 96.dp), contentAlignment = Alignment.BottomEnd) {
        Text(
            if (on) "Sample on" else "Sample off",
            style = AppType.chip,
            color = if (on) OnAccent else TextPrimary,
            modifier = Modifier
                .offset { IntOffset(x.roundToInt(), y.roundToInt()) }
                .pointerInput(Unit) { detectDragGestures { change, drag -> change.consume(); x += drag.x; y += drag.y } }
                .clip(PillShape)
                .background(if (on) Accent else SurfaceRaised)
                .clickable(role = Role.Switch, onClick = onToggle)
                .padding(horizontal = 12.dp, vertical = 8.dp),
        )
    }
}

