package com.example.focus_app.data.remote

import com.example.focus_app.domain.model.ReminderContext

interface AiReminderProvider {
    suspend fun generateBatch(
        context: ReminderContext,
        count: Int = 3
    ): Result<List<String>>
}
