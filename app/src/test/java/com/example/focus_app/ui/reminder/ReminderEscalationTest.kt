package com.example.focus_app.ui.reminder

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ReminderEscalationTest {
    @Test
    fun first_reminder_keeps_the_ai_message_without_an_extra_rebuke() {
        val copy = reminderEscalationCopy(windowReminderCount = 1, windowLimit = 5)

        assertEquals("先停一下", copy.title)
        assertNull(copy.directive)
    }

    @Test
    fun repeated_reminders_name_the_repeat_and_remaining_chances() {
        val second = reminderEscalationCopy(windowReminderCount = 2, windowLimit = 5)
        val fourth = reminderEscalationCopy(windowReminderCount = 4, windowLimit = 5)

        assertTrue(second.directive.orEmpty().contains("第 2 次"))
        assertTrue(second.directive.orEmpty().contains("剩余 3 次"))
        assertTrue(fourth.directive.orEmpty().contains("第 4 次"))
        assertTrue(fourth.directive.orEmpty().contains("剩余 1 次"))
        assertTrue(fourth.directive.orEmpty().contains("别再用“稍后”敷衍自己"))
    }

    @Test
    fun final_reminder_uses_the_strongest_action_command() {
        val copy = reminderEscalationCopy(windowReminderCount = 5, windowLimit = 5)

        assertEquals("别再拖了，现在退出", copy.title)
        assertTrue(copy.directive.orEmpty().contains("最后一次提醒"))
        assertTrue(copy.directive.orEmpty().contains("立刻回到任务"))
    }
}
