package com.bizconnect.v2.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.bizconnect.v2.service.WebSmsBatchSender
import com.bizconnect.v2.util.NotificationUtil
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * 웹 문자 배치 발송 승인 알림의 [발송]/[취소] 액션 처리.
 * 사용자가 [발송]을 눌러야만 실제 발송이 일어난다 (자동 게이트웨이 아님).
 */
@AndroidEntryPoint
class WebSmsApprovalReceiver : BroadcastReceiver() {

    companion object {
        const val ACTION_APPROVE = "com.bizconnect.v2.action.APPROVE_WEB_BATCH"
        const val ACTION_CANCEL = "com.bizconnect.v2.action.CANCEL_WEB_BATCH"
        const val EXTRA_JOB_ID = "job_id"
        const val EXTRA_MESSAGES = "messages_json"
        const val EXTRA_ALL_PENDING = "all_pending"
        const val EXTRA_NOTIF_ID = "notif_id"
        private const val TAG = "WebSmsApprovalReceiver"
    }

    @Inject lateinit var sender: WebSmsBatchSender
    @Inject lateinit var notificationUtil: NotificationUtil

    private val scope = CoroutineScope(Dispatchers.IO)

    override fun onReceive(context: Context?, intent: Intent?) {
        if (intent == null) return
        val notifId = intent.getIntExtra(EXTRA_NOTIF_ID, 0)
        if (notifId != 0) notificationUtil.cancelWebBatchApproval(notifId)

        when (intent.action) {
            ACTION_APPROVE -> {
                val allPending = intent.getBooleanExtra(EXTRA_ALL_PENDING, false)
                val jobId = intent.getStringExtra(EXTRA_JOB_ID)
                val messagesJson = intent.getStringExtra(EXTRA_MESSAGES)
                val pending = goAsync()
                scope.launch {
                    try {
                        if (allPending) sender.sendAllPending()
                        else sender.sendJob(jobId, messagesJson)
                    } catch (e: Exception) {
                        Log.e(TAG, "approve send failed", e)
                    } finally {
                        pending.finish()
                    }
                }
            }
            ACTION_CANCEL -> Log.d(TAG, "web batch send cancelled by user")
        }
    }
}
