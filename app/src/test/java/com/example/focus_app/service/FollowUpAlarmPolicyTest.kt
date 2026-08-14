package com.example.focus_app.service

import org.junit.Assert.assertEquals
import org.junit.Test

class FollowUpAlarmPolicyTest {
    @Test
    fun android_11_uses_exact_idle_alarm_without_special_access() {
        assertEquals(
            FollowUpAlarmMode.EXACT_ALLOW_IDLE,
            selectFollowUpAlarmMode(sdkInt = 30, canScheduleExact = false)
        )
    }

    @Test
    fun android_12_without_exact_access_uses_inexact_idle_alarm() {
        assertEquals(
            FollowUpAlarmMode.ALLOW_IDLE,
            selectFollowUpAlarmMode(sdkInt = 31, canScheduleExact = false)
        )
    }

    @Test
    fun android_12_with_exact_access_uses_exact_idle_alarm() {
        assertEquals(
            FollowUpAlarmMode.EXACT_ALLOW_IDLE,
            selectFollowUpAlarmMode(sdkInt = 31, canScheduleExact = true)
        )
    }

    @Test
    fun alarm_identity_is_stable_and_session_scoped() {
        assertEquals("focus://follow-up/7", followUpAlarmData(7L))
        assertEquals("focus://follow-up/8", followUpAlarmData(8L))
    }
}
