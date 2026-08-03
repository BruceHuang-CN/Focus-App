package com.example.focus_app.data.security

import kotlinx.coroutines.async
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.fail
import org.junit.Test

class MigratingApiKeyStoreTest {
    @Test
    fun first_concurrent_reads_move_the_legacy_key_once_and_clear_room() = runTest {
        val encrypted = RecordingApiKeyStore()
        val legacy = RecordingLegacyApiKeySource("legacy-secret")
        val store = MigratingApiKeyStore(encrypted, legacy)

        val first = async { store.read() }
        val second = async { store.read() }

        assertEquals("legacy-secret", first.await())
        assertEquals("legacy-secret", second.await())
        assertEquals(1, encrypted.writeCount)
        assertEquals(1, legacy.clearCount)
        assertEquals("", legacy.value)
    }

    @Test
    fun encrypted_write_with_failed_legacy_clear_retries_clear_on_next_read() = runTest {
        val encrypted = RecordingApiKeyStore()
        val legacy = RecordingLegacyApiKeySource("legacy-secret", clearFailures = 1)
        val store = MigratingApiKeyStore(encrypted, legacy)

        try {
            store.read()
            fail("first legacy clear should fail")
        } catch (_: IllegalStateException) {
            // Expected RED path.
        }
        assertEquals("legacy-secret", encrypted.value)
        assertEquals(1, encrypted.writeCount)

        assertEquals("legacy-secret", store.read())
        assertEquals(1, encrypted.writeCount)
        assertEquals(2, legacy.clearCount)
        assertEquals("", legacy.value)
    }

    @Test
    fun clear_does_not_remove_encrypted_key_when_legacy_clear_fails() = runTest {
        val encrypted = RecordingApiKeyStore("encrypted-secret")
        val legacy = RecordingLegacyApiKeySource("legacy-secret", clearFailures = 1)
        val store = MigratingApiKeyStore(encrypted, legacy)

        try {
            store.clear()
            fail("legacy clear should fail")
        } catch (_: IllegalStateException) {
            // Caller observes that clear did not complete.
        }

        assertEquals("encrypted-secret", encrypted.value)
        assertEquals(0, encrypted.clearCount)
    }

    @Test
    fun blank_write_does_not_remove_encrypted_key_when_legacy_clear_fails() = runTest {
        val encrypted = RecordingApiKeyStore("encrypted-secret")
        val legacy = RecordingLegacyApiKeySource("legacy-secret", clearFailures = 1)
        val store = MigratingApiKeyStore(encrypted, legacy)

        try {
            store.write("")
            fail("legacy clear should fail")
        } catch (_: IllegalStateException) {
            // Caller observes that the blank write did not complete.
        }

        assertEquals("encrypted-secret", encrypted.value)
        assertEquals(0, encrypted.clearCount)
        assertEquals(0, encrypted.writeCount)
    }
}

private class RecordingApiKeyStore(initial: String = "") : ApiKeyStore {
    var value = initial
    var writeCount = 0
    var clearCount = 0

    override suspend fun read(): String = value
    override suspend fun write(value: String) {
        this.value = value
        writeCount++
    }

    override suspend fun clear() {
        clearCount++
        value = ""
    }
}

private class RecordingLegacyApiKeySource(
    initial: String,
    private var clearFailures: Int = 0
) : LegacyApiKeySource {
    var value = initial
    var clearCount = 0

    override suspend fun read(): String = value
    override suspend fun clear() {
        clearCount++
        if (clearFailures > 0) {
            clearFailures--
            throw IllegalStateException("legacy clear failed")
        }
        value = ""
    }
}
