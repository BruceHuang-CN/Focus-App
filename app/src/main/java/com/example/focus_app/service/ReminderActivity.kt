package com.example.focus_app.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Bundle
import android.view.ViewTreeObserver
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.setContent
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import com.example.focus_app.data.repository.AppSessionRepository
import com.example.focus_app.data.repository.ReminderDisplayKind
import com.example.focus_app.domain.model.ReturnDestination
import com.example.focus_app.ui.reminder.ReminderOverlay
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

@AndroidEntryPoint
class ReminderActivity : ComponentActivity() {
    @Inject
    lateinit var sessionRepository: AppSessionRepository

    @Inject
    lateinit var reminderPresentationRegistry: ReminderPresentationRegistry

    @Inject
    lateinit var reminderDisplayCoordinator: ReminderDisplayCoordinator

    private var launchData: ReminderLaunchData? = null
    private var overlayAttached = false
    private var displayConfirmed = false
    private var displayConfirmationStarted = false
    private var displayConfirmationJob: Job? = null
    private var dismissReceiverRegistered = false

    private val dismissReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val sessionId = intent.getLongExtra(
                AndroidReminderLauncher.EXTRA_DISMISS_SESSION_ID,
                0L
            )
            val data = launchData
            if (sessionId == data?.sessionId) {
                reminderPresentationRegistry.hide(sessionId, data.attemptId)
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
        launchData = readLaunchData(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        val incoming = readLaunchData(intent)
        if (!isNewReminderAttempt(launchData?.attemptId, incoming.attemptId)) return
        displayConfirmationJob?.cancel()
        displayConfirmationJob = null
        setIntent(intent)
        launchData = incoming
        overlayAttached = true
        displayConfirmed = false
        displayConfirmationStarted = false
        renderReminder(incoming, interactionsEnabled = false)
    }

    private fun readLaunchData(intent: Intent): ReminderLaunchData {
        val taskId = if (intent.hasExtra(ReminderLaunchData.EXTRA_TASK_ID)) {
            intent.getLongExtra(ReminderLaunchData.EXTRA_TASK_ID, 0L)
        } else {
            null
        }
        return ReminderLaunchData(
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
            targetPackageName = intent.getStringExtra(
                ReminderLaunchData.EXTRA_TARGET_PACKAGE_NAME
            ).orEmpty(),
            windowReminderCount = intent.getIntExtra(
                ReminderLaunchData.EXTRA_WINDOW_REMINDER_COUNT, 0
            ),
            windowLimit = intent.getIntExtra(ReminderLaunchData.EXTRA_WINDOW_LIMIT, 0),
            windowMinutes = intent.getIntExtra(ReminderLaunchData.EXTRA_WINDOW_MINUTES, 0),
            returnPackageName = intent.getStringExtra(ReminderLaunchData.EXTRA_RETURN_PACKAGE_NAME).orEmpty(),
            forceReminder = intent.getBooleanExtra(ReminderLaunchData.EXTRA_FORCE_REMINDER, false),
            attemptId = intent.getStringExtra(ReminderLaunchData.EXTRA_ATTEMPT_ID).orEmpty(),
            displayKind = ReminderDisplayKind.fromKey(
                intent.getStringExtra(ReminderLaunchData.EXTRA_DISPLAY_KIND)
            )
        )
    }

    override fun onStart() {
        super.onStart()
        ContextCompat.registerReceiver(
            this,
            dismissReceiver,
            IntentFilter(AndroidReminderLauncher.ACTION_DISMISS_REMINDER),
            ContextCompat.RECEIVER_NOT_EXPORTED
        )
        dismissReceiverRegistered = true
    }

    override fun onResume() {
        super.onResume()
        confirmAndRenderIfSessionCurrent()
    }

    override fun onStop() {
        displayConfirmationJob?.cancel()
        displayConfirmationJob = null
        displayConfirmationStarted = false
        if (dismissReceiverRegistered) {
            unregisterReceiver(dismissReceiver)
            dismissReceiverRegistered = false
        }
        launchData?.let {
            reminderPresentationRegistry.onActivityStopped(it.sessionId, it.attemptId)
        }
        super.onStop()
    }

    override fun onDestroy() {
        displayConfirmationJob?.cancel()
        displayConfirmationJob = null
        launchData?.let {
            reminderPresentationRegistry.onActivityDestroyed(it.sessionId, it.attemptId)
        }
        super.onDestroy()
    }

    private fun confirmAndRenderIfSessionCurrent() {
        val data = launchData ?: return
        if (displayConfirmed || displayConfirmationStarted) return
        displayConfirmationStarted = true
        displayConfirmationJob = lifecycleScope.launch {
            val sessionIsCurrent = isReminderSessionCurrent(
                data.sessionId,
                sessionRepository.currentOpenSession()?.id
            )
            ensureActive()
            if (!shouldApplyReminderConfirmation(
                    expectedAttemptId = data.attemptId,
                    currentAttemptId = launchData?.attemptId,
                    registryAttemptIsCurrent = reminderPresentationRegistry.isCurrentAttempt(
                        data.sessionId,
                        data.attemptId
                    ),
                    isActivityResumed = lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)
                )
            ) {
                return@launch
            }
            if (!sessionIsCurrent) {
                reminderPresentationRegistry.hide(data.sessionId, data.attemptId)
                finishAndRemoveTask()
                return@launch
            }

            if (!overlayAttached) {
                overlayAttached = true
                renderReminder(data, interactionsEnabled = false)
            }
            awaitReminderDraw()
            ensureActive()
            if (!shouldApplyReminderConfirmation(
                    expectedAttemptId = data.attemptId,
                    currentAttemptId = launchData?.attemptId,
                    registryAttemptIsCurrent = reminderPresentationRegistry.isCurrentAttempt(
                        data.sessionId,
                        data.attemptId
                    ),
                    isActivityResumed = lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)
                )
            ) {
                return@launch
            }

            val confirmed = try {
                reminderDisplayCoordinator.confirm(data)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: RuntimeException) {
                null
            }
            ensureActive()
            if (!shouldApplyReminderConfirmation(
                    expectedAttemptId = data.attemptId,
                    currentAttemptId = launchData?.attemptId,
                    registryAttemptIsCurrent = reminderPresentationRegistry.isCurrentAttempt(
                        data.sessionId,
                        data.attemptId
                    ),
                    isActivityResumed = lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)
                )
            ) {
                return@launch
            }
            if (confirmed == null) {
                reminderPresentationRegistry.hide(data.sessionId, data.attemptId)
                finishAndRemoveTask()
                return@launch
            }
            if (!reminderPresentationRegistry.confirmVisible(confirmed)) return@launch
            launchData = confirmed
            displayConfirmed = true
            renderReminder(confirmed, interactionsEnabled = true)
        }.also { job ->
            job.invokeOnCompletion {
                if (!displayConfirmed && launchData?.attemptId == data.attemptId) {
                    displayConfirmationStarted = false
                }
            }
        }
    }

    private fun renderReminder(data: ReminderLaunchData, interactionsEnabled: Boolean) {
        setContent {
            ReminderOverlay(
                data = data,
                interactionsEnabled = interactionsEnabled,
                onDismiss = { finishAndRemoveTask() }
            )
        }
    }

    private suspend fun awaitReminderDraw() = suspendCancellableCoroutine { continuation ->
        val view = window.decorView
        val listener = object : ViewTreeObserver.OnDrawListener {
            override fun onDraw() {
                view.post {
                    if (view.viewTreeObserver.isAlive) {
                        view.viewTreeObserver.removeOnDrawListener(this)
                    }
                    if (continuation.isActive) continuation.resume(Unit)
                }
            }
        }
        view.viewTreeObserver.addOnDrawListener(listener)
        continuation.invokeOnCancellation {
            view.post {
                if (view.viewTreeObserver.isAlive) {
                    view.viewTreeObserver.removeOnDrawListener(listener)
                }
            }
        }
        view.invalidate()
    }
}
