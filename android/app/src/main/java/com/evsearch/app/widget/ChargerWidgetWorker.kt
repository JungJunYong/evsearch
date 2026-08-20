package com.evsearch.app.widget

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.evsearch.app.alert.AlertPrefs
import com.evsearch.app.data.api.BffApiService
import com.evsearch.app.data.local.AppDatabase
import com.evsearch.app.data.repository.ChargerRepository

/**
 * 위젯 상태 동기화 워커.
 *
 * 실행 경로는 둘뿐이다.
 * 1) 서버 푸시/수동 트리거 → 즉시 1회 (WidgetSyncScheduler.syncNow)
 * 2) 15분 고정 주기 (WidgetSyncScheduler.schedulePeriodic)
 */
class ChargerWidgetWorker(
    private val context: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(context, workerParams) {

    override suspend fun doWork(): Result {
        return try {
            val db = AppDatabase.getInstance(context)
            val apiService = BffApiService.create()
            val repository = ChargerRepository(apiService, db.savedChargerDao(), context)

            // 위젯 + 즐겨찾기 양쪽 목록의 최신 상태를 BFF에서 조회
            val result = repository.refreshTrackedChargersStatus()

            // Glance 위젯 UI 갱신
            WidgetUpdateHelper.updateAllWidgets(context)

            // 서버가 재배포/재시작되면 구독이 사라질 수 있으므로, 알림이 켜져 있으면
            // 주기 갱신 때마다 감시 대상을 다시 등록해 알림이 조용히 끊기는 것을 막는다.
            if (AlertPrefs.getEnabled(context)) {
                repository.syncAlertSubscription()
            }

            if (result.isSuccess) {
                AlertPrefs.setLastSyncAt(context, System.currentTimeMillis())
                Result.success()
            } else {
                Result.retry()
            }
        } catch (e: Exception) {
            e.printStackTrace()
            Result.retry()
        }
    }
}
