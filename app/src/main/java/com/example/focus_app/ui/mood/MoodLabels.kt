package com.example.focus_app.ui.mood

import android.content.Context
import com.example.focus_app.R

// Existing mood values are persisted keys. User-entered notes are never translated.
fun moodDisplayLabel(context: Context, storedMood: String): String {
    val key = storedMood.substringAfterLast(' ')
    val id = when (key) {
        "平静" -> R.string.core_mood_calm
        "专注" -> R.string.core_mood_focused
        "有点累" -> R.string.core_mood_tired
        "烦躁" -> R.string.core_mood_irritated
        "开心" -> R.string.core_mood_happy
        "困了" -> R.string.core_mood_sleepy
        "焦虑" -> R.string.core_mood_anxious
        "无聊" -> R.string.core_mood_bored
        "有干劲" -> R.string.core_mood_energized
        "还行" -> R.string.core_mood_okay
        "难过" -> R.string.core_mood_sad
        "迷茫" -> R.string.core_mood_lost
        else -> return storedMood
    }
    val icon = storedMood.substringBeforeLast(' ', "")
    return if (icon.isBlank()) context.getString(id) else "$icon ${context.getString(id)}"
}
