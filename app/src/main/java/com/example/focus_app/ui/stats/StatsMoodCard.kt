package com.example.focus_app.ui.stats
import com.example.focus_app.R
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
private val homeMoods = listOf("🔵" to "平静", "🌿" to "专注", "😐" to "有点累", "🙁" to "烦躁", "🙂" to "开心")

@Composable
internal fun StatsMoodCard(
    latestMood: String?,
    onOpen: () -> Unit,
    onSave: (String, String, () -> Unit) -> Unit
) {
    val textContext = androidx.compose.ui.platform.LocalContext.current
    var selected by rememberSaveable { mutableStateOf<String?>(null) }
    var note by rememberSaveable { mutableStateOf("") }
    var saving by remember { mutableStateOf(false) }
    Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(24.dp)) {
    Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(Modifier.fillMaxWidth().heightIn(min = 48.dp).clickable(role = Role.Button, onClick = onOpen),
            verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(textContext.getString(R.string.core_mood_record), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Text(latestMood?.let { textContext.getString(R.string.core_latest_mood, com.example.focus_app.ui.mood.moodDisplayLabel(textContext, it)) } ?: textContext.getString(R.string.core_record_state),
                    style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, textContext.getString(R.string.core_more_moods))
        }
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            homeMoods.forEach { (symbol, label) ->
                val active = selected == label
                Surface(
                    onClick = { selected = label }, enabled = !saving,
                    shape = RoundedCornerShape(16.dp),
                    color = if (active) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.38f),
                    border = if (active) BorderStroke(1.dp, MaterialTheme.colorScheme.primary) else null,
                    modifier = Modifier.widthIn(min = 60.dp).semantics { stateDescription = if (active) textContext.getString(R.string.core_selected) else textContext.getString(R.string.core_unselected) }
                ) {
                    Column(Modifier.padding(horizontal = 10.dp, vertical = 12.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(symbol, fontSize = 24.sp)
                        Text(com.example.focus_app.ui.mood.moodDisplayLabel(textContext, label), style = MaterialTheme.typography.labelLarge)
                    }
                }
            }
        }
        OutlinedTextField(
            value = note, onValueChange = { note = it.take(100) }, enabled = !saving,
            modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp),
            placeholder = { Text(textContext.getString(R.string.core_today_note)) },
            supportingText = { Text("${note.length}/100") }, maxLines = 3
        )
        if (selected != null) {
            Button(
                enabled = !saving,
                onClick = {
                    selected?.let { mood ->
                        saving = true
                        onSave(mood, note) { selected = null; note = ""; saving = false }
                    }
                }, modifier = Modifier.align(Alignment.End)
            ) { Text(if (saving) textContext.getString(R.string.core_saving) else textContext.getString(R.string.core_record_mood)) }
        }
    }
    }
}
