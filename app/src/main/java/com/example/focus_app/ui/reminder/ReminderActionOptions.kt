package com.example.focus_app.ui.reminder

import com.example.focus_app.domain.model.ReturnDestination
import com.example.focus_app.domain.reminder.SnoozeDurationPolicy

internal val presetSnoozeMinutes = listOf(1, 5, 10)

internal enum class ReminderDecisionAction {
    RETURN,
    INTENTIONAL,
    REST
}

private val fixedReminderActionOrder = listOf(
    ReminderDecisionAction.RETURN,
    ReminderDecisionAction.INTENTIONAL,
    ReminderDecisionAction.REST
)

private val reminderActionOrders = listOf(
    fixedReminderActionOrder,
    listOf(ReminderDecisionAction.RETURN, ReminderDecisionAction.REST, ReminderDecisionAction.INTENTIONAL),
    listOf(ReminderDecisionAction.INTENTIONAL, ReminderDecisionAction.RETURN, ReminderDecisionAction.REST),
    listOf(ReminderDecisionAction.INTENTIONAL, ReminderDecisionAction.REST, ReminderDecisionAction.RETURN),
    listOf(ReminderDecisionAction.REST, ReminderDecisionAction.RETURN, ReminderDecisionAction.INTENTIONAL),
    listOf(ReminderDecisionAction.REST, ReminderDecisionAction.INTENTIONAL, ReminderDecisionAction.RETURN)
)

internal fun reminderActionOrder(
    randomize: Boolean,
    seed: String
): List<ReminderDecisionAction> {
    if (!randomize) return fixedReminderActionOrder
    return reminderActionOrders[Math.floorMod(seed.hashCode(), reminderActionOrders.size)]
}

internal fun exitDestinations(customPackageName: String): List<ReturnDestination> =
    buildList {
        add(ReturnDestination.HOME)
        add(ReturnDestination.FOCUS)
        if (customPackageName.isNotBlank()) add(ReturnDestination.CUSTOM)
    }

internal fun parseCustomSnoozeMinutes(raw: String): Int? =
    raw.trim().toIntOrNull()?.takeIf(SnoozeDurationPolicy::isValid)
