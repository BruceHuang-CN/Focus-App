package com.example.focus_app.domain.model

enum class DetectionMode(val key: String) {
    REALTIME("realtime"),
    COMPATIBILITY("compatibility");

    companion object {
        fun fromKey(key: String): DetectionMode =
            entries.find { it.key.equals(key, ignoreCase = true) } ?: REALTIME
    }
}
