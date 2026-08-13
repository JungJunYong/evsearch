package com.evsearch.app.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface SavedChargerDao {

    @Query("SELECT * FROM saved_chargers ORDER BY sortOrder ASC")
    fun getAllSavedChargersFlow(): Flow<List<SavedChargerEntity>>

    @Query("SELECT * FROM saved_chargers ORDER BY sortOrder ASC")
    suspend fun getAllSavedChargers(): List<SavedChargerEntity>

    @Query("SELECT * FROM saved_chargers WHERE key = :key LIMIT 1")
    suspend fun getSavedChargerByKey(key: String): SavedChargerEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdate(charger: SavedChargerEntity)

    @Query("DELETE FROM saved_chargers WHERE key = :key")
    suspend fun deleteByKey(key: String)

    @Query("DELETE FROM saved_chargers")
    suspend fun deleteAll()

    // 커스텀 별칭 업데이트
    @Query("UPDATE saved_chargers SET customName = :customName WHERE `key` = :key")
    suspend fun updateCustomName(key: String, customName: String?)
}
