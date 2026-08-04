package com.example.focus_app.domain.reminder

import com.example.focus_app.domain.model.ReminderTone
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReminderTonePreviewTest {

    @Test
    fun every_tone_returns_a_non_blank_sample() {
        ReminderTone.entries.forEach { tone ->
            assertFalse(ReminderTonePreview.sampleMessage(tone, "", null).isBlank())
        }
    }

    @Test
    fun custom_tone_uses_the_custom_instruction_when_provided() {
        val message = ReminderTonePreview.sampleMessage(
            ReminderTone.CUSTOM,
            "别刷了，快去学习。",
            null
        )
        assertEquals("别刷了，快去学习。", message)
    }

    @Test
    fun custom_tone_falls_back_when_instruction_is_blank() {
        val message = ReminderTonePreview.sampleMessage(ReminderTone.CUSTOM, "   ", null)
        assertFalse(message.isBlank())
    }

    @Test
    fun task_title_is_prepended_to_the_sample() {
        val message = ReminderTonePreview.sampleMessage(
            ReminderTone.DIRECT,
            "",
            "写作业"
        )
        assertTrue(message.startsWith("【写作业】"))
    }

    @Test
    fun no_task_produces_a_plain_sample() {
        val message = ReminderTonePreview.sampleMessage(ReminderTone.GENTLE, "", null)
        assertFalse(message.contains("【"))
        assertTrue(message.contains("先停一下"))
    }
}
