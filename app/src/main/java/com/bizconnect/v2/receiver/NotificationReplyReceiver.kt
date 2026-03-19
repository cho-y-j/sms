package com.bizconnect.v2.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.RemoteInput
import com.bizconnect.v2.util.NotificationUtil
import com.bizconnect.v2.util.SmsSender
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class NotificationReplyReceiver : BroadcastReceiver() {
    @Inject lateinit var smsSender: SmsSender
    @Inject lateinit var notificationUtil: NotificationUtil

    override fun onReceive(context: Context, intent: Intent) {
        val remoteInput = RemoteInput.getResultsFromIntent(intent)
        val replyText = remoteInput?.getCharSequence("reply_text")?.toString() ?: return
        val threadId = intent.getLongExtra("threadId", 0)
        val address = intent.getStringExtra("address") ?: return

        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                smsSender.sendSms(address, replyText)
                // Update notification to show reply was sent
                notificationUtil.showNewMessageNotification(
                    threadId = threadId,
                    senderName = "나",
                    senderAddress = address,
                    messageText = replyText
                )
            } finally {
                pendingResult.finish()
            }
        }
    }
}
