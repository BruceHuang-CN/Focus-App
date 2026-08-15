package com.example.focus_app.ui.reminder

import com.example.focus_app.domain.model.ReturnDestination
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ReminderActionOptionsTest {
    @Test
    fun custom_return_destination_is_offered_only_for_a_configured_package() {
        assertEquals(
            listOf(ReturnDestination.HOME, ReturnDestination.FOCUS),
            exitDestinations("")
        )
        assertEquals(
            listOf(ReturnDestination.HOME, ReturnDestination.FOCUS, ReturnDestination.CUSTOM),
            exitDestinations("com.tencent.mm")
        )
    }

    @Test
    fun custom_snooze_accepts_only_one_to_60_minutes() {
        assertEquals(1, parseCustomSnoozeMinutes("1"))
        assertEquals(60, parseCustomSnoozeMinutes("60"))
        assertNull(parseCustomSnoozeMinutes("0"))
        assertNull(parseCustomSnoozeMinutes("61"))
        assertNull(parseCustomSnoozeMinutes("abc"))
    }
}
