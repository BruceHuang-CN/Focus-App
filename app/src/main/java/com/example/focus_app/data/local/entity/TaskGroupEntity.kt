package com.example.focus_app.data.local.entity

import androidx.room.*
import com.example.focus_app.domain.model.TaskGroup

@Entity(tableName = "task_groups")
data class TaskGroupEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val scheduleStartMinute: Int? = null,
    val scheduleEndMinute: Int? = null,
    val repeatDaysMask: Int = 0,
    val sortOrder: Long = 0
)
data class TaskGroupWithTasks(
    @Embedded val group: TaskGroupEntity,
    @Relation(parentColumn = "id", entityColumn = "groupId") val tasks: List<FocusTaskEntity>
)
fun TaskGroupEntity.toDomain() = TaskGroup(id, name, scheduleStartMinute, scheduleEndMinute, repeatDaysMask, sortOrder)
fun TaskGroup.toEntity() = TaskGroupEntity(id, name, scheduleStartMinute, scheduleEndMinute, repeatDaysMask, sortOrder)
