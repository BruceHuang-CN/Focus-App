package com.example.focus_app.ui.settings

import com.example.focus_app.data.local.dao.FocusTaskDao
import com.example.focus_app.data.local.dao.SettingsDao
import com.example.focus_app.data.local.entity.FocusTaskEntity
import com.example.focus_app.data.local.entity.SettingsEntity
import com.example.focus_app.data.repository.AiRepository
import com.example.focus_app.data.repository.ReminderCacheRepository
import com.example.focus_app.data.repository.SettingsRepository
import com.example.focus_app.data.repository.TaskRepository
import com.example.focus_app.domain.time.FakeClock
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SettingsViewModelResetWindowTest {
    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun reset_reminder_window_clears_quota_since_window_start_and_bumps_cache_revision() =
        runTest(dispatcher) {
            val dao = WindowResetSettingsDao(
                SettingsEntity(targetApps = "[]", reminderWindowMinutes = 30)
            )
            val sessionRepository = TestSessionRepository()
            val cacheRepository = ReminderCacheRepository(TestCacheDao(), FakeClock(0L))
            val viewModel = SettingsViewModel(
                settingsRepository = SettingsRepository(dao),
                aiRepository = AiRepository(TestApiKeyStore()) { TestOpenAiApi() },
                taskRepository = TaskRepository(WindowResetTaskDao()),
                permissionStatusProvider = TestPermissionProvider(),
                appSessionRepository = sessionRepository,
                reminderCacheRepository = cacheRepository,
                customReturnAppStore = TestCustomReturnAppStore(),
                followUpReminderStore = TestFollowUpReminderStore(),
                keepAliveStore = TestKeepAliveStore()
            )
            runCurrent()
            val revisionBefore = cacheRepository.revision.value

            viewModel.resetReminderWindow()
            runCurrent()

            assertEquals(1, sessionRepository.resetCalls.size)
            val since = sessionRepository.resetCalls.single()
            // 窗口 = 30 分钟：since 应约为 now - 30 分钟，且大于 0
            assertTrue(since > 0)
            val now = System.currentTimeMillis()
            assertTrue(since <= now - 30 * 60_000L + 5_000L)
            assertTrue(since >= now - 30 * 60_000L - 5_000L)
            assertEquals(revisionBefore + 1, cacheRepository.revision.value)
        }
}

private class WindowResetSettingsDao(initial: SettingsEntity) : SettingsDao {
    private val state = MutableStateFlow<SettingsEntity?>(initial)

    override suspend fun insertOrUpdate(settings: SettingsEntity) {
        state.value = settings
    }

    override fun getSettings(): Flow<SettingsEntity?> = state
    override suspend fun getSettingsOnce(): SettingsEntity? = state.value
    override suspend fun clearLegacyApiKey() {
        state.value = state.value?.copy(legacyApiKey = "")
    }
}

private class WindowResetTaskDao : FocusTaskDao {
    override fun observeAll(): Flow<List<FocusTaskEntity>> = flowOf(emptyList())
    override suspend fun insert(task: FocusTaskEntity): Long = 0
    override suspend fun update(task: FocusTaskEntity) = Unit
    override suspend fun delete(task: FocusTaskEntity) = Unit
    override suspend fun setCompleted(id: Long, isCompleted: Boolean, updatedAt: Long) = Unit
    override suspend fun canSetManualActive(id: Long): Boolean = false
    override suspend fun clearManualActive() = Unit
    override suspend fun markManualActive(id: Long) = Unit
    override suspend fun completedCountBetween(startedAt: Long, endedAt: Long): Int = 0
}
