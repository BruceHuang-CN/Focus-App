package com.example.focus_app.ui.reminder

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.TextButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.focus_app.domain.model.ReturnDestination
import com.example.focus_app.service.ReminderLaunchData
import com.example.focus_app.ui.theme.InkBlue
import kotlinx.coroutines.delay

@Composable
fun ReminderOverlay(
    data: ReminderLaunchData,
    onDismiss: () -> Unit,
    viewModel: ReminderViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    var exitMenuExpanded by remember { mutableStateOf(false) }
    var snoozeMenuExpanded by remember { mutableStateOf(false) }
    var customSnoozeVisible by remember { mutableStateOf(false) }
    var customSnoozeMinutes by remember { mutableStateOf("") }
    val urgency = remember(data.windowReminderCount, data.windowLimit) {
        reminderUrgency(data.windowReminderCount, data.windowLimit)
    }
    val escalation = remember(data.windowReminderCount, data.windowLimit) {
        reminderEscalationCopy(data.windowReminderCount, data.windowLimit)
    }
    val emphasisColor = MaterialTheme.colorScheme.error

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
        Card(
            modifier = Modifier
                .fillMaxWidth(urgency.widthFraction)
                .fillMaxHeight(urgency.heightFraction),
            shape = if (urgency.isFinalReminder) RectangleShape else MaterialTheme.shapes.large,
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 20.dp, vertical = 16.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    if (uiState.showBreathing && uiState.breathingStep > 0) {
                        Text("深呼吸一下", style = MaterialTheme.typography.headlineMedium, color = InkBlue)
                        Text("${uiState.breathingStep}", fontSize = 48.sp, color = InkBlue)
                    } else {
                        Text(
                            escalation.title,
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.ExtraBold,
                            color = if (urgency.isFinalReminder) emphasisColor else InkBlue,
                            textAlign = TextAlign.Center
                        )
                        Text(
                            buildAnnotatedString {
                                append("你刚刚打开了 ")
                                withStyle(SpanStyle(color = emphasisColor, fontWeight = FontWeight.Bold)) {
                                    append(uiState.appName)
                                }
                            },
                            style = MaterialTheme.typography.titleMedium,
                            textAlign = TextAlign.Center
                        )
                        uiState.taskTitle?.let { taskTitle ->
                            Text(
                                buildAnnotatedString {
                                    append("原本任务：")
                                    withStyle(SpanStyle(color = emphasisColor, fontWeight = FontWeight.Bold)) {
                                        append(taskTitle)
                                    }
                                },
                                style = MaterialTheme.typography.titleMedium,
                                textAlign = TextAlign.Center
                            )
                        }
                        Text(
                            uiState.message,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            lineHeight = 26.sp,
                            textAlign = TextAlign.Center
                        )
                        escalation.directive?.let { directive ->
                            Text(
                                directive,
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.Bold,
                                color = if (urgency.isFinalReminder) {
                                    emphasisColor
                                } else {
                                    MaterialTheme.colorScheme.onSurface
                                },
                                textAlign = TextAlign.Center
                            )
                        }
                        if (uiState.windowLimit > 0) {
                            Text(
                                "本窗口（${uiState.windowMinutes} 分钟）已提醒 " +
                                    "${uiState.windowReminderCount}/${uiState.windowLimit} 次",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.outline
                            )
                        }
                        uiState.customReturnError?.let { error ->
                            Text(
                                error,
                                style = MaterialTheme.typography.labelMedium,
                                color = emphasisColor,
                                textAlign = TextAlign.Center
                            )
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Box(modifier = Modifier.weight(1f)) {
                                Button(
                                    onClick = { exitMenuExpanded = true },
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = ButtonDefaults.buttonColors(containerColor = InkBlue)
                                ) {
                                    Text("\u9000\u51fa\u76ee\u6807\u5e94\u7528")
                                    Icon(
                                        imageVector = Icons.Filled.KeyboardArrowDown,
                                        contentDescription = "\u5c55\u5f00\u9000\u51fa\u9009\u9879"
                                    )
                                }
                                DropdownMenu(
                                    expanded = exitMenuExpanded,
                                    onDismissRequest = { exitMenuExpanded = false }
                                ) {
                                    exitDestinations(uiState.returnPackageName).forEach { destination ->
                                        DropdownMenuItem(
                                            text = {
                                                Text(
                                                    when (destination) {
                                                        ReturnDestination.HOME -> "\u8fd4\u56de\u684c\u9762"
                                                        ReturnDestination.FOCUS -> "\u8fd4\u56de Focus"
                                                        ReturnDestination.CUSTOM -> "\u6253\u5f00\u6307\u5b9a\u5e94\u7528"
                                                    }
                                                )
                                            },
                                            onClick = {
                                                exitMenuExpanded = false
                                                when (destination) {
                                                    ReturnDestination.FOCUS -> viewModel.returnToFocus(data.sessionId, onDismiss)
                                                    ReturnDestination.HOME -> viewModel.returnHome(data.sessionId, onDismiss)
                                                    ReturnDestination.CUSTOM -> viewModel.returnToCustom(data.sessionId, onDismiss)
                                                }
                                            }
                                        )
                                    }
                                }
                            }
                            Box(modifier = Modifier.weight(1f)) {
                                OutlinedButton(
                                    onClick = { snoozeMenuExpanded = true },
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text("\u7a0d\u540e\u63d0\u9192")
                                    Icon(
                                        imageVector = Icons.Filled.KeyboardArrowDown,
                                        contentDescription = "\u5c55\u5f00\u7a0d\u540e\u63d0\u9192\u9009\u9879"
                                    )
                                }
                                DropdownMenu(
                                    expanded = snoozeMenuExpanded,
                                    onDismissRequest = { snoozeMenuExpanded = false }
                                ) {
                                    presetSnoozeMinutes.forEach { minutes ->
                                        DropdownMenuItem(
                                            text = { Text("${minutes} \u5206\u949f\u540e\u63d0\u9192") },
                                            onClick = {
                                                snoozeMenuExpanded = false
                                                viewModel.snooze(data.sessionId, minutes, onDismiss)
                                            }
                                        )
                                    }
                                    DropdownMenuItem(
                                        text = { Text("\u81ea\u5b9a\u4e49\u5206\u949f\u6570") },
                                        onClick = {
                                            snoozeMenuExpanded = false
                                            customSnoozeVisible = true
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (customSnoozeVisible) {
        AlertDialog(
            onDismissRequest = { customSnoozeVisible = false },
            title = { Text("\u81ea\u5b9a\u4e49\u7a0d\u540e\u63d0\u9192") },
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
                            customSnoozeVisible = false
                            viewModel.snooze(data.sessionId, minutes, onDismiss)
                        }
                    }
                ) { Text("\u786e\u5b9a") }
            },
            dismissButton = {
                TextButton(onClick = { customSnoozeVisible = false }) {
                    Text("\u53d6\u6d88")
                }
            }
        )
    }
}
