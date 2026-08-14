package com.example.focus_app.service

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

/** 稍后提醒到期后的统一执行能力。 */
interface FollowUpExecutor {
    suspend fun execute(sessionId: Long): FollowUpDecision
}

/** 稍后提醒调度入口。 */
interface FollowUpScheduler {
    fun schedule(sessionId: Long, delayMillis: Long)
    fun cancel(sessionId: Long)
}

object NoOpFollowUpScheduler : FollowUpScheduler {
    override fun schedule(sessionId: Long, delayMillis: Long) = Unit
    override fun cancel(sessionId: Long) = Unit
}

/**
 * 混合调度：进程存活时用协程 delay 准时触发（可感知锁屏并重试）；
 * 同时保留 WorkManager 持久任务作为进程被回收后的兜底，避免提醒彻底丢失。
 */
@Singleton
class HybridFollowUpScheduler @Inject constructor(
    private val executor: FollowUpExecutor,
    private val workScheduler: FollowUpReminderWorkScheduler,
    private val alarmScheduler: FollowUpAlarmScheduler,
    private val scope: CoroutineScope
) : FollowUpScheduler {
    private val jobs = ConcurrentHashMap<Long, Job>()

    override fun schedule(sessionId: Long, delayMillis: Long) {
        val safeDelayMillis = delayMillis.coerceAtLeast(0L)
        cancelInProcess(sessionId)
        scheduleOnce(sessionId, safeDelayMillis, attempts = 0)
        workScheduler.schedule(sessionId, safeDelayMillis)
        alarmScheduler.schedule(sessionId, safeDelayMillis, retryAttempt = 0)
    }

    override fun cancel(sessionId: Long) {
        cancelInProcess(sessionId)
        workScheduler.cancel(sessionId)
        alarmScheduler.cancel(sessionId)
    }

    private fun scheduleOnce(sessionId: Long, delayMillis: Long, attempts: Int) {
        val job = scope.launch {
            delay(delayMillis)
            workScheduler.cancel(sessionId)
            alarmScheduler.cancel(sessionId)
            when (executor.execute(sessionId)) {
                FollowUpDecision.RETRY -> {
                    if (attempts < MAX_RETRY_ATTEMPTS) {
                        alarmScheduler.schedule(
                            sessionId,
                            RETRY_INTERVAL_MS,
                            retryAttempt = attempts + 1
                        )
                        scheduleOnce(sessionId, RETRY_INTERVAL_MS, attempts + 1)
                    }
                    jobs.remove(sessionId, coroutineContext[Job])
                }
                else -> jobs.remove(sessionId, coroutineContext[Job])
            }
        }
        jobs[sessionId] = job
    }

    private fun cancelInProcess(sessionId: Long) {
        jobs.remove(sessionId)?.cancel()
    }

    companion object {
        /** 锁屏等临时条件解除后的重试间隔。 */
        const val RETRY_INTERVAL_MS = 15_000L
        internal const val MAX_RETRY_ATTEMPTS = 20
    }
}
