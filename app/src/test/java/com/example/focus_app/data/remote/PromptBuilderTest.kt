package com.example.focus_app.data.remote

import com.example.focus_app.domain.model.ReminderContext
import com.example.focus_app.domain.model.ReminderTone
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PromptBuilderTest {
    @Test
    fun prompt_contains_only_the_approved_summary_context_and_json_contract() {
        val context = ReminderContext(
            taskTitle = "写完产品方案",
            timeBlock = "09:00-10:00",
            latestMood = "有点分心",
            appName = "抖音",
            openCountToday = 4,
            remindersInWindow = 1,
            activeExitsToday = 2,
            tone = ReminderTone.DIRECT,
            customToneInstruction = ""
        )

        val prompt = PromptBuilder.buildUserMessage(context, 3)

        listOf(
            "写完产品方案",
            "09:00-10:00",
            "有点分心",
            "抖音",
            "今日打开次数：4",
            "当前滚动周期提醒次数：1",
            "今日主动退出次数：2",
            "json",
            "{\"messages\":[\"提醒1\",\"提醒2\",\"提醒3\"]}"
        ).forEach { assertTrue("missing $it", prompt.contains(it, ignoreCase = true)) }
        listOf("设备标识", "联系人", "原始会话", "raw sessions").forEach {
            assertFalse("unexpected $it", prompt.contains(it, ignoreCase = true))
        }
    }

    @Test
    fun sarcastic_tone_forbids_personal_attacks_and_threats() {
        val prompt = PromptBuilder.buildSystemPrompt(ReminderTone.SARCASTIC, "")

        assertTrue(prompt.contains("轻度讽刺"))
        assertTrue(prompt.contains("禁止人格羞辱"))
        assertTrue(prompt.contains("禁止外貌攻击"))
        assertTrue(prompt.contains("禁止威胁"))
    }
}
