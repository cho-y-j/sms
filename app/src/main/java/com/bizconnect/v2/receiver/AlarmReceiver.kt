package com.bizconnect.v2.receiver

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.bizconnect.v2.data.local.db.dao.ContactDao
import com.bizconnect.v2.data.local.db.dao.ScheduledMessageDao
import com.bizconnect.v2.data.local.db.entity.ScheduledMessageEntity
import com.bizconnect.v2.util.SmsSender
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.Calendar
import javax.inject.Inject

@AndroidEntryPoint
class AlarmReceiver : BroadcastReceiver() {

    @Inject
    lateinit var scheduledMessageDao: ScheduledMessageDao

    @Inject
    lateinit var smsSender: SmsSender

    @Inject
    lateinit var contactDao: ContactDao

    override fun onReceive(context: Context, intent: Intent) {
        val scheduleId = intent.getStringExtra("schedule_id")
        if (scheduleId.isNullOrBlank()) {
            Log.w(TAG, "No schedule_id in intent, ignoring")
            return
        }

        Log.d(TAG, "Alarm triggered for scheduled message: $scheduleId")

        val pendingResult = goAsync()

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val entity = scheduledMessageDao.getById(scheduleId)
                if (entity == null) {
                    Log.w(TAG, "Scheduled message not found: $scheduleId")
                    return@launch
                }

                if (!entity.isActive) {
                    Log.d(TAG, "Scheduled message is inactive: $scheduleId")
                    return@launch
                }

                // Send to each recipient
                val recipients = entity.recipients
                    .split(",")
                    .map { it.trim().filter { c -> c.isDigit() || c == '+' } } // 하이픈/공백 제거
                    .filter { it.isNotEmpty() }

                // Recipient names for variable substitution
                val recipientNames = (entity.recipientNames ?: "")
                    .split(",")
                    .map { it.trim() }

                val now = Date()
                val dateStr = SimpleDateFormat("yyyy-MM-dd", Locale.KOREA).format(now)
                val timeStr = SimpleDateFormat("HH:mm", Locale.KOREA).format(now)
                val dayStr = SimpleDateFormat("EEEE", Locale.KOREA).format(now)

                Log.d(TAG, "Sending to ${recipients.size} recipients")

                for ((index, recipient) in recipients.withIndex()) {
                    try {
                        // Variable substitution per recipient
                        val recipientName = recipientNames.getOrNull(index)
                            ?: contactDao.getByPhone(recipient)?.name
                            ?: recipient
                        var msg = entity.message
                        Log.d(TAG, "Before substitution: $msg, name=$recipientName")
                        msg = msg.replace("%이름%", recipientName)
                        msg = msg.replace("%고객명%", recipientName)
                        msg = msg.replace("%전화번호%", recipient)
                        msg = msg.replace("%날짜%", dateStr)
                        msg = msg.replace("%시간%", timeStr)
                        msg = msg.replace("%요일%", dayStr)
                        msg = msg.replace("{이름}", recipientName)
                        msg = msg.replace("{고객명}", recipientName)
                        msg = msg.replace("{전화번호}", recipient)
                        msg = msg.replace("{날짜}", dateStr)
                        msg = msg.replace("{시간}", timeStr)

                        Log.d(TAG, "After substitution: $msg")

                        if (entity.isMms && entity.localImagePath != null) {
                            val imgPath = entity.localImagePath
                            val imageUri = if (imgPath.startsWith("/")) {
                                android.net.Uri.fromFile(java.io.File(imgPath))
                            } else {
                                android.net.Uri.parse(imgPath)
                            }
                            smsSender.sendMmsWithImage(recipient, msg, imageUri)
                        } else {
                            smsSender.sendSms(recipient, msg)
                        }
                        Log.d(TAG, "Sent to $recipient")
                    } catch (e: Exception) {
                        Log.e(TAG, "Failed to send to $recipient", e)
                    }
                }

                // Update lastSentAt
                scheduledMessageDao.updateLastSentAt(scheduleId, System.currentTimeMillis())

                // Handle repeat
                if (entity.repeatType != "none") {
                    scheduleNextAlarm(context, entity)
                } else {
                    // One-time: deactivate
                    scheduledMessageDao.deactivate(scheduleId)
                    Log.d(TAG, "One-time schedule deactivated: $scheduleId")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error processing scheduled message: $scheduleId", e)
            } finally {
                pendingResult.finish()
            }
        }
    }

    private suspend fun scheduleNextAlarm(context: Context, entity: ScheduledMessageEntity) {
        val nextTime = calculateNextScheduledTime(entity.scheduledAt, entity.repeatType)
        if (nextTime <= System.currentTimeMillis()) {
            // Safety: if calculated time is in the past, skip forward
            val adjusted = calculateNextFromNow(entity.repeatType)
            scheduleAlarmAt(context, entity, adjusted)
            scheduledMessageDao.update(entity.copy(scheduledAt = adjusted))
        } else {
            scheduleAlarmAt(context, entity, nextTime)
            scheduledMessageDao.update(entity.copy(scheduledAt = nextTime))
        }
    }

    private fun calculateNextScheduledTime(currentScheduledAt: Long, repeatType: String): Long {
        val calendar = Calendar.getInstance().apply { timeInMillis = currentScheduledAt }
        when (repeatType) {
            "daily" -> calendar.add(Calendar.DAY_OF_YEAR, 1)
            "weekly" -> calendar.add(Calendar.WEEK_OF_YEAR, 1)
            "monthly" -> calendar.add(Calendar.MONTH, 1)
            "yearly" -> calendar.add(Calendar.YEAR, 1)
        }
        return calendar.timeInMillis
    }

    private fun calculateNextFromNow(repeatType: String): Long {
        val calendar = Calendar.getInstance()
        when (repeatType) {
            "daily" -> calendar.add(Calendar.DAY_OF_YEAR, 1)
            "weekly" -> calendar.add(Calendar.WEEK_OF_YEAR, 1)
            "monthly" -> calendar.add(Calendar.MONTH, 1)
            "yearly" -> calendar.add(Calendar.YEAR, 1)
        }
        return calendar.timeInMillis
    }

    private fun scheduleAlarmAt(context: Context, entity: ScheduledMessageEntity, triggerAt: Long) {
        val alarmManager = context.getSystemService(AlarmManager::class.java)
        val intent = Intent(context, AlarmReceiver::class.java).apply {
            putExtra("schedule_id", entity.id)
        }
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            entity.id.hashCode(),
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        alarmManager.setExactAndAllowWhileIdle(
            AlarmManager.RTC_WAKEUP,
            triggerAt,
            pendingIntent
        )
        Log.d(TAG, "Next alarm set for ${entity.id} at $triggerAt")
    }

    companion object {
        const val TAG = "AlarmReceiver"
    }
}
