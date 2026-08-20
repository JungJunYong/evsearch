package com.evsearch.app.widget

import android.content.Context
import androidx.work.Constraints
import androidx.work.Data
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.OutOfQuotaPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.evsearch.app.alert.AlertPrefs
import java.util.concurrent.TimeUnit

/**
 * 위젯 실시간 갱신 스케줄러.
 *
 * WorkManager의 최소 주기(15분)보다 짧은 갱신을 지원하려고 OneTimeWork 체인을 사용한다.
 * 각 회차의 워커가 끝날 때 다음 회차를 사용자 설정 주기로 예약하고, 별도의 15분
 * PeriodicWork를 안전망으로 함께 등록한다. 서버가 상태 변화를 감지하면 FCM 데이터
 * 푸시로 syncNow()가 즉시 호출되므로 실제 반영은 대개 수 초 내에 이뤄진다.
 */
object WidgetSyncScheduler {

    private const val WORK_CHAIN = "ChargerWidgetChainSync"
    private const val WORK_PERIODIC = "ChargerWidgetUpdateWork"
    private const val WORK_IMMEDIATE = "ChargerWidgetImmediateSync"

    private val networkConstraints = Constraints.Builder()
        .setRequiredNetworkType(NetworkType.CONNECTED)
        .build()

    /** 앱 시작 / 위젯 추가 시 호출: 즉시 1회 + 체인 + 안전망 주기 작업 등록. */
    fun scheduleAll(context: Context) {
        syncNow(context)
        scheduleChain(context)
        schedulePeriodicFallback(context)
    }

    /** 사용자가 설정한 주기로 다음 회차 동기화를 예약한다. */
    fun scheduleChain(context: Context) {
        val delaySec = AlertPrefs.getWidgetIntervalSec(context).coerceIn(60, 3600).toLong()
        val request = OneTimeWorkRequestBuilder<ChargerWidgetWorker>()
            .setInitialDelay(delaySec, TimeUnit.SECONDS)
            .setConstraints(networkConstraints)
            .setInputData(Data.Builder().putBoolean(ChargerWidgetWorker.KEY_CHAIN, true).build())
            .build()
        WorkManager.getInstance(context)
            .enqueueUniqueWork(WORK_CHAIN, ExistingWorkPolicy.REPLACE, request)
    }

    /** 체인이 끊겼을 때를 대비한 15분 주기 안전망. */
    private fun schedulePeriodicFallback(context: Context) {
        val periodic = PeriodicWorkRequestBuilder<ChargerWidgetWorker>(15, TimeUnit.MINUTES)
            .setConstraints(networkConstraints)
            .build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            WORK_PERIODIC,
            ExistingPeriodicWorkPolicy.UPDATE,
            periodic
        )
    }

    /** 즉시 동기화(FCM 데이터 푸시, 위젯 새로고침 탭, 앱 진입 등). */
    fun syncNow(context: Context) {
        val request = OneTimeWorkRequestBuilder<ChargerWidgetWorker>()
            .setConstraints(networkConstraints)
            .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
            .build()
        WorkManager.getInstance(context)
            .enqueueUniqueWork(WORK_IMMEDIATE, ExistingWorkPolicy.REPLACE, request)
    }

    /** 사용자가 갱신 주기를 바꿨을 때: 체인을 새 주기로 다시 건다. */
    fun onIntervalChanged(context: Context) {
        scheduleChain(context)
        schedulePeriodicFallback(context)
    }
}
