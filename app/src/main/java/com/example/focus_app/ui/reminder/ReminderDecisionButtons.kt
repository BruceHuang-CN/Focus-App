package com.example.focus_app.ui.reminder

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp

internal fun centeredMenuOffset(anchorWidth: Dp, menuWidth: Dp): Dp = 0.dp

@Composable
internal fun ReminderDecisionButtons(
    actions: List<ReminderDecisionAction>,
    enabled: Boolean = true,
    onReturnClick: () -> Unit,
    onTimedDecision: (ReminderDecisionAction, Int) -> Unit,
    onCustomTimedAction: (ReminderDecisionAction) -> Unit,
    modifier: Modifier = Modifier
) {
    var expandedAction by rememberSaveable {
        mutableStateOf<ReminderDecisionAction?>(null)
    }
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        actions.forEach { action ->
            when (action) {
                ReminderDecisionAction.RETURN -> Button(
                    onClick = onReturnClick,
                    enabled = enabled,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("回到任务")
                }

                ReminderDecisionAction.INTENTIONAL,
                ReminderDecisionAction.REST -> BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
                    when (action) {
                        ReminderDecisionAction.INTENTIONAL -> FilledTonalButton(
                            onClick = { expandedAction = action },
                            enabled = enabled,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("有目的使用")
                        }

                        ReminderDecisionAction.REST -> OutlinedButton(
                            onClick = { expandedAction = action },
                            enabled = enabled,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("休息一下")
                        }

                        ReminderDecisionAction.RETURN -> Unit
                    }
                    DropdownMenu(
                        expanded = expandedAction == action,
                        onDismissRequest = { expandedAction = null },
                        offset = DpOffset(
                            x = centeredMenuOffset(maxWidth, maxWidth),
                            y = 0.dp
                        ),
                        modifier = Modifier.width(maxWidth)
                    ) {
                        presetSnoozeMinutes.forEach { minutes ->
                            DropdownMenuItem(
                                text = { Text("${minutes} 分钟后提醒") },
                                onClick = {
                                    expandedAction = null
                                    onTimedDecision(action, minutes)
                                }
                            )
                        }
                        DropdownMenuItem(
                            text = { Text("自定义分钟数") },
                            onClick = {
                                expandedAction = null
                                onCustomTimedAction(action)
                            }
                        )
                    }
                }
            }
        }
    }
}
