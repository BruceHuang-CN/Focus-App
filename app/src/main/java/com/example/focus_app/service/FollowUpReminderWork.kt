package com.example.focus_app.service

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 后续提醒只使用一次性 WorkManager 任务：进程被普通回收后仍能恢复，但不会轮询或常驻。
 */
interface FollowUpReminderWorkScheduler {
    fun schedule(sessionId: Long, delayMillis: Long)
    fun cancel(sessionId: Long)
}

@Singleton
class AndroidFollowUpReminderWorkScheduler @Inject constructor(
    private val workManager: WorkManager
) : FollowUpReminderWorkScheduler {
    override fun schedule(sessionId: Long, delayMillis: Long) {
        val request = OneTimeWorkRequestBuilder<FollowUpReminderWorker>()
            .setInitialDelay(delayMillis.coerceAtLeast(0L), TimeUnit.MILLISECONDS)
            .setInputData(workDataOf(FollowUpReminderWorker.INPUT_SESSION_ID to sessionId))
            .build()
        workManager.enqueueUniqueWork(workName(sessionId), ExistingWorkPolicy.REPLACE, request)
    }

    override fun cancel(sessionId: Long) {
        workManager.cancelUniqueWork(workName(sessionId))
    }

    private fun workName(sessionId: Long) = "focus_follow_up_$sessionId"
}

object NoOpFollowUpReminderWorkScheduler : FollowUpReminderWorkScheduler {
    override fun schedule(sessionId: Long, delayMillis: Long) = Unit
    override fun cancel(sessionId: Long) = Unit
}

/**
 * WorkManager 兜底执行：进程存活时的准时提醒由 [HybridFollowUpScheduler] 负责，
 * 本 Worker 只在进程被回收后恢复执行，并复用同一套 [FollowUpReminderGate] 决策。
 */
@HiltWorker
class FollowUpReminderWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
    private val executor: FollowUpExecutor
) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result {
        val sessionId = inputData.getLong(INPUT_SESSION_ID, 0L)
        if (sessionId <= 0L) return Result.failure()
        return when (executor.execute(sessionId)) {
            FollowUpDecision.RETRY -> Result.retry()
            else -> Result.success()
        }
    }

    companion object {
        const val INPUT_SESSION_ID = "follow_up_session_id"
    }
}
