package com.example.focus_app.data.security

interface ApiKeyStore {
    suspend fun read(): String
    suspend fun write(value: String)
    suspend fun clear()
}
