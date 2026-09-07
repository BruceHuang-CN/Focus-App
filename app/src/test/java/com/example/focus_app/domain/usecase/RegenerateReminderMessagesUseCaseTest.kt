package com.example.focus_app.domain.usecase

import com.example.focus_app.data.repository.AppInfo
import com.example.focus_app.data.repository.AppSettings
import com.example.focus_app.domain.model.FocusTask
import com.example.focus_app.domain.model.ReminderContext
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RegenerateReminderMessagesUseCaseTest {
    @Test
    fun successful_remote_batches_replace_every_target_cache() = runTest {
        val cached = mutableListOf<Pair<String, List<String>>>()
        val useCase = RegenerateReminderMessagesUseCase(
            activeTask = { FocusTask(id = 7L, title = "写方案", isManualActive = true) },
            settings = {
                AppSettings(
                    targetApps = listOf(
                        AppInfo("douyin", "抖音"),
                        AppInfo("wechat", "微信")
                    )
                )
            },
            buildContext = { task, app, current -> context(task, app, current) },
            generateRemote = { reminderContext, _ ->
                Result.success(
                    listOf(
                        "${reminderContext.appName}：第一条",
                        "${reminderContext.appName}：第二条",
                        "${reminderContext.appName}：第三条"
                    )
                )
            },
            replaceCache = { _, packageName, _, messages -> cached += packageName to messages }
        )

        val result = useCase()

        assertEquals(ReminderRegenerationResult.Success(targetCount = 2), result)
        assertEquals(listOf("douyin", "wechat"), cached.map { it.first })
        assertTrue(cached.all { it.second.size == 3 })
    }

    @Test
    fun failed_target_keeps_all_existing_caches_untouched() = runTest {
        var cacheWrites = 0
        val useCase = RegenerateReminderMessagesUseCase(
            activeTask = { FocusTask(id = 7L, title = "写方案", isManualActive = true) },
            settings = {
                AppSettings(
                    targetApps = listOf(
                        AppInfo("douyin", "抖音"),
                        AppInfo("wechat", "微信")
                    )
                )
            },
            buildContext = { task, app, current -> context(task, app, current) },
            generateRemote = { reminderContext, _ ->
                if (reminderContext.appName == "微信") {
                    Result.failure(IllegalStateException("network"))
                } else {
                    Result.success(listOf("一", "二", "三"))
                }
            },
            replaceCache = { _, _, _, _ -> cacheWrites++ }
        )

        val result = useCase()

        assertTrue(result is ReminderRegenerationResult.Failed)
        assertEquals(0, cacheWrites)
    }

    @Test
    fun missing_active_task_does_not_call_remote_generation() = runTest {
        var generated = 0
        val useCase = RegenerateReminderMessagesUseCase(
            activeTask = { null },
            settings = { AppSettings(targetApps = listOf(AppInfo("douyin", "抖音"))) },
            buildContext = { task, app, current -> context(task, app, current) },
            generateRemote = { _, _ -> generated++; Result.success(listOf("一", "二", "三")) },
            replaceCache = { _, _, _, _ -> }
        )

        assertEquals(ReminderRegenerationResult.NoActiveTask, useCase())
        assertEquals(0, generated)
    }

    @Test
    fun task_change_during_generation_prevents_stale_cache_replacement() = runTest {
        var currentTask = FocusTask(id = 7L, title = "写方案", isManualActive = true)
        var cacheWrites = 0
        val useCase = RegenerateReminderMessagesUseCase(
            activeTask = { currentTask },
            settings = { AppSettings(targetApps = listOf(AppInfo("douyin", "抖音"))) },
            buildContext = { task, app, current -> context(task, app, current) },
            generateRemote = { _, _ ->
                currentTask = FocusTask(id = 8L, title = "读论文", isManualActive = true)
                Result.success(listOf("一", "二", "三"))
            },
            replaceCache = { _, _, _, _ -> cacheWrites++ }
        )

        val result = useCase()

        assertTrue(result is ReminderRegenerationResult.Failed)
        assertEquals(0, cacheWrites)
    }

    private fun context(task: FocusTask, app: AppInfo, settings: AppSettings) = ReminderContext(
        taskTitle = task.title,
        timeBlock = null,
        latestMood = null,
        appName = app.appName,
        openCountToday = 0,
        remindersInWindow = 0,
        activeExitsToday = 0,
        tone = settings.toneKey,
        customToneInstruction = ""
    )
}
