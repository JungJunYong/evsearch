package com.evsearch.app.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "saved_chargers")
data class SavedChargerEntity(
    @PrimaryKey val key: String, // "statId:chgerId"
    val statId: String,
    val chgerId: String,
    val stationName: String,
    val chargerTypeName: String,
    val outputKw: String?,
    val status: String,
    val statusCode: Int,
    val statusUpdatedAt: String?,
    val lastFetchedAt: String,
    val sortOrder: Int = 0,
    // 사용자가 임의로 지정한 커스텀 별칭 (null이면 기본 stationName 표시)
    val customName: String? = null
)
