package com.example.focus_app.ui.settings

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.focus_app.data.repository.AppInfo
import com.example.focus_app.util.InstalledApp
import com.example.focus_app.util.loadInstalledApps
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TargetAppsScreen(onBack: () -> Unit, viewModel: SettingsViewModel = hiltViewModel()) {
    val settings by viewModel.settings.collectAsState()
    val context = LocalContext.current
    val allApps = remember { loadInstalledApps(context) }
    var searchQuery by remember { mutableStateOf("") }
    var selectedApps by remember(settings.targetApps) { mutableStateOf(settings.targetApps) }
    var showExitConfirmation by remember { mutableStateOf(false) }
    var saveRequested by remember { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    val selectedPackages = remember(selectedApps) { selectedApps.map { it.packageName }.toSet() }
    val savedPackages = remember(settings.targetApps) { settings.targetApps.map { it.packageName }.toSet() }
    val hasUnsavedChanges = TargetAppsExitPolicy.requiresConfirmation(savedPackages, selectedPackages)

    fun requestExit() {
        if (hasUnsavedChanges) showExitConfirmation = true else onBack()
    }

    BackHandler(enabled = !showExitConfirmation) { requestExit() }

    LaunchedEffect(saveRequested, hasUnsavedChanges) {
        if (saveRequested && !hasUnsavedChanges) onBack()
    }

    val filtered = remember(allApps, searchQuery) {
        if (searchQuery.isBlank()) allApps
        else allApps.filter {
            it.appName.contains(searchQuery, ignoreCase = true) ||
                it.packageName.contains(searchQuery, ignoreCase = true)
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text("选择目标 App") },
                navigationIcon = {
                    IconButton(onClick = ::requestExit) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                }
            )
        }
    ) { padding ->
        Column(modifier = Modifier.padding(padding)) {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = { Text("搜索 App 名称或包名...") },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                singleLine = true
            )

            Text(
                "已选 ${selectedPackages.size} 个 App",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.outline,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
            )

            LazyColumn(modifier = Modifier.fillMaxSize()) {
                items(filtered, key = { it.packageName }) { app ->
                    val isChecked = app.packageName in selectedPackages
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                val updated = selectedApps.toMutableList()
                                if (isChecked) {
                                    updated.removeAll { it.packageName == app.packageName }
                                } else {
                                    updated.add(app.toAppInfo())
                                }
                                selectedApps = updated
                                scope.launch {
                                    snackbarHostState.showSnackbar(
                                        if (isChecked) "已移除：${app.appName}" else "已添加：${app.appName}",
                                        duration = SnackbarDuration.Short
                                    )
                                }
                            }
                            .padding(horizontal = 16.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Checkbox(
                            checked = isChecked,
                            onCheckedChange = { _ -> } // handled by Row click
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                app.appName,
                                style = MaterialTheme.typography.bodyLarge,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                app.packageName,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.outline,
                                fontSize = 11.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }
        }
    }

    if (showExitConfirmation) {
        AlertDialog(
            onDismissRequest = { showExitConfirmation = false },
            title = { Text("保存目标应用？") },
            text = { Text("你修改了目标应用列表。保存后才会用于检测和提醒。") },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.updateTargetApps(selectedApps)
                        showExitConfirmation = false
                        saveRequested = true
                    }
                ) { Text("保存并返回") }
            },
            dismissButton = {
                TextButton(onClick = { showExitConfirmation = false }) {
                    Text("继续修改")
                }
            }
        )
    }
}

