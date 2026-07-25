package com.example.focus_app.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "settings")
data class SettingsEntity(
    @PrimaryKey val id: Int = 1,
    val targetApps: String,
    val remindDelayMinutes: Int = 2,
    val maxRemindsPerHour: Int = 3,
    val aiProvider: String = "deepseek",
    val apiEndpoint: String = "https://api.deepseek.com",
    val apiKey: String = "",
    val aiModel: String = "deepseek-chat",
    val aiPersonality: String = "gentle",
    val enableAccessibility: Boolean = false,
    val enableBreathingPause: Boolean = true
)
