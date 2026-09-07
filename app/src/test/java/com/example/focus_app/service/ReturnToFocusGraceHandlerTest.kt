package com.example.focus_app.service

import com.example.focus_app.data.repository.ReminderDisplayKind
import com.example.focus_app.domain.model.AppUsageSession
import com.example.focus_app.domain.model.ReturnDestination
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReturnToFocusGraceHandlerTest {
    @Test
    fun matching_reentry_within_30_seconds_shows_an_immediate_uncounted_reminder() {
        var now = 1_000L
        val launcher = GraceRecordingLauncher()
        val handler = ReturnToFocusGraceHandler(
            launcher = launcher,
            attemptIdProvider = { "grace-attempt" },
            elapsedRealtimeProvider = { now }
        )
        handler.start(TEMPLATE)

        now += 29_999L
        val handled = handler.showIfActive(newSession(id = 12L, packageName = TARGET_PACKAGE))

        assertTrue(handled)
        val shown = launcher.shown.single()
        assertEquals(12L, shown.sessionId)
        assertEquals("grace-attempt", shown.attemptId)
        assertEquals(ReminderDisplayKind.FORCED_REDISPLAY, shown.displayKind)
        assertTrue(shown.forceReminder)
        assertTrue(shown.message.contains("30 秒都没撑住"))
        assertTrue(shown.message.contains(TEMPLATE.message))
    }

    @Test
    fun expired_grace_uses_the_normal_new_session_flow() {
        var now = 1_000L
        val launcher = GraceRecordingLauncher()
        val handler = ReturnToFocusGraceHandler(
            launcher = launcher,
            attemptIdProvider = { "unused" },
            elapsedRealtimeProvider = { now }
        )
        handler.start(TEMPLATE)

        now += 30_000L

        assertFalse(handler.showIfActive(newSession(id = 12L, packageName = TARGET_PACKAGE)))
        assertTrue(launcher.shown.isEmpty())
    }

    @Test
    fun another_target_does_not_consume_the_original_apps_grace() {
        val launcher = GraceRecordingLauncher()
        val handler = ReturnToFocusGraceHandler(
            launcher = launcher,
            attemptIdProvider = { "grace-attempt" },
            elapsedRealtimeProvider = { 1_000L }
        )
        handler.start(TEMPLATE)

        assertFalse(handler.showIfActive(newSession(id = 12L, packageName = "com.xingin.xhs")))
        assertTrue(handler.showIfActive(newSession(id = 13L, packageName = TARGET_PACKAGE)))
        assertEquals(listOf(13L), launcher.shown.map { it.sessionId })
    }

    private fun newSession(id: Long, packageName: String) = AppUsageSession(
        id = id,
        packageName = packageName,
        appName = "Douyin",
        startedAt = 2_000L,
        taskId = 42L,
        toneKey = "sharp"
    )

    private companion object {
        const val TARGET_PACKAGE = "com.ss.android.ugc.aweme"
        val TEMPLATE = ReminderLaunchData(
            sessionId = 9L,
            taskId = 42L,
            taskTitle = "Write proposal",
            appName = "Douyin",
            message = "AI generated sharp reminder",
            showBreathing = false,
            returnDestination = ReturnDestination.FOCUS,
            targetPackageName = TARGET_PACKAGE,
            windowReminderCount = 1,
            windowLimit = 3,
            windowMinutes = 60,
            attemptId = "original-attempt"
        )
    }
}

private class GraceRecordingLauncher : ReminderLauncher {
    val shown = mutableListOf<ReminderLaunchData>()

    override fun show(data: ReminderLaunchData): Boolean {
        shown += data
        return true
    }

    override fun dismiss(sessionId: Long) = Unit
    override fun returnToFocus(taskId: Long?) = Unit
    override fun returnHome() = Unit
    override fun returnToCustom(packageName: String) = CustomReturnResult.SUCCESS
}
