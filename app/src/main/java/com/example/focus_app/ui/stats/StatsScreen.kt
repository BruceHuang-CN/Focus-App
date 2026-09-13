package com.example.focus_app.ui.stats

import com.example.focus_app.R
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
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

@Composable
fun StatsScreen(onBack: () -> Unit, viewModel: StatsViewModel = hiltViewModel()) {
    val textContext = androidx.compose.ui.platform.LocalContext.current
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var details by rememberSaveable { mutableStateOf(false) }
    var day by rememberSaveable { mutableStateOf<String?>(null) }
    var decision by rememberSaveable { mutableStateOf<String?>(null) }
    androidx.activity.compose.BackHandler(details) { details = false }
    if (details) {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            TextButton(onClick = { details = false }) { Text(textContext.getString(R.string.core_back_stats)) }
            Text(textContext.getString(R.string.core_details_range, state.range.displayLabel(textContext)), style = MaterialTheme.typography.headlineMedium)
            state.snapshot?.usage?.let { stats ->
                ChartSection(textContext.getString(R.string.core_hourly_distribution)) { HourlyBarChart(stats.hourly) }
                ChartSection(textContext.getString(R.string.core_daily_trend)) { DailyTrendChart(stats.daily) }
                ChartSection(textContext.getString(R.string.core_app_share)) { AppDonutChart(stats.byApp) }
            }
        }
    } else StatsContent(state, onRange = viewModel::selectRange, onPeriod = viewModel::selectPeriod,
        onShiftPeriod = viewModel::shiftPeriod, onDay = { day = it.toString() },
        onDecision = { decision = it.name }, onDetails = { details = true }, onRetry = viewModel::refresh)
    day?.let { selected ->
        val date = java.time.LocalDate.parse(selected)
        val zone = state.snapshot?.asOf?.zone ?: java.time.ZoneId.systemDefault()
        val events = state.snapshot?.displays.orEmpty().filter { java.time.Instant.ofEpochMilli(it.displayedAt).atZone(zone).toLocalDate() == date }
        AlertDialog(onDismissRequest = { day = null }, title = { Text(textContext.getString(R.string.core_day_reminders, date.format(java.time.format.DateTimeFormatter.ofLocalizedDate(java.time.format.FormatStyle.MEDIUM).withLocale(textContext.resources.configuration.locales[0])), events.size)) },
            text = { androidx.compose.foundation.lazy.LazyColumn(Modifier.heightIn(max = 360.dp)) {
                if (events.isEmpty()) item { Text(textContext.getString(R.string.core_no_display_history)) }
                items(events.size) { index -> val event = events[index]
                    Text(java.time.Instant.ofEpochMilli(event.displayedAt).atZone(zone).format(DateTimeFormatter.ofPattern("HH:mm:ss")) +
                        "  " + when (event.kind) { "follow_up" -> textContext.getString(R.string.core_followup); "forced_redisplay" -> textContext.getString(R.string.core_forced); else -> textContext.getString(R.string.core_reminder) }, Modifier.padding(vertical = 8.dp))
                }
            } }, confirmButton = { TextButton(onClick = { day = null }) { Text(textContext.getString(R.string.core_close)) } })
    }
    decision?.let { name ->
        val kind = com.example.focus_app.domain.stats.DecisionKind.valueOf(name)
        val events = state.snapshot?.actions.orEmpty().filter { com.example.focus_app.domain.stats.ReminderStatistics.classify(it.actionKey) == kind }
        AlertDialog(onDismissRequest = { decision = null }, title = { Text(textContext.getString(R.string.core_decision_count, kind.displayLabel(textContext), events.size)) },
            text = { androidx.compose.foundation.lazy.LazyColumn(Modifier.heightIn(max = 360.dp)) {
                if (events.isEmpty()) item { Text(textContext.getString(R.string.core_no_decisions_range)) }
                items(events.size) { index -> Text(java.time.Instant.ofEpochMilli(events[index].occurredAt)
                    .atZone(state.snapshot?.asOf?.zone ?: java.time.ZoneId.systemDefault()).format(DateTimeFormatter.ofLocalizedDateTime(java.time.format.FormatStyle.SHORT).withLocale(textContext.resources.configuration.locales[0])), Modifier.padding(vertical = 8.dp)) }
            } }, confirmButton = { TextButton(onClick = { decision = null }) { Text(textContext.getString(R.string.core_close)) } })
    }
}

@Composable
private fun ChartSection(title: String, content: @Composable () -> Unit) {
    val textContext = androidx.compose.ui.platform.LocalContext.current
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title, style = MaterialTheme.typography.titleMedium)
        Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
            Column(modifier = Modifier.padding(12.dp)) {
                content()
            }
        }
    }
}

@Composable
private fun StreakCard(status: StreakStatus) {
    val textContext = androidx.compose.ui.platform.LocalContext.current
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
                    textContext.getString(R.string.core_streak, status.streakDays),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    if (status.achievedToday) textContext.getString(R.string.core_achieved) else textContext.getString(R.string.core_not_achieved),
                    style = MaterialTheme.typography.bodySmall,
                    color = if (status.achievedToday) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    }
                )
            }
            Text(
                textContext.getString(R.string.core_daily_summary, status.shortVideoTodayMinutes, status.shortVideoLimitMinutes, status.completedToday),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun MetricGrid(stats: FocusStats) {
    val textContext = androidx.compose.ui.platform.LocalContext.current
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            MetricCard(textContext.getString(R.string.core_app_time), textContext.getString(R.string.core_minutes, stats.totalDurationMinutes), Modifier.weight(1f))
            MetricCard(textContext.getString(R.string.core_opens), "${stats.openCount}", Modifier.weight(1f))
            MetricCard(textContext.getString(R.string.core_reminder_count), "${stats.remindedCount}", Modifier.weight(1f))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            MetricCard(textContext.getString(R.string.core_active_exit), "${stats.activeExitCount}", Modifier.weight(1f))
            MetricCard(textContext.getString(R.string.core_continue_use), "${stats.continuedCount}", Modifier.weight(1f))
            MetricCard(textContext.getString(R.string.core_exit_rate), "${(stats.exitRate * 100).toInt()}%", Modifier.weight(1f))
        }
    }
}

@Composable
private fun MetricCard(label: String, value: String, modifier: Modifier = Modifier) {
    val textContext = androidx.compose.ui.platform.LocalContext.current
    Card(modifier = modifier) {
        Column(
            modifier = Modifier.padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(value, fontSize = 18.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
            Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
internal fun HourlyBarChart(data: List<HourBucket>, modifier: Modifier = Modifier) {
    val textContext = androidx.compose.ui.platform.LocalContext.current
    val graphColor = MaterialTheme.colorScheme.primary
    var selectedHour by remember { mutableStateOf<Int?>(null) }
    Column(modifier = modifier.fillMaxWidth()) {
        if (data.none { it.durationMinutes > 0 || it.openCount > 0 }) {
            Text(textContext.getString(R.string.core_no_data), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            return@Column
        }
        val max = data.maxOf { it.durationMinutes }.coerceAtLeast(1)
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(140.dp)
                .pointerInput(data) {
                    detectTapGestures { offset ->
                        val barWidth = size.width / data.size.coerceAtLeast(1)
                        selectedHour = (offset.x / barWidth).toInt().coerceIn(0, data.size - 1)
                    }
                }
                .testTag("hourly_bar_chart")
        ) {
            val barWidth = size.width / data.size
            data.forEachIndexed { index, bucket ->
                if (bucket.durationMinutes > 0) {
                    val height = size.height * bucket.durationMinutes / max
                    drawRect(
                        color = graphColor,
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
            listOf(0, 6, 12, 18, 24).map { textContext.getString(R.string.core_hour, it) }.forEach { label ->
                Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        val selected = selectedHour?.let { hour -> data.getOrNull(hour) }
        if (selected != null) {
            Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        textContext.getString(R.string.core_hour, selected.hour),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                    if (selected.apps.isEmpty()) {
                        Text(
                            textContext.getString(R.string.core_no_hour_data),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        selected.apps.forEach { app ->
                            Row(modifier = Modifier.fillMaxWidth()) {
                                Text(
                                    app.appName,
                                    style = MaterialTheme.typography.bodySmall,
                                    modifier = Modifier.weight(1f)
                                )
                                Text(
                                    textContext.getString(R.string.core_minutes, app.durationMinutes),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
internal fun DailyTrendChart(data: List<DayBucket>, modifier: Modifier = Modifier) {
    val textContext = androidx.compose.ui.platform.LocalContext.current
    val graphColor = MaterialTheme.colorScheme.primary
    Column(modifier = modifier.fillMaxWidth()) {
        if (data.none { it.durationMinutes > 0 }) {
            Text(textContext.getString(R.string.core_no_data), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
                data.first().date.format(DateTimeFormatter.ofLocalizedDate(java.time.format.FormatStyle.SHORT).withLocale(textContext.resources.configuration.locales[0])),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                data.last().date.format(DateTimeFormatter.ofLocalizedDate(java.time.format.FormatStyle.SHORT).withLocale(textContext.resources.configuration.locales[0])),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
internal fun AppDonutChart(data: List<AppShare>, modifier: Modifier = Modifier) {
    val textContext = androidx.compose.ui.platform.LocalContext.current
    val chartColors = listOf(MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.secondary, MaterialTheme.colorScheme.tertiary, MaterialTheme.colorScheme.primaryContainer, MaterialTheme.colorScheme.secondaryContainer)
    val total = data.sumOf { it.durationMinutes }
    val surfaceColor = MaterialTheme.colorScheme.surface
    Column(modifier = modifier.fillMaxWidth()) {
        if (data.isEmpty() || total <= 0) {
            Text(textContext.getString(R.string.core_no_data), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
                    textContext.getString(R.string.core_minutes_share, share.durationMinutes, (share.durationMinutes * 100f / total).toInt()),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
