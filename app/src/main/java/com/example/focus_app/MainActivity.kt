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
import androidx.compose.ui.Modifier
import androidx.lifecycle.lifecycleScope
import com.example.focus_app.data.repository.SettingsRepository
import com.example.focus_app.data.keepalive.KeepAliveStore
import com.example.focus_app.data.theme.ThemeStore
import com.example.focus_app.domain.model.DetectionMode
import com.example.focus_app.service.AppDetectionService
import com.example.focus_app.service.KeepAliveService
import com.example.focus_app.ui.navigation.NavGraph
import com.example.focus_app.ui.theme.FocusAppTheme
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    @Inject lateinit var settingsRepository: SettingsRepository
    @Inject lateinit var keepAliveStore: KeepAliveStore
    @Inject lateinit var themeStore: ThemeStore

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        lifecycleScope.launch {
            combine(
                settingsRepository.getSettingsFlow(),
                keepAliveStore.enabled
            ) { settings, keepAlive ->
                KeepAliveState(
                    mode = settings.detectionMode,
                    accessibilityEnabled = settings.enableAccessibility,
                    keepAlive = keepAlive
                )
            }
                .distinctUntilChanged()
                .collect { state ->
                    when {
                        state.mode == DetectionMode.COMPATIBILITY -> {
                            stopKeepAliveService()
                            startCompatibilityService()
                        }
                        state.mode == DetectionMode.REALTIME &&
                            state.accessibilityEnabled &&
                            state.keepAlive -> {
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
                    NavGraph()
                }
            }
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
        val keepAlive: Boolean
    )
}
