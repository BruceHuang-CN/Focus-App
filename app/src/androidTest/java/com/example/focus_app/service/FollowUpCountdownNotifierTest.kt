package com.example.focus_app.service

import android.app.NotificationManager
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class FollowUpCountdownNotifierTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val notifier = AndroidFollowUpCountdownNotifier(context)

    @After
    fun tearDown() {
        notifier.cancel(TEST_SESSION_ID)
    }

    @Test
    fun showing_countdown_creates_a_high_importance_heads_up_channel() {
        notifier.show(sessionId = TEST_SESSION_ID, delayMillis = 300_000L)

        val manager = context.getSystemService(NotificationManager::class.java)
        val channel = manager.getNotificationChannel(EXPECTED_CHANNEL_ID)

        assertNotNull(channel)
        assertEquals(NotificationManager.IMPORTANCE_HIGH, channel!!.importance)
        assertNotNull(channel.sound)
        assertFalse(channel.shouldVibrate())
    }

    private companion object {
        const val TEST_SESSION_ID = 937L
        const val EXPECTED_CHANNEL_ID = "focus_follow_up_countdown_v3"
    }
}
