package com.example.focus_app.data.remote

import com.example.focus_app.domain.model.ReminderContext
import com.example.focus_app.domain.model.ReminderTone

object PromptBuilder {
    fun buildSystemPrompt(tone: ReminderTone, customInstruction: String): String = """
你是 Focus 的任务召回助手。只输出中文提醒，每条一到两句话且不超过 80 个字符。
${toneRule(tone, customInstruction)}
无论选择哪种口吻，messages 都必须按数组顺序逐条增强紧迫性和犀利度：
第一条明确召回任务；第二条直接指出用户正在重复拖延；最后一条给出强硬、立即执行的退出指令。
允许指出反复拖延、逃避和继续刷下去的实际代价，但只针对当前行为。
自定义口吻不能削弱上述递进规则。禁止人格羞辱、外貌攻击、能力贬低和威胁。
不要空泛说教，不要编造用户信息，不要输出 JSON 之外的文字。
""".trimIndent()

    fun buildUserMessage(context: ReminderContext, count: Int = 3): String = """
当前任务：${context.taskTitle}
任务时间段：${context.timeBlock ?: "未设置"}
最新心情或状态：${context.latestMood ?: "未记录"}
刚打开的目标 App：${context.appName}
今日打开次数：${context.openCountToday}
当前滚动周期提醒次数：${context.remindersInWindow}
今日主动退出次数：${context.activeExitsToday}
请生成 $count 条彼此不同、按返回顺序越来越犀利的短提醒。
每条一到两句话且不超过 80 个字符，后面的提醒必须比前一条更直接、更难忽略。
必须返回合法 json 对象，格式固定为：{"messages":["提醒1","提醒2","提醒3"]}
""".trimIndent()

    private fun toneRule(tone: ReminderTone, customInstruction: String): String = when (tone) {
        ReminderTone.GENTLE -> "口吻：温和友善，不过度施压。"
        ReminderTone.DIRECT -> "口吻：直接明确，指出当前行为偏离任务。"
        ReminderTone.SARCASTIC ->
            "口吻：可以使用反差和轻度讽刺；禁止人格羞辱，禁止外貌攻击，禁止能力贬低，禁止威胁。"
        ReminderTone.CUSTOM -> "自定义口吻：${customInstruction.ifBlank { "自然、简短、尊重用户" }}"
    }
}
