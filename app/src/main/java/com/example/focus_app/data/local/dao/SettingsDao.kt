package com.example.focus_app.data.local.dao

import androidx.room.*
import com.example.focus_app.data.local.entity.SettingsEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface SettingsDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insertOrUpdate(settings: SettingsEntity)
    @Query("SELECT * FROM settings LIMIT 1") fun getSettings(): Flow<SettingsEntity?>
    @Query("SELECT * FROM settings LIMIT 1") suspend fun getSettingsOnce(): SettingsEntity?
    @Query("UPDATE settings SET apiKey = '' WHERE apiKey != ''")
    suspend fun clearLegacyApiKey()
}
