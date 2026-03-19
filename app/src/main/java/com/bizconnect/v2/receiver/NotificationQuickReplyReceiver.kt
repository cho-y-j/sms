package com.bizconnect.v2.receiver

import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import android.widget.Toast
import com.bizconnect.v2.util.SmsSender
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Handles quick reply button clicks from notification.
 * Sends the pre-defined reply text and dismisses the notification.
 */
@AndroidEntryPoint
class NotificationQuickReplyReceiver : BroadcastReceiver() {
    @Inject lateinit var smsSender: SmsSender

    override fun onReceive(context: Context, intent: Intent) {
        val threadId = intent.getLongExtra("threadId", 0)
        val address = intent.getStringExtra("address") ?: return
        val replyText = intent.getStringExtra("reply_text") ?: return

        Log.d("QuickReply", "Sending quick reply '$replyText' to $address")

        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                smsSender.sendSms(address, replyText)

                // Dismiss notification
                val nm = context.getSystemService(NotificationManager::class.java)
                nm.cancel(threadId.toInt())

                // Show toast on main thread
                CoroutineScope(Dispatchers.Main).launch {
                    Toast.makeText(context, "답장 전송: $replyText", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Log.e("QuickReply", "Failed to send quick reply", e)
            } finally {
                pendingResult.finish()
            }
        }
    }
}
