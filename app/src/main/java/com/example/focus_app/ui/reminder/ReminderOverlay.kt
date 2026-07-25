package com.example.focus_app.ui.reminder

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.focus_app.ui.theme.InkBlue
import kotlinx.coroutines.delay

@Composable
fun ReminderOverlay(eventId: Long, appName: String, onDismiss: () -> Unit, viewModel: ReminderViewModel = hiltViewModel()) {
    val uiState by viewModel.uiState.collectAsState()
    var breathingStep by remember { mutableIntStateOf(5) }

    LaunchedEffect(Unit) { viewModel.init(eventId, appName, "gentle", showBreathing = true) }
    LaunchedEffect(uiState.showBreathing, breathingStep) {
        if (uiState.showBreathing && breathingStep > 0) { delay(1000L); breathingStep--; viewModel.onBreathingTick(breathingStep); if (breathingStep == 0) viewModel.fadeBreathing() }
    }

    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Card(modifier = Modifier.fillMaxWidth(0.9f).padding(16.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
            Column(modifier = Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
                if (uiState.showBreathing && breathingStep > 0) {
                    Text("深呼吸...", style = MaterialTheme.typography.headlineMedium, color = InkBlue)
                    Text("$breathingStep", fontSize = 48.sp, color = InkBlue)
                } else {
                    Text("⏰ 提醒", style = MaterialTheme.typography.headlineMedium, color = InkBlue)
                    if (uiState.isLoading) { CircularProgressIndicator(color = InkBlue); Text("AI 正在分析...") } else { Text(uiState.aiMessage, style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.Center) }
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Button(onClick = { viewModel.onExited(eventId); onDismiss() }, modifier = Modifier.weight(1f), colors = ButtonDefaults.buttonColors(containerColor = InkBlue)) { Text("退出，去干正事") }
                        OutlinedButton(onClick = { viewModel.onContinued(eventId); onDismiss() }, modifier = Modifier.weight(1f)) { Text("再刷一会...") }
                    }
                    val rem = (uiState.maxReminds - uiState.remindedCount).coerceAtLeast(0)
                    Text("本小时剩余提醒额度：${rem} 次", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
                }
            }
        }
    }
}
