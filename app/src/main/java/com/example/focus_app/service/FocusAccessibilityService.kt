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
    settings.detectionMode == DetectionMode.REALTIME &&
        settings.enableAccessibility &&
        settings.guardianEnabled

internal fun shouldProcessQueuedAccessibilityEvent(settings: AppSettings): Boolean =
    shouldProcessAccessibilityEvents(settings)

internal class AccessibilityForegroundState {
    @Volatile private var latestPackageName: String? = null

    fun onWindowStateChanged(packageName: String, isApplicationTask: Boolean) {
        if (isApplicationTask) {
            latestPackageName = packageName
        }
    }

    fun isForeground(expectedPackage: String): Boolean = latestPackageName == expectedPackage
}

private data class PackageChange(
    val packageName: String,
    val isReminderPresentation: Boolean
)

@AndroidEntryPoint
class FocusAccessibilityService : AccessibilityService() {
    @Inject lateinit var settingsRepository: SettingsRepository
    @Inject lateinit var appSessionCoordinator: AppSessionCoordinator
    @Inject lateinit var reminderPresentationRegistry: ReminderPresentationRegistry

    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private val packageChanges = Channel<PackageChange>(Channel.UNLIMITED)
    private val foregroundState = AccessibilityForegroundState()
    @Volatile private var isRealtimeMode = false
    @Volatile private var currentSettings = AppSettings()
    private var monitoringStarted = false

    override fun onServiceConnected() {
        super.onServiceConnected()
        activeService = this
        if (monitoringStarted) return
        monitoringStarted = true

        scope.launch {
            settingsRepository.getSettingsFlow().collect { settings ->
                currentSettings = settings
                isRealtimeMode = shouldProcessAccessibilityEvents(settings)
            }
        }
        scope.launch {
            for (change in packageChanges) {
                if (!shouldProcessQueuedAccessibilityEvent(currentSettings)) continue
                appSessionCoordinator.onPackageChanged(
                    packageName = change.packageName,
                    foregroundVerifier = foregroundState::isForeground,
                    isReminderPresentation = change.isReminderPresentation
                )
            }
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event?.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return
        val pkg = event.packageName?.toString() ?: return
        foregroundState.onWindowStateChanged(
            packageName = pkg,
            isApplicationTask = packageManager.getLaunchIntentForPackage(pkg) != null
        )
        if (!isRealtimeMode) return
        packageChanges.trySend(
            PackageChange(
                packageName = pkg,
                isReminderPresentation = isReminderPresentationForForegroundChange(
                    reminderPresentationRegistry.isShowing()
                )
            )
        )
    }

    override fun onInterrupt() {}

    override fun onDestroy() {
        if (activeService === this) activeService = null
        packageChanges.close()
        scope.cancel()
        super.onDestroy()
    }

    companion object {
        @Volatile private var activeService: FocusAccessibilityService? = null

        fun performHomeAction(): Boolean =
            activeService?.performGlobalAction(GLOBAL_ACTION_HOME) == true
    }
}
