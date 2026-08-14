package com.example.focus_app

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import com.example.focus_app.data.remote.ReminderBatchCoordinator
import com.example.focus_app.service.PendingFollowUpRestorer
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

    override fun onCreate() {
        super.onCreate()
        reminderBatchCoordinator.start(applicationScope)
        applicationScope.launch {
            pendingFollowUpRestorer.restore()
        }
    }

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()
}
