package com.evsearch.app.widget

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.OutOfQuotaPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit

/**
 * 위젯 갱신 스케줄러.
 *
 * 실시간성은 전부 서버가 만든다. BFF가 감시 대상 상태를 조회해 **이전과 달라졌을 때만**
 * 데이터 전용 FCM 푸시를 보내고, 앱은 그 신호로 즉시 동기화한다([syncNow]).
 * 그 밖의 정기 갱신은 15분 주기 하나로 고정한다(푸시가 막혔을 때의 보조 수단).
 */
object WidgetSyncScheduler {

    private const val WORK_PERIODIC = "ChargerWidgetUpdateWork"
    private const val WORK_IMMEDIATE = "ChargerWidgetImmediateSync"

    /** 고정 보조 갱신 주기(분). WorkManager 최소 주기와 동일. */
    const val PERIODIC_INTERVAL_MIN = 15L

    private val networkConstraints = Constraints.Builder()
        .setRequiredNetworkType(NetworkType.CONNECTED)
        .build()

    /** 앱 시작 / 위젯 추가 시: 즉시 1회 + 15분 주기 등록. */
    fun scheduleAll(context: Context) {
        syncNow(context)
        schedulePeriodic(context)
    }

    /** 15분 고정 주기 갱신. */
    fun schedulePeriodic(context: Context) {
        val periodic = PeriodicWorkRequestBuilder<ChargerWidgetWorker>(
            PERIODIC_INTERVAL_MIN, TimeUnit.MINUTES
        )
            .setConstraints(networkConstraints)
            .build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            WORK_PERIODIC,
            ExistingPeriodicWorkPolicy.UPDATE,
            periodic
        )
    }

    /** 즉시 동기화(서버 푸시, 위젯 새로고침 탭, 앱 진입 등). */
    fun syncNow(context: Context) {
        val request = OneTimeWorkRequestBuilder<ChargerWidgetWorker>()
            .setConstraints(networkConstraints)
            .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
            .build()
        WorkManager.getInstance(context)
            .enqueueUniqueWork(WORK_IMMEDIATE, ExistingWorkPolicy.REPLACE, request)
    }
}
