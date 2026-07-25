package com.example.focus_app.data.remote

import com.example.focus_app.domain.model.ReminderContext

object PromptBuilder {
    fun buildSystemPrompt(personality: String): String = """
你是一个专注力助手，帮助用户减少无意识的手机使用。你的风格是"${getPersonalityDescription(personality)}"。
请根据用户打开App的上下文信息，分析用户可能的心理动机，用轻松幽默的方式提醒用户注意自己的行为。
不要说教，不要评判，像一个了解用户的朋友那样说话。
回复长度控制在100字以内，用中文回复。
""".trimIndent()

    fun buildUserMessage(context: ReminderContext): String {
        val moodLine = context.latestMood?.let { "用户最近记录的心情：$it。" } ?: ""
        val lastOpenLine = context.lastOpenTime?.let { "上次打开时间：$it。" } ?: ""
        return """
用户刚刚打开了「${context.appName}」。
当前时间：${context.currentTime}。
今天已经打开目标App ${context.openCountToday} 次，其中主动退出 ${context.exitedCountToday} 次。
$lastOpenLine
$moodLine
请根据以上信息，生成一段提醒。
""".trimIndent()
    }

    private fun getPersonalityDescription(personality: String): String = when (personality) {
        "humorous" -> "幽默风趣，喜欢用玩笑和比喻来提醒用户"
        "sarcastic" -> "毒舌但善意，用犀利的吐槽让用户清醒"
        else -> "温和友善，像朋友一样轻声提醒"
    }
}
