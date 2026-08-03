package com.example.focus_app.domain.task

import com.example.focus_app.domain.model.FocusTask
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneOffset
import java.time.ZonedDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ActiveTaskResolverTest {
    private val resolver = ActiveTaskResolver()

    @Test
    fun scheduled_task_temporarily_overrides_manual_task_and_ends_exclusively() {
        val manual = task(id = 1, manual = true)
        val scheduled = task(
            id = 2,
            start = 9 * 60,
            end = 10 * 60,
            days = MONDAY_MASK
        )

        assertEquals(2L, resolver.resolve(listOf(manual, scheduled), mondayAt(9, 30))?.id)
        assertEquals(1L, resolver.resolve(listOf(manual, scheduled), mondayAt(10, 0))?.id)
    }

    @Test
    fun schedule_only_matches_its_repeat_day_mask() {
        val manual = task(id = 1, manual = true)
        val mondaySchedule = task(
            id = 2,
            start = 9 * 60,
            end = 10 * 60,
            days = MONDAY_MASK
        )

        assertEquals(2L, resolver.resolve(listOf(manual, mondaySchedule), mondayAt(9, 30))?.id)
        assertEquals(1L, resolver.resolve(listOf(manual, mondaySchedule), tuesdayAt(9, 30))?.id)
    }

    @Test
    fun completed_task_cannot_be_resolved() {
        val completedManual = task(id = 1, completed = true, manual = true)
        val completedSchedule = task(
            id = 2,
            completed = true,
            start = 9 * 60,
            end = 10 * 60,
            days = MONDAY_MASK
        )

        assertNull(resolver.resolve(listOf(completedManual, completedSchedule), mondayAt(9, 30)))
    }

    private fun task(
        id: Long,
        completed: Boolean = false,
        manual: Boolean = false,
        start: Int? = null,
        end: Int? = null,
        days: Int = 0
    ) = FocusTask(
        id = id,
        title = "Task $id",
        isCompleted = completed,
        isManualActive = manual,
        scheduleStartMinute = start,
        scheduleEndMinute = end,
        repeatDaysMask = days,
        createdAt = 0L,
        updatedAt = 0L
    )

    private fun mondayAt(hour: Int, minute: Int): ZonedDateTime = at(2026, 8, 3, hour, minute)

    private fun tuesdayAt(hour: Int, minute: Int): ZonedDateTime = at(2026, 8, 4, hour, minute)

    private fun at(year: Int, month: Int, day: Int, hour: Int, minute: Int): ZonedDateTime =
        ZonedDateTime.of(LocalDate.of(year, month, day), LocalTime.of(hour, minute), ZoneOffset.UTC)

    private companion object {
        const val MONDAY_MASK = 1 shl 0
    }
}
