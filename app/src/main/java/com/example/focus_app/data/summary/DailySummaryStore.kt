package com.example.focus_app.data.summary

import android.content.Context
import com.example.focus_app.domain.summary.DailySummaryRecord
import com.example.focus_app.domain.summary.DailySummarySettings
import com.google.gson.Gson
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Small local preferences and the latest result only; no Room schema changes or remote storage. */
@Singleton
class DailySummaryStore @Inject constructor(@ApplicationContext context: Context) {
    private val prefs = context.getSharedPreferences("daily_summary", Context.MODE_PRIVATE)
    private val gson = Gson()
    private val mutableSettings = MutableStateFlow(DailySummarySettings(
        prefs.getBoolean("enabled", false), prefs.getInt("minute", 1260).coerceIn(0, 1439), prefs.getLong("revision", 0)
    ))
    val settings = mutableSettings.asStateFlow()
    private val mutableRecord = MutableStateFlow(runCatching {
        prefs.getString("record", null)?.let { gson.fromJson(it, DailySummaryRecord::class.java) }
    }.getOrNull())
    val record = mutableRecord.asStateFlow()

    @Synchronized
    fun updateSettings(enabled: Boolean, minute: Int) {
        val old = settings.value
        val next = DailySummarySettings(enabled, minute.coerceIn(0, 1439), old.revision + 1)
        prefs.edit().putBoolean("enabled", next.enabled).putInt("minute", next.minuteOfDay)
            .putLong("revision", next.revision).apply()
        mutableSettings.value = next
    }

    fun saveRecord(value: DailySummaryRecord) {
        prefs.edit().putString("record", gson.toJson(value)).apply()
        mutableRecord.value = value
    }

    fun wasNotified(date: String) = prefs.getString("notified_date", null) == date
    fun markNotified(date: String) { prefs.edit().putString("notified_date", date).apply() }
}
