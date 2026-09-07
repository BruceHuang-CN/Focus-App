package com.example.focus_app.service

import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PendingReminderRedisplayer internal constructor(
    private val registry: ReminderPresentationRegistry,
    private val launcher: ReminderLauncher,
    private val attemptIdProvider: () -> String
) {
    @Inject
    constructor(
        registry: ReminderPresentationRegistry,
        launcher: ReminderLauncher,
        attemptIdGenerator: ReminderAttemptIdGenerator
    ) : this(registry, launcher, attemptIdGenerator::newId)

    fun onForegroundPackage(packageName: String?): Boolean {
        val actualPackageName = packageName ?: return false
        val data = registry.prepareRedisplay(
            packageName = actualPackageName,
            attemptId = attemptIdProvider()
        ) ?: return false
        val activityRequested = launcher.show(data)
        if (!activityRequested) {
            registry.restorePending(data.sessionId, data.attemptId)
        }
        return activityRequested
    }
}
