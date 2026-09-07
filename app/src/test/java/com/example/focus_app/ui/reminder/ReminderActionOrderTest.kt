package com.example.focus_app.ui.reminder

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ReminderActionOrderTest {

    @Test
    fun disabled_randomization_uses_fixed_semantic_order() {
        assertEquals(
            listOf(
                ReminderDecisionAction.RETURN,
                ReminderDecisionAction.INTENTIONAL,
                ReminderDecisionAction.REST
            ),
            reminderActionOrder(randomize = false, seed = "attempt-1")
        )
    }

    @Test
    fun same_intervention_seed_keeps_the_same_order() {
        val first = reminderActionOrder(randomize = true, seed = "attempt-stable")

        repeat(20) {
            assertEquals(first, reminderActionOrder(randomize = true, seed = "attempt-stable"))
        }
    }

    @Test
    fun randomized_orders_cover_only_the_six_complete_permutations() {
        val observed = (0..1_000)
            .map { reminderActionOrder(randomize = true, seed = "attempt-$it") }
            .toSet()

        assertEquals(6, observed.size)
        assertTrue(observed.all { order -> order.toSet() == ReminderDecisionAction.entries.toSet() })
    }
}
