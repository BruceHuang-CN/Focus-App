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
    private val moodRevisions: Flow<Long?> = flowOf(null),
    private val buildContext: suspend (FocusTask, AppInfo, AppSettings) -> ReminderContext,
    private val generate: suspend (ReminderContext, AppSettings) -> Result<List<String>>,
    private val cache: suspend (Long, String, String, List<String>) -> Unit
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
        activeTasks = taskRepository.observeActive(),
        settings = settingsRepository.getSettingsFlow(),
        apiKeyRevisions = settingsRepository.apiKeyRevision,
        moodRevisions = moodRepository.observeLatestMood().map { mood -> mood?.id },
        buildContext = contextBuilder::invoke,
        generate = { context, currentSettings ->
            aiRepository.generateBatch(context, currentSettings, BATCH_SIZE)
        },
        cache = cacheRepository::replace
    )

    fun start(scope: CoroutineScope): Job = scope.launch {
        combine(activeTasks, settings, apiKeyRevisions, moodRevisions) {
                task, currentSettings, apiKeyRevision, moodRevision ->
            if (task == null || currentSettings.targetApps.isEmpty()) {
                null
            } else {
                BatchActivation(
                    key = ActivationKey(
                        taskId = task.id,
                        taskUpdatedAt = task.updatedAt,
                        targetPackages = currentSettings.targetApps.map { it.packageName },
                        toneKey = currentSettings.toneKey.key,
                        customToneInstruction = currentSettings.customToneInstruction,
                        provider = currentSettings.aiProvider.name,
                        endpoint = currentSettings.apiEndpoint,
                        model = currentSettings.aiModel,
                        reminderWindowMinutes = currentSettings.reminderWindowMinutes,
                        apiKeyRevision = apiKeyRevision,
                        moodRevision = moodRevision
                    ),
                    task = task,
                    settings = currentSettings
                )
            }
        }
            .distinctUntilChanged { old, new -> old?.key == new?.key }
            .collectLatest { activation ->
                if (activation == null) return@collectLatest
                activation.settings.targetApps.forEach { app ->
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
        val settings: AppSettings
    )

    private data class ActivationKey(
        val taskId: Long,
        val taskUpdatedAt: Long,
        val targetPackages: List<String>,
        val toneKey: String,
        val customToneInstruction: String,
        val provider: String,
        val endpoint: String,
        val model: String,
        val reminderWindowMinutes: Int,
        val apiKeyRevision: Long,
        val moodRevision: Long?
    )

    private companion object {
        const val BATCH_SIZE = 3
    }
}
