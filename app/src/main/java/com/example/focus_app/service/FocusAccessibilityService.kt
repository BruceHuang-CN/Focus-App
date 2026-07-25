package com.example.focus_app.service

import android.accessibilityservice.AccessibilityService
import android.view.accessibility.AccessibilityEvent
import com.example.focus_app.data.repository.SettingsRepository
import com.example.focus_app.domain.usecase.TrackAppOpenUseCase
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class FocusAccessibilityService : AccessibilityService() {
    @Inject lateinit var settingsRepository: SettingsRepository
    @Inject lateinit var trackAppOpenUseCase: TrackAppOpenUseCase
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private var isEnabled = false

    override fun onServiceConnected() {
        super.onServiceConnected()
        scope.launch { settingsRepository.getSettingsFlow().collect { isEnabled = it.enableAccessibility } }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (!isEnabled || event?.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return
        val pkg = event.packageName?.toString() ?: return
        scope.launch {
            val s = settingsRepository.getSettings()
            s.targetApps.find { it.packageName == pkg }?.let { trackAppOpenUseCase(pkg, it.appName, s.maxRemindsPerHour) }
        }
    }

    override fun onInterrupt() {}
}
