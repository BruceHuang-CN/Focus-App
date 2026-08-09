package com.example.focus_app.ui.reminder

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
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
            modifier = Modifier.fillMaxWidth(0.9f).padding(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                if (uiState.showBreathing && uiState.breathingStep > 0) {
                    Text("深呼吸一下", style = MaterialTheme.typography.headlineMedium, color = InkBlue)
                    Text("${uiState.breathingStep}", fontSize = 48.sp, color = InkBlue)
                } else {
                    Text("先停一下", style = MaterialTheme.typography.headlineMedium, color = InkBlue)
                    Text("你刚刚打开了 ${uiState.appName}", style = MaterialTheme.typography.labelLarge)
                    uiState.taskTitle?.let {
                        Text(
                            "原本要做：$it",
                            style = MaterialTheme.typography.titleMedium,
                            textAlign = TextAlign.Center
                        )
                    }
                    Text(
                        uiState.message,
                        style = MaterialTheme.typography.bodyLarge,
                        textAlign = TextAlign.Center
                    )
                    if (uiState.windowLimit > 0) {
                        Text(
                            "本窗口（${uiState.windowMinutes} 分钟）已提醒 " +
                                "${uiState.windowReminderCount}/${uiState.windowLimit} 次",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.outline
                        )
                    }
                    uiState.customReturnError?.let { error ->
                        Text(
                            error,
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.error,
                            textAlign = TextAlign.Center
                        )
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Button(
                            onClick = {
                                when (uiState.returnDestination) {
                                    ReturnDestination.FOCUS ->
                                        viewModel.returnToFocus(data.sessionId, onDismiss)
                                    ReturnDestination.HOME ->
                                        viewModel.returnHome(data.sessionId, onDismiss)
                                    ReturnDestination.CUSTOM ->
                                        viewModel.returnToCustom(data.sessionId, onDismiss)
                                }
                            },
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(containerColor = InkBlue)
                        ) {
                            Text(
                                when (uiState.returnDestination) {
                                    ReturnDestination.FOCUS -> "不刷了，回到 Focus"
                                    ReturnDestination.HOME -> "不刷了，回到桌面"
                                    ReturnDestination.CUSTOM -> "不刷了，去指定应用"
                                }
                            )
                        }
                        OutlinedButton(
                            onClick = { viewModel.continueTargetApp(data.sessionId, onDismiss) },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("仍要使用")
                        }
                    }
                }
            }
        }
    }
}
