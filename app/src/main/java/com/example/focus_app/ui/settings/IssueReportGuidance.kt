package com.example.focus_app.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.example.focus_app.R

/** 只帮助用户描述实际现象；点击提示不预填故障、不触发采集或上传。 */
@Composable
internal fun IssueReportGuidance(modifier: Modifier = Modifier) {
    var selected by rememberSaveable { mutableIntStateOf(-1) }
    IssueReportGuidanceContent(selected, { selected = if (selected == it) -1 else it }, modifier)
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun IssueReportGuidanceContent(
    selected: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val labels = listOf(R.string.feedback_guide_guardian, R.string.feedback_guide_ui, R.string.feedback_guide_other)
    val hints = listOf(R.string.feedback_guide_guardian_hint, R.string.feedback_guide_ui_hint, R.string.feedback_guide_other_hint)
    Column(modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(stringResource(R.string.feedback_guide_intro), style = MaterialTheme.typography.bodyMedium)
        Text(stringResource(R.string.feedback_guide_choose), style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            labels.forEachIndexed { index, label ->
                FilterChip(selected = selected == index, onClick = { onSelect(index) }, label = { Text(stringResource(label)) })
            }
        }
        if (selected in hints.indices) {
            Text(stringResource(hints[selected]), style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
