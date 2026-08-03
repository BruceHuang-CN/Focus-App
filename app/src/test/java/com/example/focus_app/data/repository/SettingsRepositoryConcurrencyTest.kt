package com.example.focus_app.data.repository

import com.example.focus_app.data.local.dao.SettingsDao
import com.example.focus_app.data.local.entity.SettingsEntity
import com.example.focus_app.domain.model.ReminderTone
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.yield
import org.junit.Assert.assertEquals
import org.junit.Test

class SettingsRepositoryConcurrencyTest {
    @Test
    fun concurrent_transforms_preserve_both_settings_updates() = runTest {
        val dao = FakeSettingsDao(
            SettingsEntity(targetApps = "[]", legacyApiKey = "")
        )
        val repository = SettingsRepository(dao)

        val delayUpdate = launch {
            repository.update { it.copy(reminderDelaySeconds = 45) }
        }
        val toneUpdate = launch {
            repository.update { it.copy(toneKey = ReminderTone.DIRECT) }
        }
        delayUpdate.join()
        toneUpdate.join()

        val settings = repository.getSettings()
        assertEquals(45, settings.reminderDelaySeconds)
        assertEquals(ReminderTone.DIRECT, settings.toneKey)
    }
}

private class FakeSettingsDao(initial: SettingsEntity) : SettingsDao {
    private val settings = MutableStateFlow<SettingsEntity?>(initial)

    override suspend fun insertOrUpdate(settings: SettingsEntity) {
        this.settings.value = settings
    }

    override fun getSettings(): Flow<SettingsEntity?> = settings

    override suspend fun getSettingsOnce(): SettingsEntity? {
        val snapshot = settings.value
        yield()
        return snapshot
    }
}
