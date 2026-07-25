package com.example.focus_app.service

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.example.focus_app.ui.reminder.ReminderOverlay
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class ReminderActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val eventId = intent.getLongExtra("event_id", 0)
        val appName = intent.getStringExtra("app_name") ?: "目标App"
        setContent { ReminderOverlay(eventId = eventId, appName = appName, onDismiss = { finish() }) }
    }
}
