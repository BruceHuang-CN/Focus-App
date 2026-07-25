package com.example.focus_app.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.focus_app.ui.home.HomeScreen
import com.example.focus_app.ui.mood.MoodPickerScreen
import com.example.focus_app.ui.onboarding.OnboardingScreen
import com.example.focus_app.ui.settings.SettingsScreen
import com.example.focus_app.ui.settings.TargetAppsScreen
import com.example.focus_app.ui.stats.StatsScreen
import com.example.focus_app.util.PermissionHelper

sealed class Screen(val route: String) {
    object Onboarding : Screen("onboarding"); object Home : Screen("home")
    object Mood : Screen("mood"); object Settings : Screen("settings")
    object Stats : Screen("stats"); object TargetApps : Screen("target_apps")
}

@Composable
fun NavGraph() {
    val context = LocalContext.current
    val navController = rememberNavController()
    val startDest = remember { if (PermissionHelper.isOnboardingDone(context)) Screen.Home.route else Screen.Onboarding.route }

    NavHost(navController = navController, startDestination = startDest) {
        composable(Screen.Onboarding.route) {
            OnboardingScreen(onComplete = {
                navController.navigate(Screen.Home.route) { popUpTo(Screen.Onboarding.route) { inclusive = true } }
            })
        }
        composable(Screen.Home.route) {
            HomeScreen(navigateToMood = { navController.navigate(Screen.Mood.route) }, navigateToSettings = { navController.navigate(Screen.Settings.route) }, navigateToStats = { navController.navigate(Screen.Stats.route) })
        }
        composable(Screen.Mood.route) { MoodPickerScreen(onBack = { navController.popBackStack() }) }
        composable(Screen.Settings.route) { SettingsScreen(onBack = { navController.popBackStack() }, navigateToTargetApps = { navController.navigate(Screen.TargetApps.route) }) }
        composable(Screen.Stats.route) { StatsScreen(onBack = { navController.popBackStack() }) }
        composable(Screen.TargetApps.route) { TargetAppsScreen(onBack = { navController.popBackStack() }) }
    }
}
