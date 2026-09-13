package com.example.focus_app.ui.reminder

import com.example.focus_app.R
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp

internal fun centeredMenuOffset(anchorWidth: Dp, menuWidth: Dp): Dp = 0.dp

private val DecisionButtonShape = RoundedCornerShape(50)
private const val DECISION_BUTTON_MIN_HEIGHT_DP = 52

@Composable
internal fun ReminderDecisionButtons(
    actions: List<ReminderDecisionAction>,
    enabled: Boolean = true,
    onReturnClick: () -> Unit,
    onTimedDecision: (ReminderDecisionAction, Int) -> Unit,
    onCustomTimedAction: (ReminderDecisionAction) -> Unit,
    modifier: Modifier = Modifier
) {
    val textContext = androidx.compose.ui.platform.LocalContext.current
    var expandedAction by rememberSaveable {
        mutableStateOf<ReminderDecisionAction?>(null)
    }
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        actions.forEach { action ->
            when (action) {
                ReminderDecisionAction.RETURN -> Button(
                    onClick = onReturnClick,
                    enabled = enabled,
                    shape = DecisionButtonShape,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = DECISION_BUTTON_MIN_HEIGHT_DP.dp)
                ) {
                    Text(textContext.getString(R.string.core_return_task), fontWeight = FontWeight.SemiBold)
                }

                ReminderDecisionAction.INTENTIONAL,
                ReminderDecisionAction.REST -> BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
                    OutlinedButton(
                        onClick = { expandedAction = action },
                        enabled = enabled,
                        shape = DecisionButtonShape,
                        colors = ButtonDefaults.outlinedButtonColors(
                            containerColor = MaterialTheme.colorScheme.surface,
                            contentColor = MaterialTheme.colorScheme.primary
                        ),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.45f)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = DECISION_BUTTON_MIN_HEIGHT_DP.dp)
                    ) {
                        Text(
                            text = if (action == ReminderDecisionAction.INTENTIONAL) {
                                textContext.getString(R.string.core_intentional)
                            } else {
                                textContext.getString(R.string.core_rest)
                            },
                            fontWeight = FontWeight.SemiBold
                        )
                        Spacer(Modifier.width(2.dp))
                        Icon(Icons.Filled.ArrowDropDown, contentDescription = null)
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
                                text = { Text(textContext.resources.getQuantityString(R.plurals.core_remind_after_minutes, minutes, minutes)) },
                                onClick = {
                                    expandedAction = null
                                    onTimedDecision(action, minutes)
                                }
                            )
                        }
                        DropdownMenuItem(
                            text = { Text(textContext.getString(R.string.core_custom_minutes)) },
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
