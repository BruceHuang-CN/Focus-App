package com.example.focus_app.service

import com.example.focus_app.data.repository.ReminderDisplayKind
import com.example.focus_app.domain.model.ReturnDestination
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PendingReminderRedisplayerTest {
    @Test
    fun target_foreground_replays_pending_reminder_once_with_a_new_attempt() {
        val registry = ReminderPresentationRegistry { 0L }
        val launcher = RecordingLauncher()
        val redisplayer = PendingReminderRedisplayer(
            registry = registry,
            launcher = launcher,
            attemptIdProvider = { "attempt-2" }
        )
        registry.show(FORCED_DATA)
        registry.onActivityStopped(FORCED_DATA.sessionId, FORCED_DATA.attemptId)

        assertFalse(redisplayer.onForegroundPackage("com.example.other"))
        assertTrue(redisplayer.onForegroundPackage(TARGET_PACKAGE))
        assertFalse(redisplayer.onForegroundPackage(TARGET_PACKAGE))

        val shown = launcher.shown.single()
        assertEquals("attempt-2", shown.attemptId)
        assertEquals(ReminderDisplayKind.FORCED_REDISPLAY, shown.displayKind)
    }

    @Test
    fun notification_only_result_restores_pending_state_for_a_later_retry() {
        val registry = ReminderPresentationRegistry { 0L }
        val launcher = RecordingLauncher(activityRequested = false)
        val attemptIds = ArrayDeque(listOf("attempt-2", "attempt-3"))
        val redisplayer = PendingReminderRedisplayer(
            registry = registry,
            launcher = launcher,
            attemptIdProvider = { attemptIds.removeFirst() }
        )
        registry.show(FORCED_DATA)
        registry.onActivityStopped(FORCED_DATA.sessionId, FORCED_DATA.attemptId)

        assertFalse(redisplayer.onForegroundPackage(TARGET_PACKAGE))
        assertTrue(registry.hasPendingDecision())

        launcher.activityRequested = true
        assertTrue(redisplayer.onForegroundPackage(TARGET_PACKAGE))
        assertEquals(listOf("attempt-2", "attempt-3"), launcher.shown.map { it.attemptId })
    }

    private class RecordingLauncher(
        var activityRequested: Boolean = true
    ) : ReminderLauncher {
        val shown = mutableListOf<ReminderLaunchData>()

        override fun show(data: ReminderLaunchData): Boolean {
            shown += data
            return activityRequested
        }

        override fun dismiss(sessionId: Long) = Unit
        override fun returnToFocus(taskId: Long?) = Unit
        override fun returnHome() = Unit
        override fun returnToCustom(packageName: String) = CustomReturnResult.SUCCESS
    }

    private companion object {
        const val TARGET_PACKAGE = "com.example.target"
        val FORCED_DATA = ReminderLaunchData(
            sessionId = 9L,
            taskId = null,
            taskTitle = null,
            appName = "Target",
            message = "Pause",
            showBreathing = false,
            returnDestination = ReturnDestination.FOCUS,
            targetPackageName = TARGET_PACKAGE,
            windowLimit = 3,
            windowMinutes = 30,
            forceReminder = true,
            attemptId = "attempt-1",
            displayKind = ReminderDisplayKind.INITIAL
        )
    }
}
