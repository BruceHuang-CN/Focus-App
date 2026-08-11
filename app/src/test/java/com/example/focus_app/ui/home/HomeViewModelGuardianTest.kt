package com.example.focus_app.ui.home

import android.content.SharedPreferences
import com.example.focus_app.data.appgroup.AppGroupRepository
import com.example.focus_app.data.appgroup.AppGroupStore
import com.example.focus_app.data.local.dao.AiReminderCacheDao
import com.example.focus_app.data.local.dao.FocusTaskDao
import com.example.focus_app.data.local.dao.MoodRecordDao
import com.example.focus_app.data.local.dao.SettingsDao
import com.example.focus_app.data.local.entity.AiReminderCacheEntity
import com.example.focus_app.data.local.entity.FocusTaskEntity
import com.example.focus_app.data.local.entity.MoodRecordEntity
import com.example.focus_app.data.local.entity.SettingsEntity
import com.example.focus_app.data.repository.AppInfo
import com.example.focus_app.data.repository.AppSessionRepository
import com.example.focus_app.data.repository.MoodRepository
import com.example.focus_app.data.repository.ReminderCacheRepository
import com.example.focus_app.data.repository.SettingsRepository
import com.example.focus_app.data.repository.TaskRepository
import com.example.focus_app.domain.model.AppUsageSession
import com.example.focus_app.domain.time.FakeClock
import com.example.focus_app.domain.usecase.ResetReminderQuotaUseCase
import com.example.focus_app.domain.usecase.UpdateGuardianStateUseCase
import com.example.focus_app.service.AppSessionContext
import com.example.focus_app.service.AppSessionContextProvider
import com.example.focus_app.service.AppSessionCoordinator
import com.example.focus_app.service.SessionReminderScheduler
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.async
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class HomeViewModelGuardianTest {
    private val dispatcher = StandardTestDispatcher()

    @Before fun setUp() { Dispatchers.setMain(dispatcher) }
    @After fun tearDown() { Dispatchers.resetMain() }

    @Test
    fun disabled_guardian_with_active_group_is_exposed_in_home_state() = runTest(dispatcher) {
        val fixture = fixture(guardianEnabled = false)
        fixture.groups.create("\u5b66\u4e60\u7ec4", listOf(AppInfo("study.app", "Study")))
        val viewModel = fixture.homeViewModel()
        runCurrent()
        assertFalse(viewModel.uiState.value.guardianEnabled)
        assertEquals("\u5b66\u4e60\u7ec4", viewModel.uiState.value.activeGroupName)
    }

    @Test
    fun missing_active_group_uses_unconfigured_group_name() = runTest(dispatcher) {
        val viewModel = fixture().homeViewModel()
        runCurrent()
        assertEquals("\u672a\u8bbe\u7f6e\u5e94\u7528\u7ec4", viewModel.uiState.value.activeGroupName)
    }

    @Test
    fun homepage_actions_disable_guardian_and_reset_reminder_quota() = runTest(dispatcher) {
        val fixture = fixture()
        val viewModel = fixture.homeViewModel()
        runCurrent()
        viewModel.setGuardianEnabled(false)
        viewModel.resetReminderQuota()
        runCurrent()
        assertFalse(fixture.settings.getSettings().guardianEnabled)
        assertEquals(1, fixture.sessions.resetQuotaCalls)
    }

    @Test
    fun reset_reminder_quota_emits_confirmation_after_the_reset_completes() = runTest(dispatcher) {
        val fixture = fixture()
        val viewModel = fixture.homeViewModel()
        val event = async { viewModel.events.first() }
        runCurrent()

        viewModel.resetReminderQuota()
        runCurrent()

        assertEquals(HomeEvent.ReminderQuotaReset, event.await())
        assertEquals(1, fixture.sessions.resetQuotaCalls)
    }

    private fun fixture(guardianEnabled: Boolean = true): Fixture {
        val settings = SettingsRepository(HomeSettingsDao(guardianEnabled))
        val groups = AppGroupRepository(AppGroupStore(HomePreferences()), settings)
        val sessions = HomeSessions()
        val coordinator = AppSessionCoordinator(sessions, object : AppSessionContextProvider {
            override suspend fun currentContext() = AppSessionContext(emptyMap(), null, "gentle")
        }, FakeClock(1_000_000L), HomeReminderScheduler())
        val cache = ReminderCacheRepository(HomeCacheDao(), FakeClock(1_000_000L))
        return Fixture(settings, groups, sessions, MoodRepository(HomeMoodDao()), TaskRepository(HomeTaskDao(), FakeClock(1_000_000L)), UpdateGuardianStateUseCase(groups, settings, sessions, cache, coordinator, FakeClock(1_000_000L)), ResetReminderQuotaUseCase(settings, sessions, coordinator, FakeClock(1_000_000L)))
    }

    private data class Fixture(val settings: SettingsRepository, val groups: AppGroupRepository, val sessions: HomeSessions, val moods: MoodRepository, val tasks: TaskRepository, val updateGuardianState: UpdateGuardianStateUseCase, val resetReminderQuota: ResetReminderQuotaUseCase) {
        fun homeViewModel() = HomeViewModel(sessions, moods, tasks, settings, groups, updateGuardianState, resetReminderQuota)
    }
}

private class HomeSettingsDao(guardianEnabled: Boolean) : SettingsDao {
    private val state = MutableStateFlow<SettingsEntity?>(SettingsEntity(targetApps = "[]", guardianEnabled = guardianEnabled))
    override suspend fun insertOrUpdate(settings: SettingsEntity) { state.value = settings }
    override fun getSettings(): Flow<SettingsEntity?> = state
    override suspend fun getSettingsOnce(): SettingsEntity? = state.value
    override suspend fun clearLegacyApiKey() = Unit
}
private class HomeSessions : AppSessionRepository {
    var resetQuotaCalls = 0
    override suspend fun openSession(packageName: String, appName: String, startedAt: Long, taskId: Long?, toneKey: String): AppUsageSession = error("unused")
    override suspend fun closeSession(sessionId: Long, endedAt: Long) = Unit
    override suspend fun currentOpenSession(): AppUsageSession? = null
    override suspend fun reminderTimesSince(since: Long): List<Long> = emptyList()
    override suspend fun markRemindedIfNeeded(sessionId: Long, remindedAt: Long) = false
    override suspend fun markUserAction(sessionId: Long, action: String) = Unit
    override suspend fun resetReminderQuota(since: Long) { resetQuotaCalls++ }
}
private class HomeMoodDao : MoodRecordDao {
    override suspend fun insert(mood: MoodRecordEntity) = Unit
    override suspend fun getLatestMood(): MoodRecordEntity? = null
    override fun getAllMoods(): Flow<List<MoodRecordEntity>> = flowOf(emptyList())
    override fun observeLatestMood(): Flow<MoodRecordEntity?> = flowOf(null)
}
private class HomeTaskDao : FocusTaskDao {
    override fun observeAll(): Flow<List<FocusTaskEntity>> = flowOf(emptyList())
    override suspend fun insert(task: FocusTaskEntity): Long = 0L
    override suspend fun update(task: FocusTaskEntity) = Unit
    override suspend fun delete(task: FocusTaskEntity) = Unit
    override suspend fun setCompleted(id: Long, isCompleted: Boolean, updatedAt: Long) = Unit
    override suspend fun canSetManualActive(id: Long) = false
    override suspend fun clearManualActive() = Unit
    override suspend fun markManualActive(id: Long) = Unit
    override suspend fun completedCountBetween(startedAt: Long, endedAt: Long) = 0
}
private class HomeCacheDao : AiReminderCacheDao {
    override suspend fun delete(taskId: Long, packageName: String, toneKey: String) = Unit
    override suspend fun insertAll(entries: List<AiReminderCacheEntity>) = Unit
    override suspend fun selectNext(taskId: Long, packageName: String, toneKey: String): AiReminderCacheEntity? = null
    override suspend fun maxLastUsedAt(taskId: Long, packageName: String, toneKey: String): Long? = null
    override suspend fun count(taskId: Long, packageName: String, toneKey: String) = 0
    override suspend fun updateLastUsedAt(id: Long, usedAt: Long) = Unit
}
private class HomeReminderScheduler : SessionReminderScheduler {
    override fun onSessionStarted(session: AppUsageSession, appStillForeground: suspend () -> Boolean) = Unit
    override fun cancel(sessionId: Long) = Unit
}
private class HomePreferences : SharedPreferences {
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
