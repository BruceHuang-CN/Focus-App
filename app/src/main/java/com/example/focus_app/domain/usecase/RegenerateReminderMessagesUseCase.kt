package com.example.focus_app.domain.usecase

import android.content.Context
import com.example.focus_app.data.language.AppLanguage
import com.example.focus_app.data.remote.AiFailure
import dagger.hilt.android.qualifiers.ApplicationContext
import com.example.focus_app.data.repository.AiRepository
import com.example.focus_app.data.repository.AppInfo
import com.example.focus_app.data.repository.AppSettings
import com.example.focus_app.data.repository.ReminderCacheRepository
import com.example.focus_app.data.repository.SettingsRepository
import com.example.focus_app.data.repository.TaskRepository
import com.example.focus_app.domain.model.FocusTask
import com.example.focus_app.domain.model.ReminderContext
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first

sealed interface ReminderRegenerationResult {
    data class Success(val targetCount: Int) : ReminderRegenerationResult
    data object NoActiveTask : ReminderRegenerationResult
    data object NoTargetApps : ReminderRegenerationResult
    data class Failed(val reason: String) : ReminderRegenerationResult
}

class RegenerateReminderMessagesUseCase internal constructor(
    private val activeTask: suspend () -> FocusTask?,
    private val settings: suspend () -> AppSettings,
    private val buildContext: suspend (FocusTask, AppInfo, AppSettings) -> ReminderContext,
    private val generateRemote: suspend (ReminderContext, AppSettings) -> Result<List<String>>,
    private val replaceCache: suspend (Long, String, String, List<String>) -> Unit,
    private val currentLanguage: () -> String = { "zh-CN" }
) {
    @Inject
    constructor(
        taskRepository: TaskRepository,
        settingsRepository: SettingsRepository,
        contextBuilder: BuildReminderContextUseCase,
        aiRepository: AiRepository,
        cacheRepository: ReminderCacheRepository,
        @ApplicationContext applicationContext: Context
    ) : this(
        activeTask = { taskRepository.observeActive().first() },
        settings = settingsRepository::getSettings,
        buildContext = contextBuilder::invoke,
        generateRemote = { context, current -> aiRepository.generateRemoteBatch(context, current, languageTag = context.languageTag) },
        replaceCache = { taskId, packageName, bucket, messages -> cacheRepository.replace(taskId, packageName, bucket, messages, "zh-CN") },
        currentLanguage = { AppLanguage.tag(applicationContext) }
    )

    suspend operator fun invoke(): ReminderRegenerationResult {
        return try {
            regenerate()
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            ReminderRegenerationResult.Failed(AiFailure.fromException(error).message(currentLanguage()))
        }
    }

    private suspend fun regenerate(): ReminderRegenerationResult {
        val languageTag = currentLanguage()
        val task = activeTask() ?: return ReminderRegenerationResult.NoActiveTask
        val currentSettings = settings()
        val targets = currentSettings.targetApps.distinctBy { it.packageName }
        if (targets.isEmpty()) return ReminderRegenerationResult.NoTargetApps

        val generated = mutableListOf<Pair<AppInfo, List<String>>>()
        for (app in targets) {
            val context = buildContext(task, app, currentSettings).copy(languageTag = languageTag)
            val messages = generateRemote(context, currentSettings).getOrElse { error ->
                return ReminderRegenerationResult.Failed(
                    error.message ?: AiFailure.REQUEST.message(languageTag)
                )
            }
            generated += app to messages
        }

        val latestTask = activeTask()
        val latestSettings = settings()
        val originalPackages = targets.map { it.packageName }
        val latestPackages = latestSettings.targetApps.distinctBy { it.packageName }.map { it.packageName }
        if (
            currentLanguage() != languageTag ||
            latestTask?.id != task.id ||
            latestTask?.updatedAt != task.updatedAt ||
            latestSettings.toneKey != currentSettings.toneKey ||
            latestSettings.customToneInstruction != currentSettings.customToneInstruction ||
            latestSettings.aiProvider != currentSettings.aiProvider ||
            latestSettings.apiEndpoint != currentSettings.apiEndpoint ||
            latestSettings.aiModel != currentSettings.aiModel ||
            latestPackages != originalPackages
        ) {
            return ReminderRegenerationResult.Failed(if (currentLanguage().startsWith("en", true)) "The task, language or AI settings changed. Generate again." else "任务、语言或 AI 设置已变化，请重新生成")
        }

        generated.forEach { (app, messages) ->
            replaceCache(task.id, app.packageName, ReminderCacheRepository.languageToneKey(currentSettings.toneKey.key, languageTag), messages)
        }
        return ReminderRegenerationResult.Success(targetCount = generated.size)
    }
}
