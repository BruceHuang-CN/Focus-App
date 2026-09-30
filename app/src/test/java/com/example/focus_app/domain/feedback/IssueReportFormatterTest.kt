package com.example.focus_app.domain.feedback

import com.example.focus_app.domain.model.DetectionMode
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class IssueReportFormatterTest {

    private val texts = testIssueReportTexts()
    private val formatter = IssueReportFormatter(texts, ZoneId.of("Asia/Shanghai"))

    private fun fullSnapshot(): DiagnosticSnapshot = DiagnosticSnapshot(
        device = DeviceDiagnostics(
            manufacturer = "MANUFACTURER-A",
            model = "MODEL-A",
            androidVersion = "14",
            apiLevel = 34,
            versionName = "1.0.0",
            versionCode = 1L,
            systemLanguage = "zh-CN"
        ),
        accessibility = AccessibilityDiagnosticsSnapshot(
            serviceBound = true,
            lastConnectedAt = DiagnosticTime.Recorded(1_700_000_000_000L),
            lastDestroyedAt = DiagnosticTime.Recorded(1_700_000_100_000L),
            lastInterruptedAt = DiagnosticTime.Recorded(1_700_000_200_000L)
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

    @Test
    fun `report keeps user line breaks and marks empty optional fields`() {
        val body = formatter.formatReport(
            problem = "打开小红书以后\n没有出现提醒",
            steps = "   ",
            expected = "",
            snapshot = null,
            level = DiagnosticLevel.NONE
        )

        assertTrue(body.contains("打开小红书以后\n没有出现提醒"))
        assertTrue(body.contains("STEPS:\nEMPTY"))
        assertTrue(body.contains("EXPECTED:\nEMPTY"))
        assertFalse(body.contains("DIAGNOSTICS"))
    }

    @Test
    fun `report without diagnostics contains no device version settings or collection time`() {
        val body = formatter.formatReport(
            problem = "没有提醒",
            steps = "",
            expected = "",
            snapshot = fullSnapshot(),
            level = DiagnosticLevel.NONE
        )

        assertFalse(body.contains("MANUFACTURER-A"))
        assertFalse(body.contains("MODEL-A"))
        assertFalse(body.contains("1.0.0"))
        assertFalse(body.contains("zh-CN"))
        assertFalse(body.contains("COLLECTEDAT"))
        assertFalse(body.contains("SERVICEBOUND"))
        assertFalse(body.contains("GUARDIAN"))
        assertFalse(body.contains("2023-11-15"))
        assertFalse(body.contains("DIAGNOSTICS"))
    }

    @Test
    fun `diagnostics list every whitelisted field and the collected time with a zone`() {
        val diagnostics = formatter.formatDiagnostics(fullSnapshot())

        assertTrue(diagnostics.contains("MANUFACTURER:MANUFACTURER-A"))
        assertTrue(diagnostics.contains("MODEL:MODEL-A"))
        assertTrue(diagnostics.contains("ANDROID:14"))
        assertTrue(diagnostics.contains("API:34"))
        assertTrue(diagnostics.contains("VERSIONNAME:1.0.0"))
        assertTrue(diagnostics.contains("VERSIONCODE:1"))
        assertTrue(diagnostics.contains("SYSTEMLANG:zh-CN"))
        assertTrue(diagnostics.contains("SERVICEBOUND:YES"))
        assertTrue(diagnostics.contains("GUARDIAN:YES"))
        assertTrue(diagnostics.contains("DETECTION:REALTIME"))
        assertTrue(diagnostics.contains("DELAY:10"))
        assertTrue(diagnostics.contains("WINDOW:60"))
        assertTrue(diagnostics.contains("MAX:3"))
        // 采集时间保留毫秒并注明时区。
        assertTrue(diagnostics.contains("COLLECTEDAT:2023-11-15 06:18:20.000"))
        assertTrue(diagnostics.contains("GMT"))
    }

    @Test
    fun `missing history and failed reads are shown differently and never become 1970`() {
        val snapshot = DiagnosticSnapshot(
            accessibility = AccessibilityDiagnosticsSnapshot(
                serviceBound = null,
                lastConnectedAt = DiagnosticTime.NoRecord,
                lastDestroyedAt = DiagnosticTime.Unavailable,
                lastInterruptedAt = DiagnosticTime.Recorded(0L)
            ),
            settings = null,
            collectedAtMillis = 0L
        )

        val diagnostics = formatter.formatDiagnostics(snapshot)

        assertTrue(diagnostics.contains("LASTCONNECTED:NORECORD"))
        assertTrue(diagnostics.contains("LASTDESTROYED:UNAVAILABLE"))
        assertTrue(diagnostics.contains("SERVICEBOUND:UNAVAILABLE"))
        // 整个设置读取失败时不伪造默认值。
        assertTrue(diagnostics.contains("GUARDIAN:UNAVAILABLE"))
        assertTrue(diagnostics.contains("DETECTION:UNAVAILABLE"))
        assertTrue(diagnostics.contains("DELAY:UNAVAILABLE"))
        // 真实存在的 0 毫秒仍然按 1970 显示，只用来区分“有记录”。
        assertTrue(diagnostics.contains("LASTINTERRUPTED:1970-01-01 08:00:00.000"))
        assertFalse(diagnostics.contains("LASTCONNECTED:1970"))
        assertFalse(diagnostics.contains("LASTDESTROYED:1970"))
    }

    @Test
    fun `unavailable snapshot marks every diagnostic field as unavailable`() {
        val diagnostics = formatter.formatDiagnostics(DiagnosticSnapshot.unavailable(1_700_000_300_000L))

        assertTrue(diagnostics.contains("MANUFACTURER:UNAVAILABLE"))
        assertTrue(diagnostics.contains("SERVICEBOUND:UNAVAILABLE"))
        assertTrue(diagnostics.contains("COLLECTEDAT:2023-11-15 06:18:20.000"))
        assertFalse(diagnostics.contains("NORECORD"))
    }

    @Test
    fun `output is stable and contains no fields outside the whitelist`() {
        val snapshot = fullSnapshot()
        val first = formatter.formatReport("问题", "步骤", "预期", snapshot, DiagnosticLevel.DETAILED)
        val second = formatter.formatReport("问题", "步骤", "预期", snapshot, DiagnosticLevel.DETAILED)

        assertEquals(first, second)
        assertTrue(first.contains("DIAGNOSTICS:\nMANUFACTURER:MANUFACTURER-A"))
        // 白名单之外的信息不会出现在正文里。
        assertFalse(first.contains("sk-secret-key"))
        assertFalse(first.contains("https://api.deepseek.com"))
        assertFalse(first.contains("TARGET_APPS"))
    }

    @Test
    fun `basic level omits every detailed field`() {
        val diagnostics = formatter.formatDiagnostics(fullSnapshot(), DiagnosticLevel.BASIC)

        assertTrue(diagnostics.contains("MODEL:MODEL-A"))
        assertTrue(diagnostics.contains("SERVICEBOUND:YES"))
        assertTrue(diagnostics.contains("DETECTION:REALTIME"))
        // detailed 字段不出现在 basic 中。
        assertFalse(diagnostics.contains("SYSTEMLANG"))
        assertFalse(diagnostics.contains("LASTCONNECTED"))
        assertFalse(diagnostics.contains("LASTDESTROYED"))
        assertFalse(diagnostics.contains("LASTINTERRUPTED"))
        assertFalse(diagnostics.contains("DELAY"))
        assertFalse(diagnostics.contains("WINDOW"))
        assertFalse(diagnostics.contains("MAX"))
    }

    @Test
    fun `none level produces no diagnostics section at all`() {
        val body = formatter.formatReport("没有提醒", "", "", fullSnapshot(), DiagnosticLevel.NONE)

        assertFalse(body.contains("DIAGNOSTICS"))
        assertFalse(body.contains("MODEL-A"))
        assertFalse(body.contains("COLLECTEDAT"))
    }

    @Test
    fun `compatibility mode uses its own label`() {
        val diagnostics = formatter.formatDiagnostics(
            fullSnapshot().let { it.copy(settings = it.settings?.copy(detectionMode = DetectionMode.COMPATIBILITY)) }
        )

        assertTrue(diagnostics.contains("DETECTION:COMPATIBILITY"))
    }
}

/** 供其他测试复用的固定文案，避免断言依赖真实资源。 */
internal fun testIssueReportTexts(): IssueReportTexts = IssueReportTexts(
    title = "TITLE",
    sectionProblem = "PROBLEM",
    sectionSteps = "STEPS",
    sectionExpected = "EXPECTED",
    sectionDiagnostics = "DIAGNOSTICS",
    labelSuffix = ":",
    emptyValue = "EMPTY",
    noRecord = "NORECORD",
    unavailable = "UNAVAILABLE",
    yes = "YES",
    no = "NO",
    manufacturer = "MANUFACTURER",
    model = "MODEL",
    androidVersion = "ANDROID",
    apiLevel = "API",
    versionName = "VERSIONNAME",
    versionCode = "VERSIONCODE",
    systemLanguage = "SYSTEMLANG",
    collectedAt = "COLLECTEDAT",
    serviceBound = "SERVICEBOUND",
    lastConnectedAt = "LASTCONNECTED",
    lastDestroyedAt = "LASTDESTROYED",
    lastInterruptedAt = "LASTINTERRUPTED",
    diagnosticsNote = "NOTE",
    settingsHeader = "SETTINGS",
    guardianEnabled = "GUARDIAN",
    detectionMode = "DETECTION",
    detectionRealtime = "REALTIME",
    detectionCompatibility = "COMPATIBILITY",
    reminderDelaySeconds = "DELAY",
    reminderWindowMinutes = "WINDOW",
    maxRemindersPerWindow = "MAX"
)
