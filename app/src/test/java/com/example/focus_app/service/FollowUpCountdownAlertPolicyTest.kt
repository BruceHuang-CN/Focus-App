package com.example.focus_app.service

import android.app.NotificationManager
import androidx.core.app.NotificationCompat
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FollowUpCountdownAlertPolicyTest {
    @Test
    fun countdown_uses_a_new_high_importance_heads_up_channel() {
        val policy = followUpCountdownAlertPolicy()

        assertEquals("focus_follow_up_countdown_v3", policy.channelId)
        assertEquals(NotificationManager.IMPORTANCE_HIGH, policy.channelImportance)
        assertEquals(NotificationCompat.PRIORITY_HIGH, policy.notificationPriority)
        assertTrue(policy.useDefaultSound)
    }
}
