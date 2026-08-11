package com.example.focus_app.ui.reminder

import com.example.focus_app.data.repository.AppSessionRepository
import com.example.focus_app.domain.model.AppUsageSession
import com.example.focus_app.domain.model.ReminderTone
import com.example.focus_app.domain.model.ReturnDestination
import com.example.focus_app.service.ReminderLaunchData
import com.example.focus_app.service.ReminderLauncher
import com.example.focus_app.service.ReminderPresentationRegistry
import com.example.focus_app.service.SessionReminderScheduler
import com.example.focus_app.service.CustomReturnResult
import com.example.focus_app.ui.settings.TestFollowUpReminderStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ReminderViewModelTest {
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
    fun init_uses_launch_data_without_starting_a_network_request() {
        val fixture = fixture()

        fixture.viewModel.init(LAUNCH_DATA)

        assertEquals("Put the phone down and write the proposal.", fixture.viewModel.uiState.value.message)
        assertEquals("Write proposal", fixture.viewModel.uiState.value.taskTitle)
        assertEquals("Douyin", fixture.viewModel.uiState.value.appName)
        assertEquals(false, fixture.viewModel.uiState.value.showBreathing)
        assertEquals(ReturnDestination.FOCUS, fixture.viewModel.uiState.value.returnDestination)
    }

    @Test
    fun return_to_focus_records_the_action_then_opens_the_captured_task() = runTest(dispatcher) {
        val fixture = fixture()
        fixture.viewModel.init(LAUNCH_DATA)

        fixture.viewModel.returnToFocus(LAUNCH_DATA.sessionId)
        advanceUntilIdle()

        assertEquals("returned_to_focus", fixture.repository.action)
        assertEquals(42L, fixture.launcher.focusTaskId)
        assertEquals(listOf("saved", "focus"), fixture.events)
    }

    @Test
    fun return_home_records_the_action_then_requests_accessibility_home() = runTest(dispatcher) {
        val fixture = fixture()
        fixture.viewModel.init(LAUNCH_DATA.copy(returnDestination = ReturnDestination.HOME))

        fixture.viewModel.returnHome(LAUNCH_DATA.sessionId)
        advanceUntilIdle()

        assertEquals("returned_home", fixture.repository.action)
        assertEquals(1, fixture.launcher.homeRequests)
        assertEquals(listOf("saved", "home"), fixture.events)
    }

    @Test
    fun continue_target_app_only_records_the_choice() = runTest(dispatcher) {
        val fixture = fixture()
        fixture.viewModel.init(LAUNCH_DATA)

        fixture.viewModel.continueTargetApp(LAUNCH_DATA.sessionId)
        advanceUntilIdle()

        assertEquals("continued", fixture.repository.action)
        assertEquals(LAUNCH_DATA.sessionId, fixture.scheduler.followUpSessionId)
        assertEquals(600_000L, fixture.scheduler.followUpDelay)
        assertEquals(null, fixture.launcher.focusTaskId)
        assertEquals(0, fixture.launcher.homeRequests)
    }

    @Test
    fun return_to_custom_records_action_and_opens_package() = runTest(dispatcher) {
        val fixture = fixture()
        fixture.viewModel.init(
            LAUNCH_DATA.copy(
                returnDestination = ReturnDestination.CUSTOM,
                returnPackageName = "com.tencent.mm"
            )
        )

        fixture.viewModel.returnToCustom(LAUNCH_DATA.sessionId)
        advanceUntilIdle()

        assertEquals("returned_to_custom", fixture.repository.action)
        assertEquals("com.tencent.mm", fixture.launcher.customPackage)
        assertEquals(listOf("custom", "saved"), fixture.events)
    }

    @Test
    fun failed_custom_return_keeps_the_reminder_open_and_does_not_record_an_exit() = runTest(dispatcher) {
        val fixture = fixture()
        fixture.presentationRegistry.show(LAUNCH_DATA.sessionId)
        fixture.launcher.customLaunchSucceeds = false
        fixture.viewModel.init(
            LAUNCH_DATA.copy(
                returnDestination = ReturnDestination.CUSTOM,
                returnPackageName = "com.tencent.mm"
            )
        )

        var completed = false
        fixture.viewModel.returnToCustom(LAUNCH_DATA.sessionId) { completed = true }
        advanceUntilIdle()

        assertEquals(null, fixture.repository.action)
        assertEquals(false, completed)
        assertEquals(true, fixture.presentationRegistry.isShowing())
        assertEquals("指定应用不可用，请重新选择", fixture.viewModel.uiState.value.customReturnError)
    }

    @Test
    fun explicit_reminder_choice_clears_the_pending_presentation_first() = runTest(dispatcher) {
        val fixture = fixture()
        fixture.presentationRegistry.show(LAUNCH_DATA.sessionId)
        fixture.viewModel.init(LAUNCH_DATA)

        fixture.viewModel.continueTargetApp(LAUNCH_DATA.sessionId)
        advanceUntilIdle()

        assertEquals(false, fixture.presentationRegistry.isShowing())
    }

    private fun fixture(): Fixture {
        val events = mutableListOf<String>()
        val repository = ActionRecordingSessionRepository(events)
        val launcher = ActionRecordingLauncher(events)
        val scheduler = ActionRecordingScheduler()
        val store = TestFollowUpReminderStore().apply { minutes = 10 }
        val presentationRegistry = ReminderPresentationRegistry()
        return Fixture(
            ReminderViewModel(repository, launcher, scheduler, store, presentationRegistry),
            repository,
            launcher,
            scheduler,
            presentationRegistry,
            events
        )
    }

    private data class Fixture(
        val viewModel: ReminderViewModel,
        val repository: ActionRecordingSessionRepository,
        val launcher: ActionRecordingLauncher,
        val scheduler: ActionRecordingScheduler,
        val presentationRegistry: ReminderPresentationRegistry,
        val events: List<String>
    )

    private companion object {
        val LAUNCH_DATA = ReminderLaunchData(
            sessionId = 9L,
            taskId = 42L,
            taskTitle = "Write proposal",
            appName = "Douyin",
            message = "Put the phone down and write the proposal.",
            showBreathing = false,
            returnDestination = ReturnDestination.FOCUS
        )
    }
}

private class ActionRecordingScheduler : SessionReminderScheduler {
    var followUpSessionId: Long? = null
    var followUpDelay: Long = -1L

    override fun onSessionStarted(
        session: AppUsageSession,
        appStillForeground: suspend () -> Boolean
    ) = Unit

    override fun cancel(sessionId: Long) = Unit

    override fun scheduleFollowUp(sessionId: Long, delayMillis: Long) {
        followUpSessionId = sessionId
        followUpDelay = delayMillis
    }
}

private class ActionRecordingLauncher(
    private val events: MutableList<String>
) : ReminderLauncher {
    var focusTaskId: Long? = null
    var homeRequests = 0
    var customPackage: String? = null
    var customLaunchSucceeds = true

    override fun show(data: ReminderLaunchData) = Unit

    override fun dismiss(sessionId: Long) = Unit

    override fun returnToFocus(taskId: Long?) {
        focusTaskId = taskId
        events += "focus"
    }

    override fun returnHome() {
        homeRequests++
        events += "home"
    }

    override fun returnToCustom(packageName: String): CustomReturnResult {
        customPackage = packageName
        return if (customLaunchSucceeds) {
            events += "custom"
            CustomReturnResult.SUCCESS
        } else {
            CustomReturnResult.APP_UNAVAILABLE
        }
    }
}

private class ActionRecordingSessionRepository(
    private val events: MutableList<String>
) : AppSessionRepository {
    var action: String? = null

    override suspend fun openSession(
        packageName: String,
        appName: String,
        startedAt: Long,
        taskId: Long?,
        toneKey: String
    ): AppUsageSession = error("Not used")

    override suspend fun closeSession(sessionId: Long, endedAt: Long) = Unit
    override suspend fun currentOpenSession(): AppUsageSession? = null
    override suspend fun reminderTimesSince(since: Long): List<Long> = emptyList()
    override suspend fun markRemindedIfNeeded(sessionId: Long, remindedAt: Long): Boolean = false

    override suspend fun markUserAction(sessionId: Long, action: String) {
        this.action = action
        events += "saved"
    }
}
