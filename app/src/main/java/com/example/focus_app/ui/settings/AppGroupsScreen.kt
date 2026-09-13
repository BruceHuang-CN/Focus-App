package com.example.focus_app.ui.settings

import com.example.focus_app.R

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import kotlinx.coroutines.launch

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun AppGroupsScreen(
    onBack: () -> Unit,
    onAdd: () -> Unit,
    onEdit: (String) -> Unit,
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val setupContext = androidx.compose.ui.platform.LocalContext.current
    val groups by viewModel.appGroups.collectAsState()
    val activeId by viewModel.activeAppGroupId.collectAsState()
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    LaunchedEffect(viewModel, setupContext) { viewModel.appGroupMessages.collect { snackbar.showSnackbar(it.resolve(setupContext)) } }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(title = { Text(setupContext.getString(R.string.setup_text_156)) }, navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = setupContext.getString(R.string.setup_text_164))
                }
            })
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Button(onClick = onAdd, modifier = Modifier.fillMaxWidth()) { Text(setupContext.getString(R.string.setup_text_148)) }
            }
            items(groups, key = { it.id }) { group ->
                Column(modifier = Modifier.fillMaxWidth().clickable { onEdit(group.id) }.padding(vertical = 8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(group.name, style = MaterialTheme.typography.titleMedium)
                        Spacer(Modifier.width(8.dp))
                        if (group.id == activeId) Text(setupContext.getString(R.string.setup_text_157), color = MaterialTheme.colorScheme.primary)
                    }
                    Text(setupContext.getString(R.string.setup_text_158, group.apps.size), style = MaterialTheme.typography.bodyMedium)
                    Row {
                        TextButton(onClick = { viewModel.activateAppGroup(group.id) }, enabled = group.id != activeId) { Text(setupContext.getString(R.string.setup_text_159)) }
                        TextButton(onClick = { onEdit(group.id) }) { Text(setupContext.getString(R.string.setup_text_160)) }
                        TextButton(
                            onClick = {
                                viewModel.deleteAppGroup(group.id).exceptionOrNull()?.let { error ->
                                    scope.launch { snackbar.showSnackbar(setupContext.getString(R.string.setup_text_161, error.message)) }
                                }
                            },
                            enabled = groups.size > 1 && group.id != activeId
                        ) { Text(setupContext.getString(R.string.setup_text_162)) }
                    }
                }
            }
        }
    }
}
