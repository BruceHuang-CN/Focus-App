package com.example.focus_app.ui.settings

import com.example.focus_app.data.local.dao.SettingsDao
import com.example.focus_app.data.local.entity.SettingsEntity
import com.example.focus_app.data.repository.SettingsRepository
import com.example.focus_app.domain.model.AiProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

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
        val viewModel = SettingsViewModel(SettingsRepository(dao))
        runCurrent()

        viewModel.updateAiConnection("https://custom.example/v1", "custom-model")
        runCurrent()

        assertEquals(1, dao.writeCount)
        assertEquals("https://custom.example/v1", dao.current.apiEndpoint)
        assertEquals("custom-model", dao.current.aiModel)
        assertEquals(AiProvider.CUSTOM.name.lowercase(), dao.current.aiProvider)
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
