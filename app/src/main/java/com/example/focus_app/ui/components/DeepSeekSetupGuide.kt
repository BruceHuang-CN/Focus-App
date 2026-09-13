package com.example.focus_app.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.example.focus_app.R
import com.example.focus_app.domain.model.AiProvider

/** Static, reusable instructions. Credentials are entered only in the existing form. */
@Composable
fun DeepSeekSetupGuide(modifier: Modifier = Modifier) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    var openFailed by rememberSaveable { mutableStateOf(false) }
    val uriHandler = LocalUriHandler.current
    Surface(modifier = modifier.fillMaxWidth(), shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerLow) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.padding(16.dp)) {
            Row(Modifier.fillMaxWidth().clickable { expanded = !expanded },
                verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.setup_deepseek_title), Modifier.weight(1f),
                    style = MaterialTheme.typography.titleSmall)
                Icon(if (expanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                    stringResource(if (expanded) R.string.setup_text_185 else R.string.setup_text_186))
            }
            if (expanded) {
                listOf(R.string.setup_deepseek_step_account, R.string.setup_deepseek_step_balance,
                    R.string.setup_deepseek_step_key, R.string.setup_deepseek_step_paste,
                    R.string.setup_deepseek_step_test).forEachIndexed { index, id ->
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("${index + 1}.", color = MaterialTheme.colorScheme.primary)
                        Text(stringResource(id), style = MaterialTheme.typography.bodyMedium)
                    }
                }
                Text(stringResource(R.string.setup_deepseek_defaults,
                    AiProvider.DEEPSEEK.defaultEndpoint, AiProvider.DEEPSEEK.defaultModel),
                    style = MaterialTheme.typography.bodySmall)
                Text(stringResource(R.string.setup_deepseek_key_safety),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                OutlinedButton(onClick = {
                    openFailed = runCatching { uriHandler.openUri("https://platform.deepseek.com/api_keys") }.isFailure
                }, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.setup_deepseek_open))
                }
                if (openFailed) Text(stringResource(R.string.setup_deepseek_open_failed),
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                Text(stringResource(R.string.setup_deepseek_errors), style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}
