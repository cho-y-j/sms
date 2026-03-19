package com.bizconnect.v2.domain.engine

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.app.NotificationCompat
// TaskDao, TaskEntity, and AppPreferences are in the same package (domain.engine)
import com.bizconnect.v2.receiver.ApprovalActionReceiver
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Manages SMS sending approval from web requests.
 * Shows notification with approve/cancel actions.
 * Supports auto-approve mode.
 */
@Singleton
class ApprovalManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val smsEngine: SmsEngine,
    private val queueEngine: QueueEngine,
    private val taskDao: TaskDao,
    private val appPreferences: AppPreferences
) {

    companion object {
        private const val TAG = "ApprovalManager"
        private const val NOTIFICATION_CHANNEL_APPROVAL = "sms_approval"
        private const val ACTION_APPROVE = "com.bizconnect.v2.action.APPROVE_SMS"
        private const val ACTION_CANCEL = "com.bizconnect.v2.action.CANCEL_SMS"
        private const val EXTRA_TASK_ID = "task_id"
        private const val EXTRA_TASK_IDS = "task_ids"
    }

    private val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    // Track notified tasks to prevent duplicates
    private val notifiedTaskIds = ConcurrentHashMap<String, Boolean>()
    private val mutex = Mutex()
    private var notificationIdCounter = 1000

    /**
     * Request approval for single SMS
     */
    suspend fun requestApproval(task: TaskEntity) {
        mutex.withLock {
            if (notifiedTaskIds.containsKey(task.id)) {
                Log.w(TAG, "Task already notified: ${task.id}")
                return
            }

            val notificationId = notificationIdCounter++
            notifiedTaskIds[task.id] = true

            Log.d(TAG, "Requesting approval for task: ${task.id}")
            showApprovalNotification(task, notificationId)
        }
    }

    /**
     * Request approval for batch SMS
     */
    suspend fun requestBatchApproval(tasks: List<TaskEntity>) {
        mutex.withLock {
            // Filter out already notified tasks
            val newTasks = tasks.filter { !notifiedTaskIds.containsKey(it.id) }
            if (newTasks.isEmpty()) {
                Log.w(TAG, "All tasks already notified")
                return
            }

            val notificationId = notificationIdCounter++
            newTasks.forEach { notifiedTaskIds[it.id] = true }

            Log.d(TAG, "Requesting approval for batch: ${newTasks.size} tasks")
            showBatchApprovalNotification(newTasks, notificationId)
        }
    }

    /**
     * Handle approval action from notification
     */
    suspend fun handleApproval(taskId: String) {
        try {
            Log.d(TAG, "Approving task: $taskId")

            // Update task status
            val task = taskDao.getTaskById(taskId) ?: run {
                Log.e(TAG, "Task not found: $taskId")
                return
            }

            val updatedTask = task.copy(
                status = "APPROVED",
                approvedAt = System.currentTimeMillis(),
                updatedAt = System.currentTimeMillis()
            )
            taskDao.updateTask(updatedTask)

            // Send SMS through SMS engine
            val sendResult = smsEngine.sendMessage(updatedTask.recipientAddress, updatedTask.messageBody)
            if (sendResult.success) {
                Log.d(TAG, "SMS sent successfully for task: $taskId")
                val sentTask = updatedTask.copy(
                    status = "SENT",
                    sentAt = System.currentTimeMillis(),
                    updatedAt = System.currentTimeMillis()
                )
                taskDao.updateTask(sentTask)
            } else {
                Log.e(TAG, "Failed to send SMS for task: $taskId")
                val failedTask = updatedTask.copy(
                    status = "FAILED",
                    lastError = "SMS send failed",
                    updatedAt = System.currentTimeMillis()
                )
                taskDao.updateTask(failedTask)
            }

            notifiedTaskIds.remove(taskId)
        } catch (e: Exception) {
            Log.e(TAG, "Exception handling approval", e)
        }
    }

    /**
     * Handle batch approval
     */
    suspend fun handleBatchApproval(taskIds: List<String>) {
        try {
            Log.d(TAG, "Approving batch: ${taskIds.size} tasks")

            val tasks = mutableListOf<TaskEntity>()
            for (taskId in taskIds) {
                val task = taskDao.getTaskById(taskId)
                if (task != null) {
                    tasks.add(task)
                } else {
                    Log.w(TAG, "Task not found: $taskId")
                }
            }

            if (tasks.isEmpty()) {
                Log.w(TAG, "No valid tasks found for batch approval")
                return
            }

            // Update all tasks to APPROVED
            for (task in tasks) {
                val updatedTask = task.copy(
                    status = "APPROVED",
                    approvedAt = System.currentTimeMillis(),
                    updatedAt = System.currentTimeMillis()
                )
                taskDao.updateTask(updatedTask)
            }

            // Enqueue tasks for sending via queue engine
            queueEngine.enqueueTasks(tasks)

            Log.d(TAG, "Batch tasks enqueued for sending")

            taskIds.forEach { notifiedTaskIds.remove(it) }
        } catch (e: Exception) {
            Log.e(TAG, "Exception handling batch approval", e)
        }
    }

    /**
     * Handle cancellation
     */
    suspend fun handleCancellation(taskId: String) {
        try {
            Log.d(TAG, "Cancelling task: $taskId")

            val task = taskDao.getTaskById(taskId) ?: return

            val cancelledTask = task.copy(
                status = "CANCELLED",
                updatedAt = System.currentTimeMillis()
            )
            taskDao.updateTask(cancelledTask)

            notifiedTaskIds.remove(taskId)
            Log.d(TAG, "Task cancelled: $taskId")
        } catch (e: Exception) {
            Log.e(TAG, "Exception handling cancellation", e)
        }
    }

    /**
     * Check if auto-approve is enabled
     */
    suspend fun isAutoApproveEnabled(): Boolean {
        return appPreferences.getAutoApprove()
    }

    /**
     * Show approval notification
     */
    private fun showApprovalNotification(task: TaskEntity, notificationId: Int) {
        try {
            val approveIntent = Intent(context, ApprovalActionReceiver::class.java).apply {
                action = ACTION_APPROVE
                putExtra(EXTRA_TASK_ID, task.id)
            }
            val approvePendingIntent = PendingIntent.getBroadcast(
                context,
                notificationId,
                approveIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val cancelIntent = Intent(context, ApprovalActionReceiver::class.java).apply {
                action = ACTION_CANCEL
                putExtra(EXTRA_TASK_ID, task.id)
            }
            val cancelPendingIntent = PendingIntent.getBroadcast(
                context,
                notificationId + 1,
                cancelIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val notification = NotificationCompat.Builder(context, NOTIFICATION_CHANNEL_APPROVAL)
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setContentTitle("SMS Approval Required")
                .setContentText("${task.customerName}: ${task.message.take(50)}...")
                .setStyle(NotificationCompat.BigTextStyle().bigText(task.message))
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .addAction(android.R.drawable.ic_menu_view, "Approve", approvePendingIntent)
                .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Cancel", cancelPendingIntent)
                .setAutoCancel(true)
                .build()

            notificationManager.notify(notificationId, notification)
            Log.d(TAG, "Approval notification shown for task: ${task.id}")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to show approval notification", e)
        }
    }

    /**
     * Show batch approval notification
     */
    private fun showBatchApprovalNotification(tasks: List<TaskEntity>, notificationId: Int) {
        try {
            val taskIds = tasks.map { it.id }.toTypedArray()

            val approveIntent = Intent(context, ApprovalActionReceiver::class.java).apply {
                action = ACTION_APPROVE
                putExtra(EXTRA_TASK_IDS, taskIds)
            }
            val approvePendingIntent = PendingIntent.getBroadcast(
                context,
                notificationId,
                approveIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val cancelIntent = Intent(context, ApprovalActionReceiver::class.java).apply {
                action = ACTION_CANCEL
                putExtra(EXTRA_TASK_IDS, taskIds)
            }
            val cancelPendingIntent = PendingIntent.getBroadcast(
                context,
                notificationId + 1,
                cancelIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val taskSummary = tasks.take(3).joinToString("\n") { "${it.customerName}: ${it.message.take(40)}..." }
            val summary = if (tasks.size > 3) {
                "$taskSummary\n... and ${tasks.size - 3} more"
            } else {
                taskSummary
            }

            val notification = NotificationCompat.Builder(context, NOTIFICATION_CHANNEL_APPROVAL)
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setContentTitle("Batch SMS Approval Required")
                .setContentText("${tasks.size} SMS messages pending approval")
                .setStyle(NotificationCompat.BigTextStyle().bigText(summary))
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .addAction(android.R.drawable.ic_menu_view, "Approve All", approvePendingIntent)
                .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Reject All", cancelPendingIntent)
                .setAutoCancel(true)
                .build()

            notificationManager.notify(notificationId, notification)
            Log.d(TAG, "Batch approval notification shown for ${tasks.size} tasks")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to show batch approval notification", e)
        }
    }

    /**
     * Clear notified task tracking (for testing or manual reset)
     */
    fun clearNotifiedTasks() {
        notifiedTaskIds.clear()
        Log.d(TAG, "Notified tasks cleared")
    }

    /**
     * Get count of notified tasks
     */
    fun getNotifiedTasksCount(): Int = notifiedTaskIds.size
}
