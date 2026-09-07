package com.example.focus_app.ui.navigation

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
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
import com.example.focus_app.ui.settings.AppGroupEditorScreen
import com.example.focus_app.ui.settings.AppGroupsScreen
import com.example.focus_app.ui.settings.FeedbackAndSupportScreen
import com.example.focus_app.ui.settings.SettingsScreen
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
    object FeedbackAndSupport : Screen("feedback_and_support")
    object CustomReturnPicker : Screen("custom_return_picker")
    object AppGroups : Screen("app_groups")
    object AppGroupEditor : Screen("app_group_editor/{groupId}") {
        fun routeFor(groupId: String? = null): String = "app_group_editor/${groupId ?: "new"}"
    }
}

private data class TabItem(val route: String, val label: String, val icon: ImageVector)

private sealed interface SettingsExitRequest {
    data object Back : SettingsExitRequest
    data class Tab(val route: String) : SettingsExitRequest
}

private const val SETTINGS_EXIT_BACK = "__settings_exit_back__"

private fun SettingsExitRequest.savedValue(): String = when (this) {
    SettingsExitRequest.Back -> SETTINGS_EXIT_BACK
    is SettingsExitRequest.Tab -> route
}

private fun savedSettingsExitRequest(value: String): SettingsExitRequest =
    if (value == SETTINGS_EXIT_BACK) SettingsExitRequest.Back else SettingsExitRequest.Tab(value)

private val mainTabs = listOf(
    TabItem(Screen.Home.route, "首页", Icons.Filled.Home),
    TabItem(Screen.Tasks.route, "任务", Icons.AutoMirrored.Filled.List),
    TabItem(Screen.Stats.route, "统计", Icons.Filled.DateRange),
    TabItem(Screen.Settings.route, "设置", Icons.Filled.Settings)
)

internal fun mainStartDestination(
    onboardingDone: Boolean,
    openTasksRequested: Boolean
): String = when {
    !onboardingDone -> Screen.Onboarding.route
    openTasksRequested -> Screen.Tasks.route
    else -> Screen.Home.route
}

internal fun settingsExitNeedsConfirmation(
    currentRoute: String?,
    hasUnsavedChanges: Boolean
): Boolean = currentRoute == Screen.Settings.route && hasUnsavedChanges

@Composable
fun NavGraph(openTasksRequestId: Int = 0) {
    val context = LocalContext.current
    val navController = rememberNavController()
    val onboardingDone = remember { PermissionHelper.isOnboardingDone(context) }
    val startDest = remember {
        mainStartDestination(
            onboardingDone = onboardingDone,
            openTasksRequested = openTasksRequestId > 0
        )
    }
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route
    val tabRoutes = mainTabs.map { it.route }
    var settingsEdited by rememberSaveable { mutableStateOf(false) }
    var pendingSettingsExit by rememberSaveable { mutableStateOf<String?>(null) }

    fun navigateToMainTab(route: String) {
        navController.navigate(route) {
            popUpTo(navController.graph.findStartDestination().id) {
                saveState = true
            }
            launchSingleTop = true
            restoreState = true
        }
    }

    fun performSettingsExit(request: SettingsExitRequest) {
        when (request) {
            SettingsExitRequest.Back -> navController.popBackStack()
            is SettingsExitRequest.Tab -> navigateToMainTab(request.route)
        }
    }

    fun requestSettingsExit(request: SettingsExitRequest) {
        if (settingsExitNeedsConfirmation(currentRoute, settingsEdited)) {
            pendingSettingsExit = request.savedValue()
        } else {
            performSettingsExit(request)
        }
    }

    LaunchedEffect(openTasksRequestId) {
        if (openTasksRequestId > 0 && onboardingDone && currentRoute != Screen.Tasks.route) {
            requestSettingsExit(SettingsExitRequest.Tab(Screen.Tasks.route))
        }
    }

    Scaffold(
        bottomBar = {
            if (currentRoute in tabRoutes) {
                NavigationBar {
                    mainTabs.forEach { tab ->
                        NavigationBarItem(
                            selected = currentRoute == tab.route,
                            onClick = {
                                if (currentRoute != tab.route) {
                                    requestSettingsExit(SettingsExitRequest.Tab(tab.route))
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
                    onExitRequested = {
                        requestSettingsExit(SettingsExitRequest.Back)
                    },
                    hasUnsavedChanges = settingsEdited,
                    onUnsavedChangesChanged = { settingsEdited = it },
                    navigateToCustomReturnPicker = {
                        navController.navigate(Screen.CustomReturnPicker.route)
                    },
                    navigateToFeedbackAndSupport = { navController.navigate(Screen.FeedbackAndSupport.route) },
                    navigateToAppGroups = { navController.navigate(Screen.AppGroups.route) }
                )
            }
            composable(Screen.FeedbackAndSupport.route) {
                FeedbackAndSupportScreen(onBack = { navController.popBackStack() })
            }
            composable(Screen.CustomReturnPicker.route) {
                CustomReturnAppPickerScreen(onBack = { navController.popBackStack() })
            }
            composable(Screen.Stats.route) {
                StatsScreen(onBack = { navController.popBackStack() })
            }
            composable(Screen.AppGroups.route) {
                AppGroupsScreen(
                    onBack = { navController.popBackStack() },
                    onAdd = { navController.navigate(Screen.AppGroupEditor.routeFor()) },
                    onEdit = { id -> navController.navigate(Screen.AppGroupEditor.routeFor(id)) }
                )
            }
            composable(Screen.AppGroupEditor.route) { entry ->
                val groupId = entry.arguments?.getString("groupId")?.takeUnless { it == "new" }
                AppGroupEditorScreen(groupId = groupId, onBack = { navController.popBackStack() })
            }
        }
    }

    pendingSettingsExit?.let { savedRequest ->
        val request = savedSettingsExitRequest(savedRequest)
        AlertDialog(
            onDismissRequest = { pendingSettingsExit = null },
            title = { Text("保存设置？") },
            text = { Text("你有未保存的修改。请先保存，或继续修改。") },
            confirmButton = {
                TextButton(
                    onClick = {
                        settingsEdited = false
                        pendingSettingsExit = null
                        performSettingsExit(request)
                    }
                ) {
                    Text(
                        if (request is SettingsExitRequest.Tab) "保存并切换" else "保存并退出"
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingSettingsExit = null }) {
                    Text("继续修改")
                }
            }
        )
    }

}
