package com.example.focus_app.domain.task

import java.time.LocalDate

/**
 * 连续达标天数计算：从今天开始向前数，只要当天完成过至少一个任务就计入连续。
 */
object StreakCalculator {

    suspend fun streakDays(
        today: LocalDate,
        completedOn: suspend (LocalDate) -> Int
    ): Int {
        var streak = 0
        var daysBack = 0
        while (daysBack <= MAX_DAYS) {
            if (completedOn(today.minusDays(daysBack.toLong())) > 0) {
                streak++
            } else {
                break
            }
            daysBack++
        }
        return streak
    }

    private const val MAX_DAYS = 3_650
}
