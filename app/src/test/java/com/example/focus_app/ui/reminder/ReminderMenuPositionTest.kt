package com.example.focus_app.ui.reminder

import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Test

class ReminderMenuPositionTest {
    @Test
    fun menu_starts_at_the_button_left_edge() {
        assertEquals(0.dp, centeredMenuOffset(anchorWidth = 360.dp, menuWidth = 220.dp))
    }

    @Test
    fun menu_offset_does_not_move_left_on_a_narrow_screen() {
        assertEquals(0.dp, centeredMenuOffset(anchorWidth = 180.dp, menuWidth = 220.dp))
    }
}
