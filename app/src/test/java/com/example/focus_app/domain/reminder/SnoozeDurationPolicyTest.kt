package com.example.focus_app.domain.reminder

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SnoozeDurationPolicyTest {
    @Test
    fun accepts_boundaries_and_rejects_out_of_range_values() {
        assertTrue(SnoozeDurationPolicy.isValid(1))
        assertTrue(SnoozeDurationPolicy.isValid(60))
        assertFalse(SnoozeDurationPolicy.isValid(0))
        assertFalse(SnoozeDurationPolicy.isValid(61))
        assertEquals(1, SnoozeDurationPolicy.normalizeStored(0))
        assertEquals(60, SnoozeDurationPolicy.normalizeStored(120))
    }
}
