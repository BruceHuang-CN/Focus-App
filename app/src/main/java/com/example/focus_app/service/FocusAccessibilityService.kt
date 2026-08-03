package com.example.focus_app.service

import android.accessibilityservice.AccessibilityService
import android.view.accessibility.AccessibilityEvent
import com.example.focus_app.data.repository.AppSettings
import com.example.focus_app.data.repository.SettingsRepository
import com.example.focus_app.domain.model.DetectionMode
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import javax.inject.Inject

internal fun shouldProcessAccessibilityEvents(settings: AppSettings): Boolean =
    settings.detectionMode == DetectionMode.REALTIME && settings.enableAccessibility

@AndroidEntryPoint
class FocusAccessibilityService : AccessibilityService() {
    @Inject lateinit var settingsRepository: SettingsRepository
    @Inject lateinit var appSessionCoordinator: AppSessionCoordinator

    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private val packageChanges = Channel<String>(Channel.UNLIMITED)
    @Volatile private var isRealtimeMode = false
    private var monitoringStarted = false

    override fun onServiceConnected() {
        super.onServiceConnected()
        if (monitoringStarted) return
        monitoringStarted = true

        scope.launch {
            settingsRepository.getSettingsFlow().collect { settings ->
                isRealtimeMode = shouldProcessAccessibilityEvents(settings)
            }
        }
        scope.launch {
            for (packageName in packageChanges) {
                appSessionCoordinator.onPackageChanged(packageName)
            }
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (!isRealtimeMode || event?.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return
        val pkg = event.packageName?.toString() ?: return
        packageChanges.trySend(pkg)
    }

    override fun onInterrupt() {}

    override fun onDestroy() {
        packageChanges.close()
        scope.cancel()
        super.onDestroy()
    }
}
