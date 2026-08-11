package com.example.focus_app.domain.usecase

import android.content.SharedPreferences
import com.example.focus_app.data.appgroup.AppGroupRepository
import com.example.focus_app.data.appgroup.AppGroupStore
import com.example.focus_app.data.local.dao.AiReminderCacheDao
import com.example.focus_app.data.local.dao.SettingsDao
import com.example.focus_app.data.local.entity.AiReminderCacheEntity
import com.example.focus_app.data.local.entity.SettingsEntity
import com.example.focus_app.data.repository.AppInfo
import com.example.focus_app.data.repository.AppSessionRepository
import com.example.focus_app.data.repository.ReminderCacheRepository
import com.example.focus_app.data.repository.SettingsRepository
import com.example.focus_app.domain.model.AppUsageSession
import com.example.focus_app.domain.time.FakeClock
import com.example.focus_app.service.AppSessionContext
import com.example.focus_app.service.AppSessionContextProvider
import com.example.focus_app.service.AppSessionCoordinator
import com.example.focus_app.service.SessionReminderScheduler
import com.google.gson.Gson
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UpdateGuardianStateUseCaseTest {
    @Test
    fun activate_group_closes_session_before_mirroring_apps_and_resetting_quota() = runTest {
        val fixture = fixture()
        fixture.groups.create("Social", listOf(AppInfo("social.app", "Social")))
        val groupId = fixture.groups.groups.value.last().id

        val result = fixture.useCase.activateGroup(groupId)

        assertTrue(result.isSuccess)
        assertEquals(
            listOf("cancel", "close", "settings", "quota"),
            fixture.events
        )
        assertEquals(listOf(AppInfo("social.app", "Social")), fixture.settings.getSettings().targetApps)
        assertEquals(4_400_000L, fixture.sessions.resetSince.single())
    }

    @Test
    fun activate_group_requests_reminder_cache_regeneration() = runTest {
        val fixture = fixture()
        fixture.groups.create("Social", listOf(AppInfo("social.app", "Social")))

        fixture.useCase.activateGroup(fixture.groups.groups.value.last().id)

        assertEquals(1L, fixture.cache.revision.value)
    }

    @Test
    fun updating_active_group_mirrors_apps_and_resets_guardian_state() = runTest {
        val fixture = fixture()
        val activeId = fixture.groups.activeGroupId.value

        val result = fixture.useCase.updateActiveGroup(
            activeId,
            "Social",
            listOf(AppInfo("social.app", "Social"))
        )

        assertTrue(result.isSuccess)
        assertEquals(listOf(AppInfo("social.app", "Social")), fixture.groups.groups.value.single().apps)
        assertEquals(listOf(AppInfo("social.app", "Social")), fixture.settings.getSettings().targetApps)
        assertEquals(listOf("cancel", "close", "settings", "quota"), fixture.events)
        assertEquals(1L, fixture.cache.revision.value)
    }

    @Test
    fun missing_group_fails_without_changing_session_or_settings() = runTest {
        val fixture = fixture()
        val before = fixture.settings.getSettings()

        val result = fixture.useCase.activateGroup("missing")

        assertTrue(result.isFailure)
        assertEquals(before, fixture.settings.getSettings())
        assertEquals(emptyList<String>(), fixture.events)
    }

    @Test
    fun disabling_guardian_closes_session_without_changing_accessibility() = runTest {
        val fixture = fixture(enableAccessibility = true)

        fixture.useCase.setGuardianEnabled(false)

        assertEquals(listOf("cancel", "close", "guardian"), fixture.events)
        assertFalse(fixture.settings.getSettings().guardianEnabled)
        assertTrue(fixture.settings.getSettings().enableAccessibility)
    }

    private fun fixture(enableAccessibility: Boolean = false): Fixture {
        val events = mutableListOf<String>()
        val settings = SettingsRepository(RecordingSettingsDao(events, enableAccessibility))
        val sessions = RecordingSessions(events)
        val coordinator = AppSessionCoordinator(
            repository = sessions,
            contextProvider = object : AppSessionContextProvider {
                override suspend fun currentContext() = AppSessionContext(emptyMap(), null, "gentle")
            },
            clock = FakeClock(8_000_000L),
            reminderScheduler = RecordingScheduler(events)
        )
        val groups = AppGroupRepository(AppGroupStore(MemoryPreferences()), settings)
        val cache = ReminderCacheRepository(NoOpCacheDao(), FakeClock(8_000_000L))
        return Fixture(UpdateGuardianStateUseCase(groups, settings, sessions, cache, coordinator, FakeClock(8_000_000L)), groups, settings, sessions, cache, events)
    }

    private data class Fixture(
        val useCase: UpdateGuardianStateUseCase,
        val groups: AppGroupRepository,
        val settings: SettingsRepository,
        val sessions: RecordingSessions,
        val cache: ReminderCacheRepository,
        val events: MutableList<String>
    )
}

private class RecordingSettingsDao(private val events: MutableList<String>, accessibility: Boolean) : SettingsDao {
    private val state = MutableStateFlow<SettingsEntity?>(SettingsEntity(targetApps = Gson().toJson(listOf(AppInfo("video.app", "Video"))), enableAccessibility = accessibility))
    override suspend fun insertOrUpdate(settings: SettingsEntity) { events += if (settings.guardianEnabled) "settings" else "guardian"; state.value = settings }
    override fun getSettings(): Flow<SettingsEntity?> = state
    override suspend fun getSettingsOnce(): SettingsEntity? = state.value
    override suspend fun clearLegacyApiKey() = Unit
}

private class RecordingSessions(private val events: MutableList<String>) : AppSessionRepository {
    val resetSince = mutableListOf<Long>()
    private var open = AppUsageSession(1L, "video.app", "Video", 8_000_000L, null, null, null, null, "gentle")
    override suspend fun openSession(packageName: String, appName: String, startedAt: Long, taskId: Long?, toneKey: String): AppUsageSession = open
    override suspend fun closeSession(sessionId: Long, endedAt: Long) { events += "close"; open = open.copy(endedAt = endedAt) }
    override suspend fun currentOpenSession(): AppUsageSession? = open.takeIf { it.endedAt == null }
    override suspend fun reminderTimesSince(since: Long): List<Long> = emptyList()
    override suspend fun markRemindedIfNeeded(sessionId: Long, remindedAt: Long) = false
    override suspend fun markUserAction(sessionId: Long, action: String) = Unit
    override suspend fun resetReminderQuota(since: Long) { events += "quota"; resetSince += since }
}

private class RecordingScheduler(private val events: MutableList<String>) : SessionReminderScheduler {
    override fun onSessionStarted(session: AppUsageSession, appStillForeground: suspend () -> Boolean) = Unit
    override fun cancel(sessionId: Long) { events += "cancel" }
}

private class NoOpCacheDao : AiReminderCacheDao {
    override suspend fun delete(taskId: Long, packageName: String, toneKey: String) = Unit
    override suspend fun insertAll(entries: List<AiReminderCacheEntity>) = Unit
    override suspend fun selectNext(taskId: Long, packageName: String, toneKey: String): AiReminderCacheEntity? = null
    override suspend fun maxLastUsedAt(taskId: Long, packageName: String, toneKey: String): Long? = null
    override suspend fun count(taskId: Long, packageName: String, toneKey: String) = 0
    override suspend fun updateLastUsedAt(id: Long, usedAt: Long) = Unit
}

private class MemoryPreferences : SharedPreferences {
    private val values = mutableMapOf<String, String>()
    override fun getAll(): MutableMap<String, *> = values
    override fun getString(key: String, defValue: String?) = values[key] ?: defValue
    override fun getStringSet(key: String, defValues: MutableSet<String>?) = defValues
    override fun getInt(key: String, defValue: Int) = defValue
    override fun getLong(key: String, defValue: Long) = defValue
    override fun getFloat(key: String, defValue: Float) = defValue
    override fun getBoolean(key: String, defValue: Boolean) = defValue
    override fun contains(key: String) = values.containsKey(key)
    override fun edit(): SharedPreferences.Editor = object : SharedPreferences.Editor {
        override fun putString(key: String, value: String?) = apply { if (value == null) values.remove(key) else values[key] = value }
        override fun putStringSet(key: String, values: MutableSet<String>?) = this
        override fun putInt(key: String, value: Int) = this
        override fun putLong(key: String, value: Long) = this
        override fun putFloat(key: String, value: Float) = this
        override fun putBoolean(key: String, value: Boolean) = this
        override fun remove(key: String) = apply { values.remove(key) }
        override fun clear() = apply { values.clear() }
        override fun commit() = true
        override fun apply() = Unit
    }
    override fun registerOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener) = Unit
    override fun unregisterOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener) = Unit
}
