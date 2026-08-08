package com.example.focus_app.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.focus_app.data.repository.AiRepository
import com.example.focus_app.data.repository.AppInfo
import com.example.focus_app.data.repository.AppSessionRepository
import com.example.focus_app.data.repository.AppSettings
import com.example.focus_app.data.repository.ConnectionTestResult
import com.example.focus_app.data.repository.ReminderCacheRepository
import com.example.focus_app.data.repository.SettingsRepository
import com.example.focus_app.data.repository.TaskRepository
import com.example.focus_app.data.permission.PermissionStatusProvider
import com.example.focus_app.data.followup.FollowUpReminderStore
import com.example.focus_app.data.keepalive.KeepAliveStore
import com.example.focus_app.data.returnapp.CustomReturnAppStore
import com.example.focus_app.data.theme.ThemeSettings
import com.example.focus_app.data.theme.ThemeStore
import com.example.focus_app.domain.model.AppThemeColor
import com.example.focus_app.domain.model.AppThemeMode
import com.example.focus_app.domain.model.AiProvider
import com.example.focus_app.domain.model.DetectionMode
import com.example.focus_app.domain.model.ReminderTone
import com.example.focus_app.domain.model.ReturnDestination
import com.example.focus_app.domain.permission.PermissionCheckEvaluator
import com.example.focus_app.domain.permission.PermissionCheckItem
import com.example.focus_app.domain.reminder.ReminderTonePreview
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

/** AI 连接测试的界面状态。 */
sealed interface AiConnectionUiState {
    data object Idle : AiConnectionUiState
    data object Loading : AiConnectionUiState
    data class Success(val modelIds: List<String>) : AiConnectionUiState
    data class Error(val message: String) : AiConnectionUiState
}

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository,
    private val aiRepository: AiRepository,
    private val taskRepository: TaskRepository,
    private val permissionStatusProvider: PermissionStatusProvider,
    private val appSessionRepository: AppSessionRepository,
    private val reminderCacheRepository: ReminderCacheRepository,
    private val customReturnAppStore: CustomReturnAppStore,
    private val followUpReminderStore: FollowUpReminderStore,
    private val keepAliveStore: KeepAliveStore,
    private val themeStore: ThemeStore
) : ViewModel() {
    val settings: StateFlow<AppSettings> = settingsRepository.getSettingsFlow().stateIn(viewModelScope, SharingStarted.Eagerly, AppSettings())

    private val _aiConnection = MutableStateFlow<AiConnectionUiState>(AiConnectionUiState.Idle)
    val aiConnection: StateFlow<AiConnectionUiState> = _aiConnection.asStateFlow()

    private val _permissionStatus = MutableStateFlow<List<PermissionCheckItem>>(emptyList())
    val permissionStatus: StateFlow<List<PermissionCheckItem>> = _permissionStatus.asStateFlow()

    private val _customReturnPackage = MutableStateFlow(customReturnAppStore.read())
    val customReturnPackage: StateFlow<String> = _customReturnPackage.asStateFlow()

    private val _followUpInterval = MutableStateFlow(followUpReminderStore.readMinutes())
    val followUpInterval: StateFlow<Int> = _followUpInterval.asStateFlow()

    val keepAliveEnabled: StateFlow<Boolean> = keepAliveStore.enabled
    val themeSettings: StateFlow<ThemeSettings> = themeStore.settings

    private val _tonePreview = MutableStateFlow("")
    val tonePreview: StateFlow<String> = _tonePreview.asStateFlow()

    private val _notificationPermissionRequests = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val notificationPermissionRequests: SharedFlow<Unit> = _notificationPermissionRequests.asSharedFlow()

    init {
        viewModelScope.launch {
            settingsRepository.getSettingsFlow()
                .distinctUntilChanged()
                .collect { currentSettings -> refreshPermissionStatus(currentSettings) }
        }
        viewModelScope.launch {
            combine(
                settingsRepository.getSettingsFlow(),
                taskRepository.observeActive()
            ) { currentSettings, activeTask ->
                ReminderTonePreview.sampleMessage(
                    tone = currentSettings.toneKey,
                    customInstruction = currentSettings.customToneInstruction,
                    taskTitle = activeTask?.title
                )
            }
                .distinctUntilChanged()
                .collect { preview -> _tonePreview.value = preview }
        }
    }

    /** 从系统设置页返回后手动刷新权限状态。 */
    fun refreshPermissions() {
        viewModelScope.launch {
            refreshPermissionStatus(settingsRepository.getSettings())
        }
    }

    private suspend fun refreshPermissionStatus(current: AppSettings) {
        _permissionStatus.value = PermissionCheckEvaluator.evaluate(
            mode = current.detectionMode,
            accessibilityEnabled = permissionStatusProvider.accessibilityEnabled(),
            usageStatsGranted = permissionStatusProvider.usageStatsGranted(),
            notificationGranted = permissionStatusProvider.notificationGranted(),
            overlayGranted = permissionStatusProvider.overlayGranted(),
            enableAccessibility = current.enableAccessibility,
            hasTargetApps = current.targetApps.isNotEmpty()
        )
    }

    fun updateReminderDelaySeconds(seconds: Int) {
        update { it.copy(reminderDelaySeconds = seconds.coerceIn(1, 300)) }
    }

    fun updateReminderWindowMinutes(minutes: Int) {
        update { it.copy(reminderWindowMinutes = minutes.coerceIn(5, 1_440)) }
    }

    fun updateMaxRemindersPerWindow(count: Int) {
        update { it.copy(maxRemindersPerWindow = count.coerceIn(1, 20)) }
    }

    fun updateDailyShortVideoLimitMinutes(minutes: Int) {
        update { it.copy(dailyShortVideoLimitMinutes = minutes.coerceIn(1, 1_440)) }
    }

    /** 重置当前统计窗口的提醒额度，并请求后台重新生成 AI 提醒内容。 */
    fun resetReminderWindow() {
        viewModelScope.launch {
            val current = settingsRepository.getSettings()
            val since = System.currentTimeMillis() - current.reminderWindowMinutes * 60_000L
            appSessionRepository.resetReminderQuota(since)
            reminderCacheRepository.requestRegeneration()
        }
    }

    fun updateReminderTone(tone: ReminderTone) { update { it.copy(toneKey = tone) } }
    fun updateReturnDestination(destination: ReturnDestination) { update { it.copy(returnDestination = destination) } }

    fun updateCustomReturnPackage(packageName: String) {
        val trimmed = packageName.trim()
        customReturnAppStore.write(trimmed)
        _customReturnPackage.value = trimmed
    }

    fun updateFollowUpInterval(minutes: Int) {
        val value = minutes.coerceIn(1, 120)
        followUpReminderStore.writeMinutes(value)
        _followUpInterval.value = value
    }

    fun setThemeMode(mode: AppThemeMode) {
        themeStore.setMode(mode)
    }

    fun setThemeColor(color: AppThemeColor) {
        themeStore.setColor(color)
    }

    fun setKeepAliveEnabled(enabled: Boolean) {
        keepAliveStore.setEnabled(enabled)
    }
    fun updateDetectionMode(mode: DetectionMode) {
        update { it.copy(detectionMode = mode) }
        _notificationPermissionRequests.tryEmit(Unit)
    }

    /**
     * 引导页选择检测方式时调用：持久化模式，并同步无障碍开关状态。
     */
    fun applyOnboardingDetectionMode(mode: DetectionMode) {
        update {
            it.copy(
                detectionMode = mode,
                enableAccessibility = mode == DetectionMode.REALTIME
            )
        }
    }

    /**
     * 系统无障碍已开启时，确保应用内开关同步为开启，恢复实时检测。
     */
    fun ensureAccessibilityEnabled() {
        update { it.copy(enableAccessibility = true) }
    }
    fun updateCustomToneInstruction(instruction: String) { update { it.copy(customToneInstruction = instruction) } }

    fun updateRemindDelay(minutes: Int) {
        val seconds = if (minutes == 0) 3 else minutes * 60
        update {
            it.copy(
                remindDelayMinutes = minutes,
                reminderDelaySeconds = seconds.coerceIn(1, 300)
            )
        }
    }

    fun updateMaxReminds(count: Int) {
        update {
            it.copy(
                maxRemindsPerHour = count,
                maxRemindersPerWindow = count.coerceIn(1, 20)
            )
        }
    }
    fun updateAiProvider(p: AiProvider) { update { it.copy(aiProvider = p, apiEndpoint = p.defaultEndpoint, aiModel = p.defaultModel) } }
    fun updateAiConnection(endpoint: String, model: String) {
        val savedEndpoint = endpoint.trim()
        val savedModel = model.trim()
        update {
            val provider = AiProvider.entries.firstOrNull { candidate ->
                candidate != AiProvider.CUSTOM &&
                    candidate.defaultEndpoint.trimEnd('/') == savedEndpoint.trimEnd('/')
            } ?: AiProvider.CUSTOM
            it.copy(
                aiProvider = provider,
                apiEndpoint = savedEndpoint,
                aiModel = savedModel
            )
        }
    }
    fun updateApiKey(k: String) {
        viewModelScope.launch { settingsRepository.saveApiKey(k) }
    }

    fun testAiConnection() {
        viewModelScope.launch {
            _aiConnection.value = AiConnectionUiState.Loading
            val currentSettings = settingsRepository.getSettings()
            _aiConnection.value = when (val result = aiRepository.testConnection(currentSettings)) {
                is ConnectionTestResult.Success -> AiConnectionUiState.Success(result.modelIds)
                is ConnectionTestResult.Error -> AiConnectionUiState.Error(result.message)
                ConnectionTestResult.Loading -> AiConnectionUiState.Loading
            }
        }
    }

    fun clearApiKey() {
        viewModelScope.launch { settingsRepository.clearApiKey() }
    }
    fun updatePersonality(p: String) { update { it.copy(aiPersonality = p, toneKey = ReminderTone.fromKey(p)) } }
    fun toggleBreathingPause() { update { it.copy(enableBreathingPause = !it.enableBreathingPause) } }
    fun toggleAccessibility() { update { it.copy(enableAccessibility = !it.enableAccessibility) } }
    fun updateTargetApps(apps: List<AppInfo>) { update { it.copy(targetApps = apps) } }

    private fun update(transform: (AppSettings) -> AppSettings) {
        viewModelScope.launch { settingsRepository.update(transform) }
    }
}
