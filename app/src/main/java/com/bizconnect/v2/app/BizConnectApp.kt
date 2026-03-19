package com.bizconnect.v2.app

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class BizConnectApp : Application() {
    override fun onCreate() {
        super.onCreate()
        createNotificationChannels()
    }

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channels = listOf(
                NotificationChannel(
                    "messages",
                    "메시지",
                    NotificationManager.IMPORTANCE_HIGH
                ).apply {
                    description = "새로운 메시지 알림"
                    enableLights(true)
                    enableVibration(true)
                },
                NotificationChannel(
                    "sms_approval",
                    "문자 발송 승인",
                    NotificationManager.IMPORTANCE_HIGH
                ).apply {
                    description = "문자 발송 승인 요청"
                    enableLights(true)
                    enableVibration(true)
                },
                NotificationChannel(
                    "sms_info",
                    "발송 알림",
                    NotificationManager.IMPORTANCE_DEFAULT
                ).apply {
                    description = "문자 발송 결과 알림"
                },
                NotificationChannel(
                    "callback",
                    "콜백 서비스",
                    NotificationManager.IMPORTANCE_LOW
                ).apply {
                    description = "자동 통화 콜백 서비스"
                },
                NotificationChannel(
                    "service",
                    "백그라운드 서비스",
                    NotificationManager.IMPORTANCE_MIN
                ).apply {
                    description = "백그라운드 동기화 서비스"
                },
                NotificationChannel(
                    "sync_service",
                    "동기화 서비스",
                    NotificationManager.IMPORTANCE_MIN
                ).apply {
                    description = "데이터 동기화 서비스"
                },
                NotificationChannel(
                    "callback_channel",
                    "콜백 채널",
                    NotificationManager.IMPORTANCE_LOW
                ).apply {
                    description = "자동 콜백 서비스"
                },
                NotificationChannel(
                    "sms_send",
                    "문자 발송",
                    NotificationManager.IMPORTANCE_LOW
                ).apply {
                    description = "문자 발송 서비스"
                }
            )
            val manager = getSystemService(NotificationManager::class.java)
            channels.forEach { manager.createNotificationChannel(it) }
        }
    }
}
