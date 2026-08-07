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
import com.example.focus_app.domain.model.AiProvider
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
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import retrofit2.Response

@OptIn(ExperimentalCoroutinesApi::class)
class SettingsViewModelAiConnectionTest {
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
    fun saving_connection_updates_endpoint_and_model_in_one_repository_write() = runTest(dispatcher) {
        val dao = CountingSettingsDao(
            SettingsEntity(targetApps = "[]", aiProvider = "deepseek")
        )
        val viewModel = newViewModel(dao)
        runCurrent()

        viewModel.updateAiConnection("https://custom.example/v1", "custom-model")
        runCurrent()

        assertEquals(1, dao.writeCount)
        assertEquals("https://custom.example/v1", dao.current.apiEndpoint)
        assertEquals("custom-model", dao.current.aiModel)
        assertEquals(AiProvider.CUSTOM.name.lowercase(), dao.current.aiProvider)
    }

    @Test
    fun testing_connection_reports_success_with_model_ids() = runTest(dispatcher) {
        val dao = CountingSettingsDao(SettingsEntity(targetApps = "[]", aiProvider = "deepseek"))
        val viewModel = newViewModel(
            dao,
            api = FakeOpenAiApi(
                onModels = {
                    Response.success(
                        ModelsResponse(
                            data = listOf(
                                ModelInfo("deepseek-v4-flash"),
                                ModelInfo("deepseek-chat")
                            )
                        )
                    )
                }
            )
        )
        runCurrent()
        assertEquals(AiConnectionUiState.Idle, viewModel.aiConnection.value)

        viewModel.testAiConnection()
        runCurrent()

        val state = viewModel.aiConnection.value
        assertTrue(state is AiConnectionUiState.Success)
        assertEquals(
            listOf("deepseek-v4-flash", "deepseek-chat"),
            (state as AiConnectionUiState.Success).modelIds
        )
    }

    @Test
    fun testing_connection_maps_unauthorized_to_invalid_key_error() = runTest(dispatcher) {
        val dao = CountingSettingsDao(SettingsEntity(targetApps = "[]", aiProvider = "deepseek"))
        val viewModel = newViewModel(
            dao,
            api = FakeOpenAiApi(
                onModels = {
                    Response.error(
                        401,
                        "unauthorized".toResponseBody("application/json".toMediaType())
                    )
                }
            )
        )
        runCurrent()

        viewModel.testAiConnection()
        runCurrent()

        val state = viewModel.aiConnection.value
        assertTrue(state is AiConnectionUiState.Error)
        assertEquals("API Key 无效或无权限", (state as AiConnectionUiState.Error).message)
    }

    @Test
    fun testing_connection_without_key_returns_friendly_error() = runTest(dispatcher) {
        val dao = CountingSettingsDao(SettingsEntity(targetApps = "[]", aiProvider = "deepseek"))
        val viewModel = newViewModel(dao, key = "")
        runCurrent()

        viewModel.testAiConnection()
        runCurrent()

        val state = viewModel.aiConnection.value
        assertTrue(state is AiConnectionUiState.Error)
        assertEquals("请先保存 API Key", (state as AiConnectionUiState.Error).message)
    }

    private fun newViewModel(
        dao: CountingSettingsDao,
        key: String = "sk-test",
        api: OpenAiApi = FakeOpenAiApi()
    ): SettingsViewModel {
        val aiRepository = AiRepository(FakeApiKeyStore(key)) { api }
        return SettingsViewModel(
            settingsRepository = SettingsRepository(dao),
            aiRepository = aiRepository,
            taskRepository = TaskRepository(EmptyTaskDao()),
            permissionStatusProvider = AiConnectionFakePermissionProvider()
        )
    }
}

private class CountingSettingsDao(initial: SettingsEntity) : SettingsDao {
    private val state = MutableStateFlow<SettingsEntity?>(initial)
    var writeCount = 0
    val current: SettingsEntity get() = checkNotNull(state.value)

    override suspend fun insertOrUpdate(settings: SettingsEntity) {
        writeCount++
        state.value = settings
    }

    override fun getSettings(): Flow<SettingsEntity?> = state
    override suspend fun getSettingsOnce(): SettingsEntity? = state.value
    override suspend fun clearLegacyApiKey() {
        state.value = state.value?.copy(legacyApiKey = "")
    }
}

private class FakeOpenAiApi(
    private val onModels: () -> Response<ModelsResponse> = {
        Response.success(ModelsResponse(data = listOf(ModelInfo("deepseek-v4-flash"))))
    }
) : OpenAiApi {
    override suspend fun chatCompletion(
        authorization: String,
        request: ChatRequest
    ): Response<ChatResponse> = Response.success(ChatResponse())

    override suspend fun listModels(authorization: String): Response<ModelsResponse> = onModels()
}

private class FakeApiKeyStore(private val key: String) : ApiKeyStore {
    override suspend fun read(): String = key
    override suspend fun write(value: String) = Unit
    override suspend fun clear() = Unit
}

private class EmptyTaskDao : FocusTaskDao {
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

private class AiConnectionFakePermissionProvider : PermissionStatusProvider {
    override fun accessibilityEnabled(): Boolean = true
    override fun usageStatsGranted(): Boolean = true
    override fun notificationGranted(): Boolean = true
    override fun overlayGranted(): Boolean = true
}
