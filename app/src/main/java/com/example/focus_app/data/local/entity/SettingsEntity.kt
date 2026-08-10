package com.example.focus_app.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Ignore
import androidx.room.PrimaryKey

@Entity(tableName = "settings")
data class SettingsEntity(
    @PrimaryKey val id: Int = 1,
    val targetApps: String,
    val remindDelayMinutes: Int = 2,
    val maxRemindsPerHour: Int = 3,
    val aiProvider: String = "deepseek",
    val apiEndpoint: String = "https://api.deepseek.com",
    @ColumnInfo(name = "apiKey") val legacyApiKey: String = "",
    val aiModel: String = "deepseek-v4-flash",
    val aiPersonality: String = "gentle",
    val enableAccessibility: Boolean = false,
    val enableBreathingPause: Boolean = true,
    @ColumnInfo(defaultValue = "10") val reminderDelaySeconds: Int = 10,
    @ColumnInfo(defaultValue = "60") val reminderWindowMinutes: Int = 60,
    @ColumnInfo(defaultValue = "3") val maxRemindersPerWindow: Int = 3,
    @ColumnInfo(defaultValue = "'focus'") val returnDestination: String = "focus",
    @ColumnInfo(defaultValue = "'realtime'") val detectionMode: String = "realtime",
    @ColumnInfo(defaultValue = "30") val dailyShortVideoLimitMinutes: Int = 30,
    @ColumnInfo(defaultValue = "'gentle'") val toneKey: String = "gentle",
    @ColumnInfo(defaultValue = "''") val customToneInstruction: String = "",
    @ColumnInfo(defaultValue = "1") val guardianEnabled: Boolean = true
) {
    @get:Ignore
    val apiKey: String
        get() = legacyApiKey

    @Ignore
    constructor(
        id: Int = 1,
        targetApps: String,
        remindDelayMinutes: Int = 2,
        maxRemindsPerHour: Int = 3,
        aiProvider: String = "deepseek",
        apiEndpoint: String = "https://api.deepseek.com",
        apiKey: String = "",
        aiModel: String = "deepseek-v4-flash",
        aiPersonality: String = "gentle",
        enableAccessibility: Boolean = false,
        enableBreathingPause: Boolean = true
    ) : this(
        id = id,
        targetApps = targetApps,
        remindDelayMinutes = remindDelayMinutes,
        maxRemindsPerHour = maxRemindsPerHour,
        aiProvider = aiProvider,
        apiEndpoint = apiEndpoint,
        legacyApiKey = apiKey,
        aiModel = aiModel,
        aiPersonality = aiPersonality,
        enableAccessibility = enableAccessibility,
        enableBreathingPause = enableBreathingPause,
        reminderDelaySeconds = if (remindDelayMinutes == 0) {
            3
        } else {
            remindDelayMinutes * 60
        },
        maxRemindersPerWindow = maxRemindsPerHour,
        toneKey = aiPersonality
    )
}
