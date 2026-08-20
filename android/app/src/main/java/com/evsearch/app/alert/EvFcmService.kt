package com.evsearch.app.alert

import com.evsearch.app.data.api.BffApiService
import com.evsearch.app.data.local.AppDatabase
import com.evsearch.app.data.repository.ChargerRepository
import com.evsearch.app.widget.WidgetSyncScheduler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage

/**
 * FCM 수신 서비스.
 * - onNewToken: 토큰 로컬 저장 (구독 등록은 앱에서 토큰과 함께 BFF로 전송)
 * - onMessageReceived:
 *     type=vacancy     → 빈자리 알림 표시 + 위젯 즉시 동기화
 *     type=widget_sync → 알림 없이 위젯만 즉시 동기화 (실시간 갱신 경로)
 */
class EvFcmService : FirebaseMessagingService() {

    override fun onNewToken(token: String) {
        AlertPrefs.setToken(applicationContext, token)
        // 토큰이 회전하면 서버의 감시 대상도 새 토큰으로 다시 등록해야 알림이 끊기지 않는다.
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val db = AppDatabase.getInstance(applicationContext)
                ChargerRepository(BffApiService.create(), db.savedChargerDao(), applicationContext)
                    .syncAlertSubscription()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    override fun onMessageReceived(message: RemoteMessage) {
        AlertPrefs.setLastPushAt(applicationContext, System.currentTimeMillis())

        val type = message.data["type"] ?: "vacancy"

        // 어떤 푸시든 상태가 변했다는 신호 → 위젯을 즉시 다시 읽어온다.
        WidgetSyncScheduler.syncNow(applicationContext)

        if (type == "widget_sync") return

        val title = message.notification?.title ?: message.data["title"] ?: "충전기 빈자리 알림"
        val body = message.notification?.body ?: message.data["body"] ?: "빈자리가 생겼습니다."
        val statId = message.data["statId"]
        val notifId = (statId ?: System.currentTimeMillis().toString()).hashCode()
        AlertNotifications.show(applicationContext, title, body, statId, notifId)
    }
}
