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
            "你又把注意力交给了$app。别再拖，关掉它，继续「$task」。",
            "别再用“稍后”敷衍自己。现在退出$app，立刻回到「$task」。",
            "继续滑动就是主动推迟「$task」。关掉$app，现在就开始。",
            "滑动没有终点，借口也不会完成任务。退出$app，马上做「$task」。"
        ).distinct().filter { it.length <= 80 }
        return Result.success(messages.take(count.coerceIn(1, messages.size)))
    }
}
