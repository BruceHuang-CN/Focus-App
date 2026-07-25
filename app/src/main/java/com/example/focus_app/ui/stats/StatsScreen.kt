package com.example.focus_app.ui.stats

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.focus_app.ui.theme.InkBlue
import com.example.focus_app.ui.theme.SuccessGreen

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StatsScreen(onBack: () -> Unit, viewModel: StatsViewModel = hiltViewModel()) {
    val stats by viewModel.uiState.collectAsState()
    Scaffold(topBar = { TopAppBar(title = { Text("统计分析") }) }) { padding ->
        Column(modifier = Modifier.padding(padding).padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text("今日数据", style = MaterialTheme.typography.headlineMedium)
            Card {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("打开次数: ${stats.openCount}", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = InkBlue)
                    Text("主动退出: ${stats.exitedCount}", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = SuccessGreen)
                    Text("克制率: ${(stats.exitRate * 100).toInt()}%", fontSize = 16.sp, color = MaterialTheme.colorScheme.outline)
                }
            }
            Text("更多统计图表将在后续版本中提供", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.outline)
        }
    }
}
