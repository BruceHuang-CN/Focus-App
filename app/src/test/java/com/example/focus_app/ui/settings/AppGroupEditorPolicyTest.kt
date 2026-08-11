package com.example.focus_app.ui.settings

import com.example.focus_app.data.repository.AppInfo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AppGroupEditorPolicyTest {
    @Test
    fun rejects_save_when_no_apps_are_selected() {
        val result = AppGroupEditorPolicy.validate("Short video", emptyList())

        assertFalse(result.canSave)
        assertEquals("Please select at least one app.", result.errorMessage)
    }

    @Test
    fun accepts_non_empty_selection_and_normalizes_name() {
        val result = AppGroupEditorPolicy.validate(
            name = "  Short video  ",
            apps = listOf(AppInfo("Example", "com.example.app"))
        )

        assertTrue(result.canSave)
        assertEquals("Short video", result.normalizedName)
        assertEquals(null, result.errorMessage)
    }
}
