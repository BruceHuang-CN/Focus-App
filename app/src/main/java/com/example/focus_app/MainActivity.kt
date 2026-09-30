package com.example.focus_app

import android.content.Intent
import android.content.ActivityNotFoundException
import android.net.Uri
import androidx.activity.viewModels
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.focus_app.ui.update.AppUpdateDialog
import com.example.focus_app.ui.update.AppUpdateViewModel
import com.example.focus_app.util.PermissionHelper
import android.os.Build
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import com.example.focus_app.ui.components.TaskCompletionCelebration
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.lifecycleScope
import com.example.focus_app.data.repository.SettingsRepository
import com.example.focus_app.data.keepalive.KeepAliveStore
import com.example.focus_app.data.permission.PermissionStatusProvider
import com.example.focus_app.data.theme.ThemeStore
import com.example.focus_app.domain.model.DetectionMode
import com.example.focus_app.domain.permission.isAccessibilityDetectionReady
import com.example.focus_app.service.AppDetectionService
import com.example.focus_app.service.KeepAliveService
import com.example.focus_app.ui.navigation.NavGraph
import com.example.focus_app.ui.theme.FocusAppTheme
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

internal fun shouldRunRealtimeKeepAlive(
    mode: DetectionMode,
    accessibilityEnabled: Boolean,
    guardianEnabled: Boolean,
    keepAlive: Boolean
): Boolean =
    mode == DetectionMode.REALTIME &&
        accessibilityEnabled &&
        guardianEnabled &&
        keepAlive

@AndroidEntryPoint
class MainActivity : AppCompatActivity() {
    @Inject lateinit var returnNavigationGuard: com.example.focus_app.service.ReturnNavigationGuard
    @Inject lateinit var realtimeForegroundProvider: com.example.focus_app.service.RealtimeForegroundProvider
    @Inject lateinit var settingsRepository: SettingsRepository
    @Inject lateinit var keepAliveStore: KeepAliveStore
    @Inject lateinit var themeStore: ThemeStore
    @Inject lateinit var permissionStatusProvider: PermissionStatusProvider

    private val appUpdates: AppUpdateViewModel by viewModels()

    private val systemAccessibilityEnabled = MutableStateFlow(false)
    private var openTasksRequestId by mutableIntStateOf(0)
    private var openSummaryRequestId by mutableIntStateOf(0)
    private var celebrationId by mutableIntStateOf(0)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        consumeNavigationIntent(intent)
        systemAccessibilityEnabled.value = permissionStatusProvider.accessibilityEnabled()
        lifecycleScope.launch {
            combine(
                settingsRepository.getSettingsFlow(),
                keepAliveStore.enabled,
                systemAccessibilityEnabled
            ) { settings, keepAlive, systemAccessibilityEnabled ->
                KeepAliveState(
                    mode = settings.detectionMode,
                    accessibilityEnabled = isAccessibilityDetectionReady(
                        userEnabled = settings.enableAccessibility,
                        systemEnabled = systemAccessibilityEnabled
                    ),
                    guardianEnabled = settings.guardianEnabled,
                    keepAlive = keepAlive
                )
            }
                .distinctUntilChanged()
                .collect { state ->
                    when {
                        state.mode == DetectionMode.COMPATIBILITY && state.guardianEnabled -> {
                            stopKeepAliveService()
                            startCompatibilityService()
                        }
                        shouldRunRealtimeKeepAlive(
                            mode = state.mode,
                            accessibilityEnabled = state.accessibilityEnabled,
                            guardianEnabled = state.guardianEnabled,
                            keepAlive = state.keepAlive
                        ) -> {
                            stopCompatibilityService()
                            startKeepAliveService()
                        }
                        else -> {
                            stopCompatibilityService()
                            stopKeepAliveService()
                        }
                    }
                }
        }
        setContent {
            val theme by themeStore.settings.collectAsState()
            val updateState by appUpdates.state.collectAsStateWithLifecycle()
            FocusAppTheme(mode = theme.mode, color = theme.color) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    Box(Modifier.fillMaxSize()) {
                        NavGraph(
                            openTasksRequestId = openTasksRequestId,
                            openSummaryRequestId = openSummaryRequestId,
                            onCelebrate = { celebrationId++ },
                            onCheckUpdates = { appUpdates.check(manual = true) },
                            updateChecking = updateState.checking
                        )
                        AppUpdateDialog(updateState, ::openUpdateWebsite, appUpdates::dismiss)
                        TaskCompletionCelebration(celebrationId)
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        returnNavigationGuard.onMainResumed(this)
        realtimeForegroundProvider.onRealApplicationForeground(packageName)
        systemAccessibilityEnabled.value = permissionStatusProvider.accessibilityEnabled()
        // Check on normal app entry, after setup. Reminder return and summary intents keep their flow.
        if (intent.action == Intent.ACTION_MAIN && PermissionHelper.isOnboardingDone(this)) {
            appUpdates.check()
        }
    }

    override fun onPause() {
        returnNavigationGuard.onMainPaused(this)
        super.onPause()
    }

    override fun onDestroy() {
        returnNavigationGuard.onMainPaused(this)
        super.onDestroy()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        consumeNavigationIntent(intent)
    }

    private fun consumeNavigationIntent(intent: Intent) {
        if (intent.getBooleanExtra(EXTRA_NAVIGATION_CONSUMED, false)) return
        when (intent.action) {
            ACTION_OPEN_TASKS -> {
                openTasksRequestId += 1
                if (intent.getBooleanExtra(EXTRA_CELEBRATE_RETURN, false)) celebrationId += 1
                intent.removeExtra(EXTRA_CELEBRATE_RETURN)
                intent.putExtra(EXTRA_NAVIGATION_CONSUMED, true)
            }
            ACTION_OPEN_SUMMARY -> { openSummaryRequestId += 1; intent.putExtra(EXTRA_NAVIGATION_CONSUMED, true) }
        }
    }

    private fun openUpdateWebsite(url: String) {
        try {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addCategory(Intent.CATEGORY_BROWSABLE))
            appUpdates.dismiss()
        } catch (_: ActivityNotFoundException) {
            appUpdates.browserUnavailable()
        } catch (_: SecurityException) {
            appUpdates.browserUnavailable()
        }
    }

    private fun startCompatibilityService() {
        val intent = Intent(this, AppDetectionService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent)
        } else {
            startService(intent)
        }
    }

    private fun stopCompatibilityService() {
        stopService(Intent(this, AppDetectionService::class.java))
    }

    private fun startKeepAliveService() {
        val intent = Intent(this, KeepAliveService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent)
        } else {
            startService(intent)
        }
    }

    private fun stopKeepAliveService() {
        stopService(Intent(this, KeepAliveService::class.java))
    }

    private data class KeepAliveState(
        val mode: DetectionMode,
        val accessibilityEnabled: Boolean,
        val guardianEnabled: Boolean,
        val keepAlive: Boolean
    )

    companion object {
        private const val EXTRA_NAVIGATION_CONSUMED = "navigation_consumed"
        const val EXTRA_CELEBRATE_RETURN = "celebrate_return_to_focus"
        const val ACTION_OPEN_SUMMARY = "com.example.focus_app.action.OPEN_SUMMARY"
        const val ACTION_OPEN_TASKS = "com.example.focus_app.action.OPEN_TASKS"
    }
}
