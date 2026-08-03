package com.example.focus_app.data.security

import org.junit.Assert.fail
import org.junit.Test

class EncryptedApiKeyStoreTest {
    @Test
    fun preferences_commit_failure_is_not_silent() {
        try {
            requireSuccessfulPreferencesCommit(false)
            fail("false commit must throw")
        } catch (_: IllegalStateException) {
            // Expected: write and clear callers can observe persistence failure.
        }
    }
}
