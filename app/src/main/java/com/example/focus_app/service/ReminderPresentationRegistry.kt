package com.example.focus_app.service

import android.os.SystemClock
import com.example.focus_app.data.repository.ReminderDisplayKind
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ReminderPresentationRegistry @Inject constructor() {
    private enum class Phase {
        LAUNCHING,
        VISIBLE,
        PENDING_DECISION,
        SNOOZE_TRANSITION
    }

    private data class Presentation(
        val data: ReminderLaunchData,
        val phase: Phase,
        val displayRecorded: Boolean = false,
        val launchDeadlineMillis: Long? = null,
        val snoozeTransitionDeadlineMillis: Long? = null
    )

    internal constructor(elapsedRealtime: () -> Long) : this() {
        elapsedRealtimeProvider = elapsedRealtime
    }

    private var elapsedRealtimeProvider: () -> Long = SystemClock::elapsedRealtime

    @Volatile
    private var activePresentation: Presentation? = null

    @Synchronized
    fun show(data: ReminderLaunchData) {
        activePresentation = Presentation(data, Phase.VISIBLE, displayRecorded = true)
    }

    @Synchronized
    fun confirmVisible(data: ReminderLaunchData): Boolean {
        val presentation = currentPresentation() ?: return false
        if (!presentation.matches(data.sessionId, data.attemptId)) return false
        if (presentation.phase !in setOf(Phase.LAUNCHING, Phase.PENDING_DECISION)) return false
        activePresentation = Presentation(data, Phase.VISIBLE, displayRecorded = true)
        return true
    }

    @Synchronized
    fun markLaunchRequested(data: ReminderLaunchData) {
        val displayRecorded = currentPresentation()
            ?.takeIf { it.matches(data.sessionId, data.attemptId) }
            ?.displayRecorded == true
        activePresentation = Presentation(
            data = data,
            phase = Phase.LAUNCHING,
            displayRecorded = displayRecorded,
            launchDeadlineMillis = elapsedRealtimeProvider() + LAUNCH_GRACE_MILLIS
        )
    }

    @Synchronized
    fun keepSnoozeTransition(sessionId: Long, attemptId: String) {
        val presentation = currentPresentation() ?: return
        if (!presentation.matches(sessionId, attemptId)) return
        activePresentation = presentation.copy(
            phase = Phase.SNOOZE_TRANSITION,
            launchDeadlineMillis = null,
            snoozeTransitionDeadlineMillis =
                elapsedRealtimeProvider() + SNOOZE_TRANSITION_GRACE_MILLIS
        )
    }

    @Synchronized
    fun hide(sessionId: Long, attemptId: String) {
        if (activePresentation?.matches(sessionId, attemptId) == true) activePresentation = null
    }

    @Synchronized
    fun onActivityStopped(sessionId: Long, attemptId: String) {
        val presentation = currentPresentation() ?: return
        if (!presentation.matches(sessionId, attemptId)) return
        if (presentation.phase == Phase.SNOOZE_TRANSITION) return
        activePresentation = if (presentation.data.forceReminder) {
            presentation.copy(
                phase = Phase.PENDING_DECISION,
                launchDeadlineMillis = null
            )
        } else {
            null
        }
    }

    fun onActivityDestroyed(sessionId: Long, attemptId: String) {
        onActivityStopped(sessionId, attemptId)
    }

    @Synchronized
    fun prepareRedisplay(packageName: String, attemptId: String): ReminderLaunchData? {
        val presentation = currentPresentation() ?: return null
        if (presentation.phase != Phase.PENDING_DECISION) return null
        if (presentation.data.targetPackageName != packageName || attemptId.isBlank()) return null
        val redisplay = presentation.data.copy(
            attemptId = attemptId,
            displayKind = if (presentation.displayRecorded) {
                ReminderDisplayKind.FORCED_REDISPLAY
            } else {
                presentation.data.displayKind
            }
        )
        activePresentation = Presentation(
            data = redisplay,
            phase = Phase.LAUNCHING,
            displayRecorded = presentation.displayRecorded,
            launchDeadlineMillis = elapsedRealtimeProvider() + LAUNCH_GRACE_MILLIS
        )
        return redisplay
    }

    @Synchronized
    fun restorePending(sessionId: Long, attemptId: String) {
        val presentation = currentPresentation() ?: return
        if (!presentation.matches(sessionId, attemptId) || !presentation.data.forceReminder) return
        activePresentation = presentation.copy(
            phase = Phase.PENDING_DECISION,
            launchDeadlineMillis = null
        )
    }

    @Synchronized
    fun isVisible(): Boolean = currentPresentation()?.phase == Phase.VISIBLE

    @Synchronized
    fun hasPendingDecision(): Boolean =
        currentPresentation()?.phase == Phase.PENDING_DECISION

    @Synchronized
    fun protectsSession(): Boolean = currentPresentation() != null

    @Synchronized
    fun isCurrentAttempt(sessionId: Long, attemptId: String): Boolean =
        currentPresentation()?.matches(sessionId, attemptId) == true

    private fun currentPresentation(): Presentation? {
        val presentation = activePresentation ?: return null
        if (
            presentation.phase == Phase.LAUNCHING &&
            presentation.launchDeadlineMillis?.let { it <= elapsedRealtimeProvider() } == true
        ) {
            activePresentation = if (presentation.data.forceReminder) {
                presentation.copy(
                    phase = Phase.PENDING_DECISION,
                    launchDeadlineMillis = null
                )
            } else {
                null
            }
            return activePresentation
        }
        if (
            presentation.phase == Phase.SNOOZE_TRANSITION &&
            !isSnoozeTransitionPending(presentation)
        ) {
            activePresentation = null
            return null
        }
        return presentation
    }

    private fun isSnoozeTransitionPending(presentation: Presentation): Boolean =
        presentation.snoozeTransitionDeadlineMillis?.let { it > elapsedRealtimeProvider() } == true

    private fun Presentation.matches(sessionId: Long, attemptId: String): Boolean =
        data.sessionId == sessionId && data.attemptId == attemptId

    private companion object {
        const val LAUNCH_GRACE_MILLIS = 8_000L
        const val SNOOZE_TRANSITION_GRACE_MILLIS = 8_000L
    }
}
