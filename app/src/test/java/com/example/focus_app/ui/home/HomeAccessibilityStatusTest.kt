package com.example.focus_app.ui.home

import org.junit.Assert.assertEquals
import org.junit.Test

class HomeAccessibilityStatusTest {
    @Test
    fun enabled_and_bound_are_reported_as_running() {
        assertEquals(
            "无障碍：系统已开启 · 服务运行中",
            accessibilityStatusLabel(systemEnabled = true, serviceBound = true)
        )
    }

    @Test
    fun enabled_but_unbound_is_reported_as_a_connection_problem() {
        assertEquals(
            "无障碍：系统已开启 · 服务未连接",
            accessibilityStatusLabel(systemEnabled = true, serviceBound = false)
        )
    }

    @Test
    fun disabled_system_switch_takes_priority_over_stale_binding_state() {
        assertEquals(
            "无障碍：系统未开启",
            accessibilityStatusLabel(systemEnabled = false, serviceBound = true)
        )
    }
}
