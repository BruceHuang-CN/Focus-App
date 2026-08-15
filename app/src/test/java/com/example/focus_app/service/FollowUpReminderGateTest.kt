package com.example.focus_app.service

import com.example.focus_app.domain.model.AppUsageSession
import org.junit.Assert.assertEquals
import org.junit.Test

class FollowUpReminderGateTest {

    private val gate = FollowUpReminderGate()
    private val session = AppUsageSession(
        id = 100L,
        packageName = "com.ss.android.ugc.aweme",
        appName = "抖音",
        startedAt = 1_000L,
        toneKey = "gentle"
    )

    @Test
    fun when_foreground_cannot_be_verified_reminder_retries() {
        val decision = gate.decide(
            ok().copy(foregroundSnapshot = ForegroundSnapshot.Unknown)
        )
        assertEquals(FollowUpDecision.RETRY, decision)
    }

    @Test
    fun when_foreground_is_a_different_package_reminder_is_skipped() {
        val decision = gate.decide(
            ok().copy(
                foregroundSnapshot = ForegroundSnapshot.Confirmed(
                    "com.tencent.mm",
                    ForegroundSource.USAGE_EVENTS
                )
            )
        )
        assertEquals(FollowUpDecision.SKIP, decision)
    }

    @Test
    fun when_device_is_locked_decision_is_retry() {
        val decision = gate.decide(
            ok().copy(deviceInteractive = false)
        )
        assertEquals(FollowUpDecision.RETRY, decision)
    }

    @Test
    fun when_session_is_closed_decision_is_skip() {
        val decision = gate.decide(
            ok().copy(session = session.copy(endedAt = 9_000L))
        )
        assertEquals(FollowUpDecision.SKIP, decision)
    }

    @Test
    fun when_session_does_not_exist_decision_is_skip() {
        val decision = gate.decide(
            ok().copy(session = null)
        )
        assertEquals(FollowUpDecision.SKIP, decision)
    }

    @Test
    fun when_guardian_is_disabled_decision_is_skip() {
        val decision = gate.decide(
            ok().copy(guardianEnabled = false)
        )
        assertEquals(FollowUpDecision.SKIP, decision)
    }

    @Test
    fun when_quota_is_exhausted_decision_is_skip() {
        val decision = gate.decide(
            ok().copy(remindedCountSinceWindow = 3, maxRemindersPerWindow = 3)
        )
        assertEquals(FollowUpDecision.SKIP, decision)
    }

    @Test
    fun when_everything_is_ok_decision_is_show() {
        assertEquals(FollowUpDecision.SHOW, gate.decide(ok()))
    }

    private fun ok() = FollowUpGateInput(
        guardianEnabled = true,
        session = session,
        currentOpenSessionId = session.id,
        targetPackages = listOf("com.ss.android.ugc.aweme"),
        deviceInteractive = true,
        foregroundSnapshot = ForegroundSnapshot.Confirmed(
            "com.ss.android.ugc.aweme",
            ForegroundSource.REALTIME
        ),
        remindedCountSinceWindow = 2,
        maxRemindersPerWindow = 3
    )
}
