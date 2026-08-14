package com.evsearch.app.alert

import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage

/**
 * FCM 수신 서비스.
 * - onNewToken: 토큰 로컬 저장 (구독 등록은 앱에서 토큰과 함께 BFF로 전송)
 * - onMessageReceived: 포그라운드 수신 시 알림 표시 (백그라운드는 시스템이 자동 표시)
 */
class EvFcmService : FirebaseMessagingService() {

    override fun onNewToken(token: String) {
        AlertPrefs.setToken(applicationContext, token)
    }

    override fun onMessageReceived(message: RemoteMessage) {
        val title = message.notification?.title ?: message.data["title"] ?: "충전기 빈자리 알림"
        val body = message.notification?.body ?: message.data["body"] ?: "빈자리가 생겼습니다."
        val statId = message.data["statId"]
        val notifId = (statId ?: System.currentTimeMillis().toString()).hashCode()
        AlertNotifications.show(applicationContext, title, body, statId, notifId)
    }
}
