package com.example.focus_app

import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.lifecycle.lifecycleScope
import com.example.focus_app.data.repository.SettingsRepository
import com.example.focus_app.domain.model.DetectionMode
import com.example.focus_app.service.AppDetectionService
import com.example.focus_app.service.AppSessionCoordinator
import com.example.focus_app.ui.navigation.NavGraph
import com.example.focus_app.ui.theme.FocusAppTheme
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    @Inject lateinit var settingsRepository: SettingsRepository
    @Inject lateinit var appSessionCoordinator: AppSessionCoordinator

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        lifecycleScope.launch {
            settingsRepository.getSettingsFlow()
                .map { it.detectionMode }
                .distinctUntilChanged()
                .collect { mode -> applyDetectionMode(mode) }
        }
        setContent {
            FocusAppTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    NavGraph()
                }
            }
        }
    }

    private suspend fun applyDetectionMode(mode: DetectionMode) {
        val intent = Intent(this, AppDetectionService::class.java)
        if (mode == DetectionMode.COMPATIBILITY) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(intent)
            } else {
                startService(intent)
            }
        } else {
            appSessionCoordinator.onPackageChanged(null)
            stopService(intent)
        }
    }
}
