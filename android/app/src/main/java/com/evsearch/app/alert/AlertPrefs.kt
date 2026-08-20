package com.evsearch.app.alert

import android.content.Context

/**
 * 빈자리 알림 설정(로컬 저장).
 *
 * 위젯 갱신 주기는 사용자 설정이 아니다: 서버가 변화를 감지했을 때 보내는 푸시로 즉시
 * 갱신하고, 그 밖에는 15분 고정 주기(WidgetSyncScheduler)로만 돈다.
 * 시간대는 하루 기준 '분'(0~1439)으로 저장하며, start == end 는 '종일'을 뜻한다.
 */
object AlertPrefs {
    private const val PREFS = "vacancy_alert_prefs"
    private const val K_ENABLED = "enabled"
    private const val K_START = "start_min"
    private const val K_END = "end_min"
    private const val K_INTERVAL = "interval_sec"
    private const val K_TOKEN = "fcm_token"
    private const val K_LAST_SYNC = "last_sync_at"
    private const val K_LAST_PUSH = "last_push_at"

    private fun sp(context: Context) =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun getEnabled(context: Context) = sp(context).getBoolean(K_ENABLED, false)
    fun setEnabled(context: Context, v: Boolean) = sp(context).edit().putBoolean(K_ENABLED, v).apply()

    // 기본 감시 시간대: 오후 6시~11시 (18:00~23:00)
    fun getStartMin(context: Context) = sp(context).getInt(K_START, 18 * 60)
    fun setStartMin(context: Context, v: Int) = sp(context).edit().putInt(K_START, v).apply()
    fun getEndMin(context: Context) = sp(context).getInt(K_END, 23 * 60)
    fun setEndMin(context: Context, v: Int) = sp(context).edit().putInt(K_END, v).apply()

    /** start == end 이면 종일 감시. */
    fun isAllDay(context: Context) = getStartMin(context) == getEndMin(context)
    fun setAllDay(context: Context) {
        sp(context).edit().putInt(K_START, 0).putInt(K_END, 0).apply()
    }

    /**
     * 하위 호환용 값. 실제 조회 간격은 서버가 30~60초 무작위로 정한다(과호출 방지).
     */
    fun getIntervalSec(context: Context) = sp(context).getInt(K_INTERVAL, 60)

    fun getToken(context: Context): String? = sp(context).getString(K_TOKEN, null)
    fun setToken(context: Context, token: String) = sp(context).edit().putString(K_TOKEN, token).apply()

    /** 마지막 위젯 동기화 시각(epoch ms). */
    fun getLastSyncAt(context: Context) = sp(context).getLong(K_LAST_SYNC, 0L)
    fun setLastSyncAt(context: Context, v: Long) = sp(context).edit().putLong(K_LAST_SYNC, v).apply()

    /** 마지막 서버 푸시 수신 시각(epoch ms). 실시간 경로가 살아있는지 표시용. */
    fun getLastPushAt(context: Context) = sp(context).getLong(K_LAST_PUSH, 0L)
    fun setLastPushAt(context: Context, v: Long) = sp(context).edit().putLong(K_LAST_PUSH, v).apply()
}
