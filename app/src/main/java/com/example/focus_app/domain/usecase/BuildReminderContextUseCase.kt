package com.example.focus_app.domain.usecase

import com.example.focus_app.data.repository.AppUsageRepository
import com.example.focus_app.data.repository.MoodRepository
import com.example.focus_app.domain.model.ReminderContext
import java.text.SimpleDateFormat
import java.util.*
import javax.inject.Inject

class BuildReminderContextUseCase @Inject constructor(private val appUsageRepository: AppUsageRepository, private val moodRepository: MoodRepository) {
    suspend operator fun invoke(appName: String, personality: String): ReminderContext {
        val dateFormatter = SimpleDateFormat("MM-dd HH:mm", Locale.getDefault())
        return ReminderContext(appName, dateFormatter.format(Date()), appUsageRepository.getOpenCountToday(), null, moodRepository.getLatestMood()?.mood, appUsageRepository.getExitedCountToday(), personality)
    }
}
