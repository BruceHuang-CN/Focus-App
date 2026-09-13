package com.example.focus_app.ui.settings

import com.example.focus_app.R

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.focus_app.domain.model.DetectionMode
import com.example.focus_app.domain.permission.PermissionCheckAction
import com.example.focus_app.domain.permission.PermissionCheckItem
import com.example.focus_app.domain.permission.PermissionCheckStatus
import com.example.focus_app.domain.permission.localizedLabel
import com.example.focus_app.domain.permission.localizedDetail

/**
 * 设置页顶部的「检测状态」窗口：折叠时显示当前模式与未就绪项数量，
 * 展开后列出当前检测方式所需的全部权限检查项，并可直接跳转对应设置。
 */
@Composable
fun PermissionCheckCard(
    mode: DetectionMode,
    items: List<PermissionCheckItem>,
    onAction: (PermissionCheckAction) -> Unit,
    modifier: Modifier = Modifier
) {
    val setupContext = androidx.compose.ui.platform.LocalContext.current
    var expanded by remember { mutableStateOf(false) }
    val missing = items.count { it.status == PermissionCheckStatus.MISSING }
    Card(modifier = modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { expanded = !expanded },
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(setupContext.getString(R.string.setup_text_182), style = MaterialTheme.typography.titleMedium)
                    Text(
                        if (missing == 0) {
                            setupContext.getString(R.string.setup_text_183, detectionModeLabel(mode))
                        } else {
                            setupContext.getString(R.string.setup_text_184, detectionModeLabel(mode), missing)
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = if (missing == 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                    )
                }
                Icon(
                    imageVector = if (expanded) Icons.Filled.KeyboardArrowUp else Icons.Filled.KeyboardArrowDown,
                    contentDescription = if (expanded) setupContext.getString(R.string.setup_text_185) else setupContext.getString(R.string.setup_text_186)
                )
            }
            if (expanded) {
                Divider()
                items.forEach { item -> PermissionCheckRow(item = item, onAction = onAction) }
            }
        }
    }
}

@Composable
private fun PermissionCheckRow(
    item: PermissionCheckItem,
    onAction: (PermissionCheckAction) -> Unit
) {
    val setupContext = androidx.compose.ui.platform.LocalContext.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = if (item.status == PermissionCheckStatus.OK) "✓" else "✗",
            color = if (item.status == PermissionCheckStatus.OK) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
            style = MaterialTheme.typography.titleMedium
        )
        Spacer(modifier = Modifier.width(8.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(item.localizedLabel(setupContext), style = MaterialTheme.typography.bodyMedium)
            Text(
                item.localizedDetail(setupContext),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.outline
            )
        }
        if (item.status == PermissionCheckStatus.MISSING &&
            item.action != PermissionCheckAction.NONE
        ) {
            TextButton(onClick = { onAction(item.action) }) { Text(setupContext.getString(R.string.setup_text_187)) }
        }
    }
}

@Composable
internal fun detectionModeLabel(mode: DetectionMode): String = when (mode) {
    DetectionMode.REALTIME -> androidx.compose.ui.res.stringResource(R.string.setup_text_188)
    DetectionMode.COMPATIBILITY -> androidx.compose.ui.res.stringResource(R.string.setup_text_189)
}
