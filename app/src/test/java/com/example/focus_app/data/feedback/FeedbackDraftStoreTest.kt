package com.example.focus_app.data.feedback

import com.example.focus_app.domain.feedback.DiagnosticLevel
import com.example.focus_app.domain.feedback.DeviceDiagnostics
import com.example.focus_app.domain.feedback.DiagnosticSnapshot
import com.example.focus_app.domain.feedback.buildAppFeedbackRequest
import java.io.File
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class FeedbackDraftStoreTest {

    @get:Rule
    val temp = TemporaryFolder()

    private fun file(): File = File(temp.root, "feedback_pending.json")

    private fun store(): FileFeedbackDraftStore = FileFeedbackDraftStore(file())

    private fun payload() = buildAppFeedbackRequest(
        id = "71e29975-138d-49ab-b7ef-fb094cce4601",
        problem = "没有提醒",
        steps = "步骤",
        expected = "预期",
        level = DiagnosticLevel.BASIC,
        snapshot = DiagnosticSnapshot(
            device = DeviceDiagnostics(manufacturer = "realme", model = "RMX3350"),
            collectedAtMillis = 1_790_200_000_000L
        )
    )

    @Test
    fun `saved request survives a reload with the same id and diagnostics`() = runTest {
        store().save(PendingFeedback(displayBody = "BODY", payload = payload()))

        val loaded = store().load()
        assertNotNull(loaded)
        assertEquals("BODY", loaded!!.displayBody)
        assertEquals("71e29975-138d-49ab-b7ef-fb094cce4601", loaded.payload.id)
        assertEquals("realme", loaded.payload.diagnostics?.device?.manufacturer)
        assertEquals(1_790_200_000_000L, loaded.payload.diagnostics?.collectedAtMillis)
    }

    @Test
    fun `clear removes the pending request`() = runTest {
        store().save(PendingFeedback(displayBody = "BODY", payload = payload()))
        store().clear()
        assertNull(store().load())
    }

    @Test
    fun `missing and corrupted files load as no pending request`() = runTest {
        assertNull(store().load())

        file().writeText("{ this is not json")
        assertNull(store().load())
    }

    @Test
    fun `raw steps and optional context survive file persistence`() = runTest {
        val context = com.example.focus_app.domain.feedback.FeedbackContextDiagnostics(
            targetAppCount = 0, accessibilityEnabled = true, fontScale = 1.5f
        )
        store().save(PendingFeedback("BODY", payload(), "原始步骤", context))
        val loaded = store().load()!!
        assertEquals("原始步骤", loaded.formSteps)
        assertEquals(context, loaded.context)
    }
}
