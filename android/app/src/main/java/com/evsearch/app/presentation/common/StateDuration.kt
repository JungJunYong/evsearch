package com.evsearch.app.presentation.common

import com.evsearch.app.data.model.ChargerStatus
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * 상태 지속 시간 표기.
 *
 * 서버는 UTC ISO(`...Z`)를, 앱은 로컬 스탬프(`yyyy-MM-dd'T'HH:mm:ss`)를 저장하므로 둘 다 받는다.
 * 사업자가 준 시각이 없으면 아무것도 표시하지 않는다(추정값을 만들지 않는다).
 */
object StateDuration {

    private val localFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss")

    fun epochMillis(iso: String?): Long? {
        if (iso.isNullOrBlank()) return null
        return try {
            if (iso.endsWith("Z") || iso.contains("+")) {
                Instant.parse(iso).toEpochMilli()
            } else {
                LocalDateTime.parse(iso.take(19), localFormatter)
                    .atZone(ZoneId.systemDefault())
                    .toInstant()
                    .toEpochMilli()
            }
        } catch (e: Exception) {
            null
        }
    }

    fun elapsedSeconds(iso: String?): Long? {
        val at = epochMillis(iso) ?: return null
        val sec = (System.currentTimeMillis() - at) / 1000
        return if (sec < 0) 0 else sec
    }

    /** 경과 시간 문구. 1분 미만은 "방금". */
    fun durationText(sec: Long): String = when {
        sec < 60 -> "방금"
        sec < 3600 -> "${sec / 60}분"
        sec < 86_400 -> {
            val h = sec / 3600
            val m = (sec % 3600) / 60
            if (m == 0L) "${h}시간" else "${h}시간 ${m}분"
        }
        else -> "${sec / 86_400}일"
    }

    /**
     * 상태별 지속 시간 문구.
     * - 충전 중: "충전 1시간 12분째"
     * - 충전 가능: "12분째 비어 있음"
     * - 그 외: "12분 전 상태"
     */
    fun label(status: ChargerStatus, stateSinceAt: String?, fallbackAt: String? = null): String? {
        val sec = elapsedSeconds(stateSinceAt) ?: elapsedSeconds(fallbackAt) ?: return null
        val d = durationText(sec)
        return when (status) {
            ChargerStatus.CHARGING -> if (sec < 60) "충전 시작" else "충전 ${d}째"
            ChargerStatus.AVAILABLE -> if (sec < 60) "방금 비었음" else "${d}째 비어 있음"
            else -> if (sec < 60) "방금 갱신" else "$d 전 상태"
        }
    }

    /** 위젯처럼 폭이 좁은 곳에서 쓰는 짧은 표기. "1시간 12분째" */
    fun shortLabel(stateSinceAt: String?, fallbackAt: String? = null): String? {
        val sec = elapsedSeconds(stateSinceAt) ?: elapsedSeconds(fallbackAt) ?: return null
        return if (sec < 60) "방금" else "${durationText(sec)}째"
    }
}
