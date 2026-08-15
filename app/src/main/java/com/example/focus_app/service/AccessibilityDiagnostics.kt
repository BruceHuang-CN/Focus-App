package com.example.focus_app.service

data class AccessibilityDiagnosticsState(
    val serviceBound: Boolean = false,
    val lastConnectedAtMillis: Long? = null,
    val lastDestroyedAtMillis: Long? = null,
    val lastInterruptedAtMillis: Long? = null,
    val packageLastUpdateTimeMillis: Long? = null,
    val firstLaunchAfterUpdateAtMillis: Long? = null
)

sealed interface AccessibilityDiagnosticsEvent {
    data class ServiceConnected(val occurredAtMillis: Long) : AccessibilityDiagnosticsEvent
    data class ServiceDestroyed(val occurredAtMillis: Long) : AccessibilityDiagnosticsEvent
    data class ServiceInterrupted(val occurredAtMillis: Long) : AccessibilityDiagnosticsEvent
    data class AppLaunched(
        val packageLastUpdateTimeMillis: Long,
        val launchedAtMillis: Long
    ) : AccessibilityDiagnosticsEvent
}

internal fun reduceAccessibilityDiagnostics(
    state: AccessibilityDiagnosticsState,
    event: AccessibilityDiagnosticsEvent
): AccessibilityDiagnosticsState = when (event) {
    is AccessibilityDiagnosticsEvent.ServiceConnected -> state.copy(
        serviceBound = true,
        lastConnectedAtMillis = event.occurredAtMillis
    )

    is AccessibilityDiagnosticsEvent.ServiceDestroyed -> state.copy(
        serviceBound = false,
        lastDestroyedAtMillis = event.occurredAtMillis
    )

    is AccessibilityDiagnosticsEvent.ServiceInterrupted -> state.copy(
        lastInterruptedAtMillis = event.occurredAtMillis
    )

    is AccessibilityDiagnosticsEvent.AppLaunched -> {
        if (state.packageLastUpdateTimeMillis == event.packageLastUpdateTimeMillis) {
            state
        } else {
            state.copy(
                packageLastUpdateTimeMillis = event.packageLastUpdateTimeMillis,
                firstLaunchAfterUpdateAtMillis = event.launchedAtMillis
            )
        }
    }
}
