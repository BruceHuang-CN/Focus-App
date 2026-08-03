package com.example.focus_app.data.repository

import com.example.focus_app.data.security.ApiKeyStore
import com.example.focus_app.domain.model.AiProvider
import com.example.focus_app.domain.model.ReminderContext
import com.example.focus_app.domain.model.ReminderTone
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class AiRepositoryTest {
    @Test
    fun invalid_custom_endpoint_still_returns_a_local_batch() = runTest {
        val repository = AiRepository(StaticApiKeyStore("secret"))
        val settings = AppSettings(
            aiProvider = AiProvider.CUSTOM,
            apiEndpoint = "not a url",
            aiModel = "custom-model"
        )

        val messages = repository.generateBatch(context(), settings).getOrThrow()

        assertEquals(3, messages.size)
        assertEquals(3, messages.distinct().size)
    }

    private fun context() = ReminderContext(
        taskTitle = "写方案",
        timeBlock = null,
        latestMood = null,
        appName = "抖音",
        openCountToday = 0,
        remindersInWindow = 0,
        activeExitsToday = 0,
        tone = ReminderTone.GENTLE
    )
}

private class StaticApiKeyStore(private val value: String) : ApiKeyStore {
    override suspend fun read(): String = value
    override suspend fun write(value: String) = Unit
    override suspend fun clear() = Unit
}
