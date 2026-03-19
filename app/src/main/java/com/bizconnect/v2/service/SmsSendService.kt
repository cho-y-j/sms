package com.bizconnect.v2.service

import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.telephony.SmsManager
import android.util.Log
import androidx.core.app.NotificationCompat
import com.bizconnect.v2.R
import com.bizconnect.v2.data.local.db.dao.ConversationDao
import com.bizconnect.v2.data.local.db.dao.MessageDao
import com.bizconnect.v2.util.NotificationUtil
import com.bizconnect.v2.util.PhoneNumberUtil
import com.bizconnect.v2.util.SmsSender
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class SmsSendService : Service() {
    @Inject
    lateinit var smsSender: SmsSender

    @Inject
    lateinit var phoneNumberUtil: PhoneNumberUtil

    @Inject
    lateinit var notificationUtil: NotificationUtil

    override fun onBind(intent: Intent): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        intent ?: return START_STICKY

        showForegroundNotification()

        val recipients = intent.getStringArrayExtra("recipients") ?: emptyArray()
        val messageText = intent.getStringExtra("message") ?: ""
        val messageType = intent.getStringExtra("type") ?: "SMS" // SMS, LMS, MMS

        Log.d(TAG, "SmsSendService: Sending $messageType to ${recipients.size} recipients")

        CoroutineScope(Dispatchers.IO).launch {
            var successCount = 0
            var failureCount = 0

            recipients.forEach { number ->
                try {
                    val phoneNumber = phoneNumberUtil.normalizeNumber(number)
                    val threadId = smsSender.sendSms(phoneNumber, messageText)
                    if (threadId >= 0) {
                        successCount++
                        Log.d(TAG, "SMS sent to $phoneNumber")
                    } else {
                        failureCount++
                        Log.e(TAG, "Failed to send SMS to $phoneNumber")
                    }
                } catch (e: Exception) {
                    failureCount++
                    Log.e(TAG, "Error sending SMS to $number", e)
                }
            }

            notificationUtil.showSendResultNotification(successCount, failureCount)
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf(startId)
        }

        return START_STICKY
    }

    private fun showForegroundNotification() {
        val notification = NotificationCompat.Builder(this, "service")
            .setContentTitle("메시지 발송 중")
            .setContentText("메시지를 발송하고 있습니다...")
            .setSmallIcon(R.drawable.ic_notification)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    companion object {
        const val TAG = "SmsSendService"
        const val NOTIFICATION_ID = 101
    }
}
