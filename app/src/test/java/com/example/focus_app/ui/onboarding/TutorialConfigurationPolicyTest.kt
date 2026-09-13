package com.example.focus_app.ui.onboarding

import com.example.focus_app.data.repository.AppInfo
import com.example.focus_app.data.repository.AppSettings
import com.example.focus_app.domain.model.FocusTask
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TutorialConfigurationPolicyTest {
    @Test fun completedOrDeletedTaskCannotBeEditedAsTheReplacement() {
        val old = FocusTask(id = 7, title = "原任务", isCompleted = true)
        assertNull(tutorialEditableTask(old))
        assertNull(tutorialEditableTask(null))
        val active = old.copy(isCompleted = false)
        assertEquals(active, tutorialEditableTask(active))
        assertTrue(old.isCompleted)
    }

    @Test fun appOrderingDoesNotInvalidateButActualSelectionDoes() {
        val a = AppInfo("app.a", "应用 A")
        val b = AppInfo("app.b", "应用 B")
        assertFalse(tutorialAppsChanged(listOf(a, b), listOf(b, a)))
        assertFalse(tutorialAppsChanged(listOf(a), listOf(a)))
        assertTrue(tutorialAppsChanged(listOf(a), listOf(b)))
        assertTrue(tutorialAppsChanged(listOf(a), listOf(a, b)))
    }

    @Test fun saveCannotUseAnOldOrUnloadedGroupIdentity() {
        assertFalse(tutorialGroupMatches(null, ""))
        assertFalse(tutorialGroupMatches("group.a", "group.b"))
        assertFalse(tutorialGroupMatches("group.a", ""))
        assertTrue(tutorialGroupMatches("group.b", "group.b"))
        assertTrue(tutorialGroupMatches("", ""))
    }

    @Test fun unchangedOrOmittedKeyKeepsCompletedConfiguration() {
        val old = AppSettings(apiEndpoint = "https://example.invalid", aiModel = "model")
        assertFalse(tutorialAiChanged(old, old.apiEndpoint, old.aiModel, "saved", ""))
        assertTrue(tutorialAiChanged(old, old.apiEndpoint, old.aiModel, "saved", "",
            com.example.focus_app.domain.model.AiProvider.CUSTOM))
        assertFalse(tutorialAiChanged(old, old.apiEndpoint, old.aiModel, "saved", "saved"))
        assertTrue(tutorialAiChanged(old, old.apiEndpoint, old.aiModel, "saved", "new"))
        assertTrue(tutorialAiChanged(old, "https://other.invalid", old.aiModel, "saved", ""))
        assertTrue(tutorialAiChanged(old, old.apiEndpoint, "other-model", "saved", ""))
    }
}
