package com.bizconnect.v2.service

import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.provider.CallLog
import android.util.Log
import androidx.core.app.NotificationCompat
import com.bizconnect.v2.R
import com.bizconnect.v2.domain.engine.CallbackEngine
import com.bizconnect.v2.domain.engine.CallbackEventType
import com.bizconnect.v2.domain.engine.CallDetector
import com.bizconnect.v2.util.NotificationUtil
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Short-lived foreground service for callback SMS processing.
 *
 * Lifecycle:
 * 1. Started by CallStateReceiver when IDLE detected
 * 2. Shows brief foreground notification
 * 3. Waits 3 seconds for CallLog to be written
 * 4. Queries CallLog for latest call entry
 * 5. Determines call type (ended/missed/busy)
 * 6. Sends callback SMS via CallbackEngine
 * 7. Stops self immediately
 *
 * Battery impact: runs only 3-5 seconds per call event.
 */
@AndroidEntryPoint
class CallbackService : Service() {
    @Inject
    lateinit var callbackEngine: CallbackEngine

    @Inject
    lateinit var notificationUtil: NotificationUtil

    private val serviceScope = CoroutineScope(Dispatchers.IO + Job())

    override fun onBind(intent: Intent): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        intent ?: return START_NOT_STICKY

        val previousState = intent.getStringExtra("previous_state") ?: ""
        Log.d(TAG, "CallbackService started (previousState=$previousState)")

        // Show foreground notification immediately (required for foreground service)
        showForegroundNotification()

        serviceScope.launch {
            try {
                // Wait for CallLog to be written by system (typically 1-3 seconds)
                delay(3000)

                // Query latest call from CallLog
                val latestCall = queryLatestCall()

                if (latestCall != null) {
                    Log.d(TAG, "Latest call: ${latestCall.number}, type=${latestCall.typeName}, duration=${latestCall.duration}s")

                    // Map CallLog type to CallbackEventType
                    val eventType = when (latestCall.type) {
                        CallLog.Calls.MISSED_TYPE -> CallbackEventType.MISSED
                        CallLog.Calls.REJECTED_TYPE -> CallbackEventType.BUSY
                        CallLog.Calls.INCOMING_TYPE,
                        CallLog.Calls.OUTGOING_TYPE -> CallbackEventType.ENDED
                        else -> null
                    }

                    if (eventType != null && latestCall.number.isNotBlank()) {
                        Log.d(TAG, "Processing callback: $eventType for ${latestCall.number}")

                        val callEvent = CallDetector.CallEvent(
                            type = eventType,
                            phoneNumber = latestCall.number,
                            duration = latestCall.duration * 1000L, // seconds → ms
                            timestamp = latestCall.date
                        )

                        val result = callbackEngine.processCallback(callEvent)

                        if (result.success) {
                            Log.d(TAG, "Callback sent: ${result.eventType} → ${result.phoneNumber}")
                        } else {
                            Log.d(TAG, "Callback skipped/failed: ${result.error ?: result.message}")
                        }
                    } else {
                        Log.d(TAG, "No callback needed for call type: ${latestCall.typeName}")
                    }
                } else {
                    Log.w(TAG, "Could not read latest call from CallLog")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error processing callback", e)
            } finally {
                // Always stop self
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf(startId)
            }
        }

        return START_NOT_STICKY
    }

    /**
     * Query the most recent call from the system CallLog.
     */
    private fun queryLatestCall(): CallInfo? {
        return try {
            val cursor = contentResolver.query(
                CallLog.Calls.CONTENT_URI,
                arrayOf(
                    CallLog.Calls.NUMBER,
                    CallLog.Calls.TYPE,
                    CallLog.Calls.DURATION,
                    CallLog.Calls.DATE,
                    CallLog.Calls.CACHED_NAME
                ),
                null, null,
                "${CallLog.Calls.DATE} DESC"
            )

            cursor?.use {
                if (it.moveToFirst()) {
                    val number = it.getString(0) ?: ""
                    val type = it.getInt(1)
                    val duration = it.getLong(2)
                    val date = it.getLong(3)
                    val name = it.getString(4)

                    // Only process calls from the last 30 seconds
                    val timeSinceCall = System.currentTimeMillis() - date
                    if (timeSinceCall > 30_000) {
                        Log.d(TAG, "Latest call is too old (${timeSinceCall/1000}s ago), skipping")
                        return null
                    }

                    CallInfo(
                        number = number,
                        type = type,
                        duration = duration,
                        date = date,
                        name = name,
                        typeName = when (type) {
                            CallLog.Calls.INCOMING_TYPE -> "INCOMING"
                            CallLog.Calls.OUTGOING_TYPE -> "OUTGOING"
                            CallLog.Calls.MISSED_TYPE -> "MISSED"
                            CallLog.Calls.REJECTED_TYPE -> "REJECTED"
                            else -> "UNKNOWN($type)"
                        }
                    )
                } else null
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error querying CallLog", e)
            null
        }
    }

    private fun showForegroundNotification() {
        val notification = NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
            .setContentTitle("콜백 처리 중")
            .setContentText("통화 종료 후 자동 문자를 확인합니다")
            .setSmallIcon(R.drawable.ic_notification)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .setSilent(true)
            .build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_PHONE_CALL)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        try { stopForeground(STOP_FOREGROUND_REMOVE) } catch (_: Exception) {}
    }

    private data class CallInfo(
        val number: String,
        val type: Int,
        val duration: Long, // seconds
        val date: Long,
        val name: String?,
        val typeName: String
    )

    companion object {
        const val TAG = "CallbackService"
        const val NOTIFICATION_ID = 102
        const val NOTIFICATION_CHANNEL_ID = "callback_channel"
    }
}
