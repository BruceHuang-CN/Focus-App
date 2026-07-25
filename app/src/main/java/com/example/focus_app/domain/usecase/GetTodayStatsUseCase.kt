package com.example.focus_app.domain.usecase

import com.example.focus_app.data.repository.AppUsageRepository
import javax.inject.Inject

data class TodayStats(val openCount: Int, val remindedCount: Int, val exitedCount: Int)

class GetTodayStatsUseCase @Inject constructor(private val appUsageRepository: AppUsageRepository) {
    suspend operator fun invoke(): TodayStats = TodayStats(
        appUsageRepository.getOpenCountToday(),
        appUsageRepository.getRemindedCountThisHour(),
        appUsageRepository.getExitedCountToday()
    )
}
