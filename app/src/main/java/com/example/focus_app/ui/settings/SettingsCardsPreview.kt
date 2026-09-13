package com.example.focus_app.ui.settings

import com.example.focus_app.R

import android.content.res.Configuration
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.focus_app.ui.theme.FocusAppTheme

/** Uses production card/row components; no Hilt, permissions, network or storage in previews. */
@Preview(name = "设置日间", widthDp = 393, heightDp = 852)
@Preview(name = "设置夜间", widthDp = 393, heightDp = 852, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Preview(name = "设置大字体", widthDp = 360, heightDp = 800, fontScale = 1.3f)
@Preview(name = "Settings English", widthDp = 360, heightDp = 800, locale = "en")
@Preview(name = "Settings English dark", widthDp = 360, heightDp = 800, locale = "en", uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable private fun SettingsCardsPreview() {
    val setupContext = androidx.compose.ui.platform.LocalContext.current
    FocusAppTheme {
        var enabled by remember { mutableStateOf(true) }
        LazyColumn(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background),
            contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            item {
                Text(setupContext.getString(R.string.setup_text_190), fontSize = 40.sp, fontWeight = FontWeight.ExtraBold, color = MaterialTheme.colorScheme.onBackground)
                Text(setupContext.getString(R.string.setup_text_191), color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            item {
                ForestSettingsSection(setupContext.getString(R.string.setup_text_192), icon = Icons.Default.Notifications, subtitle = setupContext.getString(R.string.setup_text_193)) {
                    SettingsToggle(Icons.Default.Lock, setupContext.getString(R.string.setup_text_194), enabled, setupContext.getString(R.string.setup_text_195)) { enabled = it }
                    SettingsToggle(Icons.Default.Warning, setupContext.getString(R.string.setup_text_196), true, setupContext.getString(R.string.setup_text_197)) { }
                    SettingsToggle(Icons.Default.Favorite, setupContext.getString(R.string.setup_text_198), true) { }
                }
            }
            item {
                ForestSettingsSection(setupContext.getString(R.string.setup_text_199), icon = Icons.Default.Settings) {
                    SettingsEntry(Icons.Default.DateRange, setupContext.getString(R.string.setup_text_200), setupContext.getString(R.string.setup_text_201)) { }
                    SettingsEntry(Icons.Default.Notifications, setupContext.getString(R.string.setup_text_202), setupContext.getString(R.string.setup_text_203)) { }
                    SettingsEntry(Icons.Default.List, setupContext.getString(R.string.setup_text_204), setupContext.getString(R.string.setup_text_205)) { }
                }
            }
            item {
                ForestSettingsSection(setupContext.getString(R.string.setup_text_002), icon = Icons.Default.Star) {
                    SettingsEntry(Icons.Default.Settings, setupContext.getString(R.string.setup_text_206), setupContext.getString(R.string.setup_text_207)) { }
                    SettingsEntry(Icons.Default.Face, setupContext.getString(R.string.setup_text_208), setupContext.getString(R.string.setup_text_209)) { }
                    SettingsEntry(Icons.Default.Refresh, setupContext.getString(R.string.setup_text_210)) { }
                }
            }
        }
    }
}
