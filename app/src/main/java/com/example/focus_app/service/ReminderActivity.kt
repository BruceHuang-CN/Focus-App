package com.example.focus_app.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.setContent
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.example.focus_app.data.repository.AppSessionRepository
import com.example.focus_app.domain.model.ReturnDestination
import com.example.focus_app.ui.reminder.ReminderOverlay
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.launch

@AndroidEntryPoint
class ReminderActivity : ComponentActivity() {
    @Inject
    lateinit var sessionRepository: AppSessionRepository

    @Inject
    lateinit var reminderPresentationRegistry: ReminderPresentationRegistry

    private var launchData: ReminderLaunchData? = null
    private var overlayAttached = false
    private var dismissReceiverRegistered = false

    private val dismissReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val sessionId = intent.getLongExtra(
                AndroidReminderLauncher.EXTRA_DISMISS_SESSION_ID,
                0L
            )
            if (sessionId == launchData?.sessionId) {
                reminderPresentationRegistry.hide(sessionId)
                finishAndRemoveTask()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (launchData?.forceReminder != true) {
                    isEnabled = false
                    onBackPressedDispatcher.onBackPressed()
                }
            }
        })
        val taskId = if (intent.hasExtra(ReminderLaunchData.EXTRA_TASK_ID)) {
            intent.getLongExtra(ReminderLaunchData.EXTRA_TASK_ID, 0L)
        } else {
            null
        }
        launchData = ReminderLaunchData(
            sessionId = intent.getLongExtra(ReminderLaunchData.EXTRA_SESSION_ID, 0L),
            taskId = taskId,
            taskTitle = intent.getStringExtra(ReminderLaunchData.EXTRA_TASK_TITLE),
            appName = intent.getStringExtra(ReminderLaunchData.EXTRA_APP_NAME) ?: "目标应用",
            message = intent.getStringExtra(ReminderLaunchData.EXTRA_MESSAGE)
                ?: "停一下，想想你原本准备完成什么。",
            showBreathing = intent.getBooleanExtra(ReminderLaunchData.EXTRA_SHOW_BREATHING, false),
            returnDestination = ReturnDestination.fromKey(
                intent.getStringExtra(ReminderLaunchData.EXTRA_RETURN_DESTINATION).orEmpty()
            ),
            windowReminderCount = intent.getIntExtra(
                ReminderLaunchData.EXTRA_WINDOW_REMINDER_COUNT, 0
            ),
            windowLimit = intent.getIntExtra(ReminderLaunchData.EXTRA_WINDOW_LIMIT, 0),
            windowMinutes = intent.getIntExtra(ReminderLaunchData.EXTRA_WINDOW_MINUTES, 0),
            returnPackageName = intent.getStringExtra(ReminderLaunchData.EXTRA_RETURN_PACKAGE_NAME).orEmpty(),
            forceReminder = intent.getBooleanExtra(ReminderLaunchData.EXTRA_FORCE_REMINDER, false)
        )

        renderIfSessionCurrent()
    }

    override fun onStart() {
        super.onStart()
        launchData?.let { data ->
            reminderPresentationRegistry.show(data.sessionId, data.forceReminder)
        }
        ContextCompat.registerReceiver(
            this,
            dismissReceiver,
            IntentFilter(AndroidReminderLauncher.ACTION_DISMISS_REMINDER),
            ContextCompat.RECEIVER_NOT_EXPORTED
        )
        dismissReceiverRegistered = true
        renderIfSessionCurrent()
    }

    override fun onStop() {
        if (dismissReceiverRegistered) {
            unregisterReceiver(dismissReceiver)
            dismissReceiverRegistered = false
        }
        super.onStop()
    }

    override fun onDestroy() {
        launchData?.let { reminderPresentationRegistry.onActivityDestroyed(it.sessionId) }
        super.onDestroy()
    }

    private fun renderIfSessionCurrent() {
        val data = launchData ?: return
        lifecycleScope.launch {
            if (!isReminderSessionCurrent(data.sessionId, sessionRepository.currentOpenSession()?.id)) {
                finishAndRemoveTask()
                return@launch
            }
            if (overlayAttached) return@launch
            overlayAttached = true
            setContent {
                ReminderOverlay(
                    data = data,
                    onDismiss = { finishAndRemoveTask() }
                )
            }
        }
    }
}
