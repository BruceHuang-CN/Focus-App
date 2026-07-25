package com.example.focus_app.data.repository

import com.example.focus_app.data.local.dao.AppUsageEventDao
import com.example.focus_app.data.local.entity.AppUsageEventEntity
import com.example.focus_app.domain.model.AppUsageEvent
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.text.SimpleDateFormat
import java.util.*
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AppUsageRepository @Inject constructor(private val dao: AppUsageEventDao) {
    fun getEventsSince(since: Long): Flow<List<AppUsageEvent>> = dao.getEventsSince(since).map { it.map { e -> e.toDomain() } }
    fun getRemindedEventsSince(since: Long): Flow<List<AppUsageEvent>> = dao.getRemindedEventsSince(since).map { it.map { e -> e.toDomain() } }

    suspend fun insertEvent(packageName: String, appName: String): Long {
        val now = System.currentTimeMillis()
        val hourBucket = SimpleDateFormat("yyyy-MM-dd-HH", Locale.getDefault()).format(Date(now))
        return dao.insert(AppUsageEventEntity(packageName = packageName, appName = appName, openTime = now, hourBucket = hourBucket))
    }

    suspend fun markReminded(eventId: Long) {
        dao.getEventsByHourBucket(currentHourBucket()).find { it.id == eventId }?.let { dao.update(it.copy(reminded = true)) }
    }

    suspend fun markUserActionById(eventId: Long, action: String) {
        dao.getEventsSince(getStartOfToday()).collect { list ->
            list.find { it.id == eventId }?.let { dao.update(it.copy(userAction = action)) }
            throw kotlinx.coroutines.CancellationException()
        }
    }

    suspend fun getRemindedCountThisHour(): Int = dao.getRemindedCountByHourBucket(currentHourBucket())
    suspend fun getOpenCountToday(): Int = dao.getEventCountSince(getStartOfToday())
    suspend fun getExitedCountToday(): Int = dao.getExitedCountSince(getStartOfToday())
    fun currentHourBucket() = SimpleDateFormat("yyyy-MM-dd-HH", Locale.getDefault()).format(Date())

    private fun getStartOfToday(): Long {
        val cal = Calendar.getInstance()
        cal.set(Calendar.HOUR_OF_DAY, 0); cal.set(Calendar.MINUTE, 0); cal.set(Calendar.SECOND, 0); cal.set(Calendar.MILLISECOND, 0)
        return cal.timeInMillis
    }

    private fun AppUsageEventEntity.toDomain() = AppUsageEvent(id, packageName, appName, openTime, reminded, userAction, hourBucket)
}
