package com.example.focus_app.service

import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ReminderAttemptIdGenerator @Inject constructor() {
    fun newId(): String = UUID.randomUUID().toString()
}
