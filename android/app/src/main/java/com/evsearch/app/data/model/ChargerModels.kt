package com.evsearch.app.data.model

import com.google.gson.annotations.SerializedName

enum class ChargerStatus {
    AVAILABLE,
    CHARGING,
    MAINTENANCE,
    COMM_ERROR,
    SUSPENDED,
    RESERVED,
    UNCONFIRMED,
    UNKNOWN;

    companion object {
        fun fromString(statusStr: String?): ChargerStatus {
            return try {
                if (statusStr == null) UNKNOWN else valueOf(statusStr)
            } catch (e: Exception) {
                UNKNOWN
            }
        }
    }
}

data class Charger(
    val statId: String,
    val chgerId: String,
    val typeCode: String,
    val typeName: String,
    val outputKw: String?,
    val method: String?,
    val status: String,
    val statusCode: Int,
    val statusUpdatedAt: String?,
    val lastChargeStartedAt: String?,
    val lastChargeEndedAt: String?,
    val isDeleted: Boolean,
    val location: String? = null,
    val chargerCode: String? = null,
    // ChargEV enriched fields
    val price: String? = null,       // 단가 (원/kWh)
    val priceType: String? = null,   // 단가 구분 (1:회원가 2:비회원가)
    // KEPCO enriched fields
    val chargeTp: String? = null,    // "1" 완속, "2" 급속
    val cpStat: String? = null,      // "1" 가능, "2" 충전중, "3" 고장/점검, "4" 통신장애, "5" 미연결, "6" 종료, "7" 계획정지
    val cpTp: String? = null         // "1"~"8" 충전방식
) {
    val chargerStatusEnum: ChargerStatus
        get() = ChargerStatus.fromString(status)
    
    val displayStatusText: String
        get() = when (cpStat) {
            "1" -> "충전가능"
            "2" -> "충전중"
            "3" -> "고장/점검"
            "4" -> "통신장애"
            "5" -> "통신미연결"
            "6" -> "충전종료"
            "7" -> "계획정지"
            else -> when (status.uppercase()) {
                "AVAILABLE" -> "충전가능"
                "CHARGING" -> "충전중"
                "MAINTENANCE" -> "점검/고장"
                "COMM_ERROR" -> "통신장애"
                "SUSPENDED" -> "운영정지"
                "RESERVED" -> "예약중"
                else -> "상태확인중"
            }
        }

    val kepcoStatusText: String
        get() = displayStatusText
    
    val kepcoTypeText: String
        get() = when (cpTp) {
            "1" -> "B타입(5핀)"
            "2" -> "C타입(5핀)"
            "3" -> "BC타입(5핀)"
            "4" -> "BC타입(7핀)"
            "5" -> "C차데모"
            "6" -> "AC3상"
            "7" -> "DC콤보"
            "8" -> "DC차데모+DC콤보"
            else -> null
        } ?: typeName
}

data class StationSummary(
    val total: Int,
    val available: Int,
    val charging: Int,
    val maintenance: Int,
    val unknown: Int
)

data class ChargerStation(
    val statId: String,
    val name: String,
    val address: String,
    val addressDetail: String?,
    val lat: Double,
    val lng: Double,
    val useTime: String?,
    val operatorName: String,
    val operatorCall: String?,
    val parkingFree: Boolean?,
    val note: String?,
    val zcode: String?,
    val zscode: String?,
    val updatedAt: String,
    val chargers: List<Charger>,
    val summary: StationSummary,
    val distanceKm: Double? = null,
    // Data provenance (BFF 통합: 소스 구분은 표시용, 처리 경로는 단일)
    val dataSource: String? = null,  // chargev-nearby | chargev-search | keco | none
    val observedAt: String? = null,
    // KEPCO enriched fields
    val carType: String? = null,     // 지원차종 (콤마 구분)
    val rapidCnt: Int? = null,       // 급속충전기 대수
    val slowCnt: Int? = null         // 완속충전기 대수
) {
    val isChargeV: Boolean
        get() = dataSource?.startsWith("chargev") == true ||
            statId.startsWith("CHARGEV_") ||
            operatorName.contains("차지비") || operatorName.contains("ChargEV")
}

/** 지도 클러스터링용 경량 마커 (BFF /v1/stations/map, /chargev/poi). */
data class StationMarker(
    val statId: String,
    val name: String,
    val lat: Double,
    val lng: Double,
    val available: Boolean,
    val operatorName: String? = null,
    val source: String? = null       // "chargev" | "keco"
) {
    val isChargeV: Boolean
        get() = source == "chargev" || statId.startsWith("CHARGEV_")
}

data class BffMapResponse(
    val success: Boolean,
    val count: Int,
    val data: List<StationMarker>
)

data class BffStationsResponse(
    val success: Boolean,
    val count: Int,
    val page: Int,
    val data: List<ChargerStation>
)

data class BffStationDetailResponse(
    val success: Boolean,
    val data: ChargerStation
)

data class BatchStatusKey(
    val statId: String,
    val chgerId: String
)

data class BatchStatusRequest(
    val keys: List<BatchStatusKey>,
    /** 서버 캐시 허용 나이(ms). 위젯 실시간 갱신은 짧게 준다. null이면 서버 기본값. */
    val maxAgeMs: Long? = null
)

data class BatchStatusItem(
    val statId: String,
    val chgerId: String,
    val status: String,
    val statusCode: Int,
    val statusUpdatedAt: String?,
    /** 충전 시작 시각(사업자 제공). 없으면 statusUpdatedAt으로 대체해 경과 시간을 계산한다. */
    val lastChargeStartedAt: String? = null,
    val fetchedAt: String
)

data class BffBatchStatusResponse(
    val success: Boolean,
    val data: Map<String, BatchStatusItem>
)

// ── 빈자리 알림 구독 (FCM) ──
/** 서버 감시 대상 1건. notify=false 는 위젯 실시간 동기화용(푸시 알림 없음). */
data class AlertWatchKey(
    val statId: String,
    val chgerId: String,
    val notify: Boolean
)

data class AlertSubscribeRequest(
    val token: String,
    val keys: List<AlertWatchKey>,
    /** 감시 시작 분(0~1439). startMin == endMin 이면 종일. */
    val startMin: Int,
    val endMin: Int,
    /** 서버 조회 주기(초). 최소 30초. */
    val intervalSec: Int,
    val enabled: Boolean,
    /** 상태가 바뀌면 데이터 전용 푸시로 위젯을 즉시 갱신할지 여부. */
    val silentSync: Boolean = true
)

data class AlertUnsubscribeRequest(
    val token: String
)

data class SimpleResponse(
    val success: Boolean
)
