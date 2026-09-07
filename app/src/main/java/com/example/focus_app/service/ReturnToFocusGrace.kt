package com.example.focus_app.service

import android.os.SystemClock
import com.example.focus_app.data.repository.ReminderDisplayKind
import com.example.focus_app.domain.model.AppUsageSession
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Inject
import javax.inject.Singleton

interface ReturnToFocusGrace {
    fun start(data: ReminderLaunchData)

    /** 返回 true 表示本次新会话已由承诺提醒接管，不应再安排普通首次提醒。 */
    fun showIfActive(session: AppUsageSession): Boolean
}

object NoOpReturnToFocusGrace : ReturnToFocusGrace {
    override fun start(data: ReminderLaunchData) = Unit
    override fun showIfActive(session: AppUsageSession): Boolean = false
}

@Singleton
class ReturnToFocusGraceHandler internal constructor(
    private val launcher: ReminderLauncher,
    private val attemptIdProvider: () -> String,
    private val elapsedRealtimeProvider: () -> Long
) : ReturnToFocusGrace {
    private data class PendingReturn(
        val template: ReminderLaunchData,
        val expiresAtElapsedMillis: Long
    )

    private val stateLock = Any()
    private var pendingReturn: PendingReturn? = null

    @Inject
    constructor(
        launcher: ReminderLauncher,
        attemptIdGenerator: ReminderAttemptIdGenerator
    ) : this(
        launcher = launcher,
        attemptIdProvider = attemptIdGenerator::newId,
        elapsedRealtimeProvider = SystemClock::elapsedRealtime
    )

    override fun start(data: ReminderLaunchData) {
        synchronized(stateLock) {
            pendingReturn = PendingReturn(
                template = data,
                expiresAtElapsedMillis = elapsedRealtimeProvider() + RETURN_GRACE_MILLIS
            )
        }
    }

    override fun showIfActive(session: AppUsageSession): Boolean {
        val pending = synchronized(stateLock) {
            val current = pendingReturn ?: return false
            if (elapsedRealtimeProvider() >= current.expiresAtElapsedMillis) {
                pendingReturn = null
                return false
            }
            if (session.packageName != current.template.targetPackageName) return false
            pendingReturn = null
            current
        }

        launcher.show(
            pending.template.copy(
                sessionId = session.id,
                taskId = session.taskId,
                appName = session.appName,
                message = returnToFocusViolationMessage(pending.template.message),
                targetPackageName = session.packageName,
                forceReminder = true,
                attemptId = attemptIdProvider(),
                displayKind = ReminderDisplayKind.FORCED_REDISPLAY
            )
        )
        return true
    }

    private companion object {
        const val RETURN_GRACE_MILLIS = 30_000L
    }
}

internal fun returnToFocusViolationMessage(originalMessage: String): String {
    val hook = "抓到你了：刚说回到任务，30 秒都没撑住。"
    if (originalMessage.startsWith(hook)) return originalMessage
    return if (originalMessage.isBlank()) hook else "$hook\n${originalMessage.trim()}"
}

@Module
@InstallIn(SingletonComponent::class)
abstract class ReturnToFocusGraceModule {
    @Binds
    @Singleton
    abstract fun bindReturnToFocusGrace(
        handler: ReturnToFocusGraceHandler
    ): ReturnToFocusGrace
}
