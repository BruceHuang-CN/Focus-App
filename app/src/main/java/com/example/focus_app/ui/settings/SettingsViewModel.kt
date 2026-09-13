package com.example.focus_app.ui.settings

import com.example.focus_app.R

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.focus_app.data.repository.AiRepository
import com.example.focus_app.data.repository.AppInfo
import com.example.focus_app.data.repository.AppSettings
import com.example.focus_app.data.repository.ConnectionTestResult
import com.example.focus_app.data.repository.SettingsRepository
import com.example.focus_app.data.repository.TaskRepository
import com.example.focus_app.data.appgroup.AppGroup
import com.example.focus_app.data.appgroup.AppGroupRepository
import com.example.focus_app.data.permission.PermissionStatusProvider
import com.example.focus_app.data.reminder.DefaultReminderActionOrderStore
import com.example.focus_app.data.reminder.ReminderActionOrderStore
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
import com.example.focus_app.domain.reminder.SnoozeDurationPolicy
import com.example.focus_app.domain.permission.PermissionCheckEvaluator
import com.example.focus_app.domain.permission.PermissionCheckItem
import com.example.focus_app.domain.reminder.ReminderTonePreview
import com.example.focus_app.domain.usecase.UpdateGuardianStateUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import com.example.focus_app.domain.usecase.ResetReminderQuotaUseCase
import com.example.focus_app.domain.usecase.RegenerateReminderMessagesUseCase
import com.example.focus_app.domain.usecase.ReminderRegenerationResult
import javax.inject.Inject

/** AI 连接测试的界面状态。 */
sealed interface AiConnectionUiState {
    data object Idle : AiConnectionUiState
    data object Loading : AiConnectionUiState
    data class Success(val modelIds: List<String>) : AiConnectionUiState
    data class Error(val message: String, val localizedMessage: SetupMessage? = null) : AiConnectionUiState
}

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository,
    private val aiRepository: AiRepository,
    private val taskRepository: TaskRepository,
    private val permissionStatusProvider: PermissionStatusProvider,
    private val customReturnAppStore: CustomReturnAppStore,
    private val followUpReminderStore: FollowUpReminderStore,
    private val keepAliveStore: KeepAliveStore,
    private val themeStore: ThemeStore,
    private val appGroupRepository: AppGroupRepository? = null,
    private val updateGuardianStateUseCase: UpdateGuardianStateUseCase? = null,
    private val reminderActionOrderStore: ReminderActionOrderStore = DefaultReminderActionOrderStore,
    private val resetQuotaUseCase: ResetReminderQuotaUseCase? = null,
    private val regenerateUseCase: RegenerateReminderMessagesUseCase? = null
) : ViewModel() {
    private val saveMutex = Mutex()
    private var pendingWrites = 0
    private var writeFailure = false
    private val _saveStatus = MutableStateFlow(R.string.setup_text_297)
    val saveStatus = _saveStatus.asStateFlow()
    private val _settingsMessages = MutableSharedFlow<SetupMessage>(extraBufferCapacity = 4)
    val settingsMessages = _settingsMessages.asSharedFlow()
    private val _regenerating = MutableStateFlow(false)
    val regenerating = _regenerating.asStateFlow()

    private fun persist(block: suspend () -> Unit) {
        if (pendingWrites == 0) writeFailure = false
        pendingWrites++
        _saveStatus.value = R.string.setup_text_298
        viewModelScope.launch {
            try {
                saveMutex.withLock { block() }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                writeFailure = true
                _settingsMessages.emit(SetupMessage(R.string.setup_text_299))
            } finally {
                pendingWrites--
                _saveStatus.value = if (pendingWrites > 0) R.string.setup_text_298 else if (writeFailure) R.string.setup_text_225 else R.string.setup_text_300
            }
        }
    }

    fun setGuardianEnabled(enabled: Boolean) = persist {
        val useCase = updateGuardianStateUseCase
        if (useCase != null) useCase.setGuardianEnabled(enabled) else settingsRepository.setGuardianEnabled(enabled)
    }
    fun setBreathingPause(enabled: Boolean) = update { it.copy(enableBreathingPause = enabled) }
    fun resetReminderQuota() = persist {
        requireNotNull(resetQuotaUseCase).invoke()
        _settingsMessages.emit(SetupMessage(R.string.setup_text_301))
    }
    fun regenerateMessages() {
        if (_regenerating.value) return
        _regenerating.value = true
        viewModelScope.launch {
            try {
                val result = requireNotNull(regenerateUseCase).invoke()
                _settingsMessages.emit(when (result) {
                    is ReminderRegenerationResult.Success -> SetupMessage(R.string.setup_text_302, result.targetCount)
                    ReminderRegenerationResult.NoActiveTask -> SetupMessage(R.string.setup_text_303)
                    ReminderRegenerationResult.NoTargetApps -> SetupMessage(R.string.setup_text_304)
                    is ReminderRegenerationResult.Failed -> SetupMessage(R.string.setup_text_305, result.reason)
                })
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { _settingsMessages.emit(SetupMessage(R.string.setup_text_306)) }
            finally { _regenerating.value = false }
        }
    }

    val settings: StateFlow<AppSettings> = settingsRepository.getSettingsFlow().stateIn(viewModelScope, SharingStarted.Eagerly, AppSettings())

    private var connectionRevision = 0L
    private val _aiConnection = MutableStateFlow<AiConnectionUiState>(AiConnectionUiState.Idle)
    val aiConnection: StateFlow<AiConnectionUiState> = _aiConnection.asStateFlow()

    private val _permissionStatus = MutableStateFlow<List<PermissionCheckItem>>(emptyList())
    val permissionStatus: StateFlow<List<PermissionCheckItem>> = _permissionStatus.asStateFlow()

    private val _customReturnPackage = MutableStateFlow(customReturnAppStore.read())
    val customReturnPackage: StateFlow<String> = _customReturnPackage.asStateFlow()

    private val _followUpInterval = MutableStateFlow(followUpReminderStore.readMinutes())
    val followUpInterval: StateFlow<Int> = _followUpInterval.asStateFlow()

    val keepAliveEnabled: StateFlow<Boolean> = keepAliveStore.enabled
    val randomizeReminderActions: StateFlow<Boolean> = reminderActionOrderStore.randomizeEnabled
    val themeSettings: StateFlow<ThemeSettings> = themeStore.settings
    val appGroups: StateFlow<List<AppGroup>> = appGroupRepository?.groups ?: MutableStateFlow(emptyList())
    val activeAppGroupId: StateFlow<String> = appGroupRepository?.activeGroupId ?: MutableStateFlow("")

    private val _tonePreviewTask = MutableStateFlow<String?>(null)
    val tonePreviewTask: StateFlow<String?> = _tonePreviewTask.asStateFlow()

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
                activeTask?.title
            }
                .distinctUntilChanged()
                .collect { title -> _tonePreviewTask.value = title }
        }
    }

    /** 从系统设置页返回后手动刷新权限状态。 */
    fun refreshPermissions() {
        viewModelScope.launch {
            refreshPermissionStatus(settingsRepository.getSettings())
        }
    }

    private suspend fun refreshPermissionStatus(current: AppSettings) {
        _permissionStatus.value = withContext(Dispatchers.IO) { PermissionCheckEvaluator.evaluate(
            mode = current.detectionMode,
            accessibilityEnabled = permissionStatusProvider.accessibilityEnabled(),
            usageStatsGranted = permissionStatusProvider.usageStatsGranted(),
            notificationGranted = permissionStatusProvider.notificationGranted(),
            overlayGranted = permissionStatusProvider.overlayGranted(),
            enableAccessibility = current.enableAccessibility,
            hasTargetApps = current.targetApps.isNotEmpty()
        ) }
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

    fun setForceReminder(enabled: Boolean) { update { it.copy(forceReminder = enabled) } }

    fun setRandomizeReminderActions(enabled: Boolean) {
        persist { reminderActionOrderStore.setRandomizeEnabled(enabled) }
    }

    fun updateDailyShortVideoLimitMinutes(minutes: Int) {
        update { it.copy(dailyShortVideoLimitMinutes = minutes.coerceIn(1, 1_440)) }
    }

    fun updateReminderTone(tone: ReminderTone) { update { it.copy(toneKey = tone) } }
    fun updateReturnDestination(destination: ReturnDestination) { update { it.copy(returnDestination = destination) } }

    fun updateCustomReturnPackage(packageName: String) {
        val trimmed = packageName.trim()
        customReturnAppStore.write(trimmed)
        _customReturnPackage.value = trimmed
    }

    fun updateFollowUpInterval(minutes: Int) {
        val value = SnoozeDurationPolicy.normalizeStored(minutes)
        followUpReminderStore.writeMinutes(value)
        _followUpInterval.value = value
    }

    fun setThemeMode(mode: AppThemeMode) {
        persist { themeStore.setMode(mode) }
    }

    fun setThemeColor(color: AppThemeColor) {
        persist { themeStore.setColor(color) }
    }

    fun setKeepAliveEnabled(enabled: Boolean) {
        persist { keepAliveStore.setEnabled(enabled) }
    }

    fun updateDetectionMode(mode: DetectionMode) {
        update { it.copy(detectionMode = DetectionMode.REALTIME) }
        _notificationPermissionRequests.tryEmit(Unit)
    }

    /**
     * 引导页选择检测方式时调用：持久化模式，并同步无障碍开关状态。
     */
    fun applyOnboardingDetectionMode(mode: DetectionMode) {
        update {
            it.copy(
                detectionMode = DetectionMode.REALTIME,
                enableAccessibility = true
            )
        }
    }

    /** 保存用户选择；系统是否真正授予无障碍权限由界面和服务单独判断。 */
    fun setAccessibilityEnabled(enabled: Boolean) {
        update { it.copy(enableAccessibility = enabled) }
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
    fun updateAiProvider(p: AiProvider) { invalidateConnection(); update { it.copy(aiProvider = p, apiEndpoint = p.defaultEndpoint, aiModel = p.defaultModel) } }
    fun updateAiConnection(endpoint: String, model: String) {
        invalidateConnection()
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
    fun updateApiKey(k: String, onSaved: () -> Unit = {}) {
        persist { settingsRepository.saveApiKey(k); invalidateConnection(); onSaved() }
    }

    private fun invalidateConnection() {
        connectionRevision++
        _aiConnection.value = AiConnectionUiState.Idle
    }

    fun testAiConnection() {
        if (_aiConnection.value is AiConnectionUiState.Loading) return
        val request = ++connectionRevision
        _aiConnection.value = AiConnectionUiState.Loading
        viewModelScope.launch {
            try {
                val currentSettings = saveMutex.withLock { settingsRepository.getSettings() }
                val result = aiRepository.testConnection(currentSettings)
                val latest = settingsRepository.getSettings()
                if (request != connectionRevision) return@launch
                if (latest.aiProvider != currentSettings.aiProvider || latest.apiEndpoint != currentSettings.apiEndpoint || latest.aiModel != currentSettings.aiModel ||
                    _aiConnection.value !is AiConnectionUiState.Loading) {
                    _aiConnection.value = AiConnectionUiState.Idle
                    return@launch
                }
                _aiConnection.value = when (result) {
                    is ConnectionTestResult.Success -> AiConnectionUiState.Success(result.modelIds)
                    is ConnectionTestResult.Error -> AiConnectionUiState.Error(result.message)
                    ConnectionTestResult.Loading -> AiConnectionUiState.Loading
                }
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { if (request == connectionRevision) _aiConnection.value = AiConnectionUiState.Error("", SetupMessage(R.string.setup_text_307)) }
        }
    }

    fun clearApiKey() {
        persist { settingsRepository.clearApiKey(); invalidateConnection() }
    }
    fun updatePersonality(p: String) { update { it.copy(aiPersonality = p, toneKey = ReminderTone.fromKey(p)) } }
    fun toggleBreathingPause() { update { it.copy(enableBreathingPause = !it.enableBreathingPause) } }
    suspend fun saveAppGroup(groupId: String?, name: String, apps: List<AppInfo>): Result<Unit> = runCatching {
        val validation = AppGroupEditorPolicy.validate(name, apps)
        require(validation.canSave) { validation.errorMessage.orEmpty() }
        val groups = requireNotNull(appGroupRepository)
        if (groupId == null) groups.create(validation.normalizedName, apps)
        else if (groupId == activeAppGroupId.value) {
            requireNotNull(updateGuardianStateUseCase)
                .updateActiveGroup(groupId, validation.normalizedName, apps)
                .getOrThrow()
        } else groups.update(groupId, validation.normalizedName, apps)
    }

    fun deleteAppGroup(groupId: String): Result<Unit> = runCatching {
        requireNotNull(appGroupRepository).delete(groupId)
    }

    fun activateAppGroup(groupId: String) {
        viewModelScope.launch {
            val failure = requireNotNull(updateGuardianStateUseCase).activateGroup(groupId).exceptionOrNull()
            if (failure != null) {
                _appGroupMessages.emit(SetupMessage(R.string.setup_text_308, failure.message ?: SetupMessage(R.string.setup_text_154)))
            }
        }
    }

    private val _appGroupMessages = MutableSharedFlow<SetupMessage>(extraBufferCapacity = 1)
    val appGroupMessages: SharedFlow<SetupMessage> = _appGroupMessages.asSharedFlow()

    private fun update(transform: (AppSettings) -> AppSettings) {
        persist { settingsRepository.update(transform) }
    }
}
