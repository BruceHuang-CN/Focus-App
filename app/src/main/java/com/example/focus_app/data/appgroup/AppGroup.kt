package com.example.focus_app.data.appgroup

import com.example.focus_app.data.repository.AppInfo

data class AppGroup(
    val id: String,
    val name: String,
    val apps: List<AppInfo>
)
