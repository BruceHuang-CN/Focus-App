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
    @Volatile private var latestEventUptime: Long = Long.MIN_VALUE

    fun onWindowStateChanged(packageName: String, isApplicationTask: Boolean,
        eventUptimeMillis: Long = 0L): Boolean {
        if (eventUptimeMillis < latestEventUptime) return false
        if (isApplicationTask) {
            latestPackageName = packageName
            latestEventUptime = eventUptimeMillis
        }
        return true
    }

    fun currentEventUptime(): Long = latestEventUptime

    fun currentPackage(): String? = latestPackageName

    fun isForeground(expectedPackage: String): Boolean = latestPackageName == expectedPackage
}

private data class PackageChange(
    val packageName: String,
    val eventUptimeMillis: Long,
    val isReminderPresentation: Boolean
)

@AndroidEntryPoint
class FocusAccessibilityService : AccessibilityService() {
    @Inject lateinit var returnNavigationGuard: ReturnNavigationGuard
    @Inject lateinit var taskRepository: com.example.focus_app.data.repository.TaskRepository
    @Inject lateinit var settingsRepository: SettingsRepository
    @Inject lateinit var appSessionCoordinator: AppSessionCoordinator
    @Inject lateinit var reminderPresentationRegistry: ReminderPresentationRegistry
    @Inject lateinit var pendingReminderRedisplayer: PendingReminderRedisplayer
    @Inject lateinit var realtimeForegroundProvider: RealtimeForegroundProvider
    @Inject lateinit var accessibilityDiagnosticsStore: AccessibilityDiagnosticsStore

    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private val packageChanges = Channel<PackageChange>(Channel.UNLIMITED)
    private val foregroundState = AccessibilityForegroundState()
    @Volatile private var isRealtimeMode = false
    @Volatile private var currentSettings = AppSettings()
    private var monitoringStarted = false

    override fun onServiceConnected() {
        super.onServiceConnected()
        activeService = this
        accessibilityDiagnosticsStore.recordServiceConnected()
        if (monitoringStarted) return
        monitoringStarted = true
        scope.launch {
            taskRepository.observeActiveEvents().collect {
                if (shouldProcessAccessibilityEvents(currentSettings)) {
                    appSessionCoordinator.refreshTaskContext(::isForeground)
                }
            }
        }

        scope.launch {
            settingsRepository.getSettingsFlow().collect { settings ->
                currentSettings = settings
                isRealtimeMode = shouldProcessAccessibilityEvents(settings)
                appSessionCoordinator.reconcileGuardian(
                    enabled = isRealtimeMode,
                    targets = settings.targetApps.map { it.packageName }.toSet(),
                    observedPackage = foregroundState.currentPackage(),
                    foregroundVerifier = ::isForeground
                )
            }
        }
        scope.launch {
            for (change in packageChanges) {
                if (!shouldProcessQueuedAccessibilityEvent(currentSettings)) continue
                appSessionCoordinator.onPackageChanged(
                    packageName = change.packageName,
                    foregroundVerifier = { expected ->
                        isForeground(expected) && (expected == packageName ||
                            returnNavigationGuard.allowsExternalEvent(change.eventUptimeMillis))
                    },
                    isReminderPresentation = change.isReminderPresentation
                )
            }
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event?.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return
        val pkg = event.packageName?.toString() ?: return
        if (pkg != packageName && !returnNavigationGuard.allowsExternalEvent(event.eventTime)) return
        val isApplicationTask = packageManager.getLaunchIntentForPackage(pkg) != null
        if (!foregroundState.onWindowStateChanged(
            packageName = pkg,
            isApplicationTask = isApplicationTask,
            eventUptimeMillis = event.eventTime
        )) return
        if (isApplicationTask) {
            realtimeForegroundProvider.onRealApplicationForeground(pkg)
        }
        if (!isRealtimeMode) return
        pendingReminderRedisplayer.onForegroundPackage(pkg)
        packageChanges.trySend(
            PackageChange(
                packageName = pkg,
                eventUptimeMillis = event.eventTime,
                isReminderPresentation = isReminderPresentationForForegroundChange(
                    reminderPresentationRegistry.protectsSession()
                )
            )
        )
    }

    private fun isForeground(expectedPackage: String): Boolean =
        foregroundState.isForeground(expectedPackage) &&
            (expectedPackage == packageName ||
                returnNavigationGuard.allowsExternalEvent(foregroundState.currentEventUptime()))

    override fun onInterrupt() {
        accessibilityDiagnosticsStore.recordServiceInterrupted()
    }

    override fun onDestroy() {
        if (activeService === this) activeService = null
        accessibilityDiagnosticsStore.recordServiceDestroyed()
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

