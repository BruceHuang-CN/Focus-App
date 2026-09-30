package com.example.focus_app.data.summary

import android.content.Context
import com.example.focus_app.R
import com.example.focus_app.data.language.AppLanguage
import com.example.focus_app.data.repository.*
import com.example.focus_app.domain.model.StatsRange
import com.example.focus_app.domain.stats.ActivityPeriod
import com.example.focus_app.domain.summary.*
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.ZonedDateTime

@Singleton
class DailySummaryRepository @Inject constructor(
    private val tasks: TaskRepository,
    private val statistics: StatisticsRepository,
    private val moods: MoodRepository,
    private val settings: SettingsRepository,
    private val ai: AiRepository,
    private val store: DailySummaryStore,
    @ApplicationContext private val context: Context
) {
    private val mutex = Mutex()
    private val mutableGenerating = MutableStateFlow(false)
    val generating = mutableGenerating.asStateFlow()
    val record = store.record

    /** Callers own the job; a Worker and a visible page cannot generate concurrently. */
    suspend fun generate(force: Boolean = false, permitted: () -> Boolean = { true }): DailySummaryRecord? = mutex.withLock {
        if (!permitted()) return@withLock null
        val now = ZonedDateTime.now()
        val date = now.toLocalDate()
        val language = AppLanguage.tag(context)
        val previous = record.value
        if (!force && previous?.facts?.date == date.toString() && previous.languageTag == language && !previous.text.isNullOrBlank()) {
            return@withLock previous
        }
        mutableGenerating.value = true
        try {
            val from = date.atStartOfDay(now.zone).toInstant().toEpochMilli()
            val to = now.toInstant().toEpochMilli() + 1
            val stats = statistics.observe(StatsRange.TODAY, ActivityPeriod.WEEK, date, now).first()
            val mood = moods.getLatestMood()?.takeIf { it.timestamp in from until to }?.mood
            val facts = DailySummaryFacts(date.toString(), tasks.completedCountBetween(from, to),
                stats.usage.totalDurationMinutes, stats.usage.openCount, mood)
            if (!permitted()) return@withLock null
            val response = ai.generateDailySummary(facts, settings.getSettings(), language)
            if (!permitted()) return@withLock null
            val result = DailySummaryRecord(facts, now.toInstant().toEpochMilli(), language,
                text = response.getOrNull(), error = response.exceptionOrNull()?.message)
            store.saveRecord(result)
            result
        } finally { mutableGenerating.value = false }
    }
}
