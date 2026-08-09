package com.example.focus_app.ui.navigation

import org.junit.Assert.assertEquals
import org.junit.Test

class SettingsBackRequestDispatcherTest {
    @Test
    fun system_back_is_forwarded_to_the_active_settings_screen() {
        val dispatcher = SettingsBackRequestDispatcher()
        var requests = 0
        dispatcher.register { requests++ }

        dispatcher.request()

        assertEquals(1, requests)
    }

    @Test
    fun cleared_settings_screen_does_not_receive_system_back() {
        val dispatcher = SettingsBackRequestDispatcher()
        var requests = 0
        dispatcher.register { requests++ }
        dispatcher.register(null)

        dispatcher.request()

        assertEquals(0, requests)
    }
}
