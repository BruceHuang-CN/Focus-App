package com.example.focus_app.ui.update

import androidx.lifecycle.viewModelScope
import com.example.focus_app.data.update.AppRelease
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.io.IOException

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class AppUpdateViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val viewModels = mutableListOf<AppUpdateViewModel>()
    private var time = 10_000L
    @Before fun setup() { Dispatchers.setMain(dispatcher) }
    @After fun cleanup() {
        viewModels.forEach { it.viewModelScope.cancel() }
        Dispatchers.resetMain()
    }
    private class Store : UpdateCheckStore { override var nextAutomaticCheckAt = 0L }
    private fun release(code: Long = 3, app: String = "com.example.focus_app", sdk: Int = 26) =
        AppRelease(app, code, "1.0.2", "https://brucehere.com/downloads/huishen/", "更新说明", sdk)
    private fun model(store: Store = Store(), fetch: suspend () -> AppRelease = { release() }) =
        AppUpdateViewModel(fetch, "com.example.focus_app", 2, 33, store) { time }.also { viewModels += it }

    @Test fun newer_code_prompts_even_if_the_display_name_is_unchanged() = runTest(dispatcher) {
        val model = model { release(3).copy(versionName = "1.0.1") }
        model.check(); runCurrent()
        assertEquals(3L, model.state.value.release?.versionCode)
        assertFalse(model.state.value.checking)
    }

    @Test fun same_and_older_versions_do_not_prompt_automatically() = runTest(dispatcher) {
        for (code in listOf(1L, 2L)) {
            val model = model { release(code) }
            model.check(); runCurrent()
            assertNull(model.state.value.release)
            assertNull(model.state.value.notice)
            model.check(manual = true); runCurrent()
            assertEquals(UpdateNotice.CURRENT, model.state.value.notice)
        }
    }

    @Test fun automatic_failure_is_quiet_and_manual_failure_is_visible() = runTest(dispatcher) {
        val model = model { throw IOException("offline or missing feed") }
        model.check(); runCurrent()
        assertEquals(AppUpdateUiState(), model.state.value)
        model.check(manual = true); runCurrent()
        assertEquals(UpdateNotice.UNAVAILABLE, model.state.value.notice)
    }

    @Test fun dismissing_persists_the_cooldown_but_manual_check_bypasses_it() = runTest(dispatcher) {
        val store = Store()
        var calls = 0
        val first = model(store) { calls++; release() }
        first.check(); runCurrent(); first.dismiss()
        val restarted = model(store) { calls++; release() }
        restarted.check(); runCurrent()
        assertEquals(1, calls)
        restarted.check(manual = true); runCurrent()
        assertEquals(2, calls)
        assertNotNull(restarted.state.value.release)
    }

    @Test fun automatic_check_retries_after_the_failure_backoff() = runTest(dispatcher) {
        var calls = 0
        val model = model { calls++; throw IOException() }
        model.check(); runCurrent()
        model.check(); runCurrent()
        assertEquals(1, calls)
        time += AppUpdateViewModel.FAILURE_RETRY_MS + 1
        model.check(); runCurrent()
        assertEquals(2, calls)
    }

    @Test fun repeated_actions_share_one_in_flight_check() = runTest(dispatcher) {
        val pending = CompletableDeferred<AppRelease>()
        var calls = 0
        val model = model { calls++; pending.await() }
        model.check(); model.check(manual = true); runCurrent()
        assertTrue(model.state.value.checking)
        assertEquals(1, calls)
        pending.complete(release()); runCurrent()
        assertNotNull(model.state.value.release)
    }

    @Test fun another_app_manifest_is_never_presented_as_a_valid_update() = runTest(dispatcher) {
        val model = model { release(app = "com.example.other") }
        model.check(manual = true); runCurrent()
        assertNull(model.state.value.release)
        assertEquals(UpdateNotice.UNAVAILABLE, model.state.value.notice)
    }

    @Test fun incompatible_android_version_gets_an_explanation_only_when_requested() = runTest(dispatcher) {
        val model = model { release(sdk = 35) }
        model.check(); runCurrent()
        assertEquals(AppUpdateUiState(), model.state.value)
        model.check(manual = true); runCurrent()
        assertEquals(UpdateNotice.UNSUPPORTED, model.state.value.notice)
    }

    @Test fun cancellation_is_not_reported_as_a_failed_check() = runTest(dispatcher) {
        val pending = CompletableDeferred<AppRelease>()
        val store = Store()
        val model = model(store) { pending.await() }
        model.check(manual = true); runCurrent()
        model.viewModelScope.cancel(); runCurrent()
        assertEquals(AppUpdateUiState(), model.state.value)
        assertEquals(0L, store.nextAutomaticCheckAt)
    }

    @Test fun clock_rollback_does_not_suppress_checks_forever() = runTest(dispatcher) {
        val store = Store().apply { nextAutomaticCheckAt = Long.MAX_VALUE / 2 }
        val model = model(store)
        model.check(); runCurrent()
        assertNotNull(model.state.value.release)
    }
}
