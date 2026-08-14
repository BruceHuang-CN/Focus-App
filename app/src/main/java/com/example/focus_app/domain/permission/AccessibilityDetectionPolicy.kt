package com.example.focus_app.domain.permission

fun isAccessibilityDetectionReady(
    userEnabled: Boolean,
    systemEnabled: Boolean
): Boolean = userEnabled && systemEnabled
