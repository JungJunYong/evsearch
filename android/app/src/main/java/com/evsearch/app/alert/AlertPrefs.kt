package com.evsearch.app.alert

import android.content.Context

/** 빈자리 알림 설정(로컬 저장). 시간대는 하루 기준 '분'(0~1439)으로 저장. */
object AlertPrefs {
    private const val PREFS = "vacancy_alert_prefs"
    private const val K_ENABLED = "enabled"
    private const val K_START = "start_min"
    private const val K_END = "end_min"
    private const val K_INTERVAL = "interval_sec"
    private const val K_TOKEN = "fcm_token"

    private fun sp(context: Context) =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    var Context.alertEnabled: Boolean
        get() = sp(this).getBoolean(K_ENABLED, false)
        set(v) { sp(this).edit().putBoolean(K_ENABLED, v).apply() }

    fun getEnabled(context: Context) = sp(context).getBoolean(K_ENABLED, false)
    fun setEnabled(context: Context, v: Boolean) = sp(context).edit().putBoolean(K_ENABLED, v).apply()

    // 기본 감시 시간대: 오후 6시~11시 (18:00~23:00)
    fun getStartMin(context: Context) = sp(context).getInt(K_START, 18 * 60)
    fun setStartMin(context: Context, v: Int) = sp(context).edit().putInt(K_START, v).apply()
    fun getEndMin(context: Context) = sp(context).getInt(K_END, 23 * 60)
    fun setEndMin(context: Context, v: Int) = sp(context).edit().putInt(K_END, v).apply()

    fun getIntervalSec(context: Context) = sp(context).getInt(K_INTERVAL, 90)
    fun setIntervalSec(context: Context, v: Int) = sp(context).edit().putInt(K_INTERVAL, v).apply()

    fun getToken(context: Context): String? = sp(context).getString(K_TOKEN, null)
    fun setToken(context: Context, token: String) = sp(context).edit().putString(K_TOKEN, token).apply()
}
