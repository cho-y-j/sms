package com.bizconnect.v2.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.CallLog
import android.telephony.TelephonyManager
import android.util.Log
import com.bizconnect.v2.domain.engine.CallbackEngine
import com.bizconnect.v2.domain.engine.CallbackEventType
import com.bizconnect.v2.domain.engine.CallDetector
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Manifest-registered BroadcastReceiver for PHONE_STATE.
 *
 * PHONE_STATE is an exempted implicit broadcast — delivered even when app is killed.
 *
 * Uses goAsync() + coroutine (same pattern as SmsReceiver) to avoid
 * ForegroundServiceStartNotAllowedException on Android 12+.
 *
 * Flow:
 * 1. PHONE_STATE broadcast received
 * 2. On IDLE (call ended): goAsync() + launch coroutine
 * 3. Wait 3 seconds for CallLog write
 * 4. Query CallLog for latest call
 * 5. Send callback via CallbackEngine
 * 6. finish() the pending result
 */
@AndroidEntryPoint
class CallStateReceiver : BroadcastReceiver() {

    @Inject
    lateinit var callbackEngine: CallbackEngine

    companion object {
        private const val TAG = "CallStateReceiver"
        private var previousState = TelephonyManager.EXTRA_STATE_IDLE
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != TelephonyManager.ACTION_PHONE_STATE_CHANGED) return

        val state = intent.getStringExtra(TelephonyManager.EXTRA_STATE) ?: return
        Log.d(TAG, "Phone state: $state (previous: $previousState)")

        // Only act when transitioning TO IDLE (= call ended)
        if (state == TelephonyManager.EXTRA_STATE_IDLE
            && previousState != TelephonyManager.EXTRA_STATE_IDLE) {

            Log.d(TAG, "Call ended (was: $previousState → IDLE). Processing callback.")

            val wasPreviouslyRinging = previousState == TelephonyManager.EXTRA_STATE_RINGING
            val pendingResult = goAsync()

            CoroutineScope(Dispatchers.IO).launch {
                try {
                    // Wait for CallLog to be written by system
                    delay(3000)

                    // Query latest call from CallLog
                    val latestCall = queryLatestCall(context)

                    if (latestCall != null) {
                        Log.d(TAG, "Latest call: ${latestCall.number}, type=${latestCall.typeName}, duration=${latestCall.duration}s")

                        val eventType = when (latestCall.type) {
                            CallLog.Calls.MISSED_TYPE -> CallbackEventType.MISSED
                            CallLog.Calls.REJECTED_TYPE -> CallbackEventType.BUSY
                            CallLog.Calls.INCOMING_TYPE -> CallbackEventType.ENDED
                            CallLog.Calls.OUTGOING_TYPE -> CallbackEventType.OUTGOING
                            else -> null
                        }

                        if (eventType != null && latestCall.number.isNotBlank()) {
                            Log.d(TAG, "Triggering callback: $eventType for ${latestCall.number}")

                            val callEvent = CallDetector.CallEvent(
                                type = eventType,
                                phoneNumber = latestCall.number,
                                duration = latestCall.duration * 1000L,
                                timestamp = latestCall.date
                            )

                            val result = callbackEngine.processCallback(callEvent)
                            Log.d(TAG, "Callback result: success=${result.success}, error=${result.error}")
                        } else {
                            Log.d(TAG, "No callback needed for call type: ${latestCall.typeName}")
                        }
                    } else {
                        // CallLog 읽기 실패 시 (권한 없음 등) — 상태 기반 폴백
                        Log.w(TAG, "CallLog query failed, using state-based detection")
                        val incomingNumber = intent.getStringExtra(TelephonyManager.EXTRA_INCOMING_NUMBER)
                        if (incomingNumber != null) {
                            val eventType = if (wasPreviouslyRinging) CallbackEventType.MISSED else CallbackEventType.ENDED
                            val callEvent = CallDetector.CallEvent(
                                type = eventType,
                                phoneNumber = incomingNumber,
                                duration = 0L,
                                timestamp = System.currentTimeMillis()
                            )
                            callbackEngine.processCallback(callEvent)
                        }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error processing callback", e)
                } finally {
                    pendingResult.finish()
                }
            }
        }

        previousState = state
    }

    private fun queryLatestCall(context: Context): CallInfo? {
        return try {
            val cursor = context.contentResolver.query(
                CallLog.Calls.CONTENT_URI,
                arrayOf(
                    CallLog.Calls.NUMBER,
                    CallLog.Calls.TYPE,
                    CallLog.Calls.DURATION,
                    CallLog.Calls.DATE
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

                    // Only process calls from the last 30 seconds
                    if (System.currentTimeMillis() - date > 30_000) {
                        Log.d(TAG, "Latest call too old, skipping")
                        return null
                    }

                    CallInfo(number, type, duration, date,
                        when (type) {
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
            Log.e(TAG, "CallLog query error: ${e.message}")
            null
        }
    }

    private data class CallInfo(
        val number: String,
        val type: Int,
        val duration: Long,
        val date: Long,
        val typeName: String
    )
}
