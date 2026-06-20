package com.bizconnect.v2.util

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.Person
import androidx.core.app.RemoteInput
import android.provider.CalendarContract
import com.bizconnect.v2.R
import com.bizconnect.v2.app.MainActivity
import com.bizconnect.v2.domain.ai.AiAssistant
import com.bizconnect.v2.receiver.CallbackActionReceiver
import com.bizconnect.v2.receiver.NotificationQuickReplyReceiver
import com.bizconnect.v2.receiver.NotificationReplyReceiver
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

class NotificationUtil @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val notificationManager = context.getSystemService(NotificationManager::class.java)

    fun showNewMessageNotification(
        threadId: Long,
        senderName: String,
        senderAddress: String,
        messageText: String,
        photoUri: String? = null
    ) {
        // 1. Create Person for sender
        val person = Person.Builder()
            .setName(senderName)
            .setKey(senderAddress)
            .build()

        // 2. Create MessagingStyle
        val style = NotificationCompat.MessagingStyle(person)
            .addMessage(messageText, System.currentTimeMillis(), person)

        // 3. Create inline reply RemoteInput
        val remoteInput = RemoteInput.Builder("reply_text")
            .setLabel("답장")
            .build()

        // 4. Create reply PendingIntent
        val replyIntent = Intent(context, NotificationReplyReceiver::class.java).apply {
            putExtra("threadId", threadId)
            putExtra("address", senderAddress)
        }
        val replyPendingIntent = PendingIntent.getBroadcast(
            context, threadId.toInt(), replyIntent,
            PendingIntent.FLAG_MUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val replyAction = NotificationCompat.Action.Builder(
            R.drawable.ic_notification, "답장", replyPendingIntent
        ).addRemoteInput(remoteInput).build()

        // 5. Create tap intent (deep link to conversation)
        val tapIntent = Intent(context, MainActivity::class.java).apply {
            putExtra("threadId", threadId)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val tapPendingIntent = PendingIntent.getActivity(
            context, threadId.toInt() + 10000, tapIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        // 6. Quick reply suggestions (삼성 스타일)
        val quickReplies = getQuickReplies(messageText)
        val quickReplyActions = quickReplies.mapIndexed { index, reply ->
            val quickIntent = Intent(context, NotificationQuickReplyReceiver::class.java).apply {
                putExtra("threadId", threadId)
                putExtra("address", senderAddress)
                putExtra("reply_text", reply)
            }
            val quickPendingIntent = PendingIntent.getBroadcast(
                context, threadId.toInt() + 100 + index, quickIntent,
                PendingIntent.FLAG_MUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
            NotificationCompat.Action.Builder(0, reply, quickPendingIntent).build()
        }

        // 7. Build notification
        val builder = NotificationCompat.Builder(context, "messages")
            .setSmallIcon(R.drawable.ic_notification)
            .setStyle(style)
            .setContentIntent(tapPendingIntent)
            .addAction(replyAction)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)
            .setGroup("sms_group")

        // Add quick reply buttons
        quickReplyActions.forEach { builder.addAction(it) }

        val notification = builder.build()

        notificationManager.notify(threadId.toInt(), notification)
    }

    fun cancelNotification(threadId: Long) {
        notificationManager.cancel(threadId.toInt())
    }

    /**
     * Manual-mode callback confirmation. Shown after a call ends when the user has
     * chosen "수동(확인 후 발송)". Buttons: [보내기] sends the prepared callback now,
     * [자동발송 금지] adds the number to the do-not-send list. (Replaces the idea of
     * auto-launching a full-screen Activity, which Android blocks from the background.)
     */
    fun showCallbackConfirmNotification(
        phoneNumber: String,
        displayName: String,
        body: String,
        eventTypeName: String,
        hasImage: Boolean
    ) {
        val notifId = CALLBACK_CONFIRM_BASE_ID + (phoneNumber.hashCode() and 0xFFFF)

        val sendIntent = Intent(context, CallbackActionReceiver::class.java).apply {
            action = CallbackActionReceiver.ACTION_SEND
            putExtra(CallbackActionReceiver.EXTRA_PHONE, phoneNumber)
            putExtra(CallbackActionReceiver.EXTRA_EVENT_TYPE, eventTypeName)
            putExtra(CallbackActionReceiver.EXTRA_NOTIF_ID, notifId)
        }
        val sendPending = PendingIntent.getBroadcast(
            context, notifId, sendIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val blockIntent = Intent(context, CallbackActionReceiver::class.java).apply {
            action = CallbackActionReceiver.ACTION_BLOCK
            putExtra(CallbackActionReceiver.EXTRA_PHONE, phoneNumber)
            putExtra(CallbackActionReceiver.EXTRA_NOTIF_ID, notifId)
        }
        val blockPending = PendingIntent.getBroadcast(
            context, notifId + 1, blockIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val preview = if (hasImage) "[명함] $body" else body

        val notification = NotificationCompat.Builder(context, "messages")
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("${displayName}님께 콜백 보낼까요?")
            .setContentText(preview)
            .setStyle(NotificationCompat.BigTextStyle().bigText(preview))
            .addAction(R.drawable.ic_notification, "보내기", sendPending)
            .addAction(0, "자동발송 금지", blockPending)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)
            .build()

        notificationManager.notify(notifId, notification)
    }

    fun cancelCallbackConfirm(notifId: Int) {
        notificationManager.cancel(notifId)
    }

    fun showApprovalNotification(messageId: String, messageText: String) {
        val approveIntent = Intent(context, MainActivity::class.java).apply {
            action = "action.approve_message"
            putExtra("message_id", messageId)
        }
        val approvePendingIntent = PendingIntent.getActivity(
            context, messageId.hashCode(), approveIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, "sms_approval")
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("메시지 발송 승인")
            .setContentText(messageText)
            .addAction(R.drawable.ic_notification, "승인", approvePendingIntent)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()

        notificationManager.notify(messageId.hashCode(), notification)
    }

    fun showSendResultNotification(successCount: Int, failureCount: Int) {
        val title = "메시지 발송 완료"
        val text = "성공: ${successCount}건, 실패: ${failureCount}건"

        val notification = NotificationCompat.Builder(context, "sms_info")
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(text)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()

        notificationManager.notify(SEND_RESULT_NOTIFICATION_ID, notification)
    }

    fun showFcmNotification(title: String, body: String) {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            context, title.hashCode(), intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, "messages")
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(body)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()

        notificationManager.notify(title.hashCode(), notification)
    }

    /**
     * Generate quick reply suggestions based on message content.
     * Simple keyword matching (no AI needed — instant, no network).
     */
    private fun getQuickReplies(messageText: String): List<String> {
        val text = messageText.lowercase()
        return when {
            // 질문
            text.contains("어디") || text.contains("언제") || text.contains("몇시") ->
                listOf("확인 후 알려드릴게요", "잠시만요")
            // 감사/인사
            text.contains("감사") || text.contains("고마") ->
                listOf("천만에요", "감사합니다")
            // 안부
            text.contains("잘 지내") || text.contains("안녕") ->
                listOf("네, 잘 지내요!", "안녕하세요!")
            // 확인 요청
            text.contains("확인") || text.contains("가능") || text.contains("되나") ->
                listOf("네, 확인했습니다", "잠시 후 답변드릴게요")
            // 약속/만남
            text.contains("만나") || text.contains("보자") || text.contains("약속") ->
                listOf("네, 좋아요!", "시간 확인하고 연락드릴게요")
            // 요청
            text.contains("부탁") || text.contains("해주") ->
                listOf("네, 알겠습니다", "확인하겠습니다")
            // 전화 관련
            text.contains("전화") || text.contains("통화") ->
                listOf("잠시 후 연락드릴게요", "지금 통화 가능합니다")
            // 기본
            else -> listOf("네", "감사합니다")
        }
    }

    fun showAppointmentNotification(
        senderName: String,
        appointment: AiAssistant.AppointmentInfo
    ) {
        try {
            // Parse date/time for calendar intent
            var beginMillis = System.currentTimeMillis() + 3600000 // fallback: 1 hour later
            try {
                val dateParts = appointment.date.split("-").map { it.toInt() }
                val timeParts = appointment.time.split(":").map { it.toInt() }
                val cal = java.util.Calendar.getInstance().apply {
                    set(dateParts[0], dateParts[1] - 1, dateParts[2], timeParts[0], timeParts[1], 0)
                }
                beginMillis = cal.timeInMillis
            } catch (_: Exception) {}

            val calendarIntent = Intent(Intent.ACTION_INSERT).apply {
                data = CalendarContract.Events.CONTENT_URI
                putExtra(CalendarContract.Events.TITLE, "${senderName} - ${appointment.description}")
                putExtra(CalendarContract.Events.DESCRIPTION, "${senderName}님과의 약속\n${appointment.description}")
                putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, beginMillis)
                putExtra(CalendarContract.EXTRA_EVENT_END_TIME, beginMillis + 3600000)
                putExtra(CalendarContract.Events.EVENT_TIMEZONE, java.util.TimeZone.getDefault().id)
                putExtra(CalendarContract.Events.AVAILABILITY, CalendarContract.Events.AVAILABILITY_BUSY)
                if (!appointment.location.isNullOrBlank()) {
                    putExtra(CalendarContract.Events.EVENT_LOCATION, appointment.location)
                }
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            val calendarPendingIntent = PendingIntent.getActivity(
                context, appointment.hashCode(), calendarIntent,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )

            val notification = NotificationCompat.Builder(context, "messages")
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle("📅 약속 감지")
                .setContentText("${senderName}: ${appointment.date} ${appointment.time} ${appointment.description}")
                .addAction(R.drawable.ic_notification, "캘린더에 추가", calendarPendingIntent)
                .setAutoCancel(true)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .build()

            notificationManager.notify(APPOINTMENT_NOTIFICATION_ID, notification)
        } catch (e: Exception) {
            android.util.Log.e("NotificationUtil", "Appointment notification failed", e)
        }
    }

    companion object {
        const val SEND_RESULT_NOTIFICATION_ID = 200
        const val APPOINTMENT_NOTIFICATION_ID = 300
        const val CALLBACK_CONFIRM_BASE_ID = 400000
    }
}
