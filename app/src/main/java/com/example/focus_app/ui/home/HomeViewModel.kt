package com.example.focus_app.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.focus_app.data.repository.AppSessionRepository
import com.example.focus_app.data.repository.MoodRepository
import com.example.focus_app.data.repository.ReminderDisplayRepository
import com.example.focus_app.data.repository.TaskRepository
import com.example.focus_app.data.repository.SettingsRepository
import com.example.focus_app.data.appgroup.AppGroupRepository
import com.example.focus_app.data.permission.PermissionStatusProvider
import com.example.focus_app.domain.model.AppUsageSession
import com.example.focus_app.domain.model.DetectionMode
import com.example.focus_app.domain.model.FocusTask
import com.example.focus_app.domain.task.StreakCalculator
import com.example.focus_app.domain.usecase.ResetReminderQuotaUseCase
import com.example.focus_app.domain.usecase.RegenerateReminderMessagesUseCase
import com.example.focus_app.domain.usecase.ReminderRegenerationResult
import com.example.focus_app.domain.usecase.UpdateGuardianStateUseCase
import com.example.focus_app.service.AccessibilityDiagnosticsStore
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject

data class HomeUiState(
    val latestMood: String? = null,
    val openCountToday: Int = 0,
    val remindedCountToday: Int = 0,
    val exitedCountToday: Int = 0,
    val recentReminders: List<AppUsageSession> = emptyList(),
    val activeTask: FocusTask? = null,
    val completedToday: Int = 0,
    val streakDays: Int = 0,
    val guardianEnabled: Boolean = true,
    val activeGroupName: String = "\u672a\u8bbe\u7f6e\u5e94\u7528\u7ec4",
    val reminderWindowMinutes: Int = 60,
    val windowReminderCount: Int = 0,
    val windowReminderLimit: Int = 3,
    val detectionMode: DetectionMode = DetectionMode.REALTIME,
    val accessibilitySystemEnabled: Boolean = false,
    val accessibilityServiceBound: Boolean = false,
    val accessibilityLastConnectedAtMillis: Long? = null,
    val accessibilityLastDestroyedAtMillis: Long? = null,
    val accessibilityLastInterruptedAtMillis: Long? = null,
    val firstLaunchAfterUpdateAtMillis: Long? = null,
    val isRegeneratingMessages: Boolean = false
)

sealed interface HomeEvent {
    data object ReminderQuotaReset : HomeEvent
    data class ReminderMessagesRegenerated(val message: String) : HomeEvent
}

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val appSessionRepository: AppSessionRepository,
    private val moodRepository: MoodRepository,
    private val taskRepository: TaskRepository,
    private val settingsRepository: SettingsRepository,
    private val reminderDisplayRepository: ReminderDisplayRepository,
    private val appGroupRepository: AppGroupRepository,
    private val permissionStatusProvider: PermissionStatusProvider,
    private val accessibilityDiagnosticsStore: AccessibilityDiagnosticsStore,
    private val updateGuardianStateUseCase: UpdateGuardianStateUseCase,
    private val resetReminderQuotaUseCase: ResetReminderQuotaUseCase,
    private val regenerateReminderMessagesUseCase: RegenerateReminderMessagesUseCase
) : ViewModel() {
    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()
    private val _events = MutableSharedFlow<HomeEvent>(extraBufferCapacity = 1)
    val events: SharedFlow<HomeEvent> = _events.asSharedFlow()

    init {
        loadStats()
        observeGuardianState()
        observeAccessibilityDiagnostics()
    }

    private fun observeAccessibilityDiagnostics() {
        viewModelScope.launch {
            accessibilityDiagnosticsStore.state.collect { diagnostics ->
                _uiState.update {
                    it.copy(
                        accessibilityServiceBound = diagnostics.serviceBound,
                        accessibilityLastConnectedAtMillis = diagnostics.lastConnectedAtMillis,
                        accessibilityLastDestroyedAtMillis = diagnostics.lastDestroyedAtMillis,
                        accessibilityLastInterruptedAtMillis = diagnostics.lastInterruptedAtMillis,
                        firstLaunchAfterUpdateAtMillis = diagnostics.firstLaunchAfterUpdateAtMillis
                    )
                }
            }
        }
    }

    private fun observeGuardianState() {
        viewModelScope.launch {
            combine(
                settingsRepository.getSettingsFlow(),
                appGroupRepository.groups,
                appGroupRepository.activeGroupId
            ) { settings, groups, activeGroupId ->
                settings.guardianEnabled to groups.firstOrNull { it.id == activeGroupId }?.name
            }.collect { (guardianEnabled, activeGroupName) ->
                _uiState.update {
                    it.copy(
                        guardianEnabled = guardianEnabled,
                        activeGroupName = activeGroupName ?: "\u672a\u8bbe\u7f6e\u5e94\u7528\u7ec4"
                    )
                }
            }
        }
    }

    private fun loadStats() {
        viewModelScope.launch {
            val zone = ZoneId.systemDefault()
            val today = LocalDate.now(zone)
            val todayStart = today.atStartOfDay(zone).toInstant().toEpochMilli()
            val tomorrowStart = todayStart + DAY_MILLIS
            val todaySessions = appSessionRepository.sessionsBetween(todayStart, tomorrowStart)
            val recentStart = System.currentTimeMillis() - DAY_MILLIS
            val recentReminders = appSessionRepository.sessionsBetween(recentStart, tomorrowStart)
                .filter { it.remindedAt != null }
                .sortedByDescending { it.startedAt }
                .take(20)
            val completedToday = taskRepository.completedCountBetween(todayStart, tomorrowStart)
            val settings = settingsRepository.getSettings()
            val windowStart = System.currentTimeMillis() -
                settings.reminderWindowMinutes * MINUTE_MILLIS
            val windowReminderCount = reminderDisplayRepository.countSince(windowStart)
            _uiState.update {
                it.copy(
                    openCountToday = todaySessions.size,
                    remindedCountToday = todaySessions.count { it.remindedAt != null },
                    exitedCountToday = todaySessions.count { it.userAction in ACTIVE_EXIT_ACTIONS },
                    latestMood = moodRepository.getLatestMood()?.mood,
                    activeTask = taskRepository.observeActive().first(),
                    completedToday = completedToday,
                    recentReminders = recentReminders,
                    reminderWindowMinutes = settings.reminderWindowMinutes,
                    windowReminderCount = windowReminderCount,
                    windowReminderLimit = settings.maxRemindersPerWindow,
                    detectionMode = settings.detectionMode,
                    accessibilitySystemEnabled = permissionStatusProvider.accessibilityEnabled(),
                    streakDays = StreakCalculator.streakDays(today) { day ->
                        val start = day.atStartOfDay(zone).toInstant().toEpochMilli()
                        taskRepository.completedCountBetween(start, start + DAY_MILLIS)
                    }
                )
            }
        }
    }

    fun completeCurrentTask() {
        _uiState.value.activeTask?.let { task ->
            viewModelScope.launch { taskRepository.setCompleted(task.id) }
        }
    }

    fun refresh() { loadStats() }

    fun setGuardianEnabled(enabled: Boolean) {
        viewModelScope.launch { updateGuardianStateUseCase.setGuardianEnabled(enabled) }
    }

    fun resetReminderQuota() {
        viewModelScope.launch {
            resetReminderQuotaUseCase()
            loadStats()
            _events.emit(HomeEvent.ReminderQuotaReset)
        }
    }

    fun regenerateReminderMessages() {
        if (_uiState.value.isRegeneratingMessages) return
        _uiState.update { it.copy(isRegeneratingMessages = true) }
        viewModelScope.launch {
            val result = regenerateReminderMessagesUseCase()
            _uiState.update { it.copy(isRegeneratingMessages = false) }
            _events.emit(HomeEvent.ReminderMessagesRegenerated(result.userMessage()))
        }
    }

    private fun ReminderRegenerationResult.userMessage(): String = when (this) {
        is ReminderRegenerationResult.Success -> "AI 文案已更新（$targetCount 个目标应用）"
        ReminderRegenerationResult.NoActiveTask -> "请先创建或选中当前任务"
        ReminderRegenerationResult.NoTargetApps -> "请先设置需要监控的目标应用"
        is ReminderRegenerationResult.Failed -> "生成失败：$reason；已保留原文案"
    }

    private companion object {
        const val DAY_MILLIS = 86_400_000L
        const val MINUTE_MILLIS = 60_000L
        val ACTIVE_EXIT_ACTIONS = setOf("returned_to_focus", "returned_home")
    }
}
