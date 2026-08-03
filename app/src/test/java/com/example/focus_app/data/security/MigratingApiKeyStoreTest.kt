package com.example.focus_app.data.security

import kotlinx.coroutines.async
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
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
}

private class RecordingApiKeyStore : ApiKeyStore {
    var value = ""
    var writeCount = 0

    override suspend fun read(): String = value
    override suspend fun write(value: String) {
        this.value = value
        writeCount++
    }

    override suspend fun clear() {
        value = ""
    }
}

private class RecordingLegacyApiKeySource(initial: String) : LegacyApiKeySource {
    var value = initial
    var clearCount = 0

    override suspend fun read(): String = value
    override suspend fun clear() {
        value = ""
        clearCount++
    }
}
