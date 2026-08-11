package com.example.focus_app.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun AppGroupsScreen(
    onBack: () -> Unit,
    onAdd: () -> Unit,
    onEdit: (String) -> Unit,
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val groups by viewModel.appGroups.collectAsState()
    val activeId by viewModel.activeAppGroupId.collectAsState()
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(Unit) { viewModel.appGroupMessages.collect { snackbar.showSnackbar(it) } }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(title = { Text("应用组管理") }, navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                }
            })
        }
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Button(onClick = onAdd, modifier = Modifier.fillMaxWidth()) { Text("新增应用组") }
            groups.forEach { group ->
                Column(modifier = Modifier.fillMaxWidth().clickable { onEdit(group.id) }.padding(vertical = 8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(group.name, style = MaterialTheme.typography.titleMedium)
                        Spacer(Modifier.width(8.dp))
                        if (group.id == activeId) Text("正在守护", color = MaterialTheme.colorScheme.primary)
                    }
                    Text("${group.apps.size} 个 App", style = MaterialTheme.typography.bodyMedium)
                    Row {
                        TextButton(onClick = { viewModel.activateAppGroup(group.id) }, enabled = group.id != activeId) { Text("启用") }
                        TextButton(onClick = { onEdit(group.id) }) { Text("编辑") }
                        TextButton(onClick = { viewModel.deleteAppGroup(group.id) }, enabled = groups.size > 1) { Text("删除") }
                    }
                }
            }
        }
    }
}
