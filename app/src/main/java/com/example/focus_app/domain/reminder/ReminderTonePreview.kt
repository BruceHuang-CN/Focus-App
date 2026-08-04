package com.example.focus_app.domain.reminder

import com.example.focus_app.domain.model.ReminderTone

/**
 * 为设置页“口吻预览”生成示例提醒文案。
 * 有当前任务时带上任务标题，没有任务时只展示口吻示例。
 */
object ReminderTonePreview {
    fun sampleMessage(
        tone: ReminderTone,
        customInstruction: String,
        taskTitle: String?
    ): String {
        val taskPart = taskTitle?.take(20)?.let { "【$it】" }
        val body = when (tone) {
            ReminderTone.GENTLE -> "先停一下，慢慢来，把手头的事做好再回来。"
            ReminderTone.DIRECT -> "你正在刷手机，现在回到任务上。"
            ReminderTone.SARCASTIC -> "视频不会跑，你的任务可一直在等你哦。"
            ReminderTone.CUSTOM ->
                customInstruction.trim().take(40).ifBlank { "按你自定义的口吻提醒你回到任务。" }
        }
        return listOfNotNull(taskPart, body).joinToString(" ")
    }
}
