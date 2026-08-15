package com.example.focus_app.ui.home

import org.junit.Assert.assertEquals
import org.junit.Test

class HomeQuotaLabelTest {
    @Test
    fun quota_label_names_the_actual_rolling_window() {
        assertEquals(
            "本时间段（30 分钟）已提醒 2/5 次，剩余 3 次",
            reminderQuotaLabel(minutes = 30, count = 2, limit = 5)
        )
    }
}
