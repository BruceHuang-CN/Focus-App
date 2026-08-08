package com.example.focus_app.domain.model

enum class AppThemeMode(val key: String) {
    SYSTEM("system"),
    DAY("day"),
    NIGHT("night");

    companion object {
        fun fromKey(key: String?): AppThemeMode =
            entries.find { it.key.equals(key, ignoreCase = true) } ?: SYSTEM
    }
}

enum class AppThemeColor(val key: String) {
    MINT("mint"),
    BLUE("blue"),
    ORANGE("orange"),
    GRAPHITE("graphite");

    companion object {
        fun fromKey(key: String?): AppThemeColor =
            entries.find { it.key.equals(key, ignoreCase = true) } ?: MINT
    }
}
