package com.example.focus_app.domain.usecase

import com.example.focus_app.data.repository.AiRepository
import com.example.focus_app.data.repository.SettingsRepository
import com.example.focus_app.domain.model.ReminderContext
import javax.inject.Inject

class GenerateAiReminderUseCase @Inject constructor(private val aiRepository: AiRepository, private val settingsRepository: SettingsRepository) {
    suspend operator fun invoke(context: ReminderContext): Result<String> {
        val settings = settingsRepository.getSettings()
        if (!settings.isAiConfigured) return Result.failure(IllegalStateException("API Key 未配置"))
        return aiRepository.generateReminder(context, settings)
    }
}
