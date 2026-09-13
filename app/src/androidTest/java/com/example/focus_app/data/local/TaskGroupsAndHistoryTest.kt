package com.example.focus_app.data.local

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.focus_app.data.local.entity.*
import com.example.focus_app.data.repository.*
import com.example.focus_app.domain.model.TaskGroup
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith

/** Uses an isolated in-memory database only. No installed app database or device settings are touched. */
@RunWith(AndroidJUnit4::class)
class TaskGroupsAndHistoryTest {
    private lateinit var db: AppDatabase
    private lateinit var groups: TaskGroupRepository
    @Before fun setUp() {
        db = Room.inMemoryDatabaseBuilder(InstrumentationRegistry.getInstrumentation().targetContext, AppDatabase::class.java).build()
        groups = TaskGroupRepository(db, db.taskGroupDao())
    }
    @After fun tearDown() { db.close() }
    @Test fun delete_group_preserves_ids_completion_and_effective_schedule() = runBlocking {
        groups.save(TaskGroup(name = "学习", scheduleStartMinute = 540, scheduleEndMinute = 600, repeatDaysMask = 31))
        val group = db.taskGroupDao().all().first { it.id != 1L }
        db.focusTaskDao().insert(FocusTaskEntity(id = 41, title = "读书", groupId = group.id,
            inheritsGroupSchedule = true, isCompleted = true, createdAt = 123, updatedAt = 456))
        groups.deleteGroup(group.id)
        val task = db.taskGroupDao().task(41)!!
        assertEquals(41L, task.id); assertEquals(1L, task.groupId); assertTrue(task.isCompleted)
        assertEquals(540, task.scheduleStartMinute); assertEquals(600, task.scheduleEndMinute)
        assertEquals(123L, task.createdAt); assertEquals(456L, task.updatedAt)
        assertFalse(task.inheritsGroupSchedule)
    }
    @Test fun move_without_inheritance_keeps_the_previous_effective_schedule() = runBlocking {
        groups.save(TaskGroup(name = "学习", scheduleStartMinute = 540, scheduleEndMinute = 600, repeatDaysMask = 31))
        val group = db.taskGroupDao().all().first { it.id != 1L }
        db.focusTaskDao().insert(FocusTaskEntity(id = 42, title = "读书", groupId = group.id,
            inheritsGroupSchedule = true, createdAt = 1, updatedAt = 1))
        groups.moveTask(42, 1, inherit = false)
        val task = db.taskGroupDao().task(42)!!
        assertEquals(1L, task.groupId); assertEquals(540, task.scheduleStartMinute); assertFalse(task.inheritsGroupSchedule)
    }
    @Test fun completing_current_manual_task_advances_with_the_same_deadline() = runBlocking {
        groups.ensureUngrouped()
        db.focusTaskDao().insert(FocusTaskEntity(id = 1, title = "第一项", createdAt = 0, updatedAt = 0))
        db.focusTaskDao().insert(FocusTaskEntity(id = 2, title = "第二项", sortOrder = 1, createdAt = 0, updatedAt = 0))
        groups.activate(1, 1000, 9000)
        groups.complete(1, true, 5000)
        val next = db.taskGroupDao().task(2)!!
        assertTrue(next.isManualActive); assertEquals(9000L, next.manualUntil); assertEquals(5000L, next.manualStartedAt)
    }
    @Test fun reset_quota_does_not_delete_displays_and_decisions_are_idempotent() = runBlocking {
        val sessionId = db.appUsageSessionDao().insert(AppUsageSessionEntity(packageName = "target", appName = "应用", startedAt = 1000, toneKey = "gentle"))
        val displays = RoomReminderDisplayRepository(db, db.reminderDisplayEventDao(), db.appUsageSessionDao())
        displays.recordDisplay("one", sessionId, 2000, ReminderDisplayKind.INITIAL, 0, 5)
        val actions = ReminderEventRepository(db)
        assertTrue(actions.record("one", sessionId, "rest_5m"))
        assertFalse(actions.record("one", sessionId, "intentional_10m"))
        displays.resetSince(0)
        assertEquals(0, displays.countSince(0))
        assertEquals(1, db.reminderAnalyticsDao().displays(0, Long.MAX_VALUE).first().size)
        assertEquals(1, db.reminderAnalyticsDao().actions(0, Long.MAX_VALUE).first().size)
        displays.recordDisplay("two", sessionId, 2000, ReminderDisplayKind.INITIAL, 0, 5)
        assertEquals(1, displays.countSince(0)) // Identical timestamps still count after reset because the cursor uses IDs.
        assertEquals(2, db.reminderAnalyticsDao().displays(0, Long.MAX_VALUE).first().size)
    }
    @Test fun rebinding_preserves_old_snooze_without_splitting_usage_or_claiming_new_context() = runBlocking {
        val dao = db.appUsageSessionDao()
        val id = dao.insert(AppUsageSessionEntity(packageName = "target", appName = "应用", startedAt = 1000,
            taskId = 1, toneKey = "gentle", snoozeUntil = 5000, remindedAt = 2000))
        dao.bindTask(id, 2, 3000)
        val rebound = dao.byId(id)!!
        assertEquals(1000L, rebound.startedAt); assertNull(rebound.endedAt)
        assertEquals(2000L, rebound.remindedAt); assertEquals(5000L, rebound.snoozeUntil)
        assertEquals(0, dao.markReminderForContext(id, 0, 4000))
        assertEquals(1, dao.markReminderForContext(id, 3000, 4000))
        dao.setSnoozeUntil(id, 9000)
        assertEquals(0, dao.claimSnoozeForContext(id, 0, 5000, 10000))
        assertEquals(0, dao.claimSnoozeForContext(id, 3000, 9000, 8000))
        assertEquals(1, dao.claimSnoozeForContext(id, 3000, 9000, 10000))
    }
    @Test fun forced_display_is_in_history_but_does_not_consume_normal_quota() = runBlocking {
        val displays = RoomReminderDisplayRepository(db, db.reminderDisplayEventDao(), db.appUsageSessionDao())
        displays.recordDisplay("forced", 1, 2000, ReminderDisplayKind.FORCED_REDISPLAY, 0, Int.MAX_VALUE)
        assertEquals(0, displays.countSince(0))
        assertEquals(1, db.reminderAnalyticsDao().displays(0, Long.MAX_VALUE).first().size)
    }
    @Test fun live_statistics_end_at_now_and_count_all_display_attempts() = runBlocking {
        val now = java.time.ZonedDateTime.parse("2026-09-09T09:10:00+08:00[Asia/Shanghai]")
        val start = now.minusMinutes(10).toInstant().toEpochMilli()
        val id = db.appUsageSessionDao().insert(AppUsageSessionEntity(packageName = "target", appName = "应用",
            startedAt = start, toneKey = "gentle"))
        repeat(3) { index -> db.reminderDisplayEventDao().insert(ReminderDisplayEventEntity(attemptId = "event-$index",
            sessionId = id, displayedAt = start + index * 1000L, kind = "initial")) }
        val repository = StatisticsRepository(db.reminderAnalyticsDao())
        val snapshot = repository.observe(com.example.focus_app.domain.model.StatsRange.TODAY,
            com.example.focus_app.domain.stats.ActivityPeriod.MONTH, now.toLocalDate(), now).first()
        assertEquals(10, snapshot.usage.totalDurationMinutes)
        assertEquals(1, snapshot.usage.openCount)
        assertEquals(1f, snapshot.coverage!!, 0.001f)
        assertEquals(3, snapshot.days.first { it.date == now.toLocalDate() }.count)
        assertEquals(0, snapshot.decisions.sumOf { it.count })
    }

}
