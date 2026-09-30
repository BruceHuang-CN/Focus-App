package com.example.focus_app.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.hilt.work.HiltWorker
import androidx.work.*
import com.example.focus_app.MainActivity
import com.example.focus_app.R
import com.example.focus_app.data.language.AppLanguage
import com.example.focus_app.data.summary.DailySummaryRepository
import com.example.focus_app.data.summary.DailySummaryStore
import com.example.focus_app.domain.summary.nextSummaryTime
import com.example.focus_app.domain.summary.summaryWorkIsCurrent
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import dagger.hilt.android.AndroidEntryPoint
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import java.time.Duration
import java.time.ZonedDateTime
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CancellationException

@Singleton
class DailySummaryScheduler @Inject constructor(
    @ApplicationContext private val context: Context, private val store: DailySummaryStore
) {
    /** WorkManager is acquired here, after Application injection/configuration is complete. */
    fun scheduleNext(reset: Boolean = false) {
        val manager = WorkManager.getInstance(context)
        val settings = store.settings.value
        if (reset || !settings.enabled) manager.cancelAllWorkByTag(TAG)
        if (!settings.enabled) {
            NotificationManagerCompat.from(context).cancel(NOTIFICATION_ID)
            return
        }
        val now = ZonedDateTime.now()
        val target = nextSummaryTime(now, settings.minuteOfDay)
        val request = OneTimeWorkRequestBuilder<DailySummaryWorker>()
            .setInitialDelay(Duration.between(now, target).toMillis(), TimeUnit.MILLISECONDS)
            .setInputData(workDataOf("date" to target.toLocalDate().toString(), "revision" to settings.revision))
            .addTag(TAG).build()
        manager.enqueueUniqueWork("${TAG}_${target.toLocalDate()}_${settings.revision}", ExistingWorkPolicy.KEEP, request)
    }

    companion object {
        const val TAG = "daily_summary"
        const val NOTIFICATION_ID = 61901
    }
}

@HiltWorker
class DailySummaryWorker @AssistedInject constructor(
    @Assisted context: Context, @Assisted params: WorkerParameters,
    private val store: DailySummaryStore,
    private val scheduler: DailySummaryScheduler,
    private val repository: DailySummaryRepository
) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        // Schedule tomorrow before network work so an API failure never breaks the daily chain.
        scheduler.scheduleNext()
        val date = inputData.getString("date").orEmpty()
        val revision = inputData.getLong("revision", -1)
        fun permitted() = summaryWorkIsCurrent(store.settings.value, revision, date, java.time.LocalDate.now())
        if (!permitted() || store.wasNotified(date)) return Result.success()
        val result = try { repository.generate(force = true, permitted = ::permitted) }
        catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { null }
        if (!permitted()) return Result.success()
        val context = AppLanguage.context(applicationContext)
        val manager = applicationContext.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(NotificationChannel("daily_summary", context.getString(R.string.summary_title), NotificationManager.IMPORTANCE_DEFAULT))
        if (!NotificationManagerCompat.from(applicationContext).areNotificationsEnabled()) return Result.success()
        val intent = Intent(applicationContext, MainActivity::class.java).apply {
            action = MainActivity.ACTION_OPEN_SUMMARY
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val pending = PendingIntent.getActivity(applicationContext, DailySummaryScheduler.NOTIFICATION_ID, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val notification = NotificationCompat.Builder(applicationContext, "daily_summary")
            .setSmallIcon(R.drawable.ic_app_logo)
            .setContentTitle(context.getString(R.string.summary_title))
            .setContentText(context.getString(if (result?.text.isNullOrBlank()) R.string.summary_notification_pending else R.string.summary_notification_ready))
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setContentIntent(pending).setAutoCancel(true).build()
        try {
            manager.notify(DailySummaryScheduler.NOTIFICATION_ID, notification)
            store.markNotified(date)
        } catch (_: SecurityException) { /* User can still view/generate the summary in Statistics. */ }
        return Result.success()
    }
}

@AndroidEntryPoint
class DailySummaryTimeReceiver : BroadcastReceiver() {
    @Inject lateinit var scheduler: DailySummaryScheduler
    @Inject lateinit var store: DailySummaryStore
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_TIME_CHANGED || intent.action == Intent.ACTION_TIMEZONE_CHANGED) {
            val settings = store.settings.value
            store.updateSettings(settings.enabled, settings.minuteOfDay)
            scheduler.scheduleNext(reset = true)
        }
    }
}
