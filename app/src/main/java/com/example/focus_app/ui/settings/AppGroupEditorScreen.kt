package com.example.focus_app.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.focus_app.data.repository.AppInfo
import com.example.focus_app.util.loadInstalledApps

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun AppGroupEditorScreen(
    groupId: String?,
    onBack: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val group = viewModel.appGroups.value.firstOrNull { it.id == groupId }
    val context = LocalContext.current
    val installedApps = remember { loadInstalledApps(context) }
    var name by remember(groupId) { mutableStateOf(group?.name.orEmpty()) }
    var selectedApps by remember(groupId) { mutableStateOf(group?.apps.orEmpty()) }
    var query by remember { mutableStateOf("") }
    val validation = AppGroupEditorPolicy.validate(name, selectedApps)
    val selectedPackages = selectedApps.map { it.packageName }.toSet()
    val filtered = installedApps.filter { query.isBlank() || it.appName.contains(query, true) || it.packageName.contains(query, true) }

    Scaffold(topBar = {
        TopAppBar(title = { Text(if (group == null) "新增应用组" else "编辑应用组") }, navigationIcon = {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
        })
    }) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp)) {
            OutlinedTextField(value = name, onValueChange = { name = it.take(24) }, label = { Text("应用组名称") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
            OutlinedTextField(value = query, onValueChange = { query = it }, label = { Text("搜索 App") }, modifier = Modifier.fillMaxWidth().padding(top = 8.dp), singleLine = true)
            Text("已选择 ${selectedApps.size} 个 App", modifier = Modifier.padding(vertical = 8.dp))
            LazyColumn(modifier = Modifier.weight(1f)) {
                items(filtered, key = { it.packageName }) { app ->
                    val checked = app.packageName in selectedPackages
                    Row(modifier = Modifier.fillMaxWidth().clickable {
                        selectedApps = if (checked) selectedApps.filterNot { it.packageName == app.packageName }
                        else selectedApps + AppInfo(app.packageName, app.appName)
                    }.padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = checked, onCheckedChange = null)
                        Spacer(Modifier.width(12.dp))
                        Column {
                            Text(app.appName, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(app.packageName, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
            }
            validation.errorMessage?.let { Text(it) }
            Button(onClick = { if (viewModel.saveAppGroup(groupId, name, selectedApps).isSuccess) onBack() }, enabled = validation.canSave, modifier = Modifier.fillMaxWidth()) { Text("保存") }
        }
    }
}
