package com.example.focus_app.ui.stats

import com.example.focus_app.R
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.focus_app.data.repository.StatisticsSnapshot
import com.example.focus_app.domain.model.*
import com.example.focus_app.domain.stats.*
import com.example.focus_app.ui.theme.FocusAppTheme
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.TemporalAdjusters
import java.time.DayOfWeek

@Composable
fun StatsContent(state: StatsUiState, modifier: Modifier = Modifier,
    onRange: (StatsRange) -> Unit = {}, onPeriod: (ActivityPeriod) -> Unit = {},
    onShiftPeriod: (Int) -> Unit = {}, onDay: (LocalDate) -> Unit = {},
    onDecision: (DecisionKind) -> Unit = {}, onDetails: () -> Unit = {}, onRetry: () -> Unit = {}) {
    val textContext = androidx.compose.ui.platform.LocalContext.current
    Column(modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)
        .verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Spacer(Modifier.height(12.dp))
        Text(textContext.getString(R.string.core_stats), fontSize = 40.sp, fontWeight = FontWeight.ExtraBold)
        Text(textContext.getString(R.string.core_stats_subtitle), color = MaterialTheme.colorScheme.onSurfaceVariant)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            StatsRange.entries.forEach { range ->
                Surface(onClick = { onRange(range) }, shape = RoundedCornerShape(18.dp), modifier = Modifier.weight(1f),
                    color = if (state.range == range) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface,
                    contentColor = if (state.range == range) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant) {
                    Box(Modifier.padding(horizontal = 3.dp, vertical = 14.dp), contentAlignment = Alignment.Center) {
                        Text(range.displayLabel(textContext), style = MaterialTheme.typography.labelLarge)
                    }
                }
            }
        }
        if (state.error != null) TextButton(onClick = onRetry) { Text(textContext.getString(state.error)) }
        if (state.loading && state.snapshot == null) LinearProgressIndicator(Modifier.fillMaxWidth())
        StatsOverviewCard(state.range, state.snapshot)
        ReminderActivityHeatmap(state.period, state.anchor, state.snapshot?.days.orEmpty(), onPeriod, onShiftPeriod, onDay)
        ReminderDecisionDonutCard(state.snapshot?.decisions.orEmpty(), onDecision, onDetails)
        state.snapshot?.let { snapshot ->
            Text(textContext.getString(R.string.core_stats_as_of, snapshot.asOf.format(java.time.format.DateTimeFormatter.ofLocalizedDateTime(java.time.format.FormatStyle.SHORT).withLocale(textContext.resources.configuration.locales[0]))),
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(textContext.getString(R.string.core_coverage_help),
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (snapshot.legacySessionCount > 0) Text(textContext.getString(R.string.core_legacy_count, snapshot.legacySessionCount),
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        StatsCard {
            SectionHeading(Icons.Default.Star, textContext.getString(R.string.core_today_status), textContext.getString(R.string.core_status_soon))
        }
        Spacer(Modifier.height(4.dp))
    }
}

@Composable private fun StatsCard(content: @Composable ColumnScope.() -> Unit) {
    val textContext = androidx.compose.ui.platform.LocalContext.current
    Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(26.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp), content = content)
    }
}
@Composable private fun SectionHeading(icon: ImageVector, title: String, subtitle: String? = null) {
    val textContext = androidx.compose.ui.platform.LocalContext.current
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(42.dp).background(MaterialTheme.colorScheme.primaryContainer, CircleShape), contentAlignment = Alignment.Center) {
            Icon(icon, null, tint = MaterialTheme.colorScheme.primary)
        }
        Column(Modifier.padding(start = 12.dp).weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            subtitle?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
    }
}
private data class Metric(val icon: ImageVector, val label: String, val value: String)
@Composable internal fun StatsOverviewCard(range: StatsRange, snapshot: StatisticsSnapshot?) {
    val textContext = androidx.compose.ui.platform.LocalContext.current
    val decisions = snapshot?.decisions.orEmpty()
    val metrics = listOf(
        Metric(Icons.Default.Phone, textContext.getString(R.string.core_opens), snapshot?.usage?.openCount?.toString() ?: "—"),
        Metric(Icons.Default.CheckCircle, textContext.getString(R.string.core_return_task), snapshot?.let { decisions.firstOrNull { it.kind == DecisionKind.RETURN }?.count ?: 0 }?.toString() ?: "—"),
        Metric(Icons.Default.PlayArrow, textContext.getString(R.string.core_intentional), snapshot?.let { decisions.firstOrNull { it.kind == DecisionKind.INTENTIONAL }?.count ?: 0 }?.toString() ?: "—"),
        Metric(Icons.Default.Notifications, textContext.getString(R.string.core_coverage), snapshot?.coverage?.let { "${kotlin.math.round(it * 100).toInt()}%" } ?: "—"),
        Metric(Icons.Default.DateRange, textContext.getString(R.string.core_app_time), snapshot?.usage?.totalDurationMinutes?.let { textContext.getString(R.string.core_minutes, it) } ?: "—")
    )
    val fontScale = LocalDensity.current.fontScale
    StatsCard {
        SectionHeading(com.example.focus_app.ui.theme.ForestIcons.Statistics, if (range == StatsRange.TODAY) textContext.getString(R.string.core_today_data) else textContext.getString(R.string.core_range_data, range.displayLabel(textContext)))
        BoxWithConstraints {
            val columns = if (maxWidth >= 700.dp && fontScale <= 1.15f) 5 else if (maxWidth >= 480.dp && fontScale <= 1.15f) 3 else 2
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                metrics.chunked(columns).forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        row.forEach { metric ->
                            Surface(Modifier.weight(1f), shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.surfaceVariant) {
                                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Icon(metric.icon, null, tint = MaterialTheme.colorScheme.primary)
                                    Text(metric.label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Text(metric.value, fontSize = 23.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable internal fun ReminderActivityHeatmap(period: ActivityPeriod, anchor: LocalDate, days: List<ActivityDay>,
    onPeriod: (ActivityPeriod) -> Unit, onShift: (Int) -> Unit, onDay: (LocalDate) -> Unit) {
    val textContext = androidx.compose.ui.platform.LocalContext.current
    val colors = MaterialTheme.colorScheme
    val (from, until) = ReminderStatistics.bounds(anchor, period)
    StatsCard {
        SectionHeading(Icons.Default.DateRange, textContext.getString(R.string.core_activity), textContext.getString(R.string.core_activity_help))
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            ActivityPeriod.entries.forEach { option ->
                FilterChip(selected = option == period, onClick = { onPeriod(option) }, label = { Text(option.displayLabel(textContext)) })
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { onShift(-1) }) { Icon(Icons.Default.KeyboardArrowLeft, textContext.getString(R.string.core_previous_period, period.displayLabel(textContext))) }
            val dateFormat = java.time.format.DateTimeFormatter.ofLocalizedDate(java.time.format.FormatStyle.MEDIUM)
                .withLocale(textContext.resources.configuration.locales[0])
            Text("${from.format(dateFormat)} — ${until.minusDays(1).format(dateFormat)}", modifier = Modifier.weight(1f), style = MaterialTheme.typography.labelMedium)
            IconButton(onClick = { onShift(1) }, enabled = until <= LocalDate.now()) { Icon(Icons.Default.KeyboardArrowRight, textContext.getString(R.string.core_next_period, period.displayLabel(textContext))) }
        }
        val max = days.maxOfOrNull { it.count }?.coerceAtLeast(1) ?: 1
        if (days.isEmpty()) Text(textContext.getString(R.string.core_no_activity), color = colors.onSurfaceVariant)
        else if (period == ActivityPeriod.YEAR) {
            val firstMonday = from.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
            val weeks = ((java.time.temporal.ChronoUnit.DAYS.between(firstMonday, until) + 6) / 7).toInt()
            val byDate = days.associateBy { it.date }
            val scroll = rememberScrollState()
            val cellWidth = with(LocalDensity.current) { 30.dp.roundToPx() }
            LaunchedEffect(from, days.size) {
                val target = LocalDate.now().coerceIn(from, until.minusDays(1)).withDayOfMonth(1)
                val week = java.time.temporal.ChronoUnit.WEEKS.between(firstMonday, target).toInt()
                scroll.scrollTo((week * cellWidth).coerceAtLeast(0))
            }
            Column(Modifier.horizontalScroll(scroll)) {
                Box(Modifier.width((weeks * 30 + 24).dp).height(28.dp)) {
                    generateSequence(from.withDayOfMonth(1)) { it.plusMonths(1) }
                        .takeWhile { it < until }.forEach { month ->
                            val column = ReminderStatistics.weekColumn(firstMonday, month)
                            Text(month.month.getDisplayName(java.time.format.TextStyle.SHORT, textContext.resources.configuration.locales[0]), style = MaterialTheme.typography.labelSmall,
                                maxLines = 1, softWrap = false,
                                modifier = Modifier.offset(x = (column * 30).dp).width(50.dp))
                        }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    repeat(weeks) { week ->
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            (0..6).forEach { row ->
                                val day = byDate[firstMonday.plusDays(week * 7L + row)]
                                if (day == null) Spacer(Modifier.size(26.dp))
                                else DayCell(day, max, Modifier.size(26.dp), onDay)
                            }
                        }
                    }
                }
            }
        } else {
            val startOffset = if (period == ActivityPeriod.WEEK) 0 else from.dayOfWeek.value - 1
            val cells: List<ActivityDay?> = List(startOffset) { null } + days
            Row { java.time.DayOfWeek.values().map { it.getDisplayName(java.time.format.TextStyle.NARROW, textContext.resources.configuration.locales[0]) }.forEach { Text(it, Modifier.weight(1f), style = MaterialTheme.typography.labelSmall) } }
            cells.chunked(7).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    repeat(7) { index ->
                        row.getOrNull(index)?.let { DayCell(it, max, Modifier.weight(1f).aspectRatio(1f), onDay, showDay = true) }
                            ?: Spacer(Modifier.weight(1f).aspectRatio(1f))
                    }
                }
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(textContext.getString(R.string.core_less), style = MaterialTheme.typography.labelSmall)
            repeat(5) { step -> Box(Modifier.size(14.dp).background(lerp(colors.primaryContainer, colors.primary, step / 4f), RoundedCornerShape(3.dp))) }
            Text(textContext.getString(R.string.core_more), style = MaterialTheme.typography.labelSmall)
        }
        Text(textContext.getString(R.string.core_heatmap_help),
            style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
    }
}

@Composable private fun DayCell(day: ActivityDay, max: Int, modifier: Modifier, onDay: (LocalDate) -> Unit, showDay: Boolean = false) {
    val textContext = androidx.compose.ui.platform.LocalContext.current
    val c = MaterialTheme.colorScheme
    val level = if (day.count == 0) 0f else 0.25f + 0.75f * day.count / max
    val fill = if (day.future) c.surfaceVariant else lerp(c.primaryContainer, c.primary, level)
    Box(modifier.background(fill, RoundedCornerShape(5.dp))
        .clickable(enabled = !day.future) { onDay(day.date) }
        .semantics { contentDescription = day.date.format(java.time.format.DateTimeFormatter.ofLocalizedDate(java.time.format.FormatStyle.MEDIUM).withLocale(textContext.resources.configuration.locales[0])) + ", " + (if (day.future) textContext.getString(R.string.core_future_date) else textContext.getString(R.string.core_reminders_count, day.count)) + (if (day.historyIncomplete) textContext.getString(R.string.core_incomplete_history) else "") },
        contentAlignment = Alignment.Center) {
        Text(if (day.future) "" else if (showDay) day.date.dayOfMonth.toString() + if (day.historyIncomplete) "·" else "" else if (day.historyIncomplete && day.count == 0) "·" else "",
            style = MaterialTheme.typography.labelSmall, color = if (level > 0.6f) c.onPrimary else c.onPrimaryContainer)
    }
}

@Composable internal fun ReminderDecisionDonutCard(data: List<DecisionCount>, onDecision: (DecisionKind) -> Unit, onDetails: () -> Unit) {
    val textContext = androidx.compose.ui.platform.LocalContext.current
    val c = MaterialTheme.colorScheme
    val tones = listOf(c.primary, c.secondary, c.primaryContainer, c.outline)
    val total = data.sumOf { it.count }
    val fontScale = LocalDensity.current.fontScale
    val chart: @Composable () -> Unit = {
        Box(Modifier.size(130.dp), contentAlignment = Alignment.Center) {
            Canvas(Modifier.fillMaxSize().padding(12.dp).semantics { contentDescription = textContext.getString(R.string.core_recorded_count, total) }) {
                if (total == 0) drawArc(c.outlineVariant, 0f, 360f, false, style = Stroke(22.dp.toPx()))
                else {
                    var start = -90f
                    data.forEach { row ->
                        val sweep = row.count.toFloat() / total * 360f
                        drawArc(tones[row.kind.ordinal], start, sweep, false, style = Stroke(22.dp.toPx()))
                        start += sweep
                    }
                }
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(textContext.getString(R.string.core_total), style = MaterialTheme.typography.labelSmall, color = c.onSurfaceVariant)
                Text(textContext.getString(R.string.core_total_count, total), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            }
        }
    }
    val legend: @Composable () -> Unit = {
        Column {
            DecisionKind.entries.filter { it != DecisionKind.LEGACY || data.any { row -> row.kind == it && row.count > 0 } }.forEach { kind ->
                val count = data.firstOrNull { it.kind == kind }?.count ?: 0
                Row(Modifier.fillMaxWidth().clickable { onDecision(kind) }.padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(10.dp).background(tones[kind.ordinal], CircleShape))
                    Text(kind.displayLabel(textContext), Modifier.weight(1f).padding(start = 7.dp), style = MaterialTheme.typography.bodySmall)
                    Text("$count", style = MaterialTheme.typography.labelLarge)
                    Text(if (total == 0) "  —" else "  ${kotlin.math.round(count * 100f / total).toInt()}%", style = MaterialTheme.typography.labelSmall, color = c.onSurfaceVariant)
                }
                HorizontalDivider(color = c.outlineVariant.copy(alpha = 0.5f))
            }
        }
    }
    StatsCard {
        SectionHeading(Icons.Default.AccountCircle, textContext.getString(R.string.core_usage), textContext.getString(R.string.core_after_reminder))
        BoxWithConstraints {
            if (maxWidth >= 440.dp && fontScale <= 1.15f) Row(verticalAlignment = Alignment.CenterVertically) {
                chart(); Spacer(Modifier.width(12.dp)); Box(Modifier.weight(1f)) { legend() }
            } else Column(horizontalAlignment = Alignment.CenterHorizontally) { chart(); legend() }
        }
        if (total == 0) Text(textContext.getString(R.string.core_no_decisions), style = MaterialTheme.typography.bodySmall, color = c.onSurfaceVariant)
        TextButton(onClick = onDetails) { Text(textContext.getString(R.string.core_usage_details)) }
    }
}

@Preview(name = "统计 · 日间", widthDp = 393, heightDp = 950)
@Composable private fun StatsDayPreview() {
    val textContext = androidx.compose.ui.platform.LocalContext.current
    StatsPreview(AppThemeMode.DAY) }
@Preview(name = "统计 · 夜间", widthDp = 393, heightDp = 950)
@Composable private fun StatsNightPreview() {
    val textContext = androidx.compose.ui.platform.LocalContext.current
    StatsPreview(AppThemeMode.NIGHT) }
@Preview(name = "统计 · 窄屏大字", widthDp = 320, heightDp = 950, fontScale = 1.3f)
@Composable private fun StatsNarrowPreview() {
    val textContext = androidx.compose.ui.platform.LocalContext.current
    StatsPreview(AppThemeMode.DAY) }
@Preview(name = "统计 · 空数据", widthDp = 393, heightDp = 950)
@Composable private fun StatsEmptyPreview() {
    val textContext = androidx.compose.ui.platform.LocalContext.current
    StatsPreview(AppThemeMode.DAY, empty = true) }
@Composable private fun StatsPreview(mode: AppThemeMode, empty: Boolean = false) {
    val textContext = androidx.compose.ui.platform.LocalContext.current
    // Fixtures are restricted to Preview; the live route receives repository snapshots.
    val today = LocalDate.of(2026, 9, 9)
    val usage = if (empty) FocusStats(0, 0, 0, 0, 0, 0f, emptyList(), emptyList(), emptyList())
        else FocusStats(38, 6, 4, 4, 2, 0.66f, emptyList(), emptyList(), emptyList())
    val days = (0L..29L).map { ActivityDay(today.withDayOfMonth(1).plusDays(it), if (!empty && it % 3L == 0L && it < 9) 3 else 0, it >= 9, false) }
    FocusAppTheme(mode) {
        StatsContent(StatsUiState(period = ActivityPeriod.MONTH, anchor = today, loading = false,
            snapshot = StatisticsSnapshot(usage, if (empty) null else 0.66f, if (empty) emptyList() else listOf(DecisionCount(DecisionKind.RETURN, 4),
                DecisionCount(DecisionKind.INTENTIONAL, 2), DecisionCount(DecisionKind.REST, 6)), days,
                emptyList(), emptyList(), 0, today.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli())))
    }
}
