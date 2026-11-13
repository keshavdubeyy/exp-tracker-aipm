package com.yourteam.expensetracker

import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.yourteam.expensetracker.ui.navigation.AppRoot
import com.yourteam.expensetracker.ui.theme.ExpenseTrackerTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen() // system splash; onboarding flags are read synchronously, so no keep-on-screen wait
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT), // icons follow system theme
            navigationBarStyle = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT),
        )
        com.yourteam.expensetracker.notifications.CategoryPrompt.cancelDaily(this)
        AppUpdates.checkOnOpen(this) // tester builds: "New version available" from Firebase
        setContent {
            ExpenseTrackerTheme {
                AppRoot()
            }
        }
    }

    /** A notification tapped while the app is open: keep its "open" extra for [AppRoot]. */
    override fun onStart() {
        super.onStart()
        com.yourteam.expensetracker.metrics.MetricsUpload.onOpen(this) // tester counts stay fresh on the dashboard
    }

    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
    }
}
