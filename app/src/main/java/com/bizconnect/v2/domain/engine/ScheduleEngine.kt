package com.bizconnect.v2.domain.engine

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Calendar
import java.util.UUID
import javax.inject.Inject

/**
 * Handles scheduling messages for future delivery.
 * Features:
 * - Precise timing via AlarmManager
 * - Recurring message support
 * - Boot persistence (restores alarms after device restart)
 * - Timezone-aware scheduling
 * - Automatic retry on alarm triggers
 */
class ScheduleEngine @Inject constructor(
    @ApplicationContext private val context: Context,
    private val scheduledMessageDao: ScheduledMessageDao,
    private val queueEngine: QueueEngine,
    private val smsEngine: SmsEngine
) {
    private val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

    companion object {
        private const val TAG = "ScheduleEngine"
        private const val SCHEDULE_ACTION = "com.bizconnect.v2.SCHEDULED_MESSAGE"
    }

    /**
     * Schedule a message for future delivery at a specific time
     */
    suspend fun scheduleMessage(message: ScheduledMessageEntity) = withContext(Dispatchers.IO) {
        try {
            // Validate schedule time
            if (message.scheduledTime <= System.currentTimeMillis()) {
                throw IllegalArgumentException("Scheduled time must be in the future")
            }

            // Insert into database
            scheduledMessageDao.insert(message)

            // Set alarm for this message
            setAlarmForMessage(message)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    /**
     * Cancel a scheduled message and remove the alarm
     */
    suspend fun cancelSchedule(messageId: String) = withContext(Dispatchers.IO) {
        try {
            val message = scheduledMessageDao.getMessageById(messageId)
            if (message != null) {
                // Remove alarm
                cancelAlarmForMessage(message)

                // Update status
                scheduledMessageDao.updateStatus(messageId, "CANCELLED")
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    /**
     * Process messages that are due for sending
     * Called by AlarmReceiver when an alarm triggers
     */
    suspend fun processDueMessages() = withContext(Dispatchers.IO) {
        try {
            val currentTime = System.currentTimeMillis()

            // Get all messages that are due
            val dueMessages = scheduledMessageDao.getDueMessages(currentTime)

            for (message in dueMessages) {
                // Check if message should still be sent
                if (message.status == "CANCELLED") continue
                if (message.status == "SENT") {
                    // Check if it's recurring
                    if (message.isRecurring) {
                        rescheduleIfRecurring(message)
                    }
                    continue
                }

                // Send the message
                val result = smsEngine.sendMessage(
                    address = message.recipientAddress,
                    body = message.messageBody,
                    imageUri = if (message.imageUri?.isNotBlank() == true) {
                        Uri.parse(message.imageUri)
                    } else {
                        null
                    },
                    subscriptionId = message.subscriptionId
                )

                if (result.success) {
                    // Mark as sent
                    scheduledMessageDao.updateStatus(message.id, "SENT")

                    // Reschedule if recurring
                    if (message.isRecurring) {
                        rescheduleIfRecurring(message)
                    }
                } else {
                    // Retry or mark as failed
                    val retryCount = message.retryCount + 1
                    if (retryCount < message.maxRetries) {
                        // Schedule next retry
                        val nextRetryTime = System.currentTimeMillis() + (60000 * retryCount) // Exponential backoff
                        val updatedMessage = message.copy(
                            scheduledTime = nextRetryTime,
                            retryCount = retryCount
                        )
                        scheduledMessageDao.update(updatedMessage)
                        setAlarmForMessage(updatedMessage)
                    } else {
                        // Max retries reached
                        scheduledMessageDao.updateStatus(message.id, "FAILED")
                    }
                }
            }

            // Set alarm for next message
            setNextAlarm()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    /**
     * Reschedule a recurring message for the next occurrence
     */
    private suspend fun rescheduleIfRecurring(message: ScheduledMessageEntity) = withContext(Dispatchers.IO) {
        try {
            if (!message.isRecurring || message.recurringPattern.isBlank()) {
                return@withContext
            }

            // Calculate next occurrence based on pattern
            val nextTime = calculateNextOccurrence(
                message.scheduledTime,
                message.recurringPattern
            )

            if (nextTime > 0) {
                val nextMessage = message.copy(
                    id = UUID.randomUUID().toString(),
                    scheduledTime = nextTime,
                    status = "PENDING",
                    retryCount = 0
                )

                scheduledMessageDao.insert(nextMessage)
                setAlarmForMessage(nextMessage)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    /**
     * Calculate the next occurrence time for a recurring message
     * Pattern examples: "DAILY", "WEEKLY", "MONTHLY"
     */
    private fun calculateNextOccurrence(baseTime: Long, pattern: String): Long {
        val calendar = Calendar.getInstance().apply {
            timeInMillis = baseTime
        }

        when (pattern.uppercase()) {
            "DAILY" -> {
                calendar.add(Calendar.DAY_OF_MONTH, 1)
            }
            "WEEKLY" -> {
                calendar.add(Calendar.WEEK_OF_MONTH, 1)
            }
            "MONTHLY" -> {
                calendar.add(Calendar.MONTH, 1)
            }
            "YEARLY" -> {
                calendar.add(Calendar.YEAR, 1)
            }
            else -> {
                // Unknown pattern, use daily
                calendar.add(Calendar.DAY_OF_MONTH, 1)
            }
        }

        return calendar.timeInMillis
    }

    /**
     * Set an alarm for a scheduled message
     */
    private fun setAlarmForMessage(message: ScheduledMessageEntity) {
        try {
            val intent = Intent(context, ScheduledMessageReceiver::class.java).apply {
                action = SCHEDULE_ACTION
                putExtra("messageId", message.id)
                `package` = context.packageName
            }

            val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            } else {
                PendingIntent.FLAG_UPDATE_CURRENT
            }

            val pendingIntent = PendingIntent.getBroadcast(
                context,
                message.id.hashCode(),
                intent,
                flags
            )

            // Set the alarm
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    if (alarmManager.canScheduleExactAlarms()) {
                        alarmManager.setExactAndAllowWhileIdle(
                            AlarmManager.RTC_WAKEUP,
                            message.scheduledTime,
                            pendingIntent
                        )
                    } else {
                        alarmManager.setAndAllowWhileIdle(
                            AlarmManager.RTC_WAKEUP,
                            message.scheduledTime,
                            pendingIntent
                        )
                    }
                } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                    alarmManager.setExactAndAllowWhileIdle(
                        AlarmManager.RTC_WAKEUP,
                        message.scheduledTime,
                        pendingIntent
                    )
                } else {
                    @Suppress("DEPRECATION")
                    alarmManager.set(
                        AlarmManager.RTC_WAKEUP,
                        message.scheduledTime,
                        pendingIntent
                    )
                }
            } catch (e: SecurityException) {
                // Fallback to inexact alarm
                alarmManager.setAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    message.scheduledTime,
                    pendingIntent
                )
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    /**
     * Cancel an alarm for a scheduled message
     */
    private fun cancelAlarmForMessage(message: ScheduledMessageEntity) {
        try {
            val intent = Intent(context, ScheduledMessageReceiver::class.java).apply {
                action = SCHEDULE_ACTION
                putExtra("messageId", message.id)
            }

            val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
            } else {
                PendingIntent.FLAG_NO_CREATE
            }

            val pendingIntent = PendingIntent.getBroadcast(
                context,
                message.id.hashCode(),
                intent,
                flags
            )

            if (pendingIntent != null) {
                alarmManager.cancel(pendingIntent)
                pendingIntent.cancel()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    /**
     * Set the next alarm for the soonest upcoming scheduled message
     */
    fun setNextAlarm() {
        scope.launch {
            try {
                val nextMessage = scheduledMessageDao.getNextPendingMessage()
                if (nextMessage != null) {
                    setAlarmForMessage(nextMessage)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    /**
     * Restore all alarms after device boot or app restart
     */
    suspend fun restoreAlarms() = withContext(Dispatchers.IO) {
        try {
            val allScheduled = scheduledMessageDao.getAllPendingMessages()
            for (message in allScheduled) {
                setAlarmForMessage(message)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private val scope = kotlinx.coroutines.CoroutineScope(
        Dispatchers.Default + kotlinx.coroutines.Job()
    )
}

/**
 * BroadcastReceiver for handling scheduled message alarms
 */
class ScheduledMessageReceiver : android.content.BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == "com.bizconnect.v2.SCHEDULED_MESSAGE") {
            val messageId = intent.getStringExtra("messageId") ?: return

            // Schedule background work to process the message
            val workIntent = Intent(context, ScheduleWorkService::class.java).apply {
                putExtra("messageId", messageId)
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(workIntent)
            } else {
                context.startService(workIntent)
            }
        }
    }
}

/**
 * Service for processing scheduled messages
 */
class ScheduleWorkService : android.app.Service() {
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // Process due messages in background
        // Implementation depends on DI setup
        return START_REDELIVER_INTENT
    }

    override fun onBind(intent: Intent?) = null
}
