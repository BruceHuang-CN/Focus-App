package com.example.focus_app.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.focus_app.domain.model.DetectionMode
import com.example.focus_app.domain.permission.PermissionCheckAction
import com.example.focus_app.domain.permission.PermissionCheckItem
import com.example.focus_app.domain.permission.PermissionCheckStatus
import com.example.focus_app.ui.theme.ErrorRed
import com.example.focus_app.ui.theme.SuccessGreen

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
                    Text("检测状态", style = MaterialTheme.typography.titleMedium)
                    Text(
                        if (missing == 0) {
                            "${detectionModeLabel(mode)} · 一切就绪"
                        } else {
                            "${detectionModeLabel(mode)} · $missing 项未就绪"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = if (missing == 0) SuccessGreen else ErrorRed
                    )
                }
                Icon(
                    imageVector = if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                    contentDescription = if (expanded) "收起" else "展开"
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
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = if (item.status == PermissionCheckStatus.OK) "✓" else "✗",
            color = if (item.status == PermissionCheckStatus.OK) SuccessGreen else ErrorRed,
            style = MaterialTheme.typography.titleMedium
        )
        Spacer(modifier = Modifier.width(8.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(item.label, style = MaterialTheme.typography.bodyMedium)
            Text(
                item.detail,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.outline
            )
        }
        if (item.status == PermissionCheckStatus.MISSING &&
            item.action != PermissionCheckAction.NONE
        ) {
            TextButton(onClick = { onAction(item.action) }) { Text("去开启") }
        }
    }
}

internal fun detectionModeLabel(mode: DetectionMode): String = when (mode) {
    DetectionMode.REALTIME -> "实时模式"
    DetectionMode.COMPATIBILITY -> "兼容模式"
}
