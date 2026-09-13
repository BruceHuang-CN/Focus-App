package com.example.focus_app.service

import com.example.focus_app.domain.model.*
import org.junit.Assert.*
import org.junit.Test

class ReminderTaskContextTest {
    @Test fun rejects_older_activation_even_for_the_same_task_and_session() {
        val session = AppUsageSession(id = 7, packageName = "target", appName = "应用", startedAt = 1000,
            taskId = 2, toneKey = "gentle", taskContextStartedAt = 5000)
        val data = ReminderLaunchData(sessionId = 7, taskId = 2, taskTitle = "学习", appName = "应用", message = "提醒",
            showBreathing = false, returnDestination = ReturnDestination.FOCUS, taskContextStartedAt = 1000)
        assertFalse(matchesReminderContext(data, session))
        assertTrue(matchesReminderContext(data.copy(taskContextStartedAt = 5000), session))
        assertFalse(matchesReminderContext(data.copy(taskContextStartedAt = 5000, taskId = 3), session))
    }
}
