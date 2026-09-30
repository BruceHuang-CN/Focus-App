package com.example.focus_app.ui.settings

import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.focus_app.R
import com.example.focus_app.domain.feedback.DiagnosticLevel
import com.example.focus_app.domain.feedback.FeedbackContextLabels
import com.example.focus_app.domain.feedback.IssueReportTexts
import com.example.focus_app.domain.feedback.PreparedSubmission
import kotlinx.coroutines.launch

/** 连点或重组在极短时间内重复触发分享时，只启动一次系统面板。 */
private const val SHARE_DEBOUNCE_MILLIS = 800L

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReportIssueScreen(
    onBack: () -> Unit,
    viewModel: ReportIssueViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val texts = remember(context) { issueReportTexts(context) }
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    var diagnosticsDialogVisible by rememberSaveable { mutableStateOf(false) }
    var lastShareAtMillis by remember { mutableStateOf(0L) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(context.getString(R.string.feedback_report_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = context.getString(R.string.setup_text_164)
                        )
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .imePadding()
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            val receipt = state.receipt
            if (receipt != null) {
                SuccessCard(
                    receipt = receipt,
                    onCopyReceipt = {
                        val copied = IssueReportShare.copy(
                            context = context,
                            body = receipt,
                            label = context.getString(R.string.feedback_report_clip_label)
                        )
                        scope.launch {
                            snackbarHostState.showSnackbar(
                                context.getString(
                                    if (copied) {
                                        R.string.feedback_report_copy_receipt_success
                                    } else {
                                        R.string.feedback_report_copy_receipt_failed
                                    }
                                )
                            )
                        }
                    },
                    onStartNew = viewModel::startNewReport
                )
            } else {
                if (state.restoredPending) {
                    RestoredPendingCard(
                        onRetry = { viewModel.submit(texts) },
                        onStartNew = viewModel::startNewReport,
                        retryEnabled = state.canSubmit
                    )
                }

                IssueReportGuidance()

                IssueInputField(
                    value = state.problem,
                    onValueChange = viewModel::updateProblem,
                    label = context.getString(R.string.feedback_report_problem_label),
                    placeholder = context.getString(R.string.feedback_report_problem_placeholder),
                    counterText = counterText(context, state.problem.length),
                    errorText = when {
                        state.problemTooLong -> context.getString(
                            R.string.feedback_report_too_long,
                            ISSUE_REPORT_MAX_FIELD_LENGTH
                        )

                        state.showProblemError && state.problem.isBlank() ->
                            context.getString(R.string.feedback_report_required)

                        else -> null
                    },
                    minLines = 3
                )

                TextButton(onClick = { viewModel.setStepsExpanded(!state.stepsExpanded) }) {
                    Text(
                        context.getString(
                            if (state.stepsExpanded) {
                                R.string.feedback_report_steps_section_toggle_expanded
                            } else {
                                R.string.feedback_report_steps_section_toggle
                            }
                        )
                    )
                }

                if (state.stepsExpanded) {
                    IssueInputField(
                        value = state.steps,
                        onValueChange = viewModel::updateSteps,
                        label = context.getString(R.string.feedback_report_steps_label),
                        placeholder = context.getString(R.string.feedback_report_steps_placeholder),
                        counterText = counterText(context, state.steps.length),
                        errorText = if (state.stepsTooLong) {
                            context.getString(R.string.feedback_report_too_long, ISSUE_REPORT_MAX_FIELD_LENGTH)
                        } else {
                            null
                        },
                        minLines = 2
                    )
                    IssueInputField(
                        value = state.expected,
                        onValueChange = viewModel::updateExpected,
                        label = context.getString(R.string.feedback_report_expected_label),
                        placeholder = context.getString(R.string.feedback_report_expected_placeholder),
                        counterText = counterText(context, state.expected.length),
                        errorText = if (state.expectedTooLong) {
                            context.getString(R.string.feedback_report_too_long, ISSUE_REPORT_MAX_FIELD_LENGTH)
                        } else {
                            null
                        },
                        minLines = 2
                    )
                }

                if (state.showCombinedLengthError) {
                    Text(context.getString(R.string.feedback_report_combined_too_long), color = MaterialTheme.colorScheme.error)
                }

                DiagnosticsOptions(
                    level = state.diagnosticLevel,
                    isCollecting = state.isCollecting,
                    onLevelChange = viewModel::setDiagnosticLevel,
                    onShowDiagnostics = {
                        diagnosticsDialogVisible = true
                        viewModel.showDiagnostics(texts)
                    }
                )

                if (state.diagnosticsFailed) {
                    Text(
                        text = context.getString(R.string.feedback_report_diagnostics_failed),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }

                Text(
                    text = context.getString(R.string.feedback_report_privacy_note),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Button(
                    onClick = { viewModel.submit(texts) },
                    enabled = state.canSubmit,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    if (state.isSubmitting) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.onPrimary
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(context.getString(R.string.feedback_report_submitting))
                    } else {
                        Text(context.getString(R.string.feedback_report_submit_action))
                    }
                }

                state.submitError?.let { error ->
                    Text(
                        text = submitErrorText(context, error),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }

                OutlinedButton(
                    onClick = { viewModel.previewSubmission(texts) },
                    enabled = state.canSubmit && state.prepared == null,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(context.getString(R.string.feedback_report_preview_action))
                }

                val prepared = state.prepared
                if (prepared != null) {
                    PreviewCard(
                        report = prepared,
                        onShare = {
                            val now = System.currentTimeMillis()
                            if (now - lastShareAtMillis >= SHARE_DEBOUNCE_MILLIS) {
                                lastShareAtMillis = now
                                val started = IssueReportShare.share(
                                    context = context,
                                    body = prepared.displayBody,
                                    subject = context.getString(R.string.feedback_report_share_subject)
                                )
                                if (!started) {
                                    scope.launch {
                                        snackbarHostState.showSnackbar(
                                            context.getString(R.string.feedback_report_share_failed)
                                        )
                                    }
                                }
                            }
                        },
                        onCopy = {
                            val copied = IssueReportShare.copy(
                                context = context,
                                body = prepared.displayBody,
                                label = context.getString(R.string.feedback_report_clip_label)
                            )
                            scope.launch {
                                snackbarHostState.showSnackbar(
                                    context.getString(
                                        if (copied) {
                                            R.string.feedback_report_copy_success
                                        } else {
                                            R.string.feedback_report_copy_failed
                                        }
                                    )
                                )
                            }
                        }
                    )
                } else {
                    Text(
                        text = context.getString(R.string.feedback_report_other_ways_hint),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }

    val diagnosticsText = state.diagnosticsText
    if (diagnosticsDialogVisible) {
        AlertDialog(
            onDismissRequest = { diagnosticsDialogVisible = false },
            title = { Text(context.getString(R.string.feedback_report_diagnostics_title)) },
            text = {
                Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                    when {
                        state.isCollecting -> Text(
                            context.getString(R.string.feedback_report_loading_diagnostics)
                        )

                        diagnosticsText != null -> Text(
                            text = diagnosticsText,
                            style = MaterialTheme.typography.bodySmall.copy(
                                fontFamily = FontFamily.Monospace
                            )
                        )

                        else -> Text(context.getString(R.string.feedback_report_diagnostics_failed))
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { diagnosticsDialogVisible = false }) {
                    Text(context.getString(R.string.feedback_report_diagnostics_close))
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { viewModel.refreshDiagnostics(texts) },
                    enabled = !state.isCollecting
                ) {
                    Text(context.getString(R.string.feedback_report_refresh_diagnostics))
                }
            }
        )
    }
}

@Composable
private fun SuccessCard(
    receipt: String,
    onCopyReceipt: () -> Unit,
    onStartNew: () -> Unit
) {
    val context = LocalContext.current
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = context.getString(R.string.feedback_report_success_title),
                style = MaterialTheme.typography.titleLarge
            )
            Text(
                text = context.getString(R.string.feedback_report_success_message),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(text = receipt, style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(onClick = onCopyReceipt, modifier = Modifier.weight(1f)) {
                    Text(context.getString(R.string.feedback_report_copy_receipt))
                }
                Button(onClick = onStartNew, modifier = Modifier.weight(1f)) {
                    Text(context.getString(R.string.feedback_report_start_new))
                }
            }
        }
    }
}

@Composable
private fun RestoredPendingCard(
    onRetry: () -> Unit,
    onStartNew: () -> Unit,
    retryEnabled: Boolean
) {
    val context = LocalContext.current
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = context.getString(R.string.feedback_report_restored_title),
                style = MaterialTheme.typography.titleSmall
            )
            Text(
                text = context.getString(R.string.feedback_report_restored_message),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(onClick = onRetry, enabled = retryEnabled, modifier = Modifier.weight(1f)) {
                    Text(context.getString(R.string.feedback_report_retry_action))
                }
                OutlinedButton(onClick = onStartNew, modifier = Modifier.weight(1f)) {
                    Text(context.getString(R.string.feedback_report_start_new))
                }
            }
        }
    }
}

@Composable
private fun IssueInputField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    placeholder: String,
    counterText: String,
    errorText: String?,
    minLines: Int
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        placeholder = { Text(placeholder) },
        isError = errorText != null,
        minLines = minLines,
        modifier = Modifier.fillMaxWidth(),
        supportingText = {
            Column {
                Text(
                    text = counterText,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (errorText != null) {
                    Text(
                        text = errorText,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        }
    )
}

@Composable
private fun DiagnosticsOptions(
    level: DiagnosticLevel,
    isCollecting: Boolean,
    onLevelChange: (DiagnosticLevel) -> Unit,
    onShowDiagnostics: () -> Unit
) {
    val context = LocalContext.current
    var advancedExpanded by rememberSaveable { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        LevelOptionRow(
            checked = level != DiagnosticLevel.NONE,
            onCheckedChange = { checked ->
                onLevelChange(if (checked) DiagnosticLevel.BASIC else DiagnosticLevel.NONE)
            },
            title = context.getString(R.string.feedback_report_diagnostics_toggle),
            hint = context.getString(R.string.feedback_report_diagnostics_toggle_hint)
        )
        if (level != DiagnosticLevel.NONE) {
            // 已选择详细诊断（包括恢复的草稿）时始终显示，避免隐藏上传范围。
            if (level != DiagnosticLevel.DETAILED) {
                TextButton(onClick = { advancedExpanded = !advancedExpanded }) {
                    Text(context.getString(
                        if (advancedExpanded) R.string.feedback_report_diagnostics_less_options
                        else R.string.feedback_report_diagnostics_more_options
                    ))
                }
            }
            if (advancedExpanded || level == DiagnosticLevel.DETAILED) {
                LevelOptionRow(
                    checked = level == DiagnosticLevel.DETAILED,
                    onCheckedChange = { checked ->
                        onLevelChange(if (checked) DiagnosticLevel.DETAILED else DiagnosticLevel.BASIC)
                    },
                    title = context.getString(R.string.feedback_report_diagnostics_detailed_toggle),
                    hint = context.getString(R.string.feedback_report_diagnostics_detailed_hint)
                )
            }
        }
        OutlinedButton(
            onClick = onShowDiagnostics,
            enabled = level != DiagnosticLevel.NONE && !isCollecting,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(context.getString(R.string.feedback_report_show_diagnostics))
        }
    }
}

@Composable
private fun LevelOptionRow(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    title: String,
    hint: String,
    enabled: Boolean = true
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Checkbox(
            checked = checked,
            onCheckedChange = onCheckedChange,
            enabled = enabled
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                color = if (enabled) {
                    MaterialTheme.colorScheme.onSurface
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                }
            )
            Text(
                text = hint,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun PreviewCard(
    report: PreparedSubmission,
    onShare: () -> Unit,
    onCopy: () -> Unit
) {
    val context = LocalContext.current
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = context.getString(R.string.feedback_report_preview_title),
                style = MaterialTheme.typography.titleMedium
            )
            Text(
                text = report.displayBody,
                style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace)
            )
            Text(
                text = context.getString(R.string.feedback_report_share_hint),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(onClick = onShare, modifier = Modifier.weight(1f)) {
                    Text(context.getString(R.string.feedback_report_share_action))
                }
                OutlinedButton(onClick = onCopy, modifier = Modifier.weight(1f)) {
                    Text(context.getString(R.string.feedback_report_copy_action))
                }
            }
        }
    }
}

private fun submitErrorText(context: Context, error: SubmitError): String = when (error) {
    SubmitError.Unavailable -> context.getString(R.string.feedback_report_error_unavailable)
    SubmitError.Conflict -> context.getString(R.string.feedback_report_error_conflict)
    is SubmitError.Rejected -> context.getString(R.string.feedback_report_error_rejected, error.code)
    is SubmitError.RateLimited -> context.getString(
        R.string.feedback_report_error_rate_limited,
        error.retryAfterSeconds
    )
}

private fun counterText(context: Context, length: Int): String =
    context.getString(R.string.feedback_report_counter, length, ISSUE_REPORT_MAX_FIELD_LENGTH)

/** 用当前 App 语言构造正文文案，保证正文语言与界面一致。 */
internal fun issueReportTexts(context: Context): IssueReportTexts = IssueReportTexts(
    title = context.getString(R.string.feedback_report_text_title),
    sectionProblem = context.getString(R.string.feedback_report_text_problem),
    sectionSteps = context.getString(R.string.feedback_report_text_steps),
    sectionExpected = context.getString(R.string.feedback_report_text_expected),
    sectionDiagnostics = context.getString(R.string.feedback_report_text_diagnostics),
    labelSuffix = context.getString(R.string.feedback_report_label_suffix),
    emptyValue = context.getString(R.string.feedback_report_text_empty),
    noRecord = context.getString(R.string.feedback_report_text_no_record),
    unavailable = context.getString(R.string.feedback_report_text_unavailable),
    yes = context.getString(R.string.feedback_report_text_yes),
    no = context.getString(R.string.feedback_report_text_no),
    manufacturer = context.getString(R.string.feedback_report_text_manufacturer),
    model = context.getString(R.string.feedback_report_text_model),
    androidVersion = context.getString(R.string.feedback_report_text_android_version),
    apiLevel = context.getString(R.string.feedback_report_text_api_level),
    versionName = context.getString(R.string.feedback_report_text_version_name),
    versionCode = context.getString(R.string.feedback_report_text_version_code),
    systemLanguage = context.getString(R.string.feedback_report_text_system_language),
    collectedAt = context.getString(R.string.feedback_report_text_collected_at),
    serviceBound = context.getString(R.string.feedback_report_text_service_bound),
    lastConnectedAt = context.getString(R.string.feedback_report_text_last_connected),
    lastDestroyedAt = context.getString(R.string.feedback_report_text_last_destroyed),
    lastInterruptedAt = context.getString(R.string.feedback_report_text_last_interrupted),
    diagnosticsNote = context.getString(R.string.feedback_report_text_diagnostics_note),
    settingsHeader = context.getString(R.string.feedback_report_text_settings_header),
    guardianEnabled = context.getString(R.string.feedback_report_text_guardian_enabled),
    detectionMode = context.getString(R.string.feedback_report_text_detection_mode),
    detectionRealtime = context.getString(R.string.feedback_report_text_detection_realtime),
    detectionCompatibility = context.getString(R.string.feedback_report_text_detection_compatibility),
    reminderDelaySeconds = context.getString(R.string.feedback_report_text_reminder_delay),
    reminderWindowMinutes = context.getString(R.string.feedback_report_text_reminder_window),
    maxRemindersPerWindow = context.getString(R.string.feedback_report_text_max_reminders),
    contextLabels = FeedbackContextLabels(
        header = context.getString(R.string.feedback_context_header),
        note = context.getString(R.string.feedback_context_note),
        accessibilityEnabled = context.getString(R.string.feedback_context_accessibility),
        notificationsEnabled = context.getString(R.string.feedback_context_notifications),
        overlayAllowed = context.getString(R.string.feedback_context_overlay),
        usageAccessAllowed = context.getString(R.string.feedback_context_usage),
        batteryOptimizationsIgnored = context.getString(R.string.feedback_context_battery),
        targetAppCount = context.getString(R.string.feedback_context_app_count),
        remindersInWindow = context.getString(R.string.feedback_context_reminder_count),
        screenWidthDp = context.getString(R.string.feedback_context_width),
        screenHeightDp = context.getString(R.string.feedback_context_height),
        fontScale = context.getString(R.string.feedback_context_font_scale),
        systemDarkTheme = context.getString(R.string.feedback_context_dark_theme)
    )
)
