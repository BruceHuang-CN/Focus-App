package com.example.focus_app.domain.model

enum class ReturnDestination(val key: String) {
    FOCUS("focus"),
    HOME("home"),
    CUSTOM("custom");

    companion object {
        fun fromKey(key: String): ReturnDestination =
            entries.find { it.key.equals(key, ignoreCase = true) } ?: FOCUS
    }
}
