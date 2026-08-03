package com.example.focus_app.data.repository

import com.example.focus_app.data.local.dao.AiReminderCacheDao
import com.example.focus_app.data.local.entity.AiReminderCacheEntity
import com.example.focus_app.domain.time.FakeClock
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class ReminderCacheRepositoryTest {
    @Test
    fun replace_keeps_exactly_three_unique_messages_for_the_key() = runTest {
        val dao = FakeReminderCacheDao()
        val repository = ReminderCacheRepository(dao, FakeClock(1_000L))

        repository.replace(7L, "video.app", "direct", listOf("旧一", "旧二", "旧三"))
        repository.replace(7L, "video.app", "direct", listOf("新一", "新二", "新三"))

        assertEquals(listOf("新一", "新二", "新三"), dao.rows.map { it.text })
    }

    @Test
    fun next_rotates_stably_even_when_clock_does_not_advance() = runTest {
        val dao = FakeReminderCacheDao()
        val repository = ReminderCacheRepository(dao, FakeClock(2_000L))
        repository.replace(7L, "video.app", "direct", listOf("第一条", "第二条", "第三条"))

        val actual = List(4) { repository.next(7L, "video.app", "direct") }

        assertEquals(listOf("第一条", "第二条", "第三条", "第一条"), actual)
        assertEquals(listOf(2_003L, 2_001L, 2_002L), dao.rows.map { it.lastUsedAt })
    }

    @Test
    fun key_is_ready_only_with_exactly_three_cached_rows() = runTest {
        val dao = FakeReminderCacheDao()
        val repository = ReminderCacheRepository(dao, FakeClock(2_000L))
        dao.insertAll(
            listOf("一", "二").map {
                AiReminderCacheEntity(
                    taskId = 7L,
                    appPackageName = "video.app",
                    toneKey = "direct",
                    text = it,
                    createdAt = 1L
                )
            }
        )
        assertEquals(false, repository.isReady(7L, "video.app", "direct"))

        dao.insertAll(
            listOf(
                AiReminderCacheEntity(
                    taskId = 7L,
                    appPackageName = "video.app",
                    toneKey = "direct",
                    text = "三",
                    createdAt = 1L
                )
            )
        )
        assertEquals(true, repository.isReady(7L, "video.app", "direct"))
    }
}

private class FakeReminderCacheDao : AiReminderCacheDao {
    val rows = mutableListOf<AiReminderCacheEntity>()
    private var nextId = 1L

    override suspend fun delete(taskId: Long, packageName: String, toneKey: String) {
        rows.removeAll {
            it.taskId == taskId && it.appPackageName == packageName && it.toneKey == toneKey
        }
    }

    override suspend fun insertAll(entries: List<AiReminderCacheEntity>) {
        rows += entries.map { it.copy(id = nextId++) }
    }

    override suspend fun selectNext(
        taskId: Long,
        packageName: String,
        toneKey: String
    ): AiReminderCacheEntity? = rows
        .filter { it.taskId == taskId && it.appPackageName == packageName && it.toneKey == toneKey }
        .minWithOrNull(compareBy<AiReminderCacheEntity> { it.lastUsedAt ?: Long.MIN_VALUE }.thenBy { it.id })

    override suspend fun maxLastUsedAt(taskId: Long, packageName: String, toneKey: String): Long? =
        rows.filter {
            it.taskId == taskId && it.appPackageName == packageName && it.toneKey == toneKey
        }.mapNotNull { it.lastUsedAt }.maxOrNull()

    override suspend fun updateLastUsedAt(id: Long, usedAt: Long) {
        val index = rows.indexOfFirst { it.id == id }
        rows[index] = rows[index].copy(lastUsedAt = usedAt)
    }

    override suspend fun count(taskId: Long, packageName: String, toneKey: String): Int =
        rows.count {
            it.taskId == taskId && it.appPackageName == packageName && it.toneKey == toneKey
        }
}
