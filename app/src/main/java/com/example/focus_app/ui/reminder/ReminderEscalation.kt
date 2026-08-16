package com.example.focus_app.ui.reminder

internal data class ReminderEscalationCopy(
    val title: String,
    val directive: String?
)

internal fun reminderEscalationCopy(
    windowReminderCount: Int,
    windowLimit: Int
): ReminderEscalationCopy {
    if (windowLimit <= 0 || windowReminderCount <= 1) {
        return ReminderEscalationCopy(
            title = "先停一下",
            directive = null
        )
    }

    val count = windowReminderCount.coerceIn(1, windowLimit)
    val remaining = (windowLimit - count).coerceAtLeast(0)
    if (remaining == 0) {
        return ReminderEscalationCopy(
            title = "别再拖了，现在退出",
            directive = "最后一次提醒。你已经给过自己足够多次“稍后”，现在退出，立刻回到任务。"
        )
    }

    val progress = count.toFloat() / windowLimit.toFloat()
    val directive = when {
        progress <= 0.4f ->
            "这是第 $count 次提醒，剩余 $remaining 次。上一次的“稍后”已经过去，别再把拖延包装成休息。"

        progress <= 0.7f ->
            "这是第 $count 次提醒，剩余 $remaining 次。你已经多次无视提醒，继续滑动只会让任务继续积压。"

        else ->
            "这是第 $count 次提醒，剩余 $remaining 次。别再用“稍后”敷衍自己，现在就退出。"
    }
    return ReminderEscalationCopy(
        title = if (progress <= 0.4f) "你又回来了" else "别再给拖延找借口",
        directive = directive
    )
}
