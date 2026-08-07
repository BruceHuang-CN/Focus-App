package com.example.focus_app.data.remote

import com.example.focus_app.data.repository.AiRepository
import com.example.focus_app.data.repository.AppInfo
import com.example.focus_app.data.repository.AppSettings
import com.example.focus_app.data.repository.ReminderCacheRepository
import com.example.focus_app.data.repository.MoodRepository
import com.example.focus_app.data.repository.SettingsRepository
import com.example.focus_app.data.repository.TaskRepository
import com.example.focus_app.domain.model.FocusTask
import com.example.focus_app.domain.model.ReminderContext
import com.example.focus_app.domain.task.MANUAL_ACTIVATION_PERIOD_TOKEN
import com.example.focus_app.domain.task.activationPeriodToken
import com.example.focus_app.domain.time.SystemClock
import com.example.focus_app.domain.usecase.BuildReminderContextUseCase
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

@Singleton
class ReminderBatchCoordinator(
    private val activeTasks: Flow<FocusTask?>,
    private val settings: Flow<AppSettings>,
    private val apiKeyRevisions: Flow<Long> = flowOf(0L),
    private val cacheRevisions: Flow<Long> = flowOf(0L),
    private val moodRevisions: Flow<Long?> = flowOf(null),
    private val buildContext: suspend (FocusTask, AppInfo, AppSettings) -> ReminderContext,
    private val generate: suspend (ReminderContext, AppSettings) -> Result<List<String>>,
    private val cache: suspend (Long, String, String, List<String>) -> Unit,
    private val cacheReady: suspend (Long, String, String) -> Boolean = { _, _, _ -> false },
    private val periodToken: (FocusTask) -> Long = { MANUAL_ACTIVATION_PERIOD_TOKEN }
) {
    @Inject
    constructor(
        taskRepository: TaskRepository,
        settingsRepository: SettingsRepository,
        moodRepository: MoodRepository,
        contextBuilder: BuildReminderContextUseCase,
        aiRepository: AiRepository,
        cacheRepository: ReminderCacheRepository
    ) : this(
        activeTasks = taskRepository.observeActiveEvents(),
        settings = settingsRepository.getSettingsFlow(),
        apiKeyRevisions = settingsRepository.apiKeyRevision,
        cacheRevisions = cacheRepository.revision,
        moodRevisions = moodRepository.observeLatestMood().map { mood -> mood?.id },
        buildContext = contextBuilder::invoke,
        generate = { context, currentSettings ->
            aiRepository.generateBatch(context, currentSettings, BATCH_SIZE)
        },
        cache = cacheRepository::replace,
        cacheReady = cacheRepository::isReady,
        periodToken = { task -> activationPeriodToken(task, SystemClock.now()) }
    )

    fun start(scope: CoroutineScope): Job = scope.launch {
        var coldStartPending = true
        val versionedTasks = activeTasks.map { task ->
            ActiveEmission(task, task?.let(periodToken))
        }.distinctUntilChanged()
        combine(versionedTasks, settings, apiKeyRevisions, cacheRevisions, moodRevisions) {
                active, currentSettings, apiKeyRevision, cacheRevision, moodRevision ->
            val task = active.task
            if (task == null || currentSettings.targetApps.isEmpty()) {
                null
            } else {
                val targetApps = currentSettings.targetApps.sortedWith(
                    compareBy<AppInfo> { it.packageName }.thenBy { it.appName }
                )
                BatchActivation(
                    key = ActivationKey(
                        taskId = task.id,
                        taskUpdatedAt = task.updatedAt,
                        periodToken = checkNotNull(active.periodToken),
                        targets = targetApps.map { it.packageName to it.appName },
                        toneKey = currentSettings.toneKey.key,
                        customToneInstruction = currentSettings.customToneInstruction,
                        provider = currentSettings.aiProvider.name,
                        endpoint = currentSettings.apiEndpoint,
                        model = currentSettings.aiModel,
                        reminderWindowMinutes = currentSettings.reminderWindowMinutes,
                        apiKeyRevision = apiKeyRevision,
                        cacheRevision = cacheRevision,
                        moodRevision = moodRevision
                    ),
                    task = task,
                    settings = currentSettings,
                    targetApps = targetApps
                )
            }
        }
            .distinctUntilChanged { old, new -> old?.key == new?.key }
            .collectLatest { activation ->
                val isColdStart = coldStartPending
                coldStartPending = false
                if (activation == null) return@collectLatest
                val targetApps = if (isColdStart) {
                    activation.targetApps.filter { app ->
                        !cacheReady(
                            activation.task.id,
                            app.packageName,
                            activation.settings.toneKey.key
                        )
                    }
                } else {
                    activation.targetApps
                }
                targetApps.forEach { app ->
                    generateAndCache(activation, app)
                }
            }
    }

    private suspend fun generateAndCache(activation: BatchActivation, app: AppInfo) {
        try {
            val context = buildContext(activation.task, app, activation.settings)
            val messages = generate(context, activation.settings).getOrNull() ?: return
            if (messages.size == BATCH_SIZE) {
                cache(
                    activation.task.id,
                    app.packageName,
                    activation.settings.toneKey.key,
                    messages
                )
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            // Cache generation is best-effort and must not stop later activations.
        }
    }

    private data class BatchActivation(
        val key: ActivationKey,
        val task: FocusTask,
        val settings: AppSettings,
        val targetApps: List<AppInfo>
    )

    private data class ActiveEmission(val task: FocusTask?, val periodToken: Long?)

    private data class ActivationKey(
        val taskId: Long,
        val taskUpdatedAt: Long,
        val periodToken: Long,
        val targets: List<Pair<String, String>>,
        val toneKey: String,
        val customToneInstruction: String,
        val provider: String,
        val endpoint: String,
        val model: String,
        val reminderWindowMinutes: Int,
        val apiKeyRevision: Long,
        val cacheRevision: Long,
        val moodRevision: Long?
    )

    private companion object {
        const val BATCH_SIZE = 3
    }
}
