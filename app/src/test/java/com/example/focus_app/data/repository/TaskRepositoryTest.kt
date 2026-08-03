package com.example.focus_app.data.repository

import com.example.focus_app.data.local.dao.FocusTaskDao
import com.example.focus_app.data.local.entity.FocusTaskEntity
import com.example.focus_app.domain.model.FocusTask
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class TaskRepositoryTest {
    @Test
    fun schedule_end_must_be_after_schedule_start() {
        val repository = TaskRepository(FakeFocusTaskDao())

        val result = repository.validateSchedule(
            task(id = 1, start = 600, end = 600, days = MONDAY_MASK),
            emptyList()
        )

        assertEquals(ScheduleValidation.END_NOT_AFTER_START, result)
    }

    @Test
    fun overlapping_enabled_schedules_on_the_same_day_are_rejected() {
        val repository = TaskRepository(FakeFocusTaskDao())

        val result = repository.validateSchedule(
            task(id = 1, start = 570, end = 630, days = MONDAY_MASK),
            listOf(task(id = 2, start = 600, end = 660, days = MONDAY_MASK))
        )

        assertEquals(ScheduleValidation.OVERLAP, result)
    }

    @Test
    fun overlapping_schedules_on_different_days_are_valid() {
        val repository = TaskRepository(FakeFocusTaskDao())

        val result = repository.validateSchedule(
            task(id = 1, start = 570, end = 630, days = MONDAY_MASK),
            listOf(task(id = 2, start = 600, end = 660, days = TUESDAY_MASK))
        )

        assertEquals(ScheduleValidation.VALID, result)
    }

    @Test
    fun schedules_that_meet_at_the_boundary_are_valid() {
        val repository = TaskRepository(FakeFocusTaskDao())

        val result = repository.validateSchedule(
            task(id = 1, start = 570, end = 600, days = MONDAY_MASK),
            listOf(task(id = 2, start = 600, end = 660, days = MONDAY_MASK))
        )

        assertEquals(ScheduleValidation.VALID, result)
    }

    @Test
    fun set_manual_active_keeps_the_current_manual_task_when_selected_task_is_completed() = runTest {
        val repository = TaskRepository(
            FakeFocusTaskDao(
                taskEntities(
                    task(id = 1, manual = true),
                    task(id = 2, completed = true)
                )
            )
        )

        repository.setManualActive(2)

        assertEquals(1L, repository.observeActive().first()?.id)
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

    private fun taskEntities(vararg tasks: FocusTask): List<FocusTaskEntity> = tasks.map {
        FocusTaskEntity(
            id = it.id,
            title = it.title,
            isCompleted = it.isCompleted,
            isManualActive = it.isManualActive,
            scheduleStartMinute = it.scheduleStartMinute,
            scheduleEndMinute = it.scheduleEndMinute,
            repeatDaysMask = it.repeatDaysMask,
            createdAt = it.createdAt,
            updatedAt = it.updatedAt
        )
    }

    private companion object {
        const val MONDAY_MASK = 1 shl 0
        const val TUESDAY_MASK = 1 shl 1
    }
}

private class FakeFocusTaskDao(initial: List<FocusTaskEntity> = emptyList()) : FocusTaskDao {
    private val tasks = MutableStateFlow(initial)

    override fun observeAll(): Flow<List<FocusTaskEntity>> = tasks

    override suspend fun insert(task: FocusTaskEntity): Long {
        tasks.value = tasks.value + task
        return task.id
    }

    override suspend fun update(task: FocusTaskEntity) {
        tasks.value = tasks.value.map { if (it.id == task.id) task else it }
    }

    override suspend fun delete(task: FocusTaskEntity) {
        tasks.value = tasks.value.filterNot { it.id == task.id }
    }

    override suspend fun setCompleted(id: Long, isCompleted: Boolean, updatedAt: Long) {
        tasks.value = tasks.value.map { task ->
            if (task.id == id) task.copy(isCompleted = isCompleted, isManualActive = false, updatedAt = updatedAt) else task
        }
    }

    override suspend fun canSetManualActive(id: Long): Boolean =
        tasks.value.any { it.id == id && !it.isCompleted }

    override suspend fun clearManualActive() {
        tasks.value = tasks.value.map { it.copy(isManualActive = false) }
    }

    override suspend fun markManualActive(id: Long) {
        tasks.value = tasks.value.map { task ->
            if (task.id == id && !task.isCompleted) task.copy(isManualActive = true) else task
        }
    }

    override suspend fun setManualActive(id: Long) {
        val selected = tasks.value.firstOrNull { it.id == id } ?: return
        if (selected.isCompleted) return

        tasks.value = tasks.value.map { task ->
            task.copy(isManualActive = task.id == id && !task.isCompleted)
        }
    }

    override suspend fun completedCountBetween(startedAt: Long, endedAt: Long): Int =
        tasks.value.count { it.isCompleted && it.updatedAt in startedAt..endedAt }
}
