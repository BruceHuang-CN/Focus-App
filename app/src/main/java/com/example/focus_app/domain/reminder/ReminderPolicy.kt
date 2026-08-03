package com.example.focus_app.domain.reminder

import com.example.focus_app.data.repository.AppSettings
import com.example.focus_app.domain.time.Clock
import javax.inject.Inject

class ReminderPolicy @Inject constructor(private val clock: Clock) {
    fun canShow(remindedAt: List<Long>, settings: AppSettings): Boolean {
        val since = clock.nowMillis() - settings.reminderWindowMinutes * 60_000L
        return remindedAt.count { it >= since } < settings.maxRemindersPerWindow
    }
}
