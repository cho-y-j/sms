package com.bizconnect.v2.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.bizconnect.v2.domain.engine.CallbackEngine
import com.bizconnect.v2.domain.engine.CallbackEventType
import com.bizconnect.v2.util.NotificationUtil
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Handles the action buttons on the manual-mode callback confirmation notification:
 *  - [보내기]: send the prepared callback now (CallbackEngine.sendCallbackNow)
 *  - [자동발송 금지]: add the number to the do-not-send list (CallbackEngine.addBlockedNumber)
 *
 * In both cases the confirmation notification is dismissed afterwards.
 */
@AndroidEntryPoint
class CallbackActionReceiver : BroadcastReceiver() {

    @Inject
    lateinit var callbackEngine: CallbackEngine

    @Inject
    lateinit var notificationUtil: NotificationUtil

    override fun onReceive(context: Context, intent: Intent) {
        val phone = intent.getStringExtra(EXTRA_PHONE) ?: return
        val notifId = intent.getIntExtra(EXTRA_NOTIF_ID, -1)
        val action = intent.action
        val pendingResult = goAsync()

        CoroutineScope(Dispatchers.IO).launch {
            try {
                when (action) {
                    ACTION_SEND -> {
                        val eventType = runCatching {
                            CallbackEventType.valueOf(intent.getStringExtra(EXTRA_EVENT_TYPE) ?: "")
                        }.getOrDefault(CallbackEventType.MISSED)
                        callbackEngine.sendCallbackNow(phone, eventType)
                    }
                    ACTION_BLOCK -> {
                        callbackEngine.addBlockedNumber(phone)
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Callback action failed: $action", e)
            } finally {
                if (notifId != -1) notificationUtil.cancelCallbackConfirm(notifId)
                pendingResult.finish()
            }
        }
    }

    companion object {
        private const val TAG = "CallbackActionReceiver"
        const val ACTION_SEND = "com.bizconnect.v2.CALLBACK_SEND"
        const val ACTION_BLOCK = "com.bizconnect.v2.CALLBACK_BLOCK"
        const val EXTRA_PHONE = "phone"
        const val EXTRA_EVENT_TYPE = "event_type"
        const val EXTRA_NOTIF_ID = "notif_id"
    }
}
