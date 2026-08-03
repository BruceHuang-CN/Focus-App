package com.example.focus_app.data.repository

import com.example.focus_app.data.local.dao.FocusTaskDao
import com.example.focus_app.data.local.entity.toDomain
import com.example.focus_app.data.local.entity.toEntity
import com.example.focus_app.domain.model.FocusTask
import com.example.focus_app.domain.task.ActiveTaskResolver
import com.example.focus_app.domain.time.SystemClock
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

enum class ScheduleValidation {
    VALID,
    END_NOT_AFTER_START,
    OVERLAP
}

@Singleton
class TaskRepository @Inject constructor(
    private val dao: FocusTaskDao
) {
    private val resolver = ActiveTaskResolver()

    fun observeAll(): Flow<List<FocusTask>> = dao.observeAll().map { tasks ->
        tasks.map { it.toDomain() }
    }

    fun observeActive(): Flow<FocusTask?> = observeAll().map { tasks ->
        resolver.resolve(tasks, SystemClock.now())
    }

    suspend fun create(task: FocusTask): ScheduleValidation {
        val validation = validateSchedule(task, observeAll().first())
        if (validation == ScheduleValidation.VALID) {
            val now = SystemClock.nowMillis()
            dao.insert(task.copy(createdAt = now, updatedAt = now).toEntity())
        }
        return validation
    }

    suspend fun update(task: FocusTask): ScheduleValidation {
        val validation = validateSchedule(task, observeAll().first())
        if (validation == ScheduleValidation.VALID) {
            val now = SystemClock.nowMillis()
            dao.update(task.copy(updatedAt = now).toEntity())
        }
        return validation
    }

    suspend fun delete(task: FocusTask) {
        dao.delete(task.toEntity())
    }

    suspend fun setCompleted(id: Long, isCompleted: Boolean = true) {
        dao.setCompleted(id, isCompleted, SystemClock.nowMillis())
    }

    suspend fun setManualActive(id: Long) {
        dao.setManualActive(id)
    }

    fun validateSchedule(task: FocusTask, existing: List<FocusTask>): ScheduleValidation {
        val start = task.scheduleStartMinute
        val end = task.scheduleEndMinute
        if (start != null && end != null && end <= start) {
            return ScheduleValidation.END_NOT_AFTER_START
        }
        if (task.isCompleted || start == null || end == null || task.repeatDaysMask == 0) {
            return ScheduleValidation.VALID
        }

        val overlaps = existing.any { other ->
            other.id != task.id &&
                !other.isCompleted &&
                other.scheduleStartMinute != null &&
                other.scheduleEndMinute != null &&
                (other.repeatDaysMask and task.repeatDaysMask) != 0 &&
                start < other.scheduleEndMinute &&
                other.scheduleStartMinute < end
        }
        return if (overlaps) ScheduleValidation.OVERLAP else ScheduleValidation.VALID
    }

    suspend fun completedCountBetween(startedAt: Long, endedAt: Long): Int =
        dao.completedCountBetween(startedAt, endedAt)
}
