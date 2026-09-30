package com.example.focus_app.ui.settings

import androidx.compose.material.icons.filled.Refresh

import com.example.focus_app.R

import android.Manifest
import android.content.Intent
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.selection.selectable
import androidx.compose.ui.semantics.Role
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.focus_app.domain.model.*
import com.example.focus_app.domain.permission.*
import com.example.focus_app.util.PermissionHelper
import com.example.focus_app.util.loadInstalledAppName
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(onExitRequested: () -> Unit, hasUnsavedChanges: Boolean,
    onUnsavedChangesChanged: (Boolean) -> Unit, navigateToCustomReturnPicker: () -> Unit,
    navigateToAppGroups: () -> Unit, navigateToFeedbackAndSupport: () -> Unit,
    onCheckUpdates: () -> Unit, updateChecking: Boolean,
    navigateToTutorial: () -> Unit = {},
    viewModel: SettingsViewModel = hiltViewModel()) {
    val setupContext = androidx.compose.ui.platform.LocalContext.current
    val context = LocalContext.current
    val owner = LocalLifecycleOwner.current
    var page by rememberSaveable { mutableStateOf("main") }
    var editor by rememberSaveable { mutableStateOf<String?>(null) }
    var battery by remember { mutableStateOf(PermissionHelper.isIgnoringBatteryOptimizations(context)) }
    val snackbar = remember { SnackbarHostState() }
    val listState = rememberLazyListState()
    val notifications = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { viewModel.refreshPermissions() }
    LaunchedEffect(viewModel) {
        viewModel.notificationPermissionRequests.collect {
            if (PermissionHelper.needsNotificationPermission(context)) notifications.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }
    LaunchedEffect(viewModel, snackbar, setupContext) { viewModel.settingsMessages.collect { snackbar.showSnackbar(it.resolve(setupContext)) } }
    // Old global dirty flag belonged to the removed "save all" button. Editors now own drafts.
    LaunchedEffect(page) { if (page == "main") onUnsavedChangesChanged(false) }
    DisposableEffect(owner, viewModel) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                battery = PermissionHelper.isIgnoringBatteryOptimizations(context)
                viewModel.refreshPermissions()
            }
        }
        owner.lifecycle.addObserver(observer)
        onDispose { owner.lifecycle.removeObserver(observer) }
    }
    fun permissionAction(action: PermissionCheckAction) {
        when (action) {
            PermissionCheckAction.OPEN_ACCESSIBILITY -> context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
            PermissionCheckAction.ENABLE_ACCESSIBILITY -> {
                viewModel.setAccessibilityEnabled(true)
                context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
            }
            PermissionCheckAction.OPEN_USAGE_STATS -> PermissionHelper.openUsageStatsSettings(context)
            PermissionCheckAction.OPEN_OVERLAY -> PermissionHelper.openOverlaySettings(context)
            PermissionCheckAction.REQUEST_NOTIFICATION -> PermissionHelper.openNotificationSettings(context)
            PermissionCheckAction.OPEN_TARGET_APPS -> navigateToAppGroups()
            PermissionCheckAction.NONE -> Unit
        }
    }
    BackHandler { if (page != "main") page = "main" else onExitRequested() }
    Scaffold(containerColor = MaterialTheme.colorScheme.background, snackbarHost = { SnackbarHost(snackbar) }) { padding ->
        if (page == "ai") {
            AiSettingsPage(viewModel, Modifier.padding(padding), onBack = { page = "main" }, onDirtyChanged = onUnsavedChangesChanged)
        } else {
            LazyColumn(Modifier.fillMaxSize().padding(padding), state = listState, contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)) {
                item(key = "header", contentType = "header") {
                    Column {
                        IconButton(onClick = onExitRequested) { Icon(Icons.AutoMirrored.Filled.ArrowBack, setupContext.getString(R.string.setup_text_164)) }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(setupContext.getString(R.string.setup_text_190), Modifier.weight(1f), fontSize = 40.sp, fontWeight = FontWeight.ExtraBold)
                            SettingsSaveBadge(viewModel)
                        }
                        Text(setupContext.getString(R.string.setup_text_191), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                item(key = "guardian", contentType = "section") { GuardianSettings(viewModel) }
                item(key = "permissions", contentType = "section") {
                    PermissionSettings(viewModel, battery, onDetails = { editor = "permissions" },
                        onPermission = ::permissionAction,
                        onBattery = { PermissionHelper.requestIgnoreBatteryOptimizations(context) })
                }
                item(key = "theme", contentType = "section") { ThemeSettingsSection(viewModel) }
                item(key = "preferences", contentType = "section") { ReminderPreferenceSettings(viewModel) { editor = it } }
                item(key = "daily_summary", contentType = "section") {
                    com.example.focus_app.ui.summary.DailySummarySettingsCard()
                }
                item(key = "apps", contentType = "section") { AppSettingsSection(viewModel, navigateToAppGroups, navigateToCustomReturnPicker) }
                item(key = "ai", contentType = "section") { AiSettingsSummary(viewModel, { page = "ai" }, { editor = "manage" }) }
                item(key = "tutorial", contentType = "section") {
                    ForestSettingsSection(setupContext.getString(R.string.setup_text_211), icon = Icons.Default.Info) {
                        SettingsEntry(Icons.Default.Info, setupContext.getString(R.string.setup_text_212), onClick = navigateToTutorial)
                    }
                }
                item(key = "support", contentType = "section") {
                    ForestSettingsSection(setupContext.getString(R.string.setup_text_173), icon = Icons.Default.Info) {
                        SettingsEntry(Icons.Default.Email, setupContext.getString(R.string.setup_text_213), onClick = navigateToFeedbackAndSupport)
                        SettingsEntry(Icons.Default.Refresh,
                            setupContext.getString(if (updateChecking) R.string.update_checking else R.string.update_check),
                            onClick = { if (!updateChecking) onCheckUpdates() })
                    }
                }
                item(key = "bottom") { Spacer(Modifier.height(16.dp)) }
            }
        }
    }
    editor?.let { selected ->
        ModalBottomSheet(onDismissRequest = { editor = null }) {
            Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).imePadding()
                .padding(start = 20.dp, end = 20.dp, bottom = 28.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                when (selected) {
                    "permissions" -> {
                        val settings by viewModel.settings.collectAsStateWithLifecycle()
                        val items by viewModel.permissionStatus.collectAsStateWithLifecycle()
                        Text(setupContext.getString(R.string.setup_text_214), style = MaterialTheme.typography.headlineSmall)
                        Text(setupContext.getString(R.string.setup_text_215), color = MaterialTheme.colorScheme.onSurfaceVariant)
                        PermissionCheckCard(settings.detectionMode, items, ::permissionAction)
                        SettingsToggle(Icons.Default.CheckCircle, setupContext.getString(R.string.setup_text_216), settings.enableAccessibility,
                            setupContext.getString(R.string.setup_text_217), viewModel::setAccessibilityEnabled)
                        val keepAlive by viewModel.keepAliveEnabled.collectAsStateWithLifecycle()
                        SettingsToggle(Icons.Default.Lock, setupContext.getString(R.string.setup_text_218), keepAlive, setupContext.getString(R.string.setup_text_219), viewModel::setKeepAliveEnabled)
                        BackgroundProtectionCard(battery) { PermissionHelper.requestIgnoreBatteryOptimizations(context) }
                    }
                    "manage" -> {
                        val regenerating by viewModel.regenerating.collectAsStateWithLifecycle()
                        Text(setupContext.getString(R.string.setup_text_220), style = MaterialTheme.typography.headlineSmall)
                        Text(setupContext.getString(R.string.setup_text_221))
                        Button(onClick = viewModel::resetReminderQuota, modifier = Modifier.fillMaxWidth()) { Text(setupContext.getString(R.string.setup_text_222)) }
                        OutlinedButton(onClick = viewModel::regenerateMessages, enabled = !regenerating, modifier = Modifier.fillMaxWidth()) {
                            Text(if (regenerating) setupContext.getString(R.string.setup_text_223) else setupContext.getString(R.string.setup_text_224))
                        }
                    }
                    else -> NumberSettingsEditor(selected, viewModel) { editor = null }
                }
            }
        }
    }
}

@Composable private fun SettingsSaveBadge(vm: SettingsViewModel) {
    val setupContext = androidx.compose.ui.platform.LocalContext.current
    val status by vm.saveStatus.collectAsStateWithLifecycle()
    Surface(shape = CircleShape, color = MaterialTheme.colorScheme.surface) {
        Row(Modifier.padding(horizontal = 14.dp, vertical = 9.dp), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Icon(if (status == R.string.setup_text_225) Icons.Default.Warning else Icons.Default.CheckCircle, null, Modifier.size(18.dp),
                tint = if (status == R.string.setup_text_225) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary)
            Text(setupContext.getString(status), style = MaterialTheme.typography.labelLarge)
        }
    }
}
@Composable private fun GuardianSettings(vm: SettingsViewModel) {
    val setupContext = androidx.compose.ui.platform.LocalContext.current
    val settings by vm.settings.collectAsStateWithLifecycle()
    ForestSettingsSection(setupContext.getString(R.string.setup_text_192), icon = Icons.Default.Notifications, subtitle = setupContext.getString(R.string.setup_text_193)) {
        SettingsToggle(Icons.Default.Lock, setupContext.getString(R.string.setup_text_194), settings.guardianEnabled, setupContext.getString(R.string.setup_text_226), vm::setGuardianEnabled)
        SettingsToggle(Icons.Default.Warning, setupContext.getString(R.string.setup_text_196), settings.forceReminder, setupContext.getString(R.string.setup_text_197), vm::setForceReminder)
        SettingsToggle(Icons.Default.Favorite, setupContext.getString(R.string.setup_text_198), settings.enableBreathingPause, setupContext.getString(R.string.setup_text_227), vm::setBreathingPause)
    }
}
@Composable private fun ReminderPreferenceSettings(vm: SettingsViewModel, onEdit: (String) -> Unit) {
    val setupContext = androidx.compose.ui.platform.LocalContext.current
    val settings by vm.settings.collectAsStateWithLifecycle()
    val random by vm.randomizeReminderActions.collectAsStateWithLifecycle()
    ForestSettingsSection(setupContext.getString(R.string.setup_text_199), icon = Icons.Default.Settings, subtitle = setupContext.getString(R.string.setup_text_228)) {
        SettingsEntry(Icons.Default.DateRange, setupContext.getString(R.string.setup_text_200), setupContext.getString(R.string.setup_text_229, settings.reminderDelaySeconds)) { onEdit("delay") }
        SettingsEntry(Icons.Default.Notifications, setupContext.getString(R.string.setup_text_202), setupContext.getString(R.string.setup_text_230, settings.reminderWindowMinutes)) { onEdit("window") }
        SettingsEntry(Icons.Default.List, setupContext.getString(R.string.setup_text_204), setupContext.getString(R.string.setup_text_231, settings.maxRemindersPerWindow)) { onEdit("quota") }
        SettingsToggle(Icons.Default.Refresh, setupContext.getString(R.string.setup_text_232), random, onCheckedChange = vm::setRandomizeReminderActions)
    }
}
@OptIn(ExperimentalLayoutApi::class)
@Composable private fun NumberSettingsEditor(kind: String, vm: SettingsViewModel, onDone: () -> Unit) {
    val setupContext = androidx.compose.ui.platform.LocalContext.current
    val settings by vm.settings.collectAsStateWithLifecycle()
    val initial = when (kind) { "delay" -> settings.reminderDelaySeconds; "window" -> settings.reminderWindowMinutes; else -> settings.maxRemindersPerWindow }
    var input by rememberSaveable(kind) { mutableStateOf(initial.toString()) }
    val range = when (kind) { "delay" -> 1..300; "window" -> 5..1440; else -> 1..20 }
    val presets = when (kind) { "delay" -> listOf(3,10,30); "window" -> listOf(30,60,120); else -> listOf(1,3,5) }
    val title = when (kind) { "delay" -> setupContext.getString(R.string.setup_text_200); "window" -> setupContext.getString(R.string.setup_text_202); else -> setupContext.getString(R.string.setup_text_204) }
    val unit = when (kind) { "delay" -> setupContext.getString(R.string.setup_text_233); "window" -> setupContext.getString(R.string.setup_text_234); else -> setupContext.getString(R.string.setup_text_235) }
    val value = input.toIntOrNull()
    val valid = value != null && value in range
    Text(title, style = MaterialTheme.typography.headlineSmall)
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        presets.forEach { preset -> FilterChip(selected = value == preset, onClick = { input = preset.toString() }, label = { Text("$preset $unit") }) }
    }
    OutlinedTextField(value = input, onValueChange = { input = it.filter(Char::isDigit).take(4) },
        label = { Text(setupContext.getString(R.string.setup_text_236)) }, suffix = { Text(unit) }, singleLine = true,
        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Number),
        supportingText = { Text(setupContext.getString(R.string.setup_text_237, range.first, range.last, unit)) }, isError = !valid, modifier = Modifier.fillMaxWidth())
    Button(onClick = {
        val confirmed = input.toIntOrNull()?.takeIf { it in range } ?: return@Button
        when (kind) { "delay" -> vm.updateReminderDelaySeconds(confirmed); "window" -> vm.updateReminderWindowMinutes(confirmed); else -> vm.updateMaxRemindersPerWindow(confirmed) }
        onDone()
    }, enabled = valid, modifier = Modifier.fillMaxWidth()) { Text(setupContext.getString(R.string.setup_text_238)) }

}

@Composable private fun ThemeSettingsSection(vm: SettingsViewModel) {
    val setupContext = androidx.compose.ui.platform.LocalContext.current
    val theme by vm.themeSettings.collectAsStateWithLifecycle()
    ForestSettingsSection(setupContext.getString(R.string.setup_text_239), icon = Icons.Default.Face, subtitle = setupContext.getString(R.string.setup_text_240)) {
        com.example.focus_app.ui.components.LanguageSelector()
        Row(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceContainerLow, CircleShape).padding(3.dp)) {
            AppThemeMode.entries.forEach { mode ->
                Surface(onClick = { vm.setThemeMode(mode) }, modifier = Modifier.weight(1f), shape = CircleShape,
                    color = if (theme.mode == mode) MaterialTheme.colorScheme.primary else Color.Transparent,
                    contentColor = if (theme.mode == mode) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant) {
                    Box(Modifier.heightIn(min = 48.dp).padding(6.dp), contentAlignment = Alignment.Center) { Text(themeModeLabel(mode), style = MaterialTheme.typography.labelLarge) }
                }
            }
        }
        HorizontalDivider(Modifier.padding(vertical = 8.dp), color = MaterialTheme.colorScheme.outlineVariant)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            AppThemeColor.entries.forEach { color -> ThemeColorSwatch(color, theme.color == color, { vm.setThemeColor(color) }, Modifier.weight(1f)) }
        }
    }
}
@Composable private fun PermissionSettings(vm: SettingsViewModel, battery: Boolean, onDetails: () -> Unit,
    onPermission: (PermissionCheckAction) -> Unit, onBattery: () -> Unit) {
    val setupContext = androidx.compose.ui.platform.LocalContext.current
    val items by vm.permissionStatus.collectAsStateWithLifecycle()
    val overlay = items.firstOrNull { it.id == "overlay" }?.status == PermissionCheckStatus.OK
    val accessibility = listOf("accessibility", "app_switch").all { id -> items.any { it.id == id && it.status == PermissionCheckStatus.OK } }
    ForestSettingsSection(setupContext.getString(R.string.setup_text_182), icon = Icons.Default.CheckCircle, subtitle = setupContext.getString(R.string.setup_text_241)) {
        val fontScale = LocalDensity.current.fontScale
        BoxWithConstraints {
            val compact = maxWidth >= 300.dp && fontScale <= 1.15f
            if (compact) Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                PermissionTile(setupContext.getString(R.string.setup_text_073), overlay, Modifier.weight(1f)) { onPermission(PermissionCheckAction.OPEN_OVERLAY) }
                PermissionTile(setupContext.getString(R.string.setup_text_242), accessibility, Modifier.weight(1f)) { onPermission(PermissionCheckAction.ENABLE_ACCESSIBILITY) }
                PermissionTile(setupContext.getString(R.string.setup_text_077), battery, Modifier.weight(1f), onBattery)
            } else Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                PermissionTile(setupContext.getString(R.string.setup_text_073), overlay, Modifier.fillMaxWidth()) { onPermission(PermissionCheckAction.OPEN_OVERLAY) }
                PermissionTile(setupContext.getString(R.string.setup_text_242), accessibility, Modifier.fillMaxWidth()) { onPermission(PermissionCheckAction.ENABLE_ACCESSIBILITY) }
                PermissionTile(setupContext.getString(R.string.setup_text_077), battery, Modifier.fillMaxWidth(), onBattery)
            }
        }
        SettingsEntry(Icons.Default.Settings, setupContext.getString(R.string.setup_text_243),
            subtitle = if (items.isEmpty()) setupContext.getString(R.string.setup_text_244) else setupContext.getString(R.string.setup_text_245, items.count { it.status == PermissionCheckStatus.MISSING }), onClick = onDetails)
    }
}
@Composable private fun PermissionTile(title: String, granted: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val setupContext = androidx.compose.ui.platform.LocalContext.current
    Surface(onClick = onClick, modifier = modifier, shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.surfaceContainerLow) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(title, style = MaterialTheme.typography.labelLarge)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                Box(Modifier.size(7.dp).background(if (granted) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error, CircleShape))
                Text(if (granted) setupContext.getString(R.string.setup_text_083) else setupContext.getString(R.string.setup_text_246), style = MaterialTheme.typography.labelMedium)
            }
        }
    }
}
@Composable private fun AppSettingsSection(vm: SettingsViewModel, onApps: () -> Unit, onReturn: () -> Unit) {
    val setupContext = androidx.compose.ui.platform.LocalContext.current
    val settings by vm.settings.collectAsStateWithLifecycle()
    val groups by vm.appGroups.collectAsStateWithLifecycle()
    val activeId by vm.activeAppGroupId.collectAsStateWithLifecycle()
    val returnPackage by vm.customReturnPackage.collectAsStateWithLifecycle()
    val context = LocalContext.current.applicationContext
    val returnName by produceState<String?>(null, returnPackage) {
        value = null
        value = withContext(Dispatchers.IO) { loadInstalledAppName(context, returnPackage) }
    }
    ForestSettingsSection(setupContext.getString(R.string.setup_text_247), icon = Icons.Default.Phone) {
        SettingsEntry(Icons.Default.Phone, setupContext.getString(R.string.setup_text_248), setupContext.getString(R.string.setup_text_249, settings.targetApps.size), onClick = onApps)
        SettingsEntry(Icons.Default.List, setupContext.getString(R.string.setup_text_250), subtitle = groups.firstOrNull { it.id == activeId }?.name ?: setupContext.getString(R.string.setup_text_251), onClick = onApps)
        SettingsEntry(Icons.Default.Home, setupContext.getString(R.string.setup_text_252), subtitle = returnName ?: returnPackage.ifBlank { setupContext.getString(R.string.setup_text_253) }, onClick = onReturn)
    }
}
@Composable private fun AiSettingsSummary(vm: SettingsViewModel, onConfigure: () -> Unit, onManage: () -> Unit) {
    val setupContext = androidx.compose.ui.platform.LocalContext.current
    val settings by vm.settings.collectAsStateWithLifecycle()
    val connection by vm.aiConnection.collectAsStateWithLifecycle()
    val status = when (connection) { is AiConnectionUiState.Success -> setupContext.getString(R.string.setup_text_254); is AiConnectionUiState.Error -> setupContext.getString(R.string.setup_text_255); AiConnectionUiState.Loading -> setupContext.getString(R.string.setup_text_256); else -> setupContext.getString(R.string.setup_text_207) }
    ForestSettingsSection(setupContext.getString(R.string.setup_text_002), icon = Icons.Default.Star, subtitle = setupContext.getString(R.string.setup_text_257)) {
        SettingsEntry(Icons.Default.Settings, setupContext.getString(R.string.setup_text_206), status, providerLabel(settings.aiProvider), onClick = onConfigure)
        SettingsEntry(Icons.Default.Face, setupContext.getString(R.string.setup_text_208), toneLabel(settings.toneKey), onClick = onConfigure)
        SettingsEntry(Icons.Default.Refresh, setupContext.getString(R.string.setup_text_210), onClick = onManage)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable private fun AiSettingsPage(viewModel: SettingsViewModel, modifier: Modifier, onBack: () -> Unit, onDirtyChanged: (Boolean) -> Unit) {
    val setupContext = androidx.compose.ui.platform.LocalContext.current
    val s by viewModel.settings.collectAsStateWithLifecycle()
    val connectionState by viewModel.aiConnection.collectAsStateWithLifecycle()
    val tonePreviewTask by viewModel.tonePreviewTask.collectAsStateWithLifecycle()
    var apiKeyInput by remember { mutableStateOf("") }
    var endpointDraft by rememberSaveable(s.aiProvider, s.apiEndpoint) { mutableStateOf(s.apiEndpoint) }
    var modelDraft by rememberSaveable(s.aiProvider, s.aiModel) { mutableStateOf(s.aiModel) }
    var customToneDraft by rememberSaveable(s.customToneInstruction) { mutableStateOf(s.customToneInstruction) }
    var discard by remember { mutableStateOf(false) }
    val dirty = endpointDraft != s.apiEndpoint || modelDraft != s.aiModel || apiKeyInput.isNotBlank() || customToneDraft != s.customToneInstruction
    LaunchedEffect(dirty) { onDirtyChanged(dirty) }
    DisposableEffect(Unit) { onDispose { onDirtyChanged(false) } }
    fun requestBack() { if (dirty) discard = true else onBack() }

    BackHandler { requestBack() }
    Column(modifier.fillMaxSize().imePadding().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = ::requestBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, setupContext.getString(R.string.setup_text_258)) }
            Text(setupContext.getString(R.string.setup_text_259), Modifier.weight(1f), style = MaterialTheme.typography.headlineSmall)
            if (dirty) Text(setupContext.getString(R.string.setup_text_260), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) else SettingsSaveBadge(viewModel)
        }
        ForestSettingsSection(setupContext.getString(R.string.setup_text_261), icon = Icons.Default.Star) {
            AiProvider.entries.forEach { p ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(
                        selected = s.aiProvider == p,
                        onClick = {
                            viewModel.updateAiProvider(p)

                        }
                    )
                    Text(providerLabel(p))
                }
            }
            if (s.aiProvider == AiProvider.DEEPSEEK) com.example.focus_app.ui.components.DeepSeekSetupGuide()
            OutlinedTextField(
                value = endpointDraft,
                onValueChange = { endpointDraft = it },
                label = { Text(setupContext.getString(R.string.setup_text_262)) },
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = modelDraft,
                onValueChange = { modelDraft = it },
                label = { Text(setupContext.getString(R.string.setup_text_263)) },
                modifier = Modifier.fillMaxWidth()
            )
            Button(
                onClick = {
                    viewModel.updateAiConnection(endpointDraft, modelDraft)

                },
                enabled = endpointDraft.isNotBlank() && modelDraft.isNotBlank()
            ) { Text(setupContext.getString(R.string.setup_text_265)) }
            OutlinedTextField(
                value = apiKeyInput,
                onValueChange = { apiKeyInput = it },
                label = { Text(setupContext.getString(R.string.setup_text_266)) },
                visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation(),
                modifier = Modifier.fillMaxWidth()
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = {
                        viewModel.updateApiKey(apiKeyInput) { apiKeyInput = "" }

                    },
                    enabled = apiKeyInput.isNotBlank()
                ) { Text(setupContext.getString(R.string.setup_text_267)) }
                TextButton(
                    onClick = {
                        viewModel.clearApiKey()
                        apiKeyInput = ""

                    }
                ) { Text(setupContext.getString(R.string.setup_text_269)) }
            }
            OutlinedButton(
                onClick = { viewModel.testAiConnection() },
                enabled = connectionState !is AiConnectionUiState.Loading,
                modifier = Modifier.fillMaxWidth()
            ) {
                if (connectionState is AiConnectionUiState.Loading) {
                    CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(setupContext.getString(R.string.setup_text_270))
                } else {
                    Text(setupContext.getString(R.string.setup_text_271))
                }
            }
            when (val state = connectionState) {
                AiConnectionUiState.Idle, AiConnectionUiState.Loading -> Unit
                is AiConnectionUiState.Success -> Text(
                    setupContext.getString(R.string.setup_text_272, state.modelIds.take(3).joinToString(setupContext.getString(R.string.setup_list_separator))) +
                        if (state.modelIds.size > 3) setupContext.getString(R.string.setup_text_273, state.modelIds.size) else "",
                    color = MaterialTheme.colorScheme.primary,
                    style = MaterialTheme.typography.bodySmall
                )
                is AiConnectionUiState.Error -> Text(
                    state.localizedMessage?.resolve(setupContext) ?: state.message,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall
                )
            }


            // ── 口吻 ──

            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                ReminderTone.entries.forEach { tone ->
                    FilterChip(
                        selected = s.toneKey == tone,
                        onClick = {
                            viewModel.updateReminderTone(tone)

                        },
                        label = { Text(toneLabel(tone)) }
                    )
                }
            }
            if (s.toneKey == ReminderTone.CUSTOM) {
                OutlinedTextField(
                    value = customToneDraft,
                    onValueChange = { customToneDraft = it },
                    label = { Text(setupContext.getString(R.string.setup_text_275)) },
                    modifier = Modifier.fillMaxWidth()
                )
                Button(
                    onClick = {
                        viewModel.updateCustomToneInstruction(customToneDraft)

                    },
                    enabled = customToneDraft.isNotBlank()
                ) { Text(setupContext.getString(R.string.setup_text_277)) }
            }
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text(setupContext.getString(R.string.setup_text_278), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        com.example.focus_app.domain.reminder.ReminderTonePreview.sampleMessage(
                            setupContext, s.toneKey, s.customToneInstruction, tonePreviewTask),
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }



        }
    }
    if (discard) AlertDialog(onDismissRequest = { discard = false }, title = { Text(setupContext.getString(R.string.setup_text_279)) },
        text = { Text(setupContext.getString(R.string.setup_text_280)) },
        confirmButton = { TextButton(onClick = onBack) { Text(setupContext.getString(R.string.setup_text_281)) } },
        dismissButton = { TextButton(onClick = { discard = false }) { Text(setupContext.getString(R.string.setup_text_282)) } })
}

@Composable
internal fun themeModeLabel(mode: AppThemeMode): String = when (mode) {
    AppThemeMode.SYSTEM -> androidx.compose.ui.res.stringResource(R.string.setup_text_283)
    AppThemeMode.DAY -> androidx.compose.ui.res.stringResource(R.string.setup_text_284)
    AppThemeMode.NIGHT -> androidx.compose.ui.res.stringResource(R.string.setup_text_285)
}

@Composable
internal fun themeColorLabel(color: AppThemeColor): String = when (color) {
    AppThemeColor.MINT -> androidx.compose.ui.res.stringResource(R.string.setup_text_286)
    AppThemeColor.BLUE -> androidx.compose.ui.res.stringResource(R.string.setup_text_287)
    AppThemeColor.ORANGE -> androidx.compose.ui.res.stringResource(R.string.setup_text_288)
    AppThemeColor.GRAPHITE -> androidx.compose.ui.res.stringResource(R.string.setup_text_289)
}

internal val themeGradient: Map<AppThemeColor, List<Color>> = mapOf(
    AppThemeColor.MINT to listOf(Color(0xFF205C35), Color(0xFF42694D)),
    AppThemeColor.BLUE to listOf(Color(0xFF16304F), Color(0xFF2E5EAA)),
    AppThemeColor.ORANGE to listOf(Color(0xFFC85A12), Color(0xFFF5A623)),
    AppThemeColor.GRAPHITE to listOf(Color(0xFF1F242B), Color(0xFF2F80ED))
)

@Composable
internal fun ThemeColorSwatch(
    color: AppThemeColor,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier.selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
    ) {
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(CircleShape)
                .background(Brush.linearGradient(themeGradient[color] ?: emptyList()))
                .border(
                    width = if (selected) 3.dp else 1.dp,
                    color = if (selected) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    shape = CircleShape
                )
        )
        Spacer(modifier = Modifier.height(6.dp))
        Text(themeColorLabel(color), style = MaterialTheme.typography.labelSmall)
    }
}

@Composable
internal fun BackgroundProtectionCard(
    batteryOptimizationIgnored: Boolean,
    onOpenSystemSettings: () -> Unit
) {
    val setupContext = androidx.compose.ui.platform.LocalContext.current
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(setupContext.getString(R.string.setup_text_077), style = MaterialTheme.typography.titleMedium)
            Text(
                if (batteryOptimizationIgnored) {
                    setupContext.getString(R.string.setup_text_290)
                } else {
                    setupContext.getString(R.string.setup_text_291)
                },
                style = MaterialTheme.typography.bodyMedium
            )
            if (!batteryOptimizationIgnored) {
                TextButton(onClick = onOpenSystemSettings) { Text(setupContext.getString(R.string.setup_text_292)) }
            }
            Text(
                setupContext.getString(R.string.setup_text_293),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                PermissionHelper.backgroundProtectionHint(setupContext),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
internal fun toneLabel(tone: ReminderTone): String = when (tone) {
    ReminderTone.GENTLE -> androidx.compose.ui.res.stringResource(R.string.setup_text_209)
    ReminderTone.DIRECT -> androidx.compose.ui.res.stringResource(R.string.setup_text_294)
    ReminderTone.SARCASTIC -> androidx.compose.ui.res.stringResource(R.string.setup_text_295)
    ReminderTone.CUSTOM -> androidx.compose.ui.res.stringResource(R.string.setup_text_296)
}

@Composable
internal fun providerLabel(provider: AiProvider): String = when (provider) {
    AiProvider.DEEPSEEK -> "DeepSeek"
    AiProvider.OPENAI -> "OpenAI"
    AiProvider.QWEN -> androidx.compose.ui.res.stringResource(R.string.setup_provider_qwen)
    AiProvider.CUSTOM -> androidx.compose.ui.res.stringResource(R.string.setup_provider_custom)
}
