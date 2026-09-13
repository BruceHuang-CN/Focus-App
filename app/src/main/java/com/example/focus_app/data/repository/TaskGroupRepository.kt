package com.example.focus_app.data.repository

import androidx.room.withTransaction
import com.example.focus_app.data.local.AppDatabase
import com.example.focus_app.data.local.dao.TaskGroupDao
import com.example.focus_app.data.local.entity.*
import com.example.focus_app.domain.model.TaskGroup
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TaskGroupRepository @Inject constructor(private val db: AppDatabase, private val dao: TaskGroupDao) {
    fun observeSnapshot() = dao.observeGroupsWithTasks()
    suspend fun writeTask(task: com.example.focus_app.domain.model.FocusTask, insert: Boolean) = db.withTransaction {
        ensureUngrouped()
        val valid = if (dao.all().any { it.id == task.groupId }) task else task.copy(groupId = 1, inheritsGroupSchedule = false)
        if (insert) db.focusTaskDao().insert(valid.toEntity()) else db.focusTaskDao().update(valid.toEntity())
    }
    suspend fun ensureUngrouped() { dao.insert(TaskGroupEntity(id = 1, name = "未分组")) }
    suspend fun save(group: TaskGroup): ScheduleValidation = db.withTransaction {
        ensureUngrouped()
        if (group.name.isBlank()) return@withTransaction ScheduleValidation.END_NOT_AFTER_START
        val start = group.scheduleStartMinute
        val end = group.scheduleEndMinute
        if ((start == null) != (end == null) || (start != null && end != null &&
                (start !in 0..1439 || end !in 1..1440 || end <= start || group.repeatDaysMask == 0))) {
            return@withTransaction ScheduleValidation.END_NOT_AFTER_START
        }
        if (start != null && end != null && dao.all().any { other ->
            other.id != group.id && (other.repeatDaysMask and group.repeatDaysMask) != 0 &&
                other.scheduleStartMinute?.let { it < end } == true &&
                other.scheduleEndMinute?.let { start < it } == true
        }) return@withTransaction ScheduleValidation.OVERLAP
        if (group.id == 0L) dao.insert(group.copy(name = group.name.trim(), sortOrder = System.currentTimeMillis()).toEntity())
        else dao.update(group.copy(name = group.name.trim()).toEntity())
        // A changed schedule is a new activation context; never carry a manual override through it.
        dao.tasks(group.id).filter { it.isManualActive }.forEach {
            db.focusTaskDao().update(it.copy(isManualActive = false, manualStartedAt = null, manualUntil = null))
        }
        ScheduleValidation.VALID
    }
    suspend fun moveTask(taskId: Long, groupId: Long, inherit: Boolean) = db.withTransaction {
        ensureUngrouped()
        if (dao.all().none { it.id == groupId }) return@withTransaction
        val raw = dao.task(taskId) ?: return@withTransaction
        val originalGroup = dao.all().firstOrNull { it.id == raw.groupId }
        val task = if (raw.inheritsGroupSchedule && originalGroup != null) raw.copy(
            scheduleStartMinute = originalGroup.scheduleStartMinute, scheduleEndMinute = originalGroup.scheduleEndMinute,
            repeatDaysMask = originalGroup.repeatDaysMask) else raw
        db.focusTaskDao().update(task.copy(groupId = groupId, inheritsGroupSchedule = inherit,
            sortOrder = (dao.tasks(groupId).maxOfOrNull { it.sortOrder } ?: 0) + 1,
            isManualActive = false, manualStartedAt = null, manualUntil = null))
    }
    /** Delete the container only. Materialize inherited times before moving tasks to 未分组. */
    suspend fun deleteGroup(id: Long) = db.withTransaction {
        if (id == 1L) return@withTransaction
        ensureUngrouped()
        val group = dao.all().firstOrNull { it.id == id } ?: return@withTransaction
        dao.tasks(id).forEach { task ->
            db.focusTaskDao().update(task.copy(groupId = 1, inheritsGroupSchedule = false,
                scheduleStartMinute = if (task.inheritsGroupSchedule) group.scheduleStartMinute else task.scheduleStartMinute,
                scheduleEndMinute = if (task.inheritsGroupSchedule) group.scheduleEndMinute else task.scheduleEndMinute,
                repeatDaysMask = if (task.inheritsGroupSchedule) group.repeatDaysMask else task.repeatDaysMask,
                isManualActive = false, manualStartedAt = null, manualUntil = null))
        }
        dao.delete(id)
    }
    suspend fun activate(id: Long, start: Long, end: Long) = db.withTransaction {
        if (dao.task(id)?.isCompleted != false) return@withTransaction
        dao.clearManual()
        dao.activate(id, start, end)
    }
    suspend fun complete(id: Long, completed: Boolean, now: Long) = db.withTransaction {
        val task = dao.task(id) ?: return@withTransaction
        db.focusTaskDao().setCompleted(id, completed, now)
        if (completed && task.isManualActive && (task.manualUntil ?: 0) > now) {
            dao.tasks(task.groupId).firstOrNull { !it.isCompleted && it.id != id }?.let {
                dao.activate(it.id, now, task.manualUntil!!)
            }
        }
    }
}
