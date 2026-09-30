package com.example.focus_app.data.diagnostics

import com.example.focus_app.data.repository.AppInfo
import com.example.focus_app.data.repository.AppSettings
import com.example.focus_app.data.repository.ReminderDisplayKind
import com.example.focus_app.data.repository.ReminderDisplayRepository
import com.example.focus_app.data.repository.ReminderDisplayResult
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FeedbackContextSourceTest {
    private class FakeSystem : FeedbackSystemStateSource {
        var failingFields: Set<String> = emptySet()
        var failure: Exception = IllegalStateException("unavailable")
        var width: Int? = 393
        var height: Int? = 851
        var scale: Float? = 1.3f
        var dark: Boolean? = true
        var battery: Boolean? = false

        private fun <T> read(name: String, value: T): T {
            if (name in failingFields) throw failure
            return value
        }

        override fun accessibilityEnabled() = read("accessibility", true)
        override fun notificationsEnabled() = read("notifications", false)
        override fun overlayAllowed() = read("overlay", true)
        override fun usageAccessAllowed() = read("usage", true)
        override fun batteryOptimizationsIgnored() = read("battery", battery)
        override fun screenWidthDp() = read("width", width)
        override fun screenHeightDp() = read("height", height)
        override fun fontScale() = read("font", scale)
        override fun systemDarkTheme() = read("dark", dark)
    }

    private class FakeDisplays : ReminderDisplayRepository {
        var failure: Exception? = null
        var count = 2
        val requestedSince = mutableListOf<Long>()
        override suspend fun countSince(since: Long): Int {
            requestedSince += since
            failure?.let { throw it }
            return count
        }

        override suspend fun recordDisplay(
            attemptId: String,
            sessionId: Long,
            displayedAt: Long,
            kind: ReminderDisplayKind,
            windowStart: Long,
            limit: Int
        ): ReminderDisplayResult = error("Diagnostics must never record a reminder")

        override suspend fun timesSince(since: Long): List<Long> =
            error("Diagnostics must never read individual reminder history")

        override suspend fun resetSince(since: Long): Unit =
            error("Diagnostics must never reset quota")
    }

    @Test
    fun `snapshot keeps independent permission values and counts only the configured window`() = runTest {
        val displays = FakeDisplays()
        val settings = AppSettings(
            targetApps = listOf(AppInfo("test.one", "One"), AppInfo("test.two", "Two")),
            reminderWindowMinutes = 15,
            maxRemindersPerWindow = 4
        )
        val result = DefaultFeedbackContextSource(FakeSystem(), displays).collect(settings, 2_000_000L)

        assertEquals(true, result.accessibilityEnabled)
        assertEquals(false, result.notificationsEnabled)
        assertEquals(true, result.overlayAllowed)
        assertEquals(true, result.usageAccessAllowed)
        assertEquals(false, result.batteryOptimizationsIgnored)
        assertEquals(2, result.targetAppCount)
        assertEquals(2, result.remindersInWindow)
        assertEquals(15, result.reminderWindowMinutes)
        assertEquals(4, result.reminderLimit)
        assertEquals(listOf(1_100_000L), displays.requestedSince)
        assertEquals(393, result.screenWidthDp)
        assertEquals(851, result.screenHeightDp)
        assertEquals(1.3f, result.fontScale)
        assertEquals(true, result.systemDarkTheme)
    }

    @Test
    fun `each failed system read stays unknown while other fields remain available`() = runTest {
        for (name in listOf("accessibility", "notifications", "overlay", "usage", "battery", "width", "height", "font", "dark")) {
            val system = FakeSystem().apply { failingFields = setOf(name) }
            val result = DefaultFeedbackContextSource(system, FakeDisplays()).collect(AppSettings(), 4_000_000L)
            val values = mapOf(
                "accessibility" to result.accessibilityEnabled,
                "notifications" to result.notificationsEnabled,
                "overlay" to result.overlayAllowed,
                "usage" to result.usageAccessAllowed,
                "battery" to result.batteryOptimizationsIgnored,
                "width" to result.screenWidthDp,
                "height" to result.screenHeightDp,
                "font" to result.fontScale,
                "dark" to result.systemDarkTheme
            )
            assertNull(name, values[name])
            assertEquals(name, 1, values.values.count { it == null })
            assertEquals(2, result.remindersInWindow)
        }
    }

    @Test
    fun `unavailable settings do not invent empty selection or a default quota window`() = runTest {
        val displays = FakeDisplays()
        val result = DefaultFeedbackContextSource(FakeSystem(), displays).collect(null, 4_000_000L)

        assertNull(result.targetAppCount)
        assertNull(result.remindersInWindow)
        assertNull(result.reminderWindowMinutes)
        assertNull(result.reminderLimit)
        assertEquals(true, result.accessibilityEnabled)
        assertEquals(393, result.screenWidthDp)
        assertTrue(displays.requestedSince.isEmpty())
    }

    @Test
    fun `quota read failure stays unknown and does not discard its settings or permissions`() = runTest {
        val displays = FakeDisplays().apply { failure = IllegalStateException("database unavailable") }
        val result = DefaultFeedbackContextSource(FakeSystem(), displays).collect(
            AppSettings(reminderWindowMinutes = 30, maxRemindersPerWindow = 5), 4_000_000L
        )

        assertNull(result.remindersInWindow)
        assertEquals(30, result.reminderWindowMinutes)
        assertEquals(5, result.reminderLimit)
        assertEquals(0, result.targetAppCount)
        assertEquals(true, result.usageAccessAllowed)
    }

    @Test
    fun `undefined display and permission values are not reported as normal or disabled`() = runTest {
        val system = FakeSystem().apply { width = 0; height = -1; scale = Float.NaN; dark = null; battery = null }
        val result = DefaultFeedbackContextSource(system, FakeDisplays()).collect(AppSettings(), 4_000_000L)
        assertNull(result.screenWidthDp)
        assertNull(result.screenHeightDp)
        assertNull(result.fontScale)
        assertNull(result.systemDarkTheme)
        assertNull(result.batteryOptimizationsIgnored)
    }

    @Test
    fun `invalid quota settings do not query a fabricated window`() = runTest {
        val displays = FakeDisplays()
        val result = DefaultFeedbackContextSource(FakeSystem(), displays).collect(
            AppSettings(reminderWindowMinutes = -1, maxRemindersPerWindow = -1), 4_000_000L
        )
        assertNull(result.reminderWindowMinutes)
        assertNull(result.reminderLimit)
        assertNull(result.remindersInWindow)
        assertTrue(displays.requestedSince.isEmpty())
    }

    @Test
    fun `system and database cancellation propagate instead of becoming partial snapshots`() = runTest {
        for (cancelSystem in listOf(true, false)) {
            val system = FakeSystem().apply {
                if (cancelSystem) {
                    failingFields = setOf("accessibility")
                    failure = CancellationException("cancelled")
                }
            }
            val displays = FakeDisplays().apply {
                if (!cancelSystem) failure = CancellationException("cancelled")
            }
            var caught: Throwable? = null
            try {
                DefaultFeedbackContextSource(system, displays).collect(AppSettings(), 4_000_000L)
            } catch (throwable: Throwable) {
                caught = throwable
            }
            assertTrue(caught is CancellationException)
        }
    }
}
