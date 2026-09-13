package com.example.focus_app.ui.onboarding

import com.example.focus_app.R

import android.util.LruCache
import android.Manifest
import android.content.Intent
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.focus_app.util.PermissionHelper
import com.example.focus_app.ui.settings.providerLabel
import com.example.focus_app.ui.components.DeepSeekSetupGuide
import com.example.focus_app.ui.components.LanguageSelector
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

internal data class TutorialActions(
    val go: (Int) -> Unit = {}, val nextWelcome: () -> Unit = {}, val defer: () -> Unit = {},
    val refresh: () -> Unit = {}, val aiDraftChanged: () -> Unit = {},
    val connect: (String, String, String, com.example.focus_app.domain.model.AiProvider, () -> Unit) -> Unit = { _, _, _, _, _ -> },
    val selectTask: (Long) -> Unit = {}, val saveTask: (String, String, String, Int) -> Unit = { _, _, _, _ -> },
    val toggleApp: (String) -> Unit = {}, val saveApps: () -> Unit = {},
    val generate: () -> Unit = {}, val enableDetection: () -> Unit = {}, val finish: (Boolean) -> Unit = {}
)

@Composable
fun TutorialScreen(onComplete: () -> Unit, viewModel: TutorialViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val actions = remember(viewModel) { TutorialActions(
        go = viewModel::go, nextWelcome = viewModel::nextWelcome, defer = viewModel::defer,
        refresh = viewModel::refresh, aiDraftChanged = viewModel::aiDraftChanged,
        connect = viewModel::connect, selectTask = viewModel::selectTask, saveTask = viewModel::saveTask,
        toggleApp = viewModel::toggleApp, saveApps = viewModel::saveApps, generate = viewModel::generate,
        enableDetection = viewModel::enableDetection, finish = viewModel::finish
    ) }
    TutorialContent(state, actions, onComplete)
}

@Composable
internal fun TutorialContent(state: TutorialUiState, actions: TutorialActions, onComplete: () -> Unit) {
    val setupContext = androidx.compose.ui.platform.LocalContext.current
    val context = LocalContext.current
    val owner = LocalLifecycleOwner.current
    var provider by rememberSaveable(state.provider) { mutableStateOf(state.provider) }
    var providerMenu by remember { mutableStateOf(false) }
    var endpoint by rememberSaveable(state.endpoint) { mutableStateOf(state.endpoint) }
    var model by rememberSaveable(state.model) { mutableStateOf(state.model) }
    // Credentials must never enter saved instance state.
    var key by remember { mutableStateOf("") }
    var title by rememberSaveable { mutableStateOf("") }
    var start by rememberSaveable { mutableStateOf("09:00") }
    var end by rememberSaveable { mutableStateOf("18:00") }
    var days by rememberSaveable { mutableIntStateOf(127) }
    var search by rememberSaveable { mutableStateOf("") }
    var startNow by rememberSaveable { mutableStateOf(false) }
    var localMessage by remember { mutableStateOf("") }
    var draftTaskId by rememberSaveable { mutableStateOf<Long?>(null) }
    val editable = !state.busy && !state.loading
    val listState = rememberLazyListState()
    val appNames by produceState<Map<String, String>>(emptyMap(), state.savedApps, state.apps) {
        value = withContext(Dispatchers.IO) {
            state.savedApps.map { it.packageName }.associateWith { pkg -> state.apps.firstOrNull { it.packageName == pkg }?.appName
                ?: com.example.focus_app.util.loadInstalledAppName(context, pkg) ?: setupContext.getString(R.string.setup_text_012) }
        }
    }
    val notifications = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { actions.refresh() }
    LaunchedEffect(state.task?.id) {
        state.task?.takeIf { it.id != draftTaskId }?.let { task ->
            draftTaskId = task.id
            title = task.title
            start = tutorialFormatTime(task.scheduleStartMinute ?: 540)
            end = tutorialFormatTime(task.scheduleEndMinute ?: 1080)
            days = task.repeatDaysMask.takeIf { it != 0 } ?: 127
        }
    }
    LaunchedEffect(state.exit) { if (state.exit) onComplete() }
    LaunchedEffect(state.step) { listState.scrollToItem(0); localMessage = "" }
    DisposableEffect(owner, actions.refresh) {
        val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_RESUME) actions.refresh() }
        owner.lifecycle.addObserver(observer)
        onDispose { owner.lifecycle.removeObserver(observer) }
    }
    fun leave() { key = ""; if (state.completed) onComplete() else actions.defer() }
    BackHandler { if (!state.busy) { if (state.step > 0) actions.go(state.step - 1) else leave() } }
    fun systemAction(index: Int) {
        try {
            when (index) {
                0 -> PermissionHelper.openOverlaySettings(context)
                1 -> context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                2 -> PermissionHelper.openUsageStatsSettings(context)
                3 -> if (PermissionHelper.needsNotificationPermission(context)) notifications.launch(Manifest.permission.POST_NOTIFICATIONS)
                     else PermissionHelper.openNotificationSettings(context)
                4 -> PermissionHelper.requestIgnoreBatteryOptimizations(context)
            }
        } catch (_: Exception) { localMessage = setupContext.getString(R.string.setup_text_013) }
    }
    val titles = listOf(setupContext.getString(R.string.setup_text_014), setupContext.getString(R.string.setup_text_015), setupContext.getString(R.string.setup_text_016), setupContext.getString(R.string.setup_text_017), setupContext.getString(R.string.setup_text_018), setupContext.getString(R.string.setup_text_019), setupContext.getString(R.string.setup_text_020))
    val subtitles = listOf(setupContext.getString(R.string.setup_text_021), setupContext.getString(R.string.setup_text_022), setupContext.getString(R.string.setup_text_023), setupContext.getString(R.string.setup_text_024), setupContext.getString(R.string.setup_text_025), setupContext.getString(R.string.setup_text_026), setupContext.getString(R.string.setup_text_027))
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            Surface(shadowElevation = 4.dp) {
                Column(Modifier.fillMaxWidth().navigationBarsPadding().imePadding().padding(horizontal = 20.dp, vertical = 12.dp)) {
                    val enabled = !state.busy && !state.loading && (state.completed || when(state.step) {
                        1 -> state.connected
                        2 -> state.task != null
                        3 -> state.appsLoaded && state.selected.isNotEmpty()
                        4 -> state.generated
                        5 -> state.permissions.size == 5 && state.permissions.take(4).all { it } && state.detectionEnabled
                        6 -> state.connected && state.generated && state.task != null && state.selected.isNotEmpty() && state.permissions.size == 5 && state.permissions.take(4).all { it } && state.detectionEnabled
                        else -> true
                    })
                    Button(onClick = {
                        if (state.completed && state.step < 6) actions.go(state.step + 1) else when(state.step) {
                            0 -> actions.nextWelcome()
                            1 -> { key = ""; actions.go(2) }
                            2 -> actions.go(3)
                            3 -> actions.saveApps()
                            4 -> actions.go(5)
                            5 -> actions.go(6)
                            6 -> if (state.completed) onComplete() else actions.finish(startNow)
                        }
                    }, enabled = enabled, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) {
                        Text(if (state.busy) setupContext.getString(R.string.setup_text_028) else when(state.step) {
                            0 -> setupContext.getString(R.string.setup_text_029)
                            3 -> if (state.completed) setupContext.getString(R.string.setup_text_030) else setupContext.getString(R.string.setup_text_031)
                            6 -> if (state.completed) setupContext.getString(R.string.setup_text_032) else if (startNow) setupContext.getString(R.string.setup_text_033) else setupContext.getString(R.string.setup_text_034)
                            else -> setupContext.getString(R.string.setup_text_030)
                        })
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        TextButton(onClick = { actions.go(state.step - 1) }, enabled = state.step > 0 && !state.busy) { Text(setupContext.getString(R.string.setup_text_035)) }
                        TextButton(onClick = ::leave, enabled = !state.busy) { Text(if (state.completed) setupContext.getString(R.string.setup_text_032) else setupContext.getString(R.string.setup_text_036)) }
                    }
                }
            }
        }
    ) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding), state = listState,
            contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            item("header") { TutorialHeader(titles[state.step], subtitles[state.step], state.step + 1, welcome = state.step == 0) }
            if (state.loading || state.busy) item("loading") { LinearProgressIndicator(Modifier.fillMaxWidth()) }
            if (state.message.isNotBlank() || localMessage.isNotBlank()) item("message") {
                Text(localMessage.ifBlank { state.message }, color = MaterialTheme.colorScheme.primary)
            }
            when (state.step) {
                0 -> item {
                    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        LanguageSelector(enabled = !state.busy && key.isBlank())
                        if (key.isNotBlank()) Text(setupContext.getString(R.string.setup_key_language_hint),
                            style = MaterialTheme.typography.bodySmall)
                        TutorialWelcomeContent()
                    }
                }
                1 -> item {
                    TutorialCard {
                        Box {
                            OutlinedButton(onClick = { providerMenu = true }, enabled = editable) { Text(setupContext.getString(R.string.setup_text_037, providerLabel(provider))) }
                            DropdownMenu(expanded = providerMenu, onDismissRequest = { providerMenu = false }) {
                                com.example.focus_app.domain.model.AiProvider.entries.forEach { choice ->
                                    DropdownMenuItem(text = { Text(providerLabel(choice)) }, onClick = {
                                        provider = choice; providerMenu = false
                                        if (choice.defaultEndpoint.isNotBlank()) endpoint = choice.defaultEndpoint
                                        if (choice.defaultModel.isNotBlank()) model = choice.defaultModel
                                        actions.aiDraftChanged()
                                    })
                                }
                            }
                        }
                        if (provider == com.example.focus_app.domain.model.AiProvider.DEEPSEEK) DeepSeekSetupGuide()
                        OutlinedTextField(endpoint, { endpoint = it; actions.aiDraftChanged() }, label = { Text(setupContext.getString(R.string.setup_text_038)) }, singleLine = true, enabled = editable, modifier = Modifier.fillMaxWidth())
                        OutlinedTextField(key, { key = it; actions.aiDraftChanged() }, label = { Text(if (state.hasKey) setupContext.getString(R.string.setup_text_039) else "API Key") }, visualTransformation = PasswordVisualTransformation(), singleLine = true, enabled = editable, modifier = Modifier.fillMaxWidth())
                        OutlinedTextField(model, { model = it; actions.aiDraftChanged() }, label = { Text(setupContext.getString(R.string.setup_text_040)) }, singleLine = true, enabled = editable, modifier = Modifier.fillMaxWidth())
                        Text(setupContext.getString(R.string.setup_text_041), style = MaterialTheme.typography.bodySmall)
                        OutlinedButton(onClick = { actions.connect(endpoint, key, model, provider) { key = "" } }, enabled = editable && endpoint.isNotBlank() && model.isNotBlank() && (key.isNotBlank() || state.hasKey)) { Text(if (state.connected) setupContext.getString(R.string.setup_text_042) else setupContext.getString(R.string.setup_text_043)) }
                    }
                }
                2 -> {
                    if (state.tasks.isNotEmpty()) item { Text(setupContext.getString(R.string.setup_text_044), style = MaterialTheme.typography.titleMedium) }
                    items(state.tasks, key = { "task-${it.id}" }) { task ->
                        TutorialCard(Modifier.clickable(enabled = editable) { actions.selectTask(task.id) }) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                RadioButton(selected = state.task?.id == task.id, onClick = { actions.selectTask(task.id) }, enabled = editable)
                                Text(task.title, Modifier.weight(1f))
                            }
                        }
                    }
                    item {
                        TutorialCard {
                            Text(if (state.task == null) setupContext.getString(R.string.setup_text_045) else setupContext.getString(R.string.setup_text_046), style = MaterialTheme.typography.titleMedium)
                            Text(setupContext.getString(R.string.setup_text_047), style = MaterialTheme.typography.bodySmall)
                            OutlinedTextField(title, { title = it }, label = { Text(setupContext.getString(R.string.setup_text_048)) }, enabled = editable, modifier = Modifier.fillMaxWidth())
                            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                OutlinedTextField(start, { start = it }, label = { Text(setupContext.getString(R.string.setup_text_049)) }, singleLine = true, enabled = editable, modifier = Modifier.weight(1f))
                                OutlinedTextField(end, { end = it }, label = { Text(setupContext.getString(R.string.setup_text_050)) }, singleLine = true, enabled = editable, modifier = Modifier.weight(1f))
                            }
                            Text(setupContext.getString(R.string.setup_text_051))
                            listOf(setupContext.getString(R.string.setup_text_052), setupContext.getString(R.string.setup_text_053), setupContext.getString(R.string.setup_text_054), setupContext.getString(R.string.setup_text_055), setupContext.getString(R.string.setup_text_056), setupContext.getString(R.string.setup_text_057), setupContext.getString(R.string.setup_text_058)).forEachIndexed { index, day ->
                                Row(Modifier.fillMaxWidth().clickable(enabled = editable) { days = days xor (1 shl index) }, verticalAlignment = Alignment.CenterVertically) {
                                    Checkbox(days and (1 shl index) != 0, { days = days xor (1 shl index) }, enabled = editable)
                                    Text(day)
                                }
                            }
                            OutlinedButton(onClick = { actions.saveTask(title, start, end, days) }, enabled = editable && title.isNotBlank() && days != 0) { Text(setupContext.getString(R.string.setup_text_059)) }
                        }
                    }
                }
                3 -> {
                    item {
                        OutlinedTextField(search, { search = it }, label = { Text(setupContext.getString(R.string.setup_text_060)) }, singleLine = true, modifier = Modifier.fillMaxWidth(), enabled = !state.busy)
                        Text(setupContext.getString(R.string.setup_text_061, state.selected.size))
                        if (state.selected != state.savedApps.map { it.packageName }.toSet())
                            Text(setupContext.getString(R.string.setup_text_062), style = MaterialTheme.typography.bodySmall)
                        if (state.completed) OutlinedButton(onClick = actions.saveApps, enabled = !state.busy && state.appsLoaded && state.selected.isNotEmpty()) { Text(setupContext.getString(R.string.setup_text_063)) }
                    }
                    val filtered = state.apps.filter { it.appName.contains(search, true) || it.packageName.contains(search, true) }
                    if (state.appsLoaded && filtered.isEmpty()) item { Text(setupContext.getString(R.string.setup_text_064)) }
                    items(filtered, key = { it.packageName }) { app ->
                        TutorialCard {
                        Row(Modifier.fillMaxWidth().clickable(enabled = editable) { actions.toggleApp(app.packageName) }.padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                            TutorialAppIcon(app.packageName)
                            Column(Modifier.weight(1f).padding(horizontal = 12.dp)) { Text(app.appName) }
                            Checkbox(app.packageName in state.selected, { actions.toggleApp(app.packageName) }, enabled = editable)
                        }
                        }
                    }
                }
                4 -> {
                    item { TutorialCard {
                        Text(setupContext.getString(R.string.setup_text_065, state.task?.title.orEmpty()))
                        Text(setupContext.getString(R.string.setup_text_066, state.savedApps.size))
                        OutlinedButton(onClick = actions.generate, enabled = editable) { Text(if (state.generated) setupContext.getString(R.string.setup_text_067) else setupContext.getString(R.string.setup_text_068)) }
                        if (!state.connected) TextButton(onClick = { actions.go(1) }, enabled = !state.busy) { Text(setupContext.getString(R.string.setup_text_069)) }
                    } }
                    items(state.previews) { preview -> TutorialCard { Text(preview) } }
                }
                5 -> {
                    item { TutorialCard {
                        Text(if (state.detectionEnabled) setupContext.getString(R.string.setup_text_070) else setupContext.getString(R.string.setup_text_071))
                        if (!state.detectionEnabled) OutlinedButton(onClick = actions.enableDetection, enabled = editable) { Text(setupContext.getString(R.string.setup_text_072)) }
                    } }
                    val names = listOf(setupContext.getString(R.string.setup_text_073), setupContext.getString(R.string.setup_text_074), setupContext.getString(R.string.setup_text_075), setupContext.getString(R.string.setup_text_076), setupContext.getString(R.string.setup_text_077))
                    val descriptions = listOf(setupContext.getString(R.string.setup_text_078), setupContext.getString(R.string.setup_text_079), setupContext.getString(R.string.setup_text_080), setupContext.getString(R.string.setup_text_081), setupContext.getString(R.string.setup_text_082))
                    items(5) { index -> TutorialCard {
                        Text(names[index], style = MaterialTheme.typography.titleMedium)
                        Text(descriptions[index])
                        Text(if (state.permissions.getOrNull(index) == true) setupContext.getString(R.string.setup_text_083) else setupContext.getString(R.string.setup_text_084), color = MaterialTheme.colorScheme.primary)
                        TextButton(onClick = { systemAction(index) }, enabled = !state.busy) { Text(setupContext.getString(R.string.setup_text_085)) }
                    } }
                    item { Text(setupContext.getString(R.string.setup_text_086), style = MaterialTheme.typography.bodySmall) }
                }
                6 -> {
                    item { TutorialCard {
                        Text(if (state.completed) setupContext.getString(R.string.setup_text_087) else setupContext.getString(R.string.setup_text_088), style = MaterialTheme.typography.titleLarge)
                        Text(setupContext.getString(R.string.setup_text_065, state.task?.title ?: setupContext.getString(R.string.setup_text_089)))
                        state.task?.let { task ->
                            val scheduledStart = task.scheduleStartMinute
                            val scheduledEnd = task.scheduleEndMinute
                            Text(if (scheduledStart != null && scheduledEnd != null) setupContext.getString(R.string.setup_text_090, tutorialFormatTime(scheduledStart), tutorialFormatTime(scheduledEnd)) else setupContext.getString(R.string.setup_text_091))
                        }
                        Text(setupContext.getString(R.string.setup_text_092, state.savedApps.joinToString(setupContext.getString(R.string.setup_list_separator)) { it.appName }))
                        Text("AI：${state.model} · ${if (state.generated) setupContext.getString(R.string.setup_text_309) else if (state.completed) setupContext.getString(R.string.setup_text_310) else setupContext.getString(R.string.setup_text_311)}")
                        Text(setupContext.getString(R.string.setup_text_093, state.permissions.take(4).count { it }, if (state.detectionEnabled) setupContext.getString(R.string.setup_text_094) else setupContext.getString(R.string.setup_text_095)))
                        Text(setupContext.getString(R.string.setup_text_096, state.delaySeconds, if (state.breathing) setupContext.getString(R.string.setup_text_094) else setupContext.getString(R.string.setup_text_095)))
                        if (!state.completed) {
                            if (!state.connected) TextButton(onClick = { actions.go(1) }, enabled = editable) { Text(setupContext.getString(R.string.setup_text_097)) }
                            if (!state.generated) TextButton(onClick = { actions.go(4) }, enabled = editable) { Text(setupContext.getString(R.string.setup_text_098)) }
                            if (state.permissions.take(4).count { it } != 4 || !state.detectionEnabled)
                                TextButton(onClick = { actions.go(5) }, enabled = editable) { Text(setupContext.getString(R.string.setup_text_099)) }
                            Row(Modifier.fillMaxWidth().clickable(enabled = !state.busy) { startNow = !startNow }, verticalAlignment = Alignment.CenterVertically) {
                                Checkbox(startNow, { startNow = it }, enabled = !state.busy)
                                Text(setupContext.getString(R.string.setup_text_100), Modifier.weight(1f))
                            }
                            Text(setupContext.getString(R.string.setup_text_101))
                        } else {
                            TextButton(onClick = { actions.go(0) }, enabled = !state.busy) { Text(setupContext.getString(R.string.setup_text_102)) }
                            Text(setupContext.getString(R.string.setup_text_103))
                            Text(if (state.experienceDone) setupContext.getString(R.string.setup_text_104) else setupContext.getString(R.string.setup_text_105))
                            Text(if (state.guardian) setupContext.getString(R.string.setup_text_106) else setupContext.getString(R.string.setup_text_107))
                        }
                    } }
                    if (state.completed) {
                        item { Text(setupContext.getString(R.string.setup_text_108), style = MaterialTheme.typography.titleMedium) }
                        items(state.savedApps.map { it.packageName }, key = { "launch-$it" }) { pkg ->
                            val name = appNames[pkg] ?: setupContext.getString(R.string.setup_text_109)
                            OutlinedButton(onClick = {
                                try {
                                    val intent = context.packageManager.getLaunchIntentForPackage(pkg)
                                    if (intent == null) localMessage = setupContext.getString(R.string.setup_text_110)
                                    else context.startActivity(intent)
                                } catch (_: Exception) { localMessage = setupContext.getString(R.string.setup_text_111) }
                            }, enabled = editable, modifier = Modifier.fillMaxWidth()) { Text(setupContext.getString(R.string.setup_text_112, name)) }
                        }
                    }
                }
            }
        }
    }
}

private val tutorialIconCache = LruCache<String, ImageBitmap>(48)

@Composable
private fun TutorialAppIcon(packageName: String) {
    val context = LocalContext.current
    val bitmap by produceState<ImageBitmap?>(null, packageName) {
        value = withContext(Dispatchers.IO) {
            tutorialIconCache.get(packageName) ?: runCatching {
                context.packageManager.getApplicationIcon(packageName).toBitmap(96, 96).asImageBitmap()
                    .also { tutorialIconCache.put(packageName, it) }
            }.getOrNull()
        }
    }
    val icon = bitmap
    if (icon == null) Spacer(Modifier.size(40.dp)) else Image(icon, contentDescription = null, modifier = Modifier.size(40.dp))
}

@androidx.compose.ui.tooling.preview.Preview(name = "教程欢迎 · 日间", showBackground = true, widthDp = 360, heightDp = 800)
@androidx.compose.ui.tooling.preview.Preview(name = "Tutorial English", showBackground = true, widthDp = 360, heightDp = 800, locale = "en")
@Composable private fun TutorialWelcomeDayPreview() {
    com.example.focus_app.ui.theme.FocusAppTheme(mode = com.example.focus_app.domain.model.AppThemeMode.DAY) {
        TutorialContent(TutorialUiState(loading = false), TutorialActions(), {})
    }
}

@androidx.compose.ui.tooling.preview.Preview(name = "教程欢迎 · 夜间", showBackground = true, widthDp = 360, heightDp = 800)
@Composable private fun TutorialWelcomeNightPreview() {
    com.example.focus_app.ui.theme.FocusAppTheme(mode = com.example.focus_app.domain.model.AppThemeMode.NIGHT) {
        TutorialContent(TutorialUiState(loading = false), TutorialActions(), {})
    }
}

@androidx.compose.ui.tooling.preview.Preview(name = "教程权限 · 大字体", showBackground = true, widthDp = 360, heightDp = 800, fontScale = 1.3f)
@Composable private fun TutorialPermissionsPreview() {
    com.example.focus_app.ui.theme.FocusAppTheme {
        TutorialContent(TutorialUiState(loading = false, step = 5, detectionEnabled = true,
            permissions = listOf(true, false, false, true, false)), TutorialActions(), {})
    }
}
