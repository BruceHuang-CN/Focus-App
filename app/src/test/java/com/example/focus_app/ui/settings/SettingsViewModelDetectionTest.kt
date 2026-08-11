package com.example.focus_app.ui.settings

import com.example.focus_app.data.local.dao.FocusTaskDao
import com.example.focus_app.data.local.dao.SettingsDao
import com.example.focus_app.data.local.entity.FocusTaskEntity
import com.example.focus_app.data.local.entity.SettingsEntity
import com.example.focus_app.data.remote.OpenAiApi
import com.example.focus_app.data.remote.dto.ChatRequest
import com.example.focus_app.data.remote.dto.ChatResponse
import com.example.focus_app.data.remote.dto.ModelInfo
import com.example.focus_app.data.remote.dto.ModelsResponse
import com.example.focus_app.data.repository.AiRepository
import com.example.focus_app.data.repository.SettingsRepository
import com.example.focus_app.data.repository.TaskRepository
import com.example.focus_app.data.security.ApiKeyStore
import com.example.focus_app.data.permission.PermissionStatusProvider
import com.example.focus_app.domain.model.DetectionMode
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
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import retrofit2.Response

@OptIn(ExperimentalCoroutinesApi::class)
class SettingsViewModelDetectionTest {
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
    fun onboarding_realtime_persists_mode_and_enables_accessibility() = runTest(dispatcher) {
        val dao = DetectionSettingsDao(SettingsEntity(targetApps = "[]"))
        val viewModel = newViewModel(dao)
        runCurrent()

        viewModel.applyOnboardingDetectionMode(DetectionMode.REALTIME)
        runCurrent()

        assertEquals("realtime", dao.current.detectionMode)
        assertTrue(dao.current.enableAccessibility)
    }

    @Test
    fun onboarding_compatibility_persists_mode_and_disables_accessibility() = runTest(dispatcher) {
        val dao = DetectionSettingsDao(SettingsEntity(targetApps = "[]"))
        val viewModel = newViewModel(dao)
        runCurrent()

        viewModel.applyOnboardingDetectionMode(DetectionMode.COMPATIBILITY)
        runCurrent()

        assertEquals("compatibility", dao.current.detectionMode)
        assertFalse(dao.current.enableAccessibility)
    }

    @Test
    fun ensure_accessibility_enabled_turns_on_the_flag() = runTest(dispatcher) {
        val dao = DetectionSettingsDao(SettingsEntity(targetApps = "[]"))
        val viewModel = newViewModel(dao)
        runCurrent()

        viewModel.ensureAccessibilityEnabled()
        runCurrent()

        assertTrue(dao.current.enableAccessibility)
    }

    @Test
    fun updating_detection_mode_persists_the_choice() = runTest(dispatcher) {
        val dao = DetectionSettingsDao(SettingsEntity(targetApps = "[]"))
        val viewModel = newViewModel(dao)
        runCurrent()

        viewModel.updateDetectionMode(DetectionMode.COMPATIBILITY)
        runCurrent()

        assertEquals("compatibility", dao.current.detectionMode)
    }

    private fun newViewModel(dao: DetectionSettingsDao): SettingsViewModel =
        newViewModel(dao, TestCustomReturnAppStore(), TestFollowUpReminderStore(), TestKeepAliveStore())

    private fun newViewModel(
        dao: DetectionSettingsDao,
        store: TestCustomReturnAppStore,
        followUp: TestFollowUpReminderStore,
        keepAlive: TestKeepAliveStore = TestKeepAliveStore()
    ): SettingsViewModel =
        SettingsViewModel(
            settingsRepository = SettingsRepository(dao),
            aiRepository = AiRepository(DetectionApiKeyStore("sk-test")) { DetectionOpenAiApi() },
            taskRepository = TaskRepository(DetectionTaskDao()),
            permissionStatusProvider = DetectionFakePermissionProvider(),
            customReturnAppStore = store,
            followUpReminderStore = followUp,
            keepAliveStore = keepAlive,
            themeStore = TestThemeStore()
        )

    @Test
    fun updating_custom_return_package_persists_to_store() = runTest(dispatcher) {
        val dao = DetectionSettingsDao(SettingsEntity(targetApps = "[]"))
        val store = TestCustomReturnAppStore()
        val viewModel = newViewModel(dao, store, TestFollowUpReminderStore())
        runCurrent()

        viewModel.updateCustomReturnPackage(" com.tencent.mm ")
        runCurrent()

        assertEquals("com.tencent.mm", store.value)
        assertEquals("com.tencent.mm", viewModel.customReturnPackage.value)
    }

    @Test
    fun updating_follow_up_interval_persists_to_store() = runTest(dispatcher) {
        val dao = DetectionSettingsDao(SettingsEntity(targetApps = "[]"))
        val followUp = TestFollowUpReminderStore()
        val viewModel = newViewModel(dao, TestCustomReturnAppStore(), followUp)
        runCurrent()

        viewModel.updateFollowUpInterval(15)
        runCurrent()

        assertEquals(15, followUp.minutes)
        assertEquals(15, viewModel.followUpInterval.value)
    }

    @Test
    fun toggling_keep_alive_updates_the_store() = runTest(dispatcher) {
        val dao = DetectionSettingsDao(SettingsEntity(targetApps = "[]"))
        val keepAlive = TestKeepAliveStore()
        val viewModel = newViewModel(
            dao,
            TestCustomReturnAppStore(),
            TestFollowUpReminderStore(),
            keepAlive
        )
        runCurrent()

        viewModel.setKeepAliveEnabled(false)
        runCurrent()

        assertEquals(false, keepAlive.enabled.value)
        assertEquals(false, viewModel.keepAliveEnabled.value)
    }

}

private class DetectionSettingsDao(initial: SettingsEntity) : SettingsDao {
    private val state = MutableStateFlow<SettingsEntity?>(initial)
    val current: SettingsEntity get() = checkNotNull(state.value)

    override suspend fun insertOrUpdate(settings: SettingsEntity) {
        state.value = settings
    }
    override fun getSettings(): Flow<SettingsEntity?> = state
    override suspend fun getSettingsOnce(): SettingsEntity? = state.value
    override suspend fun clearLegacyApiKey() {
        state.value = state.value?.copy(legacyApiKey = "")
    }
}

private class DetectionOpenAiApi : OpenAiApi {
    override suspend fun chatCompletion(
        authorization: String,
        request: ChatRequest
    ): Response<ChatResponse> = Response.success(ChatResponse())

    override suspend fun listModels(authorization: String): Response<ModelsResponse> =
        Response.success(ModelsResponse(data = listOf(ModelInfo("deepseek-v4-flash"))))
}

private class DetectionApiKeyStore(private val key: String) : ApiKeyStore {
    override suspend fun read(): String = key
    override suspend fun write(value: String) = Unit
    override suspend fun clear() = Unit
}

private class DetectionTaskDao : FocusTaskDao {
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

private class DetectionFakePermissionProvider : PermissionStatusProvider {
    override fun accessibilityEnabled(): Boolean = true
    override fun usageStatsGranted(): Boolean = true
    override fun notificationGranted(): Boolean = true
    override fun overlayGranted(): Boolean = true
}
