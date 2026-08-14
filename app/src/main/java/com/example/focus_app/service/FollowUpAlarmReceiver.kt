package com.example.focus_app.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class FollowUpAlarmReceiver : BroadcastReceiver() {
    @Inject
    lateinit var handler: FollowUpAlarmHandler

    @Inject
    lateinit var scope: CoroutineScope

    override fun onReceive(context: Context, intent: Intent) {
        val pendingResult = goAsync()
        val sessionId = intent.getLongExtra(
            AndroidFollowUpAlarmScheduler.EXTRA_SESSION_ID,
            0L
        )
        val retryAttempt = intent.getIntExtra(
            AndroidFollowUpAlarmScheduler.EXTRA_RETRY_ATTEMPT,
            0
        ).coerceAtLeast(0)

        scope.launch {
            try {
                handler.handle(sessionId, retryAttempt)
            } catch (throwable: Throwable) {
                Log.e(TAG, "Follow-up alarm handling failed", throwable)
            } finally {
                pendingResult.finish()
            }
        }
    }

    companion object {
        private const val TAG = "FollowUpAlarmReceiver"
    }
}

class FollowUpAlarmHandler @Inject constructor(
    private val executor: FollowUpExecutor,
    private val scheduler: FollowUpScheduler,
    private val alarmScheduler: FollowUpAlarmScheduler
) {
    suspend fun handle(sessionId: Long, retryAttempt: Int = 0) {
        if (sessionId <= 0L) return

        when (executor.execute(sessionId)) {
            FollowUpDecision.SHOW,
            FollowUpDecision.SKIP -> scheduler.cancel(sessionId)

            FollowUpDecision.RETRY -> {
                if (retryAttempt < HybridFollowUpScheduler.MAX_RETRY_ATTEMPTS) {
                    alarmScheduler.schedule(
                        sessionId = sessionId,
                        delayMillis = HybridFollowUpScheduler.RETRY_INTERVAL_MS,
                        retryAttempt = retryAttempt + 1
                    )
                }
            }
        }
    }
}
