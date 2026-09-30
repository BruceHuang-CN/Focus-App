package com.example.focus_app.domain.feedback

import com.example.focus_app.domain.model.DetectionMode
import com.google.gson.Gson
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FeedbackPayloadTest {

    private val gson = Gson()

    private fun snapshot(): DiagnosticSnapshot = DiagnosticSnapshot(
        device = DeviceDiagnostics(
            manufacturer = "realme",
            model = "RMX3350",
            androidVersion = "11",
            apiLevel = 30,
            versionName = "1.2.3",
            versionCode = 42L,
            systemLanguage = "zh-CN"
        ),
        accessibility = AccessibilityDiagnosticsSnapshot(
            serviceBound = false,
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
        collectedAtMillis = 1_790_200_000_000L
    )

    private fun request(level: DiagnosticLevel) = buildAppFeedbackRequest(
        id = "71e29975-138d-49ab-b7ef-fb094cce4601",
        problem = "  打开目标应用后没有出现提醒。 ",
        steps = "",
        expected = "",
        level = level,
        snapshot = if (level == DiagnosticLevel.NONE) null else snapshot()
    )

    @Test
    fun `none level sends null diagnostics and no device fields`() {
        val payload = request(DiagnosticLevel.NONE)
        assertNull(payload.diagnostics)
        val json = gson.toJson(payload)
        assertFalse(json.contains("diagnostics"))
        assertFalse(json.contains("realme"))
        assertFalse(json.contains("RMX3350"))
        assertFalse(json.contains("zh-CN"))
        // 正文首尾空白被去掉，内部内容保留。
        assertEquals("打开目标应用后没有出现提醒。", payload.problem)
    }

    @Test
    fun `basic level keeps only basic fields on the wire`() {
        val json = gson.toJson(request(DiagnosticLevel.BASIC))

        assertTrue(json.contains("\"diagnosticLevel\":\"basic\""))
        assertTrue(json.contains("\"manufacturer\":\"realme\""))
        assertTrue(json.contains("\"model\":\"RMX3350\""))
        assertTrue(json.contains("\"apiLevel\":30"))
        assertTrue(json.contains("\"versionCode\":\"42\""))
        assertTrue(json.contains("\"serviceBound\":false"))
        assertTrue(json.contains("\"detectionMode\":\"realtime\""))
        // basic 不能携带 detailed 字段。
        assertFalse(json.contains("systemLanguage"))
        assertFalse(json.contains("lastConnectedAt"))
        assertFalse(json.contains("lastDestroyedAt"))
        assertFalse(json.contains("lastInterruptedAt"))
        assertFalse(json.contains("reminderDelaySeconds"))
        assertFalse(json.contains("reminderWindowMinutes"))
        assertFalse(json.contains("maxRemindersPerWindow"))
        // 不导出用户 AI 配置。
        assertFalse(json.contains("apiEndpoint"))
        assertFalse(json.contains("apiKey"))
    }

    @Test
    fun `detailed level sends language history times and reminder settings`() {
        val diagnostics = request(DiagnosticLevel.DETAILED).diagnostics!!
        assertEquals("zh-CN", diagnostics.device.systemLanguage)
        assertEquals(AppFeedbackTime.RECORDED, diagnostics.accessibility.lastConnectedAt?.state)
        assertEquals(1_700_000_000_000L, diagnostics.accessibility.lastConnectedAt?.epochMillis)
        assertEquals(AppFeedbackTime.NO_RECORD, diagnostics.accessibility.lastDestroyedAt?.state)
        assertNull(diagnostics.accessibility.lastDestroyedAt?.epochMillis)
        assertEquals(AppFeedbackTime.UNAVAILABLE, diagnostics.accessibility.lastInterruptedAt?.state)
        assertEquals(10, diagnostics.settings?.reminderDelaySeconds)
        assertEquals(60, diagnostics.settings?.reminderWindowMinutes)
        assertEquals(3, diagnostics.settings?.maxRemindersPerWindow)
        assertEquals(1_790_200_000_000L, diagnostics.collectedAtMillis)
    }

    @Test
    fun `version code is a decimal string and null stays null`() {
        val withVersion = request(DiagnosticLevel.BASIC)
        assertEquals("42", withVersion.diagnostics?.device?.versionCode)

        val noVersion = buildAppFeedbackRequest(
            id = "71e29975-138d-49ab-b7ef-fb094cce4601",
            problem = "问题",
            steps = "",
            expected = "",
            level = DiagnosticLevel.BASIC,
            snapshot = snapshot().copy(device = DeviceDiagnostics(apiLevel = null, versionCode = null))
        )
        assertNull(noVersion.diagnostics?.device?.versionCode)
    }

    @Test
    fun `restored pending request rebuilds the same whitelisted snapshot`() {
        val original = request(DiagnosticLevel.DETAILED)
        val restored = original.toDiagnosticSnapshot()!!

        assertEquals(original.diagnostics?.collectedAtMillis, restored.collectedAtMillis)
        assertEquals("realme", restored.device.manufacturer)
        assertEquals(42L, restored.device.versionCode)
        assertEquals("zh-CN", restored.device.systemLanguage)
        assertEquals(false, restored.accessibility.serviceBound)
        assertEquals(DiagnosticTime.Recorded(1_700_000_000_000L), restored.accessibility.lastConnectedAt)
        assertEquals(DiagnosticTime.NoRecord, restored.accessibility.lastDestroyedAt)
        assertEquals(DiagnosticTime.Unavailable, restored.accessibility.lastInterruptedAt)
        assertEquals(DetectionMode.REALTIME, restored.settings?.detectionMode)
        assertEquals(10, restored.settings?.reminderDelaySeconds)
    }

    @Test
    fun `diagnostic level parses known wire values and defaults to none`() {
        assertEquals(DiagnosticLevel.NONE, DiagnosticLevel.fromWire("none"))
        assertEquals(DiagnosticLevel.BASIC, DiagnosticLevel.fromWire("basic"))
        assertEquals(DiagnosticLevel.DETAILED, DiagnosticLevel.fromWire("detailed"))
        assertEquals(DiagnosticLevel.NONE, DiagnosticLevel.fromWire(null))
        assertEquals(DiagnosticLevel.NONE, DiagnosticLevel.fromWire("verbose"))
    }
}
