package com.example.focus_app.domain.task

import com.example.focus_app.domain.model.FocusTask
import java.time.ZonedDateTime
import org.junit.Assert.*
import org.junit.Test

class ActiveTaskResolverTest {
    private val resolver = ActiveTaskResolver()
    private fun time(value: String) = ZonedDateTime.parse(value)
    private val start = time("2026-09-07T09:00:00+08:00[Asia/Shanghai]")
    private val scheduled = FocusTask(id = 1, title = "学习", scheduleStartMinute = 540, scheduleEndMinute = 600, repeatDaysMask = 1)

    @Test fun schedule_includes_start_but_excludes_end() {
        assertNull(resolver.resolve(listOf(scheduled), start.minusNanos(1)))
        assertEquals(1L, resolver.resolve(listOf(scheduled), start)?.id)
        assertNull(resolver.resolve(listOf(scheduled), start.plusHours(1)))
    }
    @Test fun incomplete_manual_state_does_not_extend_schedule() {
        assertNull(resolver.resolve(listOf(scheduled.copy(isManualActive = true)), start.plusHours(1)))
        assertNull(resolver.resolve(listOf(FocusTask(title = "旧手动任务", isManualActive = true)), start))
    }
    @Test fun finite_manual_selection_has_priority_and_expires() {
        val manual = FocusTask(id = 2, title = "临时任务", isManualActive = true,
            manualStartedAt = start.toInstant().toEpochMilli(), manualUntil = start.plusMinutes(15).toInstant().toEpochMilli())
        assertEquals(2L, resolver.resolve(listOf(scheduled, manual), start.plusMinutes(14))?.id)
        assertEquals(1L, resolver.resolve(listOf(scheduled, manual), start.plusMinutes(15))?.id)
        assertNull(resolver.resolve(listOf(manual), start.minusSeconds(1)))
    }
    @Test fun completed_task_advances_to_next_pending_in_group_order() {
        val next = scheduled.copy(id = 2, sortOrder = 2)
        assertEquals(2L, resolver.resolve(listOf(scheduled.copy(isCompleted = true), next), start)?.id)
        assertNull(resolver.resolve(listOf(next.copy(isCompleted = true)), start))
    }
    @Test fun wrong_weekday_and_previous_weeks_session_are_ineligible() {
        assertNull(resolver.resolve(listOf(scheduled), start.plusDays(1)))
        assertFalse(TaskActivation.accepts(scheduled, start.toInstant().toEpochMilli(), start.plusWeeks(1)))
        assertTrue(TaskActivation.accepts(scheduled, start.toInstant().toEpochMilli(), start.plusMinutes(10)))
    }
    @Test fun midnight_and_dst_use_local_calendar_boundaries() {
        val midnight = scheduled.copy(scheduleStartMinute = 1380, scheduleEndMinute = 1440)
        val late = start.withHour(23)
        assertNotNull(TaskActivation.window(midnight, late.plusMinutes(59)))
        assertNull(TaskActivation.window(midnight, late.plusHours(1)))
        val dst = time("2026-03-08T03:30:00-04:00[America/New_York]")
        val task = scheduled.copy(scheduleStartMinute = 60, scheduleEndMinute = 240, repeatDaysMask = 64)
        val window = TaskActivation.window(task, dst)!!
        assertEquals(2 * 3_600_000L, window.second - window.first)
    }
}
