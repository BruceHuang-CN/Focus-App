package com.example.focus_app.data.remote

import com.example.focus_app.domain.model.ReminderContext

class LocalReminderProvider : AiReminderProvider {
    override suspend fun generateBatch(
        context: ReminderContext,
        count: Int
    ): Result<List<String>> {
        val task = context.taskTitle.take(24)
        val app = context.appName.take(16)
        val messages = listOf(
            "你原本准备完成「$task」。先放下$app，回去做最小的一步。",
            "再刷一会不会让任务变轻。关掉$app，继续「$task」。",
            "注意力正在被${app}带走。现在停下，回到「$task」。",
            "先把「$task」推进十分钟，再决定要不要打开$app。",
            "滑动没有终点，任务有。放下$app，继续「$task」。"
        ).distinct().filter { it.length <= 80 }
        return Result.success(messages.take(count.coerceIn(1, messages.size)))
    }
}
