package com.bizconnect.v2.service

import android.app.Service
import android.content.Intent
import android.os.IBinder
import android.util.Log
import com.bizconnect.v2.util.PhoneNumberUtil
import com.bizconnect.v2.util.SmsSender
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class HeadlessSmsSendService : Service() {
    @Inject
    lateinit var smsSender: SmsSender

    @Inject
    lateinit var phoneNumberUtil: PhoneNumberUtil

    override fun onBind(intent: Intent): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        intent ?: return START_STICKY

        // Handle RESPOND_VIA_MESSAGE intent (quick reply from notification)
        val recipients = intent.getStringArrayExtra(Intent.EXTRA_PHONE_NUMBER)
        val replyText = intent.getStringExtra(Intent.EXTRA_TEXT)

        Log.d(TAG, "HeadlessSmsSendService started: recipients=${recipients?.size}, reply=$replyText")

        if (recipients != null && replyText != null) {
            CoroutineScope(Dispatchers.IO).launch {
                recipients.forEach { number ->
                    try {
                        val phoneNumber = phoneNumberUtil.normalizeNumber(number)
                        val threadId = smsSender.sendSms(phoneNumber, replyText)
                        if (threadId >= 0) {
                            Log.d(TAG, "Quick reply sent to $phoneNumber")
                        } else {
                            Log.e(TAG, "Failed to send quick reply to $phoneNumber")
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Error sending quick reply to $number", e)
                    }
                }
            }
        }

        stopSelf(startId)
        return START_STICKY
    }

    companion object {
        const val TAG = "HeadlessSmsSendService"
    }
}
