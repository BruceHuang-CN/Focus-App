package com.example.focus_app.data.remote

import com.example.focus_app.data.repository.AppInfo
import com.example.focus_app.data.repository.AppSettings
import com.example.focus_app.domain.model.FocusTask
import com.example.focus_app.domain.model.ReminderContext
import com.example.focus_app.domain.model.ReminderTone
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ReminderBatchCoordinatorTest {
    @Test
    fun active_task_generates_once_per_target_and_ignores_irrelevant_settings_updates() = runTest {
        val tasks = MutableStateFlow<FocusTask?>(null)
        val settings = MutableStateFlow(
            AppSettings(
                targetApps = listOf(
                    AppInfo("douyin", "抖音"),
                    AppInfo("bilibili", "哔哩哔哩")
                ),
                toneKey = ReminderTone.DIRECT
            )
        )
        val generated = mutableListOf<String>()
        val cached = mutableListOf<String>()
        val coordinator = ReminderBatchCoordinator(
            activeTasks = tasks,
            settings = settings,
            buildContext = { task, app, current -> context(task, app, current.toneKey) },
            generate = { context, _ ->
                generated += context.appName
                Result.success(listOf("一", "二", "三"))
            },
            cache = { _, packageName, _, _ -> cached += packageName }
        )
        coordinator.start(backgroundScope)

        tasks.value = FocusTask(id = 7L, title = "写方案", isManualActive = true)
        runCurrent()
        settings.value = settings.value.copy(enableBreathingPause = false)
        runCurrent()
        settings.value = settings.value.copy(targetApps = settings.value.targetApps.reversed())
        runCurrent()

        assertEquals(listOf("哔哩哔哩", "抖音"), generated)
        assertEquals(listOf("bilibili", "douyin"), cached)
    }

    @Test
    fun cold_start_with_complete_cache_skips_generation_once() = runTest {
        val tasks = MutableStateFlow<FocusTask?>(
            FocusTask(id = 7L, title = "写方案", isManualActive = true)
        )
        val settings = MutableStateFlow(
            AppSettings(
                targetApps = listOf(
                    AppInfo("douyin", "抖音"),
                    AppInfo("bilibili", "哔哩哔哩")
                )
            )
        )
        val checkedPackages = mutableListOf<String>()
        var generated = 0
        val coordinator = ReminderBatchCoordinator(
            activeTasks = tasks,
            settings = settings,
            buildContext = { task, app, current -> context(task, app, current.toneKey) },
            generate = { _, _ -> generated++; Result.success(listOf("一", "二", "三")) },
            cache = { _, _, _, _ -> },
            cacheReady = { _, packageName, _ -> checkedPackages += packageName; true }
        )

        coordinator.start(backgroundScope)
        runCurrent()

        assertEquals(0, generated)
        assertEquals(listOf("bilibili", "douyin"), checkedPackages)
    }

    @Test
    fun cold_start_without_active_task_does_not_skip_later_activation() = runTest {
        val tasks = MutableStateFlow<FocusTask?>(null)
        val settings = MutableStateFlow(
            AppSettings(targetApps = listOf(AppInfo("douyin", "抖音")))
        )
        var generated = 0
        val coordinator = ReminderBatchCoordinator(
            activeTasks = tasks,
            settings = settings,
            buildContext = { task, app, current -> context(task, app, current.toneKey) },
            generate = { _, _ -> generated++; Result.success(listOf("一", "二", "三")) },
            cache = { _, _, _, _ -> },
            cacheReady = { _, _, _ -> true }
        )
        coordinator.start(backgroundScope)
        runCurrent()

        tasks.value = FocusTask(id = 7L, title = "写方案", isManualActive = true)
        runCurrent()

        assertEquals(1, generated)
    }

    @Test
    fun same_task_second_boundary_emission_generates_a_second_batch() = runTest {
        val task = FocusTask(id = 7L, title = "写方案", isManualActive = true)
        val tasks = MutableSharedFlow<FocusTask?>(replay = 1)
        tasks.emit(task)
        val settings = MutableStateFlow(
            AppSettings(targetApps = listOf(AppInfo("douyin", "抖音")))
        )
        var generated = 0
        val coordinator = ReminderBatchCoordinator(
            activeTasks = tasks,
            settings = settings,
            buildContext = { currentTask, app, current -> context(currentTask, app, current.toneKey) },
            generate = { _, _ -> generated++; Result.success(listOf("一", "二", "三")) },
            cache = { _, _, _, _ -> },
            cacheReady = { _, _, _ -> false }
        )
        coordinator.start(backgroundScope)
        runCurrent()

        tasks.emit(task)
        runCurrent()

        assertEquals(2, generated)
    }

    @Test
    fun key_or_mood_change_regenerates_without_polling() = runTest {
        val tasks = MutableStateFlow<FocusTask?>(
            FocusTask(id = 7L, title = "写方案", isManualActive = true)
        )
        val settings = MutableStateFlow(
            AppSettings(targetApps = listOf(AppInfo("douyin", "抖音")))
        )
        val keyRevision = MutableStateFlow(0L)
        val moodRevision = MutableStateFlow<Long?>(null)
        var generated = 0
        val coordinator = ReminderBatchCoordinator(
            activeTasks = tasks,
            settings = settings,
            apiKeyRevisions = keyRevision,
            moodRevisions = moodRevision,
            buildContext = { task, app, current -> context(task, app, current.toneKey) },
            generate = { _, _ ->
                generated++
                Result.success(listOf("一", "二", "三"))
            },
            cache = { _, _, _, _ -> }
        )
        coordinator.start(backgroundScope)
        runCurrent()

        keyRevision.value = 1L
        runCurrent()
        moodRevision.value = 9L
        runCurrent()

        assertEquals(3, generated)
    }

    private fun context(task: FocusTask, app: AppInfo, tone: ReminderTone) = ReminderContext(
        taskTitle = task.title,
        timeBlock = null,
        latestMood = null,
        appName = app.appName,
        openCountToday = 0,
        remindersInWindow = 0,
        activeExitsToday = 0,
        tone = tone
    )
}
