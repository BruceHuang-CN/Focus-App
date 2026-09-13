package com.example.focus_app.ui.onboarding

import com.example.focus_app.R
import com.example.focus_app.data.language.AppLanguage

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.focus_app.data.appgroup.AppGroupRepository
import com.example.focus_app.data.local.AppDatabase
import com.example.focus_app.data.repository.*
import com.example.focus_app.domain.model.*
import com.example.focus_app.domain.usecase.BuildReminderContextUseCase
import com.example.focus_app.domain.usecase.UpdateGuardianStateUseCase
import com.example.focus_app.util.*
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

data class TutorialUiState(
    val loading: Boolean = true, val busy: Boolean = false, val step: Int = 0,
    val message: String = "", val exit: Boolean = false, val completed: Boolean = false,
    val provider: AiProvider = AiProvider.DEEPSEEK, val endpoint: String = "", val model: String = "", val hasKey: Boolean = false,
    val connected: Boolean = false, val task: FocusTask? = null,
    val tasks: List<FocusTask> = emptyList(), val apps: List<InstalledApp> = emptyList(),
    val savedApps: List<AppInfo> = emptyList(), val selected: Set<String> = emptySet(), val appsLoaded: Boolean = false,
    val previews: List<String> = emptyList(), val generated: Boolean = false,
    val permissions: List<Boolean> = emptyList(), val breathing: Boolean = true,
    val detectionEnabled: Boolean = false, val delaySeconds: Int = 10, val guardian: Boolean = false, val experienceDone: Boolean = false
)

@HiltViewModel
class TutorialViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val store: TutorialStore, private val settings: SettingsRepository,
    private val tasks: TaskRepository, private val ai: AiRepository,
    private val cache: ReminderCacheRepository, private val buildContext: BuildReminderContextUseCase,
    private val groups: AppGroupRepository, private val guardian: UpdateGuardianStateUseCase,
    private val database: AppDatabase
) : ViewModel() {
    private val state = MutableStateFlow(TutorialUiState())
    val uiState = state.asStateFlow()
    private var testedSettings: AppSettings? = null
    private var testedKeyRevision = -1L
    private var generatedSignature: String? = null
    private var preparedSignature: String? = null
    private val preparedMessages = mutableMapOf<String, List<String>>()
    private var editingGroupId: String? = null
    private var groupLoaded = false
    private suspend fun invalidateConfiguration(edit: android.content.SharedPreferences.Editor.() -> Unit = {}) {
        withContext(NonCancellable) {
            store.invalidateConfiguration(edit)
            generatedSignature = null
            change { it.copy(completed = false, experienceDone = false, generated = false) }
        }
    }
    private fun change(block: (TutorialUiState) -> TutorialUiState) { state.update(block) }
    private fun work(block: suspend () -> Unit) {
        if (state.value.busy) return
        change { it.copy(busy = true, message = "") }
        viewModelScope.launch {
            try { block() }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (error: Exception) { change { it.copy(message = error.message ?: com.example.focus_app.data.language.AppLanguage.context(context).getString(R.string.setup_text_114)) } }
            finally { change { it.copy(busy = false, loading = false) } }
        }
    }

    init { work {
        if (!store.initialized) {
            // Only a genuinely new installation is silenced while configuring the tutorial.
            if (!store.oldUser) {
                guardian.setGuardianEnabled(false)
                settings.update { it.copy(detectionMode = DetectionMode.REALTIME,
                    enableAccessibility = true, enableBreathingPause = true) }
            }
            store.save { putBoolean("initialized", true) }
        }
        val current = settings.getSettings()
        val all = tasks.observeAll().first().filterNot { it.isCompleted }
        val selectedTask = all.firstOrNull { it.id == store.taskId }
        val hasKey = settings.readApiKey().isNotBlank()
        change { it.copy(step = if (store.completed) 6 else store.step,
            completed = store.completed, provider = current.aiProvider, endpoint = current.apiEndpoint, model = current.aiModel,
            hasKey = hasKey, task = selectedTask, tasks = all,
            savedApps = current.targetApps, selected = current.targetApps.map { app -> app.packageName }.toSet(),
            breathing = current.enableBreathingPause, delaySeconds = current.reminderDelaySeconds,
            guardian = current.guardianEnabled, detectionEnabled = current.enableAccessibility) }
        refreshNow()
        if (state.value.step == 3) loadAppsNow()
    } }

    fun nextWelcome() = go(1)
    fun go(step: Int) = work {
        require(step in 0..6) { com.example.focus_app.data.language.AppLanguage.context(context).getString(R.string.setup_text_115) }
        store.setStep(step)
        change { it.copy(step = step) }
        if (step == 3) loadAppsNow()
        if (step == 5 || step == 6) refreshNow()
    }
    fun defer() = work { store.defer(); change { it.copy(exit = true) } }
    fun refresh() { if (!state.value.loading) work { refreshNow(); if (state.value.step == 3) loadAppsNow() } }

    private suspend fun refreshNow() {
        val values = withContext(Dispatchers.IO) { listOf(
            PermissionHelper.hasOverlayPermission(context),
            PermissionHelper.isAccessibilityServiceEnabled(context),
            PermissionHelper.hasUsageStatsPermission(context),
            PermissionHelper.notificationsEnabled(context),
            PermissionHelper.isIgnoringBatteryOptimizations(context)) }
        val current = settings.getSettings()
        val latestTask = tasks.observeAll().first().firstOrNull { it.id == store.taskId && !it.isCompleted }
        val generationValid = latestTask != null && connectionMatches(current) &&
            generatedSignature == signature(latestTask, current)
        if (!generationValid) generatedSignature = null
        var done = store.experienceDone
        if (!done && store.completed && store.taskId != 0L && store.experienceSince > 0) {
            done = database.reminderAnalyticsDao().tutorialExperienceRecorded(
                store.taskId, store.experienceSince, current.targetApps.map { it.packageName })
            if (done) store.save { putBoolean("experience_done", true) }
        }
        change { it.copy(savedApps = current.targetApps, permissions = values, breathing = current.enableBreathingPause,
            guardian = current.guardianEnabled, experienceDone = done,
            detectionEnabled = current.enableAccessibility, delaySeconds = current.reminderDelaySeconds,
            connected = connectionMatches(current),
            generated = it.generated && generationValid) }
    }

    fun connect(endpoint: String, key: String, model: String, provider: AiProvider, onKeySaved: () -> Unit) = work {
        require(endpoint.trim().startsWith("https://") || endpoint.trim().startsWith("http://")) { com.example.focus_app.data.language.AppLanguage.context(context).getString(R.string.setup_text_116) }
        require(model.isNotBlank()) { com.example.focus_app.data.language.AppLanguage.context(context).getString(R.string.setup_text_117) }
        val previous = settings.getSettings()
        val previousKey = settings.readApiKey()
        val configurationChanged = tutorialAiChanged(previous, endpoint.trim(), model.trim(), previousKey, key, provider)
        testedSettings = null
        change { it.copy(connected = false) }
        var savedChange = false
        try {
            settings.update { it.copy(aiProvider = provider, apiEndpoint = endpoint.trim(), aiModel = model.trim()) }
            savedChange = previous.aiProvider != provider || previous.apiEndpoint != endpoint.trim() || previous.aiModel != model.trim()
            if (key.isNotBlank()) {
                if (key.trim() != previousKey) {
                    settings.saveApiKey(key)
                    savedChange = true
                }
                onKeySaved()
            }
        } finally {
            // 仅使实际写入变化失效；部分保存失败也不能沿用旧完成记录。
            if (configurationChanged && savedChange) withContext(NonCancellable) { invalidateConfiguration() }
        }
        val current = settings.getSettings()
        val keyRevision = settings.apiKeyRevision.value
        val keyAvailable = settings.readApiKey().isNotBlank()
        change { it.copy(hasKey = keyAvailable, provider = current.aiProvider,
            endpoint = current.apiEndpoint, model = current.aiModel) }
        require(keyAvailable) { com.example.focus_app.data.language.AppLanguage.context(context).getString(R.string.setup_text_118) }
        when (val result = ai.testConnection(current)) {
            is ConnectionTestResult.Success -> {
                val latest = settings.getSettings()
                require(current.apiEndpoint == latest.apiEndpoint && current.aiModel == latest.aiModel &&
                    current.aiProvider == latest.aiProvider && keyRevision == settings.apiKeyRevision.value) { com.example.focus_app.data.language.AppLanguage.context(context).getString(R.string.setup_text_119) }
                testedSettings = current; testedKeyRevision = keyRevision
                change { it.copy(provider = current.aiProvider, endpoint = current.apiEndpoint, model = current.aiModel,
                    hasKey = true, connected = true, message = com.example.focus_app.data.language.AppLanguage.context(context).getString(R.string.setup_text_120)) }
            }
            is ConnectionTestResult.Error -> error(result.message)
            ConnectionTestResult.Loading -> error(com.example.focus_app.data.language.AppLanguage.context(context).getString(R.string.setup_text_121))
        }
    }
    fun aiDraftChanged() {
        testedSettings = null; generatedSignature = null
        change { it.copy(connected = false, generated = false) }
    }
    fun selectTask(id: Long) = work {
        val task = tasks.observeAll().first().firstOrNull { it.id == id && !it.isCompleted }
            ?: error(com.example.focus_app.data.language.AppLanguage.context(context).getString(R.string.setup_text_122))
        val changed = store.taskId != task.id
        if (changed) invalidateConfiguration { putLong("task_id", task.id); remove("pending_task_id") }
        else store.save { putLong("task_id", task.id); remove("pending_task_id") }
        change { it.copy(task = task) }
    }
    fun saveTask(title: String, start: String, end: String, days: Int) = work {
        require(title.isNotBlank()) { com.example.focus_app.data.language.AppLanguage.context(context).getString(R.string.setup_text_123) }
        val startMinute = tutorialTime(start) ?: error(com.example.focus_app.data.language.AppLanguage.context(context).getString(R.string.setup_text_124))
        val endMinute = if (end.trim() == "24:00") 1440 else tutorialTime(end) ?: error(com.example.focus_app.data.language.AppLanguage.context(context).getString(R.string.setup_text_125))
        require(endMinute > startMinute && days in 1..127) { com.example.focus_app.data.language.AppLanguage.context(context).getString(R.string.setup_text_126) }
        val before = tasks.observeAll().first()
        val selected = before.firstOrNull { it.id == store.taskId }
        val existing = tutorialEditableTask(selected)
            ?: before.firstOrNull { it.id == store.pendingTaskId && !it.isCompleted }
        // 独立预留 ID：失败不会替换已选任务，写入后重启也能找到同一新任务。
        if (existing == null && (store.pendingTaskId == 0L || before.any { it.id == store.pendingTaskId })) {
            val allIds = before.map { it.id }.toSet() + store.taskId
            var candidate = System.currentTimeMillis().coerceAtLeast(1L)
            while (candidate in allIds) candidate++
            store.save { putLong("pending_task_id", candidate) }
        }
        val task = (existing ?: FocusTask(id = store.pendingTaskId, title = title.trim())).copy(
            title = title.trim(), scheduleStartMinute = startMinute, scheduleEndMinute = endMinute,
            repeatDaysMask = days, inheritsGroupSchedule = false)
        val result = when {
            existing == null -> tasks.create(task)
            existing == task -> ScheduleValidation.VALID
            else -> tasks.update(task)
        }
        require(result == ScheduleValidation.VALID) {
            if (result == ScheduleValidation.OVERLAP) com.example.focus_app.data.language.AppLanguage.context(context).getString(R.string.setup_text_127) else com.example.focus_app.data.language.AppLanguage.context(context).getString(R.string.setup_text_128)
        }
        val configurationChanged = store.taskId != task.id || existing != task
        if (configurationChanged) invalidateConfiguration { putLong("task_id", task.id); remove("pending_task_id") }
        else store.save { putLong("task_id", task.id); remove("pending_task_id") }
        val all = tasks.observeAll().first().filterNot { it.isCompleted }
        change { it.copy(task = all.first { task -> task.id == store.taskId }, tasks = all) }
        store.setStep(3); change { it.copy(step = 3) }; loadAppsNow()
    }
    private suspend fun loadAppsNow() {
        val apps = withContext(Dispatchers.IO) { loadInstalledApps(context).filter { it.packageName != context.packageName } }
        val activeId = groups.activeGroupId.value
        val groupChanged = groupLoaded && editingGroupId != activeId
        val selected = if (!groupLoaded || groupChanged) {
            settings.getSettings().targetApps.map { it.packageName }.toSet()
        } else state.value.selected
        editingGroupId = activeId
        groupLoaded = true
        change { it.copy(apps = apps, appsLoaded = true,
            selected = selected.intersect(apps.map { app -> app.packageName }.toSet()),
            message = if (groupChanged) com.example.focus_app.data.language.AppLanguage.context(context).getString(R.string.setup_text_129) else it.message) }
    }
    fun toggleApp(pkg: String) {
        if (state.value.busy) return
        generatedSignature = null
        change { it.copy(selected = if (pkg in it.selected) it.selected - pkg else it.selected + pkg, generated = false) }
    }
    fun saveApps() = work {
        val apps = state.value.apps.filter { it.packageName in state.value.selected }.map { it.toAppInfo() }
        require(apps.isNotEmpty()) { com.example.focus_app.data.language.AppLanguage.context(context).getString(R.string.setup_text_130) }
        require(groupLoaded && tutorialGroupMatches(editingGroupId, groups.activeGroupId.value)) { com.example.focus_app.data.language.AppLanguage.context(context).getString(R.string.setup_text_131) }
        val previous = settings.getSettings()
        withContext(Dispatchers.IO) {
            require(tutorialGroupMatches(editingGroupId, groups.activeGroupId.value)) { com.example.focus_app.data.language.AppLanguage.context(context).getString(R.string.setup_text_132) }
            val existing = groups.groups.value.firstOrNull { it.id == editingGroupId }
                ?: if (editingGroupId.isNullOrEmpty()) groups.groups.value.firstOrNull {
                    it.name == com.example.focus_app.data.language.AppLanguage.context(context).getString(R.string.setup_text_133) && !tutorialAppsChanged(it.apps, apps)
                } else null
            require(editingGroupId.isNullOrEmpty() || existing != null) { com.example.focus_app.data.language.AppLanguage.context(context).getString(R.string.setup_text_134) }
            if (existing == null) {
                groups.create(com.example.focus_app.data.language.AppLanguage.context(context).getString(R.string.setup_text_133), apps)
                val created = groups.groups.value.last()
                store.save { putString("group_id", created.id) }
                groups.activate(created.id)
                editingGroupId = created.id
            } else {
                if (existing.apps != apps) groups.update(existing.id, existing.name, apps)
                if (editingGroupId.isNullOrEmpty()) {
                    groups.activate(existing.id)
                    editingGroupId = existing.id
                }
                store.save { putString("group_id", existing.id) }
            }
        }
        // Keep quota and original cache intact; the tutorial does not activate groups via quota-reset flows.
        require(tutorialGroupMatches(editingGroupId, groups.activeGroupId.value)) { com.example.focus_app.data.language.AppLanguage.context(context).getString(R.string.setup_text_132) }
        settings.update { it.copy(targetApps = apps) }
        change { it.copy(savedApps = apps) }
        if (tutorialAppsChanged(previous.targetApps, apps)) invalidateConfiguration()
        store.setStep(4); change { it.copy(step = 4) }
    }
    private fun connectionMatches(current: AppSettings): Boolean {
        val tested = testedSettings ?: return false
        return current.apiEndpoint == tested.apiEndpoint && current.aiModel == tested.aiModel &&
            current.aiProvider == tested.aiProvider && testedKeyRevision == settings.apiKeyRevision.value
    }
    private fun signature(task: FocusTask, current: AppSettings) =
        listOf(task, current.aiProvider, current.apiEndpoint, current.aiModel,
            current.toneKey, current.customToneInstruction, current.targetApps.sortedBy { it.packageName },
            settings.apiKeyRevision.value, AppLanguage.tag(context)).joinToString("|")

    fun generate() = work {
        val language = AppLanguage.tag(context)
        val current = settings.getSettings()
        require(connectionMatches(current)) { com.example.focus_app.data.language.AppLanguage.context(context).getString(R.string.setup_text_135) }
        val task = tasks.observeAll().first().firstOrNull { it.id == store.taskId && !it.isCompleted }
            ?: error(com.example.focus_app.data.language.AppLanguage.context(context).getString(R.string.setup_text_136))
        require(current.targetApps.isNotEmpty()) { com.example.focus_app.data.language.AppLanguage.context(context).getString(R.string.setup_text_137) }
        val original = signature(task, current)
        if (preparedSignature != original) {
            preparedMessages.clear()
            preparedSignature = original
        }
        val previews = mutableListOf<String>()
        generatedSignature = null; change { it.copy(generated = false, previews = emptyList()) }
        for ((index, app) in current.targetApps.withIndex()) {
            change { it.copy(message = com.example.focus_app.data.language.AppLanguage.context(context).getString(R.string.setup_text_138, app.appName, index + 1, current.targetApps.size)) }
            val messages = preparedMessages[app.packageName]
                ?: ai.generateRemoteBatch(buildContext(task, app, current).copy(languageTag = language),
                    current, languageTag = language).getOrThrow()
            val latestTask = tasks.observeAll().first().firstOrNull { it.id == task.id && !it.isCompleted }
                ?: error(com.example.focus_app.data.language.AppLanguage.context(context).getString(R.string.setup_text_139))
            require(signature(latestTask, settings.getSettings()) == original) { com.example.focus_app.data.language.AppLanguage.context(context).getString(R.string.setup_text_140) }
            require(messages.isNotEmpty() && messages.all { it.isNotBlank() }) { com.example.focus_app.data.language.AppLanguage.context(context).getString(R.string.setup_text_141) }
            cache.replace(task.id, app.packageName, current.toneKey.key, messages, languageTag = language)
            preparedMessages[app.packageName] = messages
            previews += messages.map { "${app.appName} · $it" }
            change { it.copy(previews = previews.toList()) }
        }
        val finalTask = tasks.observeAll().first().firstOrNull { it.id == task.id && !it.isCompleted }
            ?: error(com.example.focus_app.data.language.AppLanguage.context(context).getString(R.string.setup_text_139))
        require(connectionMatches(settings.getSettings()) && signature(finalTask, settings.getSettings()) == original) { com.example.focus_app.data.language.AppLanguage.context(context).getString(R.string.setup_text_140) }
        generatedSignature = original
        change { it.copy(generated = true, message = com.example.focus_app.data.language.AppLanguage.context(context).getString(R.string.setup_text_142)) }
    }
    fun enableDetection() = work {
        settings.update { it.copy(enableAccessibility = true, detectionMode = DetectionMode.REALTIME) }
        refreshNow()
    }
    fun finish(startNow: Boolean) = work {
        val language = AppLanguage.tag(context)
        refreshNow()
        require(state.value.permissions.take(4).size == 4 && state.value.permissions.take(4).all { it }) { com.example.focus_app.data.language.AppLanguage.context(context).getString(R.string.setup_text_143) }
        val current = settings.getSettings()
        require(current.enableAccessibility) { com.example.focus_app.data.language.AppLanguage.context(context).getString(R.string.setup_text_144) }
        val task = tasks.observeAll().first().firstOrNull { it.id == store.taskId && !it.isCompleted }
            ?: error(com.example.focus_app.data.language.AppLanguage.context(context).getString(R.string.setup_text_122))
        require(connectionMatches(current) && generatedSignature == signature(task, current)) { com.example.focus_app.data.language.AppLanguage.context(context).getString(R.string.setup_text_145) }
        require(current.targetApps.isNotEmpty()) { com.example.focus_app.data.language.AppLanguage.context(context).getString(R.string.setup_text_137) }
        for (app in current.targetApps) {
            require(context.packageManager.getLaunchIntentForPackage(app.packageName) != null) { com.example.focus_app.data.language.AppLanguage.context(context).getString(R.string.setup_text_146, app.appName) }
            require(cache.isReady(task.id, app.packageName, current.toneKey.key, languageTag = language)) { com.example.focus_app.data.language.AppLanguage.context(context).getString(R.string.setup_text_147) }
        }
        if (startNow) tasks.setManualActive(task.id, 30)
        withContext(NonCancellable) {
            try {
                guardian.setGuardianEnabled(true)
                store.complete()
            } catch (error: Exception) {
                try { guardian.setGuardianEnabled(current.guardianEnabled) }
                catch (rollback: Exception) { error.addSuppressed(rollback) }
                throw error
            }
        }
        change { it.copy(step = 6, completed = true, guardian = true, exit = false) }
    }
}

internal fun tutorialEditableTask(task: FocusTask?): FocusTask? = task?.takeUnless { it.isCompleted }

internal fun tutorialAppsChanged(before: List<AppInfo>, after: List<AppInfo>): Boolean =
    before.sortedBy { it.packageName } != after.sortedBy { it.packageName }

internal fun tutorialGroupMatches(editingId: String?, activeId: String): Boolean =
    editingId != null && editingId == activeId

internal fun tutorialAiChanged(previous: AppSettings, endpoint: String, model: String,
    previousKey: String, submittedKey: String, provider: AiProvider = previous.aiProvider): Boolean =
    previous.aiProvider != provider || previous.apiEndpoint != endpoint || previous.aiModel != model ||
        (submittedKey.isNotBlank() && submittedKey.trim() != previousKey)
