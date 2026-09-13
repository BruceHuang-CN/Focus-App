package com.example.focus_app.ui.mood

import com.example.focus_app.R
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MoodPickerScreen(onBack: () -> Unit, viewModel: MoodViewModel = hiltViewModel()) {
    val textContext = androidx.compose.ui.platform.LocalContext.current
    var note by remember { mutableStateOf("") }
    Scaffold(topBar = { TopAppBar(title = { Text(textContext.getString(R.string.core_record_mood)) }) }) { padding ->
        Column(modifier = Modifier.padding(padding).padding(16.dp)) {
            Text(textContext.getString(R.string.core_current_feeling), style = MaterialTheme.typography.titleMedium)
            Spacer(modifier = Modifier.height(12.dp))
            LazyVerticalGrid(columns = GridCells.Fixed(2), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(MOOD_OPTIONS.size) { i ->
                    OutlinedButton(onClick = { viewModel.saveMood(MOOD_OPTIONS[i], note.ifBlank { null }) { onBack() } }, modifier = Modifier.fillMaxWidth()) { Text(moodDisplayLabel(textContext, MOOD_OPTIONS[i])) }
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
            OutlinedTextField(value = note, onValueChange = { note = it }, modifier = Modifier.fillMaxWidth(), placeholder = { Text(textContext.getString(R.string.core_mood_note)) })
        }
    }
}
