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
        setContent {
            ExpenseTrackerTheme {
                AppRoot()
            }
        }
    }
}
