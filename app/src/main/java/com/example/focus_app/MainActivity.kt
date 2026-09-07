package com.example.focus_app

import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
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
class MainActivity : ComponentActivity() {
    @Inject lateinit var settingsRepository: SettingsRepository
    @Inject lateinit var keepAliveStore: KeepAliveStore
    @Inject lateinit var themeStore: ThemeStore
    @Inject lateinit var permissionStatusProvider: PermissionStatusProvider

    private val systemAccessibilityEnabled = MutableStateFlow(false)
    private var openTasksRequestId by mutableIntStateOf(0)

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
            FocusAppTheme(mode = theme.mode, color = theme.color) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    NavGraph(openTasksRequestId = openTasksRequestId)
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        systemAccessibilityEnabled.value = permissionStatusProvider.accessibilityEnabled()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        consumeNavigationIntent(intent)
    }

    private fun consumeNavigationIntent(intent: Intent) {
        if (intent.action == ACTION_OPEN_TASKS) {
            openTasksRequestId += 1
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
        const val ACTION_OPEN_TASKS = "com.example.focus_app.action.OPEN_TASKS"
    }
}
