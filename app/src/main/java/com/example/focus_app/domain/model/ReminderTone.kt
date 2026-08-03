package com.example.focus_app.domain.model

enum class ReminderTone(val key: String) {
    GENTLE("gentle"),
    DIRECT("direct"),
    SARCASTIC("sarcastic"),
    CUSTOM("custom");

    companion object {
        fun fromKey(key: String): ReminderTone =
            entries.find { it.key.equals(key, ignoreCase = true) } ?: GENTLE
    }
}
