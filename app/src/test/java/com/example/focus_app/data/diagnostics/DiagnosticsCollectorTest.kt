package com.example.focus_app.data.diagnostics

import com.example.focus_app.data.local.dao.SettingsDao
import com.example.focus_app.data.local.entity.SettingsEntity
import com.example.focus_app.data.repository.SettingsRepository
import com.example.focus_app.domain.feedback.DiagnosticTime
import com.example.focus_app.domain.feedback.DeviceDiagnostics
import com.example.focus_app.domain.model.DetectionMode
import com.example.focus_app.service.AccessibilityDiagnosticsState
import com.example.focus_app.service.AccessibilityDiagnosticsStore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

private const val COLLECTED_AT = 1_700_000_300_000L

class DiagnosticsCollectorTest {

    private class FakeSettingsDao(private val entity: SettingsEntity?) : SettingsDao {
        var failure: Throwable? = null
        var writes = 0
            private set

        override suspend fun insertOrUpdate(settings: SettingsEntity) {
            writes++
        }

        override fun getSettings(): Flow<SettingsEntity?> = MutableStateFlow(entity)

        override suspend fun getSettingsOnce(): SettingsEntity? {
            failure?.let { throw it }
            return entity
        }

        override suspend fun clearLegacyApiKey() = Unit
    }

    private class FakeStore(initial: AccessibilityDiagnosticsState) : AccessibilityDiagnosticsStore {
        private val mutable = MutableStateFlow(initial)

        var recordCalls = 0
            private set

        override val state: StateFlow<AccessibilityDiagnosticsState> = mutable

        override fun recordServiceConnected(nowMillis: Long) {
            recordCalls++
        }

        override fun recordServiceDestroyed(nowMillis: Long) {
            recordCalls++
        }

        override fun recordServiceInterrupted(nowMillis: Long) {
            recordCalls++
        }

        override fun recordAppLaunch(packageLastUpdateTimeMillis: Long, nowMillis: Long) {
            recordCalls++
        }
    }

    private class FakeDeviceInfo(private val diagnostics: DeviceDiagnostics) : DeviceInfoSource {
        override fun device(): DeviceDiagnostics = diagnostics

        override fun collectedAtMillis(): Long = COLLECTED_AT
    }

    private fun collector(
        state: AccessibilityDiagnosticsState = AccessibilityDiagnosticsState(),
        dao: SettingsDao = FakeSettingsDao(null),
        device: DeviceDiagnostics = DeviceDiagnostics(),
        store: FakeStore = FakeStore(state)
    ): DiagnosticsCollector = DiagnosticsCollector(
        deviceInfoSource = FakeDeviceInfo(device),
        accessibilityDiagnosticsStore = store,
        settingsRepository = SettingsRepository(dao)
    )

    @Test
    fun `store fields are mapped verbatim and missing history stays missing`() = runTest {
        val snapshot = collector(
            state = AccessibilityDiagnosticsState(
                serviceBound = true,
                lastConnectedAtMillis = 1_000L,
                lastDestroyedAtMillis = null,
                lastInterruptedAtMillis = 2_000L
            )
        ).collect()

        assertEquals(true, snapshot.accessibility.serviceBound)
        assertEquals(DiagnosticTime.Recorded(1_000L), snapshot.accessibility.lastConnectedAt)
        // 没有记录不能被写成 1970 年。
        assertEquals(DiagnosticTime.NoRecord, snapshot.accessibility.lastDestroyedAt)
        assertEquals(DiagnosticTime.Recorded(2_000L), snapshot.accessibility.lastInterruptedAt)
    }

    @Test
    fun `device versions and system language come from the installed package source`() = runTest {
        val snapshot = collector(
            device = DeviceDiagnostics(
                manufacturer = "realme",
                model = "RMX3350",
                androidVersion = "11",
                apiLevel = 30,
                versionName = "1.2.3",
                versionCode = 42L,
                systemLanguage = "en-US"
            )
        ).collect()

        assertEquals("realme", snapshot.device.manufacturer)
        assertEquals("RMX3350", snapshot.device.model)
        assertEquals("11", snapshot.device.androidVersion)
        assertEquals(30, snapshot.device.apiLevel)
        assertEquals("1.2.3", snapshot.device.versionName)
        assertEquals(42L, snapshot.device.versionCode)
        assertEquals("en-US", snapshot.device.systemLanguage)
        assertEquals(COLLECTED_AT, snapshot.collectedAtMillis)
    }

    @Test
    fun `settings summary keeps only the whitelisted fields`() = runTest {
        val snapshot = collector().collect()

        val settings = snapshot.settings
        assertNotNull(settings)
        assertEquals(true, settings?.guardianEnabled)
        assertEquals(DetectionMode.REALTIME, settings?.detectionMode)
        assertEquals(10, settings?.reminderDelaySeconds)
        assertEquals(60, settings?.reminderWindowMinutes)
        assertEquals(3, settings?.maxRemindersPerWindow)
    }

    @Test
    fun `settings read failure does not fake defaults`() = runTest {
        val dao = FakeSettingsDao(null).apply { failure = IllegalStateException("boom") }

        val snapshot = collector(dao = dao).collect()

        assertNull(snapshot.settings)
    }

    @Test
    fun `collecting never writes to settings or the service store`() = runTest {
        val dao = FakeSettingsDao(null)
        val store = FakeStore(AccessibilityDiagnosticsState(serviceBound = true))

        collector(dao = dao, store = store).collect()

        assertEquals(0, dao.writes)
        assertEquals(0, store.recordCalls)
    }

    @Test
    fun `cancellation is not swallowed by the failure fallbacks`() = runTest {
        val dao = FakeSettingsDao(null).apply { failure = CancellationException("cancelled") }

        var caught: Throwable? = null
        try {
            collector(dao = dao).collect()
        } catch (throwable: Throwable) {
            caught = throwable
        }

        assertTrue(caught is CancellationException)
    }
}
