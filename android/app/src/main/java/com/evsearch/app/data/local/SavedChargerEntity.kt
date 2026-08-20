package com.evsearch.app.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * 앱이 추적하는 단말기 1건.
 *
 * 위젯 목록과 즐겨찾기 목록은 서로 독립적인 두 개의 목록이며, 하나의 행이 두 목록에
 * 동시에 속할 수 있다(플래그로 구분). 두 플래그가 모두 false인 행은 보관하지 않는다.
 * - isWidget:   홈 화면 위젯에 표시할 대상
 * - isFavorite: 즐겨찾기(빈자리 알림 후보) 대상
 * - alertEnabled: 즐겨찾기 중 실제로 푸시 알림을 받을지 여부
 */
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
    val customName: String? = null,
    /** 현재 상태로 바뀐 시각(충전 시작 시각). 경과 시간 표기에 쓴다. */
    val stateSinceAt: String? = null,
    val isWidget: Boolean = false,
    val isFavorite: Boolean = false,
    val alertEnabled: Boolean = true
)
