package com.example.focus_app.ui.reminder

import androidx.compose.animation.Crossfade
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

// 决策页固定配色（与呼吸页绿色系同源），不随应用主题切换。
private val ReminderHighlightRed = Color(0xFFE53935)
private val DecisionInk = Color(0xFF17342A)
private val DecisionBody = Color(0xFF5B6B60)
private val DecisionFaint = Color(0xFF8A9A8F)
private val DecisionCardWhite = Color(0xFFFFFFFF)

@Composable
fun ReminderOverlay(
    data: ReminderLaunchData,
    interactionsEnabled: Boolean = true,
    onDismiss: () -> Unit,
    viewModel: ReminderViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    var customTimedAction by rememberSaveable(data.attemptId) {
        mutableStateOf<ReminderDecisionAction?>(null)
    }
    var customSnoozeMinutes by rememberSaveable(data.attemptId) { mutableStateOf("") }
    val urgency = remember(data.windowReminderCount, data.windowLimit) {
        reminderUrgency(data.windowReminderCount, data.windowLimit)
    }
    val escalation = remember(data.windowReminderCount, data.windowLimit) {
        reminderEscalationCopy(data.windowReminderCount, data.windowLimit)
    }
    val actionOrderSeed = data.attemptId.ifBlank { "session-${data.sessionId}" }
    val actionOrder = remember(actionOrderSeed, uiState.randomizeActions) {
        uiState.randomizeActions?.let { randomize ->
            reminderActionOrder(randomize, actionOrderSeed)
        }.orEmpty()
    }

    LaunchedEffect(data) { viewModel.init(data) }
    LaunchedEffect(uiState.showBreathing, uiState.breathingStep) {
        if (uiState.showBreathing && uiState.breathingStep > 0) {
            delay(1_000L)
            val nextStep = uiState.breathingStep - 1
            viewModel.onBreathingTick(nextStep)
            if (nextStep == 0) viewModel.fadeBreathing()
        }
    }

    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Crossfade(
            targetState = uiState.showBreathing && uiState.breathingStep > 0,
            label = "breathingTransition"
        ) { breathing ->
            if (breathing) {
                BreathingScreen(step = uiState.breathingStep, modifier = Modifier.fillMaxSize())
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
                                        append("你刚刚打开了 ")
                                        withStyle(
                                            SpanStyle(color = ReminderHighlightRed, fontWeight = FontWeight.Bold)
                                        ) {
                                            append(uiState.appName)
                                        }
                                    },
                                    style = MaterialTheme.typography.titleMedium,
                                    color = DecisionBody,
                                    textAlign = TextAlign.Center
                                )
                                uiState.taskTitle?.let { taskTitle ->
                                    Text(
                                        buildAnnotatedString {
                                            append("原本任务：")
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
                                    Text(text = "此刻，就是最好的开始。", fontSize = 14.sp, color = DecisionFaint)
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
                                        "本窗口（${uiState.windowMinutes} 分钟）已提醒 " +
                                            "${uiState.windowReminderCount}/${uiState.windowLimit} 次",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = DecisionFaint
                                    )
                                }
                                uiState.customReturnError?.let { error ->
                                    Text(
                                        error,
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
                        "有目的使用多久"
                    } else {
                        "休息多久"
                    }
                )
            },
            text = {
                OutlinedTextField(
                    value = customSnoozeMinutes,
                    onValueChange = { customSnoozeMinutes = it },
                    label = { Text("\u8bf7\u8f93\u5165 1-60 \u5206\u949f") },
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
                ) { Text("\u786e\u5b9a") }
            },
            dismissButton = {
                TextButton(onClick = { customTimedAction = null }) {
                    Text("\u53d6\u6d88")
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
