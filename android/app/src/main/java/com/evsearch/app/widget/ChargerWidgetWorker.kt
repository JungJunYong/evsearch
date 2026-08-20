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
 * 세 가지 경로로 실행된다.
 * 1) 체인(chain=true): 사용자가 설정한 주기로 스스로 다음 회차를 예약한다(15분 미만 주기 지원).
 * 2) 주기(15분 PeriodicWork): 체인이 도즈/재부팅으로 끊겼을 때의 안전망.
 * 3) 즉시(FCM 데이터 푸시 / 앱 진입 / 위젯 새로고침 탭).
 */
class ChargerWidgetWorker(
    private val context: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(context, workerParams) {

    override suspend fun doWork(): Result {
        val isChain = inputData.getBoolean(KEY_CHAIN, false)
        return try {
            val db = AppDatabase.getInstance(context)
            val apiService = BffApiService.create()
            val repository = ChargerRepository(apiService, db.savedChargerDao(), context)

            // 위젯 + 즐겨찾기 양쪽 목록의 최신 상태를 BFF에서 조회
            val result = repository.refreshTrackedChargersStatus()

            // Glance 위젯 UI 갱신
            WidgetUpdateHelper.updateAllWidgets(context)

            if (result.isSuccess) {
                AlertPrefs.setLastSyncAt(context, System.currentTimeMillis())
            }

            if (isChain) {
                // 실패해도 다음 회차를 예약해 체인이 끊기지 않게 한다.
                WidgetSyncScheduler.scheduleChain(context)
                Result.success()
            } else if (result.isSuccess) {
                Result.success()
            } else {
                Result.retry()
            }
        } catch (e: Exception) {
            e.printStackTrace()
            if (isChain) {
                WidgetSyncScheduler.scheduleChain(context)
                Result.success()
            } else {
                Result.retry()
            }
        }
    }

    companion object {
        const val KEY_CHAIN = "chain"
    }
}
