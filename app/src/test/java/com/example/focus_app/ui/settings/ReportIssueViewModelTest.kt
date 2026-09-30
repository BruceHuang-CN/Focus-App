package com.example.focus_app.ui.settings

import androidx.lifecycle.SavedStateHandle
import com.example.focus_app.data.diagnostics.DiagnosticSnapshotSource
import com.example.focus_app.data.feedback.FeedbackDraftStore
import com.example.focus_app.data.feedback.PendingFeedback
import com.example.focus_app.data.remote.FeedbackApi
import com.example.focus_app.data.repository.FeedbackRepository
import com.example.focus_app.domain.feedback.AccessibilityDiagnosticsSnapshot
import com.example.focus_app.domain.feedback.AppFeedbackRequest
import com.example.focus_app.domain.feedback.AppFeedbackResponse
import com.example.focus_app.domain.feedback.FeedbackContextDiagnostics
import com.example.focus_app.domain.feedback.DiagnosticLevel
import com.example.focus_app.domain.feedback.DiagnosticSnapshot
import com.example.focus_app.domain.feedback.DiagnosticTime
import com.example.focus_app.domain.feedback.DeviceDiagnostics
import com.example.focus_app.domain.feedback.SettingsDiagnostics
import com.example.focus_app.domain.feedback.buildAppFeedbackRequest
import com.example.focus_app.domain.feedback.testIssueReportTexts
import com.example.focus_app.domain.model.DetectionMode
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import retrofit2.Response

@OptIn(ExperimentalCoroutinesApi::class)
class ReportIssueViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private val texts = testIssueReportTexts()

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private class CountingSource(private val value: DiagnosticSnapshot) : DiagnosticSnapshotSource {
        var calls = 0
            private set

        override suspend fun collect(): DiagnosticSnapshot {
            calls++
            return value
        }
    }

    private class FakeApi : FeedbackApi {
        val requests = mutableListOf<AppFeedbackRequest>()
        var responder: (AppFeedbackRequest) -> Response<AppFeedbackResponse> =
            { Response.success(AppFeedbackResponse(ok = true, receipt = it.id)) }

        override suspend fun submit(payload: AppFeedbackRequest): Response<AppFeedbackResponse> {
            requests += payload
            return responder(payload)
        }
    }

    private class FakeDraftStore : FeedbackDraftStore {
        var pending: PendingFeedback? = null

        override suspend fun save(pending: PendingFeedback) {
            this.pending = pending
        }

        override suspend fun load(): PendingFeedback? = pending

        override suspend fun clear() {
            pending = null
        }
    }

    private fun snapshot(tag: String): DiagnosticSnapshot = DiagnosticSnapshot(
        device = DeviceDiagnostics(
            manufacturer = tag,
            model = "model-$tag",
            androidVersion = "14",
            apiLevel = 34,
            versionName = "1.0.0",
            versionCode = 1L,
            systemLanguage = "zh-CN"
        ),
        accessibility = AccessibilityDiagnosticsSnapshot(
            serviceBound = true,
            lastConnectedAt = DiagnosticTime.Recorded(1_700_000_000_000L),
            lastDestroyedAt = DiagnosticTime.NoRecord,
            lastInterruptedAt = DiagnosticTime.Unavailable
        ),
        settings = SettingsDiagnostics(
            guardianEnabled = true,
            detectionMode = DetectionMode.REALTIME,
            reminderDelaySeconds = 10,
            reminderWindowMinutes = 60,
            maxRemindersPerWindow = 3
        ),
        collectedAtMillis = 1_700_000_300_000L
    )

    private fun errorResponse(code: Int): Response<AppFeedbackResponse> =
        Response.error(code, "{}".toResponseBody("application/json".toMediaType()))

    private fun viewModel(
        source: DiagnosticSnapshotSource = CountingSource(snapshot("A")),
        api: FakeApi = FakeApi(),
        drafts: FakeDraftStore = FakeDraftStore(),
        handle: SavedStateHandle = SavedStateHandle()
    ) = ReportIssueViewModel(source, FeedbackRepository(api), drafts, handle)

    @Test
    fun `blank description never submits`() = runTest(dispatcher) {
        val api = FakeApi()
        val vm = viewModel(api = api)

        vm.updateProblem("   ")
        vm.submit(texts)
        advanceUntilIdle()

        assertEquals(0, api.requests.size)
        assertTrue(vm.state.value.showProblemError)
    }

    @Test
    fun `successful submission clears the draft and shows the receipt`() = runTest(dispatcher) {
        val api = FakeApi()
        val drafts = FakeDraftStore()
        val vm = viewModel(api = api, drafts = drafts)

        vm.updateProblem("没有提醒")
        vm.submit(texts)
        advanceUntilIdle()

        assertEquals(1, api.requests.size)
        assertEquals(api.requests[0].id, vm.state.value.receipt)
        assertNull(drafts.pending)
        assertNull(vm.state.value.prepared)
        assertFalse(vm.state.value.restoredPending)
    }

    @Test
    fun `failed submission keeps the same request for retry`() = runTest(dispatcher) {
        val api = FakeApi().apply { responder = { errorResponse(503) } }
        val drafts = FakeDraftStore()
        val vm = viewModel(api = api, drafts = drafts)

        vm.updateProblem("没有提醒")
        vm.submit(texts)
        advanceUntilIdle()

        val firstId = api.requests.single().id
        assertEquals(SubmitError.Unavailable, vm.state.value.submitError)
        assertNotNull(vm.state.value.prepared)
        assertEquals(firstId, drafts.pending?.payload?.id)

        api.responder = { Response.success(AppFeedbackResponse(ok = true, receipt = it.id)) }
        vm.submit(texts)
        advanceUntilIdle()

        assertEquals(2, api.requests.size)
        // 同一份内容重试沿用同一个 id。
        assertEquals(firstId, api.requests[1].id)
        assertEquals(firstId, vm.state.value.receipt)
        assertNull(drafts.pending)
    }

    @Test
    fun `editing after a failure creates a new request id`() = runTest(dispatcher) {
        val api = FakeApi().apply { responder = { errorResponse(503) } }
        val vm = viewModel(api = api)

        vm.updateProblem("没有提醒")
        vm.submit(texts)
        advanceUntilIdle()
        val firstId = api.requests.single().id

        api.responder = { Response.success(AppFeedbackResponse(ok = true, receipt = it.id)) }
        vm.updateProblem("没有提醒，补充说明")
        vm.submit(texts)
        advanceUntilIdle()

        assertNotEquals(firstId, api.requests[1].id)
    }

    @Test
    fun `rate limit surfaces the wait and keeps the draft`() = runTest(dispatcher) {
        val api = FakeApi().apply { responder = { errorResponse(429) } }
        val drafts = FakeDraftStore()
        val vm = viewModel(api = api, drafts = drafts)

        vm.updateProblem("没有提醒")
        vm.submit(texts)
        advanceUntilIdle()

        assertEquals(SubmitError.RateLimited(60), vm.state.value.submitError)
        assertNotNull(drafts.pending)
        assertNull(vm.state.value.receipt)
    }

    @Test
    fun `preview and submit send the exact same frozen payload`() = runTest(dispatcher) {
        val api = FakeApi()
        val vm = viewModel(api = api)

        vm.updateProblem("没有提醒")
        vm.previewSubmission(texts)
        advanceUntilIdle()
        val previewedId = vm.state.value.prepared?.id
        assertNotNull(previewedId)

        vm.submit(texts)
        advanceUntilIdle()

        assertEquals(1, api.requests.size)
        assertEquals(previewedId, api.requests[0].id)
    }

    @Test
    fun `none level sends no diagnostics even after a snapshot was collected`() = runTest(dispatcher) {
        val source = CountingSource(snapshot("SENTINEL"))
        val api = FakeApi()
        val vm = viewModel(source = source, api = api)

        vm.updateProblem("没有提醒")
        vm.showDiagnostics(texts)
        advanceUntilIdle()
        assertNotNull(vm.state.value.snapshot)

        vm.setDiagnosticLevel(DiagnosticLevel.NONE)
        assertNull(vm.state.value.snapshot)

        vm.submit(texts)
        advanceUntilIdle()

        val request = api.requests.single()
        assertNull(request.diagnostics)
        assertEquals("none", request.diagnosticLevel)
    }

    @Test
    fun `turning diagnostics off invalidates preview and snapshot`() = runTest(dispatcher) {
        val vm = viewModel()

        vm.updateProblem("没有提醒")
        vm.previewSubmission(texts)
        advanceUntilIdle()
        assertNotNull(vm.state.value.prepared)

        vm.setDiagnosticLevel(DiagnosticLevel.NONE)

        assertNull(vm.state.value.prepared)
        assertNull(vm.state.value.snapshot)
        assertNull(vm.state.value.diagnosticsText)
    }

    @Test
    fun `late diagnostics result is not attached after the level changes`() = runTest(dispatcher) {
        val gate = CompletableDeferred<DiagnosticSnapshot>()
        val source = object : DiagnosticSnapshotSource {
            override suspend fun collect(): DiagnosticSnapshot = gate.await()
        }
        val vm = viewModel(source = source)

        vm.updateProblem("没有提醒")
        vm.previewSubmission(texts)
        advanceUntilIdle()
        assertTrue(vm.state.value.isCollecting)

        vm.setDiagnosticLevel(DiagnosticLevel.NONE)
        gate.complete(snapshot("LATE"))
        advanceUntilIdle()

        assertNull(vm.state.value.snapshot)
        assertNull(vm.state.value.prepared)
        assertFalse(vm.state.value.isCollecting)
    }

    @Test
    fun `restored pending keeps the same id and is offered for manual retry`() = runTest(dispatcher) {
        val pendingPayload = buildAppFeedbackRequest(
            id = "restored-id",
            problem = "上次的问题",
            steps = "",
            expected = "",
            level = DiagnosticLevel.BASIC,
            snapshot = snapshot("R")
        )
        val drafts = FakeDraftStore().apply {
            pending = PendingFeedback(displayBody = "RESTORED BODY", payload = pendingPayload)
        }
        val api = FakeApi()
        val vm = viewModel(api = api, drafts = drafts)
        advanceUntilIdle()

        assertTrue(vm.state.value.restoredPending)
        assertEquals("上次的问题", vm.state.value.problem)
        assertEquals("restored-id", vm.state.value.prepared?.id)

        vm.submit(texts)
        advanceUntilIdle()

        assertEquals("restored-id", api.requests.single().id)
    }

    @Test
    fun `start new report clears the form and the stored draft`() = runTest(dispatcher) {
        val drafts = FakeDraftStore()
        val vm = viewModel(drafts = drafts)

        vm.updateProblem("没有提醒")
        vm.submit(texts)
        advanceUntilIdle()
        assertNotNull(vm.state.value.receipt)

        vm.startNewReport()
        advanceUntilIdle()

        assertEquals("", vm.state.value.problem)
        assertNull(vm.state.value.receipt)
        assertNull(drafts.pending)
    }

    @Test
    fun `draft text and level survive the saved state`() = runTest(dispatcher) {
        val handle = SavedStateHandle()
        val first = viewModel(handle = handle)
        first.updateProblem("草稿内容")
        first.updateSteps("复现步骤")
        first.setStepsExpanded(true)
        first.setDiagnosticLevel(DiagnosticLevel.NONE)
        advanceUntilIdle()

        val restored = viewModel(handle = handle)
        advanceUntilIdle()

        assertEquals("草稿内容", restored.state.value.problem)
        assertEquals("复现步骤", restored.state.value.steps)
        assertTrue(restored.state.value.stepsExpanded)
        assertEquals(DiagnosticLevel.NONE, restored.state.value.diagnosticLevel)
    }

    @Test
    fun `editing to an oversized report during collection never uploads`() = runTest(dispatcher) {
        val collecting = CompletableDeferred<DiagnosticSnapshot>()
        val api = FakeApi()
        val vm = viewModel(source = DiagnosticSnapshotSource { collecting.await() }, api = api)
        vm.updateProblem("没有提醒")
        vm.submit(texts)
        runCurrent()
        vm.updateSteps("字".repeat(ISSUE_REPORT_MAX_FIELD_LENGTH + 1))
        collecting.complete(snapshot("A"))
        advanceUntilIdle()
        assertTrue(api.requests.isEmpty())
        assertNull(vm.state.value.prepared)
    }

    private fun contextSnapshot() = snapshot("CONTEXT").copy(context = FeedbackContextDiagnostics(
        accessibilityEnabled = true, targetAppCount = 0, remindersInWindow = 3,
        reminderWindowMinutes = 60, reminderLimit = 3, fontScale = 1.5f
    ))

    @Test
    fun `automatic context is visible and sent through existing fields only`() = runTest(dispatcher) {
        val api = FakeApi()
        val vm = viewModel(source = CountingSource(contextSnapshot()), api = api)
        vm.updateProblem("没有提醒")
        vm.updateSteps("我的操作\n没有改写")
        vm.previewSubmission(texts)
        advanceUntilIdle()
        val preview = vm.state.value.prepared!!
        assertTrue(preview.displayBody.contains("Selected guardian app count:0"))
        assertTrue(preview.payload.steps.startsWith("我的操作\n没有改写\n\n"))
        assertTrue(preview.payload.steps.contains("Selected guardian app count:0"))
        val json = com.google.gson.JsonParser.parseString(com.google.gson.Gson().toJson(preview.payload)).asJsonObject
        assertEquals(setOf("schemaVersion", "id", "problem", "steps", "expected", "diagnosticLevel", "diagnostics"), json.keySet())
        assertFalse(json.getAsJsonObject("diagnostics").has("context"))
        vm.submit(texts)
        advanceUntilIdle()
        assertEquals(preview.payload, api.requests.single())
    }

    @Test
    fun `none removes automatic context from text as well as diagnostics`() = runTest(dispatcher) {
        val api = FakeApi()
        val vm = viewModel(source = CountingSource(contextSnapshot()), api = api)
        vm.updateProblem("没有提醒")
        vm.updateSteps("用户步骤")
        vm.previewSubmission(texts)
        advanceUntilIdle()
        vm.setDiagnosticLevel(DiagnosticLevel.NONE)
        vm.submit(texts)
        advanceUntilIdle()
        assertEquals("用户步骤", api.requests.single().steps)
        assertNull(api.requests.single().diagnostics)
    }

    @Test
    fun `combined diagnostic length is checked without truncating original text`() = runTest(dispatcher) {
        val api = FakeApi()
        val vm = viewModel(source = CountingSource(contextSnapshot()), api = api)
        val original = "字".repeat(ISSUE_REPORT_MAX_FIELD_LENGTH)
        vm.updateProblem("没有提醒")
        vm.updateSteps(original)
        vm.submit(texts)
        advanceUntilIdle()
        assertTrue(api.requests.isEmpty())
        assertTrue(vm.state.value.showCombinedLengthError)
        assertEquals(original, vm.state.value.steps)
        vm.setDiagnosticLevel(DiagnosticLevel.NONE)
        vm.submit(texts)
        advanceUntilIdle()
        assertEquals(original, api.requests.single().steps)
    }

    @Test
    fun `pending context restores raw input and can be removed without duplication`() = runTest(dispatcher) {
        val api = FakeApi().apply { responder = { errorResponse(503) } }
        val drafts = FakeDraftStore()
        val vm = viewModel(source = CountingSource(contextSnapshot()), api = api, drafts = drafts)
        vm.updateProblem("没有提醒")
        vm.updateSteps("原始操作")
        vm.submit(texts)
        advanceUntilIdle()
        val originalId = api.requests.single().id
        val restored = viewModel(source = CountingSource(contextSnapshot()), api = api, drafts = drafts)
        advanceUntilIdle()
        assertEquals("原始操作", restored.state.value.steps)
        assertEquals(originalId, restored.state.value.prepared!!.id)
        restored.submit(texts)
        advanceUntilIdle()
        assertEquals(api.requests[0], api.requests[1])
        restored.setDiagnosticLevel(DiagnosticLevel.NONE)
        restored.submit(texts)
        advanceUntilIdle()
        assertEquals("原始操作", api.requests.last().steps)
        assertNull(api.requests.last().diagnostics)
        assertNotEquals(originalId, api.requests.last().id)
    }

    @Test
    fun `opting out without resubmitting cannot resurrect pending diagnostics`() = runTest(dispatcher) {
        val api = FakeApi().apply { responder = { errorResponse(503) } }
        val drafts = FakeDraftStore()
        val handle = SavedStateHandle()
        val vm = viewModel(source = CountingSource(contextSnapshot()), api = api, drafts = drafts, handle = handle)
        vm.updateProblem("没有提醒")
        vm.updateSteps("用户输入")
        vm.submit(texts)
        advanceUntilIdle()
        assertNotNull(drafts.pending)
        vm.setDiagnosticLevel(DiagnosticLevel.NONE)
        advanceUntilIdle()
        val restored = viewModel(api = api, drafts = drafts, handle = handle)
        advanceUntilIdle()
        assertEquals(DiagnosticLevel.NONE, restored.state.value.diagnosticLevel)
        assertNull(restored.state.value.snapshot)
        assertEquals("用户输入", restored.state.value.steps)
        restored.submit(texts)
        advanceUntilIdle()
        assertEquals("用户输入", api.requests.last().steps)
        assertNull(api.requests.last().diagnostics)
    }

    @Test
    fun `late pending restore cannot overwrite a new description or opt out`() = runTest(dispatcher) {
        val gate = CompletableDeferred<PendingFeedback?>()
        val drafts = object : FeedbackDraftStore {
            override suspend fun load(): PendingFeedback? = gate.await()
            override suspend fun save(pending: PendingFeedback) = Unit
            override suspend fun clear() = Unit
        }
        val vm = ReportIssueViewModel(CountingSource(contextSnapshot()), FeedbackRepository(FakeApi()), drafts, SavedStateHandle())
        runCurrent()
        vm.updateProblem("新输入")
        vm.setDiagnosticLevel(DiagnosticLevel.NONE)
        gate.complete(PendingFeedback("旧正文", buildAppFeedbackRequest(
            "old-id", "旧输入", "", "", DiagnosticLevel.BASIC, contextSnapshot()
        )))
        advanceUntilIdle()
        assertEquals("新输入", vm.state.value.problem)
        assertEquals(DiagnosticLevel.NONE, vm.state.value.diagnosticLevel)
        assertNull(vm.state.value.prepared)
    }

    @Test
    fun `immediate state saving preserves a pending report without a saved privacy choice`() = runTest(dispatcher) {
        Dispatchers.setMain(kotlinx.coroutines.test.UnconfinedTestDispatcher(testScheduler))
        for (level in listOf(DiagnosticLevel.NONE, DiagnosticLevel.DETAILED)) {
            val payload = buildAppFeedbackRequest("pending-${level.name}", "未发送的反馈", "步骤", "预期", level,
                if (level == DiagnosticLevel.NONE) null else contextSnapshot())
            val drafts = FakeDraftStore().apply { pending = PendingFeedback("BODY", payload) }
            val vm = viewModel(drafts = drafts)
            assertEquals(level, vm.state.value.diagnosticLevel)
            assertEquals(payload.id, vm.state.value.prepared?.id)
            assertEquals("未发送的反馈", vm.state.value.problem)
            assertNotNull(drafts.pending)
        }
    }
}
