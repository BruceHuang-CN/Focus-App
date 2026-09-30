package com.example.focus_app.ui.update

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.example.focus_app.R

/** Plain rendering: checking and browser navigation are owned by the Activity and its ViewModel. */
@Composable
internal fun AppUpdateDialog(
    state: AppUpdateUiState,
    onOpenWebsite: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val release = state.release
    if (release != null) {
        AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text(stringResource(R.string.update_available, release.versionName)) },
            text = {
                Column(Modifier.heightIn(max = 360.dp).verticalScroll(rememberScrollState())) {
                    if (release.releaseNotes.isNotBlank()) {
                        Text(release.releaseNotes)
                        Spacer(Modifier.height(16.dp))
                    }
                    Text(stringResource(R.string.update_browser_help))
                }
            },
            confirmButton = {
                TextButton(onClick = { onOpenWebsite(release.downloadPageUrl) }) {
                    Text(stringResource(R.string.update_open_website))
                }
            },
            dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.update_later)) } }
        )
    } else if (state.notice != null) {
        val message = when (state.notice) {
            UpdateNotice.CURRENT -> R.string.update_current
            UpdateNotice.UNAVAILABLE -> R.string.update_unavailable
            UpdateNotice.UNSUPPORTED -> R.string.update_unsupported
            UpdateNotice.BROWSER_UNAVAILABLE -> R.string.update_browser_unavailable
        }
        AlertDialog(onDismissRequest = onDismiss,
            title = { Text(stringResource(R.string.update_check)) },
            text = { Text(stringResource(message)) },
            confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.update_ok)) } })
    }
}
