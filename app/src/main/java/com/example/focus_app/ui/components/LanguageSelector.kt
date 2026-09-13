package com.example.focus_app.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.example.focus_app.R
import com.example.focus_app.data.language.AppLanguage

@Composable
fun LanguageSelector(modifier: Modifier = Modifier, enabled: Boolean = true) {
    val context = LocalContext.current
    var expanded by remember { mutableStateOf(false) }
    val labels = listOf(
        AppLanguage.SYSTEM to stringResource(R.string.language_system),
        AppLanguage.CHINESE to "简体中文",
        AppLanguage.ENGLISH to "English"
    )
    val current = AppLanguage.selection(context)
    Box(modifier) {
        OutlinedButton(onClick = { expanded = true }, enabled = enabled) {
            Text(stringResource(R.string.language_selection, labels.firstOrNull { it.first == current }?.second.orEmpty()))
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            labels.forEach { (tag, label) ->
                DropdownMenuItem(text = { Text(label) }, onClick = {
                    expanded = false
                    AppLanguage.set(context, tag)
                })
            }
        }
    }
}
