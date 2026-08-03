package com.example.focus_app.data.repository

import com.example.focus_app.data.local.dao.FocusTaskDao
import com.example.focus_app.data.local.entity.toDomain
import com.example.focus_app.data.local.entity.toEntity
import com.example.focus_app.domain.model.FocusTask
import com.example.focus_app.domain.task.ActiveTaskResolver
import com.example.focus_app.domain.time.Clock
import com.example.focus_app.domain.time.SystemClock
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.map
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZonedDateTime
import javax.inject.Inject
import javax.inject.Singleton

enum class ScheduleValidation {
    VALID,
    END_NOT_AFTER_START,
    OVERLAP
}

@Singleton
class TaskRepository(
    private val dao: FocusTaskDao,
    private val clock: Clock
) {
    @Inject
    constructor(dao: FocusTaskDao) : this(dao, SystemClock)

    private val resolver = ActiveTaskResolver()

    fun observeAll(): Flow<List<FocusTask>> = dao.observeAll().map { tasks ->
        tasks.map { it.toDomain() }
    }

    fun observeActive(): Flow<FocusTask?> = activeTaskFlow().distinctUntilChanged()

    fun observeActiveEvents(): Flow<FocusTask?> = activeTaskFlow()

    @OptIn(ExperimentalCoroutinesApi::class)
    private fun activeTaskFlow(): Flow<FocusTask?> = observeAll().flatMapLatest { tasks ->
        flow {
            while (true) {
                val now = clock.now()
                emit(resolver.resolve(tasks, now))
                val waitMillis = millisUntilNextScheduleBoundary(tasks, now)
                if (waitMillis == null) awaitCancellation()
                delay(waitMillis)
            }
        }
    }

    suspend fun create(task: FocusTask): ScheduleValidation {
        val validation = validateSchedule(task, observeAll().first())
        if (validation == ScheduleValidation.VALID) {
            val now = clock.nowMillis()
            dao.insert(task.copy(createdAt = now, updatedAt = now).toEntity())
        }
        return validation
    }

    suspend fun update(task: FocusTask): ScheduleValidation {
        val validation = validateSchedule(task, observeAll().first())
        if (validation == ScheduleValidation.VALID) {
            val now = clock.nowMillis()
            dao.update(task.copy(updatedAt = now).toEntity())
        }
        return validation
    }

    suspend fun delete(task: FocusTask) {
        dao.delete(task.toEntity())
    }

    suspend fun setCompleted(id: Long, isCompleted: Boolean = true) {
        dao.setCompleted(id, isCompleted, clock.nowMillis())
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

    private fun millisUntilNextScheduleBoundary(tasks: List<FocusTask>, now: ZonedDateTime): Long? {
        var nextBoundary: ZonedDateTime? = null
        for (offset in 0..7) {
            val date = now.toLocalDate().plusDays(offset.toLong())
            val dayMask = 1 shl (date.dayOfWeek.value - 1)
            tasks.filter { task ->
                !task.isCompleted &&
                    task.scheduleStartMinute != null &&
                    task.scheduleEndMinute != null &&
                    task.scheduleStartMinute in 0 until 1_440 &&
                    task.scheduleEndMinute in 1..1_440 &&
                    task.scheduleEndMinute > task.scheduleStartMinute &&
                    (task.repeatDaysMask and dayMask) != 0
            }.forEach { task ->
                val boundaries = listOf(
                    scheduleBoundary(date, task.scheduleStartMinute!!, now),
                    scheduleBoundary(date, task.scheduleEndMinute!!, now)
                )
                boundaries.forEach { boundary ->
                    if (boundary.isAfter(now) && (nextBoundary == null || boundary.isBefore(nextBoundary))) {
                        nextBoundary = boundary
                    }
                }
            }
        }
        return nextBoundary?.toInstant()?.toEpochMilli()?.minus(now.toInstant().toEpochMilli())
    }

    private fun scheduleBoundary(date: LocalDate, minute: Int, now: ZonedDateTime): ZonedDateTime {
        val boundaryDate = if (minute == 1_440) date.plusDays(1) else date
        return ZonedDateTime.of(
            boundaryDate,
            LocalTime.of(minute / 60 % 24, minute % 60),
            now.zone
        )
    }
}
