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
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.selection.selectable
import com.yourteam.expensetracker.ui.theme.AppIcons
import com.yourteam.expensetracker.ui.theme.TextSecondary
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

    // MVP M24 tester metrics: note the day the app was opened, keep the daily upload scheduled,
    // and ask once (test builds with a server only) before anything is sent.
    androidx.lifecycle.compose.LifecycleResumeEffect(Unit) {
        com.yourteam.expensetracker.metrics.UsageLog.appOpened(context)
        onPauseOrDispose { }
    }
    var askMetrics by remember {
        androidx.compose.runtime.mutableStateOf(
            com.yourteam.expensetracker.metrics.MetricsUpload.available && com.yourteam.expensetracker.metrics.UsageLog.needsAsk(context),
        )
    }
    LaunchedEffect(Unit) { com.yourteam.expensetracker.metrics.MetricsUpload.schedule(context) }
    if (askMetrics) {
        com.yourteam.expensetracker.metrics.MetricsConsentDialog(onAnswer = { yes ->
            com.yourteam.expensetracker.metrics.UsageLog.setConsent(context, yes)
            com.yourteam.expensetracker.metrics.MetricsUpload.schedule(context)
            if (yes) com.yourteam.expensetracker.metrics.MetricsUpload.sendNow(context)
            Feedback.tick()
            askMetrics = false
        })
    }

    fun openTab(route: String) = navController.navigate(route) {
        popUpTo(navController.graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
    val openPayment = { key: String -> navController.navigate("${Routes.PAYMENT}/${Uri.encode(key)}") }
    val back: () -> Unit = { navController.popBackStack() }
    val openBudgetCategory = { name: String -> navController.navigate("${Routes.BUDGET_CATEGORY}/${Uri.encode(name)}") }
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

    // Opened from a category notification: go straight to Review (Feature 1 Flow 3).
    val activity = context as? android.app.Activity
    LaunchedEffect(activity?.intent) {
        val intent = activity?.intent ?: return@LaunchedEffect
        val open = intent.getStringExtra(com.yourteam.expensetracker.notifications.CategoryPrompt.EXTRA_OPEN) ?: return@LaunchedEffect
        intent.removeExtra(com.yourteam.expensetracker.notifications.CategoryPrompt.EXTRA_OPEN)
        val budgets = com.yourteam.expensetracker.notifications.BudgetAlerts
        when {
            open == "review" -> openTab(TopLevelDestination.Review.route)
            open == budgets.OPEN_BUDGETS -> openTab(TopLevelDestination.Budgets.route)
            open.startsWith(budgets.OPEN_CATEGORY_PREFIX) -> { // Feature 3 alert → that category's budget
                openTab(TopLevelDestination.Budgets.route)
                openBudgetCategory(open.removePrefix(budgets.OPEN_CATEGORY_PREFIX))
            }
        }
    }

    Box(Modifier.fillMaxSize().background(Bg)) {
        Column(Modifier.fillMaxSize().systemBarsPadding().nestedScroll(hideOnScroll)) {
            // Profile button on top and the nav bar at the bottom, only on the main tabs; sub-screens have their own Back.
            val showTabs = onTab != null || currentDestination == null
            if (showTabs) {
                ProfileBar(
                    modifier = Modifier.clipToBounds().layout { m, c ->
                        val p = m.measure(c)
                        barHeight[0] = p.height
                        val hidden = barHidden.roundToInt().coerceAtMost(p.height)
                        layout(p.width, p.height - hidden) { p.place(0, -hidden) }
                    },
                    onProfile = { navController.navigate(Routes.PROFILE) },
                    // Test builds only: switch to made-up data, in the top bar instead of floating.
                    sample = if (BuildConfig.TEST_BUILD) sampleMode else null,
                    onToggleSample = {
                        Feedback.toggle(!sampleMode) // F16
                        repo.setSampleMode(!sampleMode)
                    },
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
                        onOpenReceived = { navController.navigate(Routes.PAYMENTS_RECEIVED) },
                        onOpenReview = { openTab(TopLevelDestination.Review.route) },
                        onOpenBudgets = { openTab(TopLevelDestination.Budgets.route) },
                        onOpenInsights = { openTab(TopLevelDestination.Insights.route) },
                    )
                }
                composable(TopLevelDestination.Review.route) { ReviewScreen(onOpenPayment = openPayment) }
                composable(TopLevelDestination.Budgets.route) {
                    com.yourteam.expensetracker.ui.screens.budgets.BudgetsScreen(
                        onSetup = { mode -> navController.navigate("${Routes.BUDGET_SETUP}/$mode") },
                        onOpenCategory = openBudgetCategory,
                        onOpenPast = { m -> navController.navigate("${Routes.BUDGET_PAST}/${m ?: "latest"}") },
                        onOpenReview = { openTab(TopLevelDestination.Review.route) },
                    )
                }
                composable(
                    route = "${Routes.BUDGET_SETUP}/{mode}",
                    arguments = listOf(navArgument("mode") { type = NavType.StringType }),
                    enterTransition = { slideIn() }, popExitTransition = { slideOut() },
                ) { entry ->
                    com.yourteam.expensetracker.ui.screens.budgets.BudgetSetupScreen(
                        mode = entry.arguments?.getString("mode").orEmpty(),
                        onDone = back,
                    )
                }
                composable(
                    route = "${Routes.BUDGET_CATEGORY}/{name}",
                    arguments = listOf(navArgument("name") { type = NavType.StringType }),
                    enterTransition = { slideIn() }, popExitTransition = { slideOut() },
                ) { entry ->
                    com.yourteam.expensetracker.ui.screens.budgets.BudgetCategoryScreen(
                        category = Uri.decode(entry.arguments?.getString("name").orEmpty()),
                        onBack = back,
                        onOpenPayment = openPayment,
                    )
                }
                composable(
                    route = "${Routes.BUDGET_PAST}/{month}",
                    arguments = listOf(navArgument("month") { type = NavType.StringType }),
                    enterTransition = { slideIn() }, popExitTransition = { slideOut() },
                ) { entry ->
                    com.yourteam.expensetracker.ui.screens.budgets.PastBudgetsScreen(
                        initial = entry.arguments?.getString("month")?.let { runCatching { java.time.YearMonth.parse(it) }.getOrNull() },
                        onBack = back,
                    )
                }
                composable(TopLevelDestination.Insights.route) {
                    InsightsScreen(
                        onOpenReview = { openTab(TopLevelDestination.Review.route) },
                        onNavigate = { navController.navigate(it) },
                        onOpenSources = { navController.navigate(Routes.SOURCES) },
                    )
                }
                composable(
                    route = "${com.yourteam.expensetracker.ui.screens.insights.InsightNav.CATEGORY}/{${com.yourteam.expensetracker.ui.screens.insights.InsightNav.ARG_TARGET}}/{${com.yourteam.expensetracker.ui.screens.insights.InsightNav.ARG_PERIOD}}",
                    arguments = listOf(
                        navArgument(com.yourteam.expensetracker.ui.screens.insights.InsightNav.ARG_TARGET) { type = NavType.StringType },
                        navArgument(com.yourteam.expensetracker.ui.screens.insights.InsightNav.ARG_PERIOD) { type = NavType.StringType },
                    ),
                    enterTransition = { slideIn() }, popExitTransition = { slideOut() },
                ) { entry ->
                    com.yourteam.expensetracker.ui.screens.insights.CategoryDetailScreen(
                        category = Uri.decode(entry.arguments?.getString(com.yourteam.expensetracker.ui.screens.insights.InsightNav.ARG_TARGET).orEmpty()),
                        period = com.yourteam.expensetracker.ui.screens.insights.InsightNav.decodePeriod(entry.arguments?.getString(com.yourteam.expensetracker.ui.screens.insights.InsightNav.ARG_PERIOD).orEmpty()),
                        onBack = back,
                        onOpenPayment = openPayment,
                    )
                }
                composable(
                    route = "${com.yourteam.expensetracker.ui.screens.insights.InsightNav.INSIGHT}/{${com.yourteam.expensetracker.ui.screens.insights.InsightNav.ARG_TARGET}}/{${com.yourteam.expensetracker.ui.screens.insights.InsightNav.ARG_PERIOD}}",
                    arguments = listOf(
                        navArgument(com.yourteam.expensetracker.ui.screens.insights.InsightNav.ARG_TARGET) { type = NavType.StringType },
                        navArgument(com.yourteam.expensetracker.ui.screens.insights.InsightNav.ARG_PERIOD) { type = NavType.StringType },
                    ),
                    enterTransition = { slideIn() }, popExitTransition = { slideOut() },
                ) { entry ->
                    com.yourteam.expensetracker.ui.screens.insights.InsightDetailScreen(
                        insightId = Uri.decode(entry.arguments?.getString(com.yourteam.expensetracker.ui.screens.insights.InsightNav.ARG_TARGET).orEmpty()),
                        period = com.yourteam.expensetracker.ui.screens.insights.InsightNav.decodePeriod(entry.arguments?.getString(com.yourteam.expensetracker.ui.screens.insights.InsightNav.ARG_PERIOD).orEmpty()),
                        onBack = back,
                        onOpenPayment = openPayment,
                    )
                }
                subScreen(Routes.SOURCES) { SourcesScreen(onBack = back) }
                subScreen(Routes.DUPLICATES) { com.yourteam.expensetracker.ui.screens.profile.DuplicatesScreen(onBack = back) }
                subScreen(Routes.DEVELOPER) { com.yourteam.expensetracker.ui.screens.profile.DeveloperScreen(onBack = back) }
                subScreen(Routes.CATEGORIES) { com.yourteam.expensetracker.ui.screens.profile.CategoriesScreen(onBack = back) }
                subScreen(Routes.ASK) { AskScreen(onBack = back) }
                subScreen(Routes.PAYMENTS) { PaymentsScreen(onBack = back, onOpenPayment = openPayment) }
                subScreen(Routes.PAYMENTS_RECEIVED) { PaymentsScreen(onBack = back, onOpenPayment = openPayment, onlyReceived = true) }
                subScreen(Routes.PROFILE) {
                    ProfileScreen(onBack = back, onOpenSources = { navController.navigate(Routes.SOURCES) }, onOpenCategories = { navController.navigate(Routes.CATEGORIES) }, onOpenDeveloper = { navController.navigate(Routes.DEVELOPER) }, onOpenDuplicates = { navController.navigate(Routes.DUPLICATES) })
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
            if (showTabs) {
                BottomNavBar(
                    current = onTab ?: TopLevelDestination.Home,
                    reviewCount = (reviewState as? com.yourteam.expensetracker.ui.screens.review.ReviewUiState.Loaded)?.toCheck?.count ?: 0,
                    onTab = {
                        if (it != onTab) Feedback.tick() // F15
                        openTab(it.route)
                    },
                )
            }
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

/** Profile avatar, top left of the main tabs (DESIGN.md §4.1). Hides while content scrolls down. */
@Composable
private fun ProfileBar(
    modifier: Modifier = Modifier,
    onProfile: () -> Unit,
    /** Null hides the Sample switch (release builds). */
    sample: Boolean? = null,
    onToggleSample: () -> Unit = {},
) {
    Row(modifier.fillMaxWidth().padding(horizontal = Spacing.screen, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier
                .size(Spacing.circle)
                .clip(PillShape)
                .background(Accent)
                .clickable(role = Role.Button, onClick = onProfile),
            contentAlignment = Alignment.Center,
        ) {
            Icon(com.yourteam.expensetracker.ui.theme.HugeIcons.User, contentDescription = "Profile, accounts and settings", tint = OnAccent, modifier = Modifier.size(24.dp))
        }
        if (sample != null) {
            Spacer(Modifier.weight(1f))
            Text(
                if (sample) "Sample on" else "Sample off",
                style = AppType.chip,
                color = if (sample) OnAccent else TextPrimary,
                modifier = Modifier
                    .heightIn(min = Spacing.circle)
                    .clip(PillShape)
                    .background(if (sample) Accent else Surface)
                    .clickable(role = Role.Switch, onClick = onToggleSample)
                    .padding(horizontal = 16.dp, vertical = 13.dp),
            )
        }
    }
}

private fun TopLevelDestination.icon() = when (this) {
    TopLevelDestination.Home -> AppIcons.Home
    TopLevelDestination.Insights -> AppIcons.Chart
    TopLevelDestination.Budgets -> AppIcons.Wallet
    TopLevelDestination.Review -> AppIcons.CheckBox
}

/**
 * Bottom navigation bar: Home · Insights · Budgets · Review (DESIGN.md §4.1). Active tab: icon in an
 * accent pill, label in textPrimary. Review shows how many payments need a check.
 */
@Composable
private fun BottomNavBar(current: TopLevelDestination, reviewCount: Int, onTab: (TopLevelDestination) -> Unit) {
    Row(
        Modifier.fillMaxWidth().background(Bg).padding(horizontal = 8.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TopLevelDestination.entries.forEach { d ->
            val selected = d == current
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .weight(1f)
                    .clip(PillShape)
                    .selectable(selected = selected, role = Role.Tab, onClick = { onTab(d) })
                    .padding(vertical = 4.dp),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Box(
                        Modifier
                            .size(width = 60.dp, height = 32.dp)
                            .clip(PillShape)
                            .background(if (selected) Accent else Bg),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(d.icon(), contentDescription = null, tint = if (selected) OnAccent else TextSecondary, modifier = Modifier.size(22.dp))
                    }
                    if (d == TopLevelDestination.Review && reviewCount > 0) {
                        Text(
                            "$reviewCount",
                            style = AppType.chip,
                            color = OnAccent,
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .offset(x = 6.dp, y = (-4).dp)
                                .background(Accent, PillShape)
                                .padding(horizontal = 6.dp),
                        )
                    }
                }
                Spacer(Modifier.height(4.dp))
                Text(d.label, style = AppType.chip, color = if (selected) TextPrimary else TextSecondary)
            }
        }
    }
}



