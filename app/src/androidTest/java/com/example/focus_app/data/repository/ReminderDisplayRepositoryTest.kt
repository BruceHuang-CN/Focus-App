package com.example.focus_app.data.repository

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.focus_app.data.local.AppDatabase
import com.example.focus_app.data.local.entity.AppUsageSessionEntity
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ReminderDisplayRepositoryTest {
    private lateinit var database: AppDatabase
    private lateinit var repository: ReminderDisplayRepository
    private var sessionId: Long = 0L

    @Before
    fun setUp() = runTest {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext<Context>(),
            AppDatabase::class.java
        ).allowMainThreadQueries().build()
        sessionId = database.appUsageSessionDao().insert(
            AppUsageSessionEntity(
                packageName = "target",
                appName = "Target",
                startedAt = 1_000L,
                toneKey = "gentle"
            )
        )
        repository = RoomReminderDisplayRepository(
            database,
            database.reminderDisplayEventDao(),
            database.appUsageSessionDao()
        )
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun same_attempt_is_idempotent_and_quota_never_exceeds_limit() = runTest {
        val first = repository.recordDisplay(
            "a", sessionId, 2_000L, ReminderDisplayKind.INITIAL, 0L, 1
        )
        val duplicate = repository.recordDisplay(
            "a", sessionId, 2_000L, ReminderDisplayKind.INITIAL, 0L, 1
        )
        val blocked = repository.recordDisplay(
            "b", sessionId, 3_000L, ReminderDisplayKind.FOLLOW_UP, 0L, 1
        )

        assertEquals(ReminderDisplayResult.Displayed(1), first)
        assertEquals(ReminderDisplayResult.Displayed(1), duplicate)
        assertEquals(ReminderDisplayResult.QuotaExceeded, blocked)
        assertEquals(1, repository.countSince(0L))
    }

    @Test
    fun three_visible_attempts_in_one_session_count_as_three() = runTest {
        repository.recordDisplay("a", sessionId, 2_000L, ReminderDisplayKind.INITIAL, 0L, 5)
        repository.recordDisplay("b", sessionId, 3_000L, ReminderDisplayKind.FOLLOW_UP, 0L, 5)
        repository.recordDisplay("c", sessionId, 4_000L, ReminderDisplayKind.FOLLOW_UP, 0L, 5)

        assertEquals(3, repository.countSince(0L))
        assertEquals(listOf(2_000L, 3_000L, 4_000L), repository.timesSince(0L))
    }
}
