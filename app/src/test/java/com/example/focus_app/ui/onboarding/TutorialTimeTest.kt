package com.example.focus_app.ui.onboarding

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TutorialTimeTest {
    @Test fun parsesDayBoundariesAndWhitespace() {
        assertEquals(0, tutorialTime("00:00"))
        assertEquals(1439, tutorialTime("23:59"))
        assertEquals(545, tutorialTime(" 09:05 "))
    }

    @Test fun rejectsInvalidClockTimes() {
        listOf("", "09", "09:00:00", "aa:00", "24:00", "23:60", "-1:00", "00:-1")
            .forEach { assertNull(it, tutorialTime(it)) }
    }

    @Test fun formatsAllValidMinutesWithoutLosingInformation() {
        for (minute in 0 until 1440) {
            assertEquals(minute, tutorialTime(tutorialFormatTime(minute)))
        }
        // 结束时间的 24:00 由 saveTask 单独接收，不属于普通钟点解析范围。
        assertEquals("24:00", tutorialFormatTime(1440))
    }
}
