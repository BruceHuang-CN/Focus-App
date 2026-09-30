package com.example.focus_app.ui.summary

import android.Manifest
import android.app.TimePickerDialog
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationManagerCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.example.focus_app.R
import com.example.focus_app.util.PermissionHelper
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
fun DailySummarySettingsCard(viewModel: DailySummaryViewModel = hiltViewModel()) {
    val context = LocalContext.current
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    var notificationAllowed by remember { mutableStateOf(NotificationManagerCompat.from(context).areNotificationsEnabled()) }
    LifecycleResumeEffect(context) {
        notificationAllowed = NotificationManagerCompat.from(context).areNotificationsEnabled()
        onPauseOrDispose { }
    }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        notificationAllowed = NotificationManagerCompat.from(context).areNotificationsEnabled()
    }
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.summary_schedule), Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
                Switch(settings.enabled, onCheckedChange = { enabled ->
                    viewModel.configure(enabled, settings.minuteOfDay)
                    if (enabled && Build.VERSION.SDK_INT >= 33 && !NotificationManagerCompat.from(context).areNotificationsEnabled()) {
                        launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
                    }
                })
            }
            Text(stringResource(R.string.summary_schedule_help), style = MaterialTheme.typography.bodySmall)
            OutlinedButton(onClick = {
                TimePickerDialog(context, { _, hour, minute ->
                    viewModel.configure(settings.enabled, hour * 60 + minute)
                }, settings.minuteOfDay / 60, settings.minuteOfDay % 60, true).show()
            }, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.summary_time, String.format(Locale.ROOT, "%02d:%02d", settings.minuteOfDay / 60, settings.minuteOfDay % 60)))
            }
            if (settings.enabled && !notificationAllowed) TextButton(onClick = { PermissionHelper.openNotificationSettings(context) }) {
                Text(stringResource(R.string.summary_enable_notifications))
            }
        }
    }
}

@Composable
fun DailySummaryCard(viewModel: DailySummaryViewModel) {
    val record by viewModel.record.collectAsStateWithLifecycle()
    val generating by viewModel.generating.collectAsStateWithLifecycle()
    val failed by viewModel.failed.collectAsStateWithLifecycle()
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(stringResource(R.string.summary_title), style = MaterialTheme.typography.titleLarge)
            Text(stringResource(R.string.summary_data_help), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            record?.let { result ->
                val time = Instant.ofEpochMilli(result.generatedAtMillis).atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("HH:mm"))
                Text("${result.facts.date} · $time", style = MaterialTheme.typography.labelMedium)
                Text(stringResource(R.string.summary_facts, result.facts.completedTasks, result.facts.trackedAppMinutes, result.facts.appOpens))
                result.text?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
                result.error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
            } ?: Text(stringResource(R.string.summary_empty))
            if (failed) Text(stringResource(R.string.summary_failed), color = MaterialTheme.colorScheme.error)
            if (generating) LinearProgressIndicator(Modifier.fillMaxWidth())
            Button(onClick = viewModel::generate, enabled = !generating, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(if (generating) R.string.summary_generating else R.string.summary_generate))
            }
        }
    }
}
