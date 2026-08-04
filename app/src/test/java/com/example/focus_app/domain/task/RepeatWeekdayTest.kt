package com.example.focus_app.domain.task

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RepeatWeekdayTest {

    @Test
    fun toggle_adds_and_removes_a_day() {
        var mask = 0
        mask = RepeatWeekday.toggle(mask, 0)
        assertTrue(RepeatWeekday.has(mask, 0))
        mask = RepeatWeekday.toggle(mask, 0)
        assertFalse(RepeatWeekday.has(mask, 0))
    }

    @Test
    fun label_for_all_days_is_每天() {
        assertEquals("每天", RepeatWeekday.label(RepeatWeekday.ALL))
    }

    @Test
    fun label_for_empty_mask_is_未设置() {
        assertEquals("未设置", RepeatWeekday.label(0))
    }

    @Test
    fun label_lists_selected_days_in_order() {
        val mask = RepeatWeekday.MONDAY or RepeatWeekday.WEDNESDAY or RepeatWeekday.SUNDAY
        assertEquals("周一、周三、周日", RepeatWeekday.label(mask))
    }

    @Test
    fun mask_matches_active_task_resolver_day_bits() {
        // 位 0 对应周一：与 ActiveTaskResolver 的 dayOfWeek.value - 1 一致
        assertEquals(1, RepeatWeekday.MONDAY)
        assertEquals(64, RepeatWeekday.SUNDAY)
    }
}
