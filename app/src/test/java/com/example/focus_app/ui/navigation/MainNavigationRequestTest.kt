package com.example.focus_app.ui.navigation

import org.junit.Assert.assertEquals
import org.junit.Test

class MainNavigationRequestTest {
    @Test
    fun task_request_keeps_home_as_stable_graph_root() {
        assertEquals(
            Screen.Home.route,
            mainStartDestination(onboardingDone = true, openTasksRequested = true)
        )
    }

    @Test
    fun task_request_does_not_bypass_onboarding() {
        assertEquals(
            Screen.Onboarding.route,
            mainStartDestination(onboardingDone = false, openTasksRequested = true)
        )
    }

    @Test
    fun ordinary_launch_starts_on_home_after_onboarding() {
        assertEquals(
            Screen.Home.route,
            mainStartDestination(onboardingDone = true, openTasksRequested = false)
        )
    }
}
