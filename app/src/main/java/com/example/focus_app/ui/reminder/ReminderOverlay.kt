package com.example.focus_app.ui.reminder

import com.example.focus_app.R
import android.os.SystemClock
import androidx.compose.runtime.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.TextButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.focus_app.service.ReminderLaunchData
import kotlinx.coroutines.delay

@Composable
fun ReminderOverlay(
    data: ReminderLaunchData,
    interactionsEnabled: Boolean = true,
    onDismiss: () -> Unit,
    viewModel: ReminderViewModel = hiltViewModel()
) {
    val textContext = androidx.compose.ui.platform.LocalContext.current
    val uiState by viewModel.uiState.collectAsState()
    val colors = MaterialTheme.colorScheme
    val ReminderHighlightRed = colors.primary
    val DecisionInk = colors.onSurface
    val DecisionBody = colors.onSurfaceVariant
    val DecisionFaint = colors.onSurfaceVariant
    val DecisionCardWhite = colors.surface
    val startedElapsed = rememberSaveable(data.attemptId) { SystemClock.elapsedRealtime() }
    val startedWall = rememberSaveable(data.attemptId) { System.currentTimeMillis() }
    val progress = produceState(0f, data.attemptId) {
        do {
            val elapsed = if (SystemClock.elapsedRealtime() >= startedElapsed)
                SystemClock.elapsedRealtime() - startedElapsed else System.currentTimeMillis() - startedWall
            value = (elapsed / BREATHING_TOTAL_MS.toFloat()).coerceIn(0f, 1f)
            if (value < 1f) withFrameNanos { }
        } while (value < 1f)
    }
    val breathing by remember(data.attemptId, data.showBreathing, progress) { derivedStateOf { data.showBreathing && progress.value < 1f } }

    var customTimedAction by rememberSaveable(data.attemptId) {
        mutableStateOf<ReminderDecisionAction?>(null)
    }
    var customSnoozeMinutes by rememberSaveable(data.attemptId) { mutableStateOf("") }
    val urgency = remember(data.windowReminderCount, data.windowLimit) {
        reminderUrgency(data.windowReminderCount, data.windowLimit)
    }
    val escalation = localizedReminderEscalationCopy(textContext, data.windowReminderCount, data.windowLimit)
    val actionOrderSeed = data.attemptId.ifBlank { "session-${data.sessionId}" }
    val actionOrder = remember(actionOrderSeed, uiState.randomizeActions) {
        uiState.randomizeActions?.let { randomize ->
            reminderActionOrder(randomize, actionOrderSeed)
        }.orEmpty()
    }

    LaunchedEffect(data) { viewModel.init(data) }
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        run {
            if (breathing) {
                BreathingScreen(progress = progress, modifier = Modifier.fillMaxSize(), animationKey = data.attemptId)
            } else {
                ReminderGreenBackdrop(modifier = Modifier.fillMaxSize()) {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth(urgency.widthFraction)
                            .fillMaxHeight(urgency.heightFraction)
                            .then(
                                if (urgency.isFinalReminder) {
                                    Modifier
                                } else {
                                    Modifier.padding(24.dp)
                                }
                            ),
                        shape = if (urgency.isFinalReminder) RectangleShape else RoundedCornerShape(28.dp),
                        colors = CardDefaults.cardColors(containerColor = DecisionCardWhite)
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .verticalScroll(rememberScrollState())
                                .padding(horizontal = 24.dp, vertical = 28.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Text(
                                    escalation.title,
                                    style = MaterialTheme.typography.headlineSmall,
                                    fontSize = 28.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = if (urgency.isFinalReminder) ReminderHighlightRed else DecisionInk,
                                    textAlign = TextAlign.Center
                                )
                                Text(
                                    buildAnnotatedString {
                                        append(textContext.getString(R.string.core_opened_prefix))
                                        withStyle(
                                            SpanStyle(color = ReminderHighlightRed, fontWeight = FontWeight.Bold)
                                        ) {
                                            append(uiState.appName.ifBlank { textContext.getString(R.string.core_target_app) })
                                        }
                                    },
                                    style = MaterialTheme.typography.titleMedium,
                                    color = DecisionBody,
                                    textAlign = TextAlign.Center
                                )
                                uiState.taskTitle?.let { taskTitle ->
                                    Text(
                                        buildAnnotatedString {
                                            append(textContext.getString(R.string.core_task_prefix))
                                            withStyle(
                                                SpanStyle(color = ReminderHighlightRed, fontWeight = FontWeight.Bold)
                                            ) {
                                                append(taskTitle)
                                            }
                                        },
                                        style = MaterialTheme.typography.titleMedium,
                                        color = DecisionBody,
                                        textAlign = TextAlign.Center
                                    )
                                }
                                Text(
                                    uiState.message,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    lineHeight = 26.sp,
                                    color = DecisionBody,
                                    textAlign = TextAlign.Center
                                )
                                escalation.directive?.let { directive ->
                                    Text(
                                        directive,
                                        style = MaterialTheme.typography.bodyLarge,
                                        fontWeight = FontWeight.Bold,
                                        color = if (urgency.isFinalReminder) ReminderHighlightRed else DecisionBody,
                                        textAlign = TextAlign.Center
                                    )
                                }
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = "“",
                                        fontSize = 20.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = DecisionFaint
                                    )
                                    Spacer(Modifier.width(8.dp))
                                    Text(text = textContext.getString(R.string.core_start_now), fontSize = 14.sp, color = DecisionFaint)
                                    Spacer(Modifier.width(8.dp))
                                    Text(
                                        text = "”",
                                        fontSize = 20.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = DecisionFaint
                                    )
                                }
                                ReminderDecisionButtons(
                                    actions = actionOrder,
                                    enabled = interactionsEnabled,
                                    onReturnClick = {
                                        viewModel.returnToFocus(data.sessionId, onDismiss)
                                    },
                                    onTimedDecision = { action, minutes ->
                                        applyTimedDecision(
                                            viewModel = viewModel,
                                            action = action,
                                            sessionId = data.sessionId,
                                            minutes = minutes,
                                            onDismiss = onDismiss
                                        )
                                    },
                                    onCustomTimedAction = { action -> customTimedAction = action },
                                    modifier = Modifier.fillMaxWidth()
                                )
                                if (uiState.windowLimit > 0) {
                                    Text(
                                        textContext.getString(R.string.core_window_count, uiState.windowMinutes, uiState.windowReminderCount, uiState.windowLimit),
                                        style = MaterialTheme.typography.labelMedium,
                                        color = DecisionFaint
                                    )
                                }
                                uiState.customReturnError?.let { error ->
                                    Text(
                                        textContext.getString(error),
                                        style = MaterialTheme.typography.labelMedium,
                                        color = ReminderHighlightRed,
                                        textAlign = TextAlign.Center
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    customTimedAction?.let { action ->
        AlertDialog(
            onDismissRequest = { customTimedAction = null },
            title = {
                Text(
                    if (action == ReminderDecisionAction.INTENTIONAL) {
                        textContext.getString(R.string.core_intentional_duration)
                    } else {
                        textContext.getString(R.string.core_rest_duration)
                    }
                )
            },
            text = {
                OutlinedTextField(
                    value = customSnoozeMinutes,
                    onValueChange = { customSnoozeMinutes = it },
                    label = { Text(textContext.getString(R.string.core_custom_minute_range)) },
                    singleLine = true
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        parseCustomSnoozeMinutes(customSnoozeMinutes)?.let { minutes ->
                            customTimedAction = null
                            applyTimedDecision(
                                viewModel = viewModel,
                                action = action,
                                sessionId = data.sessionId,
                                minutes = minutes,
                                onDismiss = onDismiss
                            )
                        }
                    }
                ) { Text(textContext.getString(R.string.core_confirm)) }
            },
            dismissButton = {
                TextButton(onClick = { customTimedAction = null }) {
                    Text(textContext.getString(R.string.core_cancel))
                }
            }
        )
    }
}

private fun applyTimedDecision(
    viewModel: ReminderViewModel,
    action: ReminderDecisionAction,
    sessionId: Long,
    minutes: Int,
    onDismiss: () -> Unit
) {
    when (action) {
        ReminderDecisionAction.INTENTIONAL ->
            viewModel.useIntentionally(sessionId, minutes, onDismiss)
        ReminderDecisionAction.REST ->
            viewModel.takeBreak(sessionId, minutes, onDismiss)
        ReminderDecisionAction.RETURN -> Unit
    }
}
