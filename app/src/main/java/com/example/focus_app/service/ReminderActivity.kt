package com.example.focus_app.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Bundle
import com.example.focus_app.R
import com.example.focus_app.data.language.localizedText
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import com.example.focus_app.data.theme.ThemeStore
import com.example.focus_app.ui.theme.FocusAppTheme
import android.view.ViewTreeObserver
import androidx.appcompat.app.AppCompatActivity
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
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

@AndroidEntryPoint
class ReminderActivity : AppCompatActivity() {
    @Inject lateinit var settingsRepository: com.example.focus_app.data.repository.SettingsRepository
    @Inject lateinit var taskRepository: com.example.focus_app.data.repository.TaskRepository
    @Inject lateinit var themeStore: ThemeStore
    @Inject
    lateinit var sessionRepository: AppSessionRepository

    @Inject
    lateinit var reminderPresentationRegistry: ReminderPresentationRegistry

    @Inject
    lateinit var reminderDisplayCoordinator: ReminderDisplayCoordinator

    private var taskValidityJob: Job? = null
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
        displayConfirmed = false
        displayConfirmationStarted = false
        overlayAttached = false
        confirmAndRenderIfSessionCurrent()
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
            taskContextStartedAt = intent.getLongExtra(ReminderLaunchData.EXTRA_TASK_CONTEXT_STARTED_AT, 0L),
            taskTitle = intent.getStringExtra(ReminderLaunchData.EXTRA_TASK_TITLE),
            appName = intent.getStringExtra(ReminderLaunchData.EXTRA_APP_NAME) ?: localizedText(R.string.service_target_app),
            message = intent.getStringExtra(ReminderLaunchData.EXTRA_MESSAGE)
                ?: localizedText(R.string.service_generic_reminder),
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
        taskValidityJob?.cancel()
        taskValidityJob = lifecycleScope.launch {
            combine(taskRepository.observeActiveEvents(), settingsRepository.getSettingsFlow()) { _, settings -> settings }.collect { settings ->
                val data = launchData ?: return@collect
                val session = sessionRepository.sessionById(data.sessionId)
                if (!settings.guardianEnabled || settings.targetApps.none { it.packageName == data.targetPackageName } || session == null || !matchesReminderContext(data, session) || !taskRepository.isSessionEligible(session)) {
                    reminderPresentationRegistry.hide(data.sessionId, data.attemptId)
                    finishAndRemoveTask()
                }
            }
        }
    }

    override fun onStop() {
        taskValidityJob?.cancel()
        taskValidityJob = null
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
            val boundSession = sessionRepository.sessionById(data.sessionId)
            if (!guardianAllows(data) || !sessionIsCurrent || boundSession == null || !matchesReminderContext(data, boundSession) ||
                !taskRepository.isSessionEligible(boundSession)) {
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

            val latestSession = sessionRepository.sessionById(data.sessionId)
            if (!guardianAllows(data) || latestSession == null || !matchesReminderContext(data, latestSession) || !taskRepository.isSessionEligible(latestSession)) {
                reminderPresentationRegistry.hide(data.sessionId, data.attemptId)
                finishAndRemoveTask()
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

    private suspend fun guardianAllows(data: ReminderLaunchData): Boolean {
        val settings = settingsRepository.getSettings()
        return settings.guardianEnabled && settings.targetApps.any { it.packageName == data.targetPackageName }
    }

    private fun renderReminder(data: ReminderLaunchData, interactionsEnabled: Boolean) {
        setContent {
            val theme by themeStore.settings.collectAsState()
            FocusAppTheme(mode = theme.mode, color = theme.color) {
                key(data.attemptId) {
                    ReminderOverlay(
                        data = data,
                        interactionsEnabled = interactionsEnabled,
                        onDismiss = { finishAndRemoveTask() }
                    )
                }
            }
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
