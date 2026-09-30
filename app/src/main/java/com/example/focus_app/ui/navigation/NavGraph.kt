package com.example.focus_app.ui.navigation

import com.example.focus_app.R
import kotlinx.coroutines.flow.first
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.dp
import androidx.compose.material3.MaterialTheme
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
import com.example.focus_app.ui.onboarding.TutorialScreen
import com.example.focus_app.ui.settings.CustomReturnAppPickerScreen
import com.example.focus_app.ui.settings.AppGroupEditorScreen
import com.example.focus_app.ui.settings.AppGroupsScreen
import com.example.focus_app.ui.settings.FeedbackAndSupportScreen
import com.example.focus_app.ui.settings.ReportIssueScreen
import com.example.focus_app.ui.settings.SettingsScreen
import com.example.focus_app.ui.stats.StatsScreen
import com.example.focus_app.ui.tasks.TaskListScreen
import com.example.focus_app.util.PermissionHelper

sealed class Screen(val route: String) {
    object Onboarding : Screen("onboarding")
    object Tutorial : Screen("tutorial")
    object Home : Screen("home")
    object Tasks : Screen("tasks")
    object Mood : Screen("mood")
    object Settings : Screen("settings")
    object Stats : Screen("stats")
    object FeedbackAndSupport : Screen("feedback_and_support")
    object ReportIssue : Screen("report_issue")
    object CustomReturnPicker : Screen("custom_return_picker")
    object AppGroups : Screen("app_groups")
    object AppGroupEditor : Screen("app_group_editor/{groupId}") {
        fun routeFor(groupId: String? = null): String = "app_group_editor/${groupId ?: "new"}"
    }
}

private data class TabItem(val route: String, val label: Int, val icon: ImageVector)

private sealed interface SettingsExitRequest {
    data object Back : SettingsExitRequest
    data object Summary : SettingsExitRequest
    data class Tab(val route: String) : SettingsExitRequest
}

private const val SETTINGS_EXIT_BACK = "__settings_exit_back__"

private fun SettingsExitRequest.savedValue(): String = when (this) {
    SettingsExitRequest.Back -> SETTINGS_EXIT_BACK
    SettingsExitRequest.Summary -> "__settings_exit_summary__"
    is SettingsExitRequest.Tab -> route
}

private fun savedSettingsExitRequest(value: String): SettingsExitRequest =
    when (value) {
        SETTINGS_EXIT_BACK -> SettingsExitRequest.Back
        "__settings_exit_summary__" -> SettingsExitRequest.Summary
        else -> SettingsExitRequest.Tab(value)
    }

private val mainTabs = listOf(
    TabItem(Screen.Home.route, R.string.core_home, Icons.Filled.Home),
    TabItem(Screen.Tasks.route, R.string.core_tasks, Icons.AutoMirrored.Filled.List),
    TabItem(Screen.Stats.route, R.string.core_stats, com.example.focus_app.ui.theme.ForestIcons.Statistics),
    TabItem(Screen.Settings.route, R.string.core_settings, Icons.Filled.Settings)
)

internal fun mainStartDestination(
    onboardingDone: Boolean,
    openTasksRequested: Boolean
): String = when {
    !onboardingDone -> Screen.Onboarding.route
    else -> Screen.Home.route
}

internal fun settingsExitNeedsConfirmation(
    currentRoute: String?,
    hasUnsavedChanges: Boolean
): Boolean = currentRoute == Screen.Settings.route && hasUnsavedChanges

@Composable
fun NavGraph(
    openTasksRequestId: Int = 0,
    openSummaryRequestId: Int = 0,
    onCelebrate: () -> Unit,
    onCheckUpdates: () -> Unit,
    updateChecking: Boolean
) {
    val textContext = androidx.compose.ui.platform.LocalContext.current
    val context = LocalContext.current
    val navController = rememberNavController()
    var onboardingDone by remember { mutableStateOf(PermissionHelper.isOnboardingDone(context)) }
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

    fun navigateToMainTab(route: String, restore: Boolean = true) {
        navController.navigate(route) {
            popUpTo(navController.graph.findStartDestination().id) {
                saveState = restore
            }
            launchSingleTop = true
            restoreState = restore && route != Screen.Home.route
        }
    }

    fun performSettingsExit(request: SettingsExitRequest) {
        when (request) {
            SettingsExitRequest.Back -> navController.popBackStack()
            // A summary notification must show the overview, even if Statistics last showed details.
            SettingsExitRequest.Summary -> navigateToMainTab(Screen.Stats.route, restore = false)
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

    LaunchedEffect(openTasksRequestId, onboardingDone) {
        if (openTasksRequestId > 0 && onboardingDone) {
            // NavHost installs its graph during composition. A cold-start request can
            // arrive first; wait for an entry instead of reading an uninitialized graph.
            val entry = navController.currentBackStackEntryFlow.first()
            if (entry.destination.route != Screen.Tasks.route) {
                requestSettingsExit(SettingsExitRequest.Tab(Screen.Tasks.route))
            }
        }
    }

    LaunchedEffect(openSummaryRequestId, onboardingDone) {
        if (openSummaryRequestId > 0 && onboardingDone) {
            navController.currentBackStackEntryFlow.first()
            requestSettingsExit(SettingsExitRequest.Summary)
        }
    }

    Scaffold(
        bottomBar = {
            if (currentRoute in tabRoutes) {
                NavigationBar(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp).clip(RoundedCornerShape(26.dp)),
                    containerColor = MaterialTheme.colorScheme.surface
                ) {
                    mainTabs.forEach { tab ->
                        NavigationBarItem(
                            selected = currentRoute == tab.route,
                            onClick = {
                                if (currentRoute != tab.route) {
                                    requestSettingsExit(SettingsExitRequest.Tab(tab.route))
                                }
                            },
                            icon = { Icon(tab.icon, contentDescription = textContext.getString(tab.label)) },
                            label = { Text(textContext.getString(tab.label)) }
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
                    onboardingDone = PermissionHelper.isOnboardingDone(context)
                    navController.navigate(Screen.Home.route) {
                        popUpTo(Screen.Onboarding.route) { inclusive = true }
                    }
                })
            }
            composable(Screen.Tutorial.route) {
                TutorialScreen(onComplete = { navController.popBackStack() })
            }
            composable(Screen.Home.route) {
                HomeScreen(
                    navigateToMood = { navController.navigate(Screen.Mood.route) },
                    navigateToTasks = { navigateToMainTab(Screen.Tasks.route) },
                    navigateToTutorial = { navController.navigate(Screen.Tutorial.route) },
                    navigateToAppGroups = { navController.navigate(Screen.AppGroups.route) },
                    onCelebrate = onCelebrate
                )
            }
            composable(Screen.Tasks.route) {
                TaskListScreen(onBack = { navController.popBackStack() }, onCelebrate = onCelebrate)
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
                    navigateToAppGroups = { navController.navigate(Screen.AppGroups.route) },
                    navigateToTutorial = { navController.navigate(Screen.Tutorial.route) },
                    onCheckUpdates = onCheckUpdates,
                    updateChecking = updateChecking
                )
            }
            composable(Screen.FeedbackAndSupport.route) {
                FeedbackAndSupportScreen(
                    onBack = { navController.popBackStack() },
                    onReportIssue = { navController.navigate(Screen.ReportIssue.route) }
                )
            }
            composable(Screen.ReportIssue.route) {
                ReportIssueScreen(onBack = { navController.popBackStack() })
            }
            composable(Screen.CustomReturnPicker.route) {
                CustomReturnAppPickerScreen(onBack = { navController.popBackStack() })
            }
            composable(Screen.Stats.route) {
                StatsScreen(onBack = { navController.popBackStack() },
                    navigateToMood = { navController.navigate(Screen.Mood.route) })
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
            title = { Text(textContext.getString(R.string.core_leave_settings)) },
            text = { Text(textContext.getString(R.string.core_unsaved_settings)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        settingsEdited = false
                        pendingSettingsExit = null
                        performSettingsExit(request)
                    }
                ) {
                    Text(
                        if (request != SettingsExitRequest.Back) textContext.getString(R.string.core_leave_switch) else textContext.getString(R.string.core_leave)
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingSettingsExit = null }) {
                    Text(textContext.getString(R.string.core_keep_editing))
                }
            }
        )
    }

}
