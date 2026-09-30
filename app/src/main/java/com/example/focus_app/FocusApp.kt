package com.example.focus_app

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import com.example.focus_app.data.remote.ReminderBatchCoordinator
import com.example.focus_app.service.PendingFollowUpRestorer
import com.example.focus_app.service.AccessibilityDiagnosticsStore
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

@HiltAndroidApp
class FocusApp : Application(), Configuration.Provider {

    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    @Inject
    lateinit var workerFactory: HiltWorkerFactory

    @Inject
    lateinit var reminderBatchCoordinator: ReminderBatchCoordinator

    @Inject
    lateinit var pendingFollowUpRestorer: PendingFollowUpRestorer

    @Inject
    lateinit var accessibilityDiagnosticsStore: AccessibilityDiagnosticsStore

    @Inject lateinit var dailySummaryScheduler: com.example.focus_app.service.DailySummaryScheduler

    override fun onCreate() {
        super.onCreate()
        com.example.focus_app.data.language.AppLanguage.initialize(this)
        accessibilityDiagnosticsStore.recordAppLaunch(packageLastUpdateTime())
        dailySummaryScheduler.scheduleNext()
        reminderBatchCoordinator.start(applicationScope)
        applicationScope.launch {
            pendingFollowUpRestorer.restore()
        }
    }

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()

    override fun onConfigurationChanged(newConfig: android.content.res.Configuration) {
        super.onConfigurationChanged(newConfig)
        com.example.focus_app.data.language.AppLanguage.refresh(this)
    }

    @Suppress("DEPRECATION")
    private fun packageLastUpdateTime(): Long =
        packageManager.getPackageInfo(packageName, 0).lastUpdateTime
}
