package com.example.focus_app.data.repository

import com.example.focus_app.data.local.dao.FocusTaskDao
import com.example.focus_app.data.local.entity.toDomain
import com.example.focus_app.data.local.entity.toEntity
import com.example.focus_app.domain.model.FocusTask
import com.example.focus_app.domain.task.ActiveTaskResolver
import com.example.focus_app.domain.time.Clock
import com.example.focus_app.domain.time.SystemClock
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
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
    private val clock: Clock,
    private val groups: TaskGroupRepository? = null
) {
    @Inject
    constructor(dao: FocusTaskDao, groups: TaskGroupRepository) : this(dao, SystemClock, groups)

    constructor(dao: FocusTaskDao) : this(dao, SystemClock, null)

    private val resolver = ActiveTaskResolver()

    fun observeAll(): Flow<List<FocusTask>> = groups?.observeSnapshot()?.map { snapshot ->
        snapshot.flatMap { row -> row.tasks.map { entity ->
            val task = entity.toDomain()
            if (task.inheritsGroupSchedule) task.copy(scheduleStartMinute = row.group.scheduleStartMinute,
                scheduleEndMinute = row.group.scheduleEndMinute, repeatDaysMask = row.group.repeatDaysMask)
            else task
        } }
    } ?: dao.observeAll().map { tasks -> tasks.map { it.toDomain() } }

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
                delay(minOf(waitMillis, 30_000L))
            }
        }
    }

    suspend fun create(task: FocusTask): ScheduleValidation {
        groups?.ensureUngrouped()
        val validation = validateSchedule(task, observeAll().first())
        if (validation == ScheduleValidation.VALID) {
            val now = clock.nowMillis()
            val saved = task.copy(createdAt = now, updatedAt = now)
            if (groups != null) groups.writeTask(saved, insert = true) else dao.insert(saved.toEntity())
        }
        return validation
    }

    suspend fun update(task: FocusTask): ScheduleValidation {
        val validation = validateSchedule(task, observeAll().first())
        if (validation == ScheduleValidation.VALID) {
            val now = clock.nowMillis()
            val saved = task.copy(updatedAt = now, isManualActive = false, manualStartedAt = null, manualUntil = null)
            if (groups != null) groups.writeTask(saved, insert = false) else dao.update(saved.toEntity())
        }
        return validation
    }

    suspend fun delete(task: FocusTask) {
        dao.delete(task.toEntity())
    }

    suspend fun setCompleted(id: Long, isCompleted: Boolean = true) {
        if (groups != null) groups.complete(id, isCompleted, clock.nowMillis())
        else dao.setCompleted(id, isCompleted, clock.nowMillis())
    }

    suspend fun setManualActive(id: Long, minutes: Int = 30) {
        val task = observeAll().first().firstOrNull { it.id == id && !it.isCompleted } ?: return
        val now = clock.now()
        val start = clock.nowMillis()
        val requestedEnd = start + minutes.coerceIn(1, 180) * 60_000L
        val scheduledEnd = com.example.focus_app.domain.task.TaskActivation.window(task, now)?.second
        val end = scheduledEnd ?: requestedEnd
        if (groups != null) groups.activate(id, start, end)
        else {
            dao.clearManualActive()
            dao.update(task.copy(isManualActive = true, manualStartedAt = start, manualUntil = end).toEntity())
        }
    }

    /** Validate optional task context, not whether guardian monitoring is enabled.
     * A taskless session is current when there is no active task. Guardian/app/quotas are checked by callers.
     * Keep rejecting a stale non-null task so an old reminder cannot quote a completed/expired task.
     */
    suspend fun isSessionEligible(session: com.example.focus_app.domain.model.AppUsageSession): Boolean {
        if (session.endedAt != null) return false
        val task = observeActive().first() ?: return session.taskId == null
        return task.id == session.taskId && com.example.focus_app.domain.task.TaskActivation.accepts(
            task, session.taskContextStartedAt.takeIf { it > 0 } ?: session.startedAt, clock.now())
    }

    fun validateSchedule(task: FocusTask, existing: List<FocusTask>): ScheduleValidation {
        val start = task.scheduleStartMinute
        val end = task.scheduleEndMinute
        if (!task.inheritsGroupSchedule && ((start == null) != (end == null) || (start != null && end != null && (end <= start || start !in 0..1439 || end !in 1..1440 || task.repeatDaysMask == 0)))) {
            return ScheduleValidation.END_NOT_AFTER_START
        }
        if (task.inheritsGroupSchedule || task.isCompleted || start == null || end == null || task.repeatDaysMask == 0) {
            return ScheduleValidation.VALID
        }

        val overlaps = existing.any { other ->
            other.id != task.id && (other.groupId != task.groupId || task.groupId == 1L) &&
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
        var nextBoundary: ZonedDateTime? = tasks.filter { !it.isCompleted && it.isManualActive }
            .mapNotNull { it.manualUntil }.filter { it > now.toInstant().toEpochMilli() }.minOrNull()
            ?.let { java.time.Instant.ofEpochMilli(it).atZone(now.zone) }
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
