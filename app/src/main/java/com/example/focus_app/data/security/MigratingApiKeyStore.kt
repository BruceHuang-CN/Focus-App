package com.example.focus_app.data.security

import com.example.focus_app.data.local.dao.SettingsDao
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject

interface LegacyApiKeySource {
    suspend fun read(): String
    suspend fun clear()
}

class RoomLegacyApiKeySource @Inject constructor(
    private val settingsDao: SettingsDao
) : LegacyApiKeySource {
    override suspend fun read(): String = settingsDao.getSettingsOnce()?.legacyApiKey.orEmpty()

    override suspend fun clear() {
        settingsDao.clearLegacyApiKey()
    }
}

class MigratingApiKeyStore(
    private val encryptedStore: ApiKeyStore,
    private val legacySource: LegacyApiKeySource
) : ApiKeyStore {
    private val mutex = Mutex()
    private var migrationChecked = false

    override suspend fun read(): String = mutex.withLock {
        val encrypted = encryptedStore.read()
        if (migrationChecked) return@withLock encrypted

        val legacy = legacySource.read()
        if (legacy.isNotBlank()) {
            if (encrypted.isBlank()) encryptedStore.write(legacy)
            legacySource.clear()
        }
        migrationChecked = true
        encrypted.ifBlank { legacy }
    }

    override suspend fun write(value: String) = mutex.withLock {
        if (value.isBlank()) {
            clearLocked()
            return@withLock
        }
        encryptedStore.write(value)
        legacySource.clear()
        migrationChecked = true
    }

    override suspend fun clear() = mutex.withLock {
        clearLocked()
    }

    private suspend fun clearLocked() {
        legacySource.clear()
        encryptedStore.clear()
        migrationChecked = true
    }
}
