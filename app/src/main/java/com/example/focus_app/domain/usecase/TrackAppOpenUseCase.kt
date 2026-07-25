package com.example.focus_app.domain.usecase

import com.example.focus_app.data.repository.AppUsageRepository
import javax.inject.Inject

data class TrackResult(val eventId: Long, val shouldRemind: Boolean, val remindedCountThisHour: Int, val maxReminds: Int)

class TrackAppOpenUseCase @Inject constructor(private val appUsageRepository: AppUsageRepository) {
    suspend operator fun invoke(packageName: String, appName: String, maxRemindsPerHour: Int): TrackResult {
        val eventId = appUsageRepository.insertEvent(packageName, appName)
        val remindedCount = appUsageRepository.getRemindedCountThisHour()
        return TrackResult(eventId, remindedCount < maxRemindsPerHour, remindedCount, maxRemindsPerHour)
    }
}
