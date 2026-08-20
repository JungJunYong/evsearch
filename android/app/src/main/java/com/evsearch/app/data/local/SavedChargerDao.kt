package com.evsearch.app.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface SavedChargerDao {

    // ── 위젯 목록 ────────────────────────────────────────────────────────────
    @Query("SELECT * FROM saved_chargers WHERE isWidget = 1 ORDER BY sortOrder ASC, key ASC")
    fun getWidgetChargersFlow(): Flow<List<SavedChargerEntity>>

    @Query("SELECT * FROM saved_chargers WHERE isWidget = 1 ORDER BY sortOrder ASC, key ASC")
    suspend fun getWidgetChargers(): List<SavedChargerEntity>

    // ── 즐겨찾기 목록 ────────────────────────────────────────────────────────
    @Query("SELECT * FROM saved_chargers WHERE isFavorite = 1 ORDER BY sortOrder ASC, key ASC")
    fun getFavoriteChargersFlow(): Flow<List<SavedChargerEntity>>

    @Query("SELECT * FROM saved_chargers WHERE isFavorite = 1 ORDER BY sortOrder ASC, key ASC")
    suspend fun getFavoriteChargers(): List<SavedChargerEntity>

    // ── 두 목록 합집합 (상태 동기화 대상) ────────────────────────────────────
    @Query("SELECT * FROM saved_chargers WHERE isWidget = 1 OR isFavorite = 1 ORDER BY sortOrder ASC, key ASC")
    fun getTrackedChargersFlow(): Flow<List<SavedChargerEntity>>

    @Query("SELECT * FROM saved_chargers WHERE isWidget = 1 OR isFavorite = 1 ORDER BY sortOrder ASC, key ASC")
    suspend fun getTrackedChargers(): List<SavedChargerEntity>

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

    // ── 목록 소속 플래그 ─────────────────────────────────────────────────────
    @Query("UPDATE saved_chargers SET isWidget = :isWidget WHERE `key` = :key")
    suspend fun setWidgetFlag(key: String, isWidget: Boolean)

    @Query("UPDATE saved_chargers SET isFavorite = :isFavorite WHERE `key` = :key")
    suspend fun setFavoriteFlag(key: String, isFavorite: Boolean)

    @Query("UPDATE saved_chargers SET alertEnabled = :enabled WHERE `key` = :key")
    suspend fun setAlertEnabled(key: String, enabled: Boolean)

    /** 두 목록 어디에도 속하지 않게 된 행 정리. */
    @Query("DELETE FROM saved_chargers WHERE isWidget = 0 AND isFavorite = 0")
    suspend fun deleteOrphans()

    @Query("UPDATE saved_chargers SET status = :status, statusCode = :statusCode, statusUpdatedAt = :statusUpdatedAt, stateSinceAt = :stateSinceAt, lastFetchedAt = :lastFetchedAt WHERE `key` = :key")
    suspend fun updateStatus(
        key: String,
        status: String,
        statusCode: Int,
        statusUpdatedAt: String?,
        stateSinceAt: String?,
        lastFetchedAt: String
    )
}
