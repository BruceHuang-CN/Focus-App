package com.example.focus_app.ui.navigation

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.focus_app.ui.home.HomeScreen
import com.example.focus_app.ui.mood.MoodPickerScreen
import com.example.focus_app.ui.onboarding.OnboardingScreen
import com.example.focus_app.ui.settings.CustomReturnAppPickerScreen
import com.example.focus_app.ui.settings.SettingsScreen
import com.example.focus_app.ui.settings.TargetAppsScreen
import com.example.focus_app.ui.stats.StatsScreen
import com.example.focus_app.ui.tasks.TaskListScreen
import com.example.focus_app.util.PermissionHelper

sealed class Screen(val route: String) {
    object Onboarding : Screen("onboarding")
    object Home : Screen("home")
    object Tasks : Screen("tasks")
    object Mood : Screen("mood")
    object Settings : Screen("settings")
    object Stats : Screen("stats")
    object TargetApps : Screen("target_apps")
    object CustomReturnPicker : Screen("custom_return_picker")
}

private data class TabItem(val route: String, val label: String, val icon: ImageVector)

private val mainTabs = listOf(
    TabItem(Screen.Home.route, "首页", Icons.Filled.Home),
    TabItem(Screen.Tasks.route, "任务", Icons.AutoMirrored.Filled.List),
    TabItem(Screen.Stats.route, "统计", Icons.Filled.DateRange),
    TabItem(Screen.Settings.route, "设置", Icons.Filled.Settings)
)

@Composable
fun NavGraph() {
    val context = LocalContext.current
    val navController = rememberNavController()
    val startDest = remember {
        if (PermissionHelper.isOnboardingDone(context)) Screen.Home.route else Screen.Onboarding.route
    }
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route
    val tabRoutes = mainTabs.map { it.route }

    Scaffold(
        bottomBar = {
            if (currentRoute in tabRoutes) {
                NavigationBar {
                    mainTabs.forEach { tab ->
                        NavigationBarItem(
                            selected = currentRoute == tab.route,
                            onClick = {
                                navController.navigate(tab.route) {
                                    popUpTo(navController.graph.findStartDestination().id) {
                                        saveState = true
                                    }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            icon = { Icon(tab.icon, contentDescription = tab.label) },
                            label = { Text(tab.label) }
                        )
                    }
                }
            }
        }
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = startDest,
            modifier = Modifier.padding(padding)
        ) {
            composable(Screen.Onboarding.route) {
                OnboardingScreen(onComplete = {
                    navController.navigate(Screen.Home.route) {
                        popUpTo(Screen.Onboarding.route) { inclusive = true }
                    }
                })
            }
            composable(Screen.Home.route) {
                HomeScreen(
                    navigateToMood = { navController.navigate(Screen.Mood.route) },
                    navigateToTasks = { navController.navigate(Screen.Tasks.route) }
                )
            }
            composable(Screen.Tasks.route) {
                TaskListScreen(onBack = { navController.popBackStack() })
            }
            composable(Screen.Mood.route) {
                MoodPickerScreen(onBack = { navController.popBackStack() })
            }
            composable(Screen.Settings.route) {
                SettingsScreen(
                    onBack = { navController.popBackStack() },
                    navigateToTargetApps = { navController.navigate(Screen.TargetApps.route) },
                    navigateToCustomReturnPicker = {
                        navController.navigate(Screen.CustomReturnPicker.route)
                    }
                )
            }
            composable(Screen.CustomReturnPicker.route) {
                CustomReturnAppPickerScreen(onBack = { navController.popBackStack() })
            }
            composable(Screen.Stats.route) {
                StatsScreen(onBack = { navController.popBackStack() })
            }
            composable(Screen.TargetApps.route) {
                TargetAppsScreen(onBack = { navController.popBackStack() })
            }
        }
    }

}
