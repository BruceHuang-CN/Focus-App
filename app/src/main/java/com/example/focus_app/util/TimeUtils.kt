package com.example.focus_app.util

import java.text.SimpleDateFormat
import java.util.*

object TimeUtils {
    fun formatTime(ts: Long) = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(ts))
    fun formatDateTime(ts: Long) = SimpleDateFormat("MM-dd HH:mm", Locale.getDefault()).format(Date(ts))
}
