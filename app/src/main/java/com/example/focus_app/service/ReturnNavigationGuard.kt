package com.example.focus_app.service

import android.os.SystemClock
import javax.inject.Inject
import javax.inject.Singleton

/** Own-activity lifecycle is authoritative; window events may describe an activity behind it. */
@Singleton
class ReturnNavigationGuard internal constructor(private val uptimeMillis: () -> Long) {
    @Inject constructor() : this(SystemClock::uptimeMillis)

    private val resumedActivities = mutableSetOf<Any>()
    private var ignoreEventsThrough = Long.MIN_VALUE
    private var navigationDeadline: Long? = null

    @Synchronized fun beginReturn() {
        val now = uptimeMillis()
        ignoreEventsThrough = now
        navigationDeadline = now + 3000L
    }

    @Synchronized fun cancelReturn() {
        navigationDeadline = null
    }

    @Synchronized fun onMainResumed(activity: Any) {
        resumedActivities += activity
        ignoreEventsThrough = uptimeMillis()
        navigationDeadline = null
    }

    @Synchronized fun onMainPaused(activity: Any) {
        if (resumedActivities.remove(activity) && resumedActivities.isEmpty()) {
            ignoreEventsThrough = uptimeMillis()
        }
    }

    @Synchronized fun allowsExternalEvent(eventUptimeMillis: Long): Boolean =
        resumedActivities.isEmpty() &&
            (navigationDeadline?.let { uptimeMillis() >= it } ?: true) &&
            eventUptimeMillis > ignoreEventsThrough
}
