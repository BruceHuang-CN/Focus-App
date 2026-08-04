package com.example.focus_app.ui.stats

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.focus_app.domain.model.AppShare
import com.example.focus_app.domain.model.DayBucket
import com.example.focus_app.domain.model.FocusStats
import com.example.focus_app.domain.model.HourBucket
import com.example.focus_app.domain.model.StatsRange
import com.example.focus_app.domain.usecase.StreakStatus
import com.example.focus_app.ui.theme.ErrorRed
import com.example.focus_app.ui.theme.InkBlue
import com.example.focus_app.ui.theme.SuccessGreen
import com.example.focus_app.ui.theme.WarningAmber
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StatsScreen(
    onBack: () -> Unit,
    viewModel: StatsViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("统计分析") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StatsRange.entries.forEach { range ->
                    FilterChip(
                        selected = uiState.range == range,
                        onClick = { viewModel.selectRange(range) },
                        label = { Text(range.label) }
                    )
                }
            }

            uiState.streak?.let { StreakCard(it) }

            uiState.stats?.let { stats ->
                MetricGrid(stats)
                ChartSection("按小时使用分布") {
                    HourlyBarChart(stats.hourly)
                }
                ChartSection("每日使用趋势") {
                    DailyTrendChart(stats.daily)
                }
                ChartSection("各应用占比") {
                    AppDonutChart(stats.byApp)
                }
            }
        }
    }
}

@Composable
private fun ChartSection(title: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title, style = MaterialTheme.typography.titleMedium)
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(12.dp)) {
                content()
            }
        }
    }
}

@Composable
private fun StreakCard(status: StreakStatus) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = if (status.achievedToday) {
            CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
        } else {
            CardDefaults.cardColors()
        }
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "连续达标 ${status.streakDays} 天",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    if (status.achievedToday) "今日达标 ✓" else "今日未达标",
                    style = MaterialTheme.typography.bodySmall,
                    color = if (status.achievedToday) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.outline
                    }
                )
            }
            Text(
                "今日短视频 ${status.shortVideoTodayMinutes}/${status.shortVideoLimitMinutes} 分钟 · " +
                    "完成任务 ${status.completedToday} 个",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.outline
            )
        }
    }
}

@Composable
private fun MetricGrid(stats: FocusStats) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            MetricCard("总时长", "${stats.totalDurationMinutes} 分钟", Modifier.weight(1f))
            MetricCard("打开次数", "${stats.openCount}", Modifier.weight(1f))
            MetricCard("提醒次数", "${stats.remindedCount}", Modifier.weight(1f))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            MetricCard("主动退出", "${stats.activeExitCount}", Modifier.weight(1f))
            MetricCard("继续使用", "${stats.continuedCount}", Modifier.weight(1f))
            MetricCard("退出率", "${(stats.exitRate * 100).toInt()}%", Modifier.weight(1f))
        }
    }
}

@Composable
private fun MetricCard(label: String, value: String, modifier: Modifier = Modifier) {
    Card(modifier = modifier) {
        Column(
            modifier = Modifier.padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(value, fontSize = 18.sp, fontWeight = FontWeight.Bold, color = InkBlue)
            Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
        }
    }
}

@Composable
internal fun HourlyBarChart(data: List<HourBucket>, modifier: Modifier = Modifier) {
    Column(modifier = modifier.fillMaxWidth()) {
        if (data.none { it.durationMinutes > 0 || it.openCount > 0 }) {
            Text("暂无数据", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
            return@Column
        }
        val max = data.maxOf { it.durationMinutes }.coerceAtLeast(1)
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(140.dp)
                .testTag("hourly_bar_chart")
        ) {
            val barWidth = size.width / data.size
            data.forEachIndexed { index, bucket ->
                if (bucket.durationMinutes > 0) {
                    val height = size.height * bucket.durationMinutes / max
                    drawRect(
                        color = InkBlue,
                        topLeft = Offset(index * barWidth + barWidth * 0.15f, size.height - height),
                        size = Size(barWidth * 0.7f, height)
                    )
                }
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            listOf("0时", "6时", "12时", "18时", "24时").forEach { label ->
                Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
            }
        }
    }
}

@Composable
internal fun DailyTrendChart(data: List<DayBucket>, modifier: Modifier = Modifier) {
    Column(modifier = modifier.fillMaxWidth()) {
        if (data.none { it.durationMinutes > 0 }) {
            Text("暂无数据", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
            return@Column
        }
        val max = data.maxOf { it.durationMinutes }.coerceAtLeast(1)
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(140.dp)
                .testTag("daily_trend_chart")
        ) {
            val stepX = if (data.size > 1) size.width / (data.size - 1) else 0f
            val points = data.mapIndexed { index, bucket ->
                Offset(
                    x = index * stepX,
                    y = size.height - size.height * bucket.durationMinutes / max
                )
            }
            if (points.size == 1) {
                drawCircle(SuccessGreen, 5.dp.toPx(), points.first())
            } else {
                val path = Path().apply {
                    moveTo(points.first().x, points.first().y)
                    points.drop(1).forEach { lineTo(it.x, it.y) }
                }
                drawPath(path, SuccessGreen, style = Stroke(2.dp.toPx()))
                points.forEach { drawCircle(SuccessGreen, 3.dp.toPx(), it) }
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                data.first().date.format(DAY_FORMAT),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.outline
            )
            Text(
                data.last().date.format(DAY_FORMAT),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.outline
            )
        }
    }
}

@Composable
internal fun AppDonutChart(data: List<AppShare>, modifier: Modifier = Modifier) {
    val total = data.sumOf { it.durationMinutes }
    val surfaceColor = MaterialTheme.colorScheme.surface
    Column(modifier = modifier.fillMaxWidth()) {
        if (data.isEmpty() || total <= 0) {
            Text("暂无数据", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
            return@Column
        }
        Canvas(
            modifier = Modifier
                .align(Alignment.CenterHorizontally)
                .size(160.dp)
                .testTag("app_donut_chart")
        ) {
            var startAngle = -90f
            data.forEachIndexed { index, share ->
                val sweep = share.durationMinutes.toFloat() / total * 360f
                drawArc(
                    color = chartColors[index % chartColors.size],
                    startAngle = startAngle,
                    sweepAngle = sweep,
                    useCenter = true
                )
                startAngle += sweep
            }
            drawCircle(
                color = surfaceColor,
                radius = size.minDimension * 0.45f
            )
        }
        data.forEachIndexed { index, share ->
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(12.dp)
                        .background(chartColors[index % chartColors.size], CircleShape)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    share.appName,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    "${share.durationMinutes} 分钟 · ${(share.durationMinutes * 100f / total).toInt()}%",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.outline
                )
            }
        }
    }
}

private val chartColors = listOf(
    InkBlue,
    SuccessGreen,
    WarningAmber,
    ErrorRed,
    Color(0xFF7E57C2),
    Color(0xFF26A69A)
)

private val DAY_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("MM-dd")
