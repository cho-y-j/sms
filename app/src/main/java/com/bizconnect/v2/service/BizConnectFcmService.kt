package com.bizconnect.v2.service

import android.util.Log
import com.bizconnect.v2.data.local.db.dao.TaskDao
import com.bizconnect.v2.data.local.db.entity.TaskEntity
import com.bizconnect.v2.data.preferences.AppPreferences
import com.bizconnect.v2.data.remote.api.BizConnectApi
import com.bizconnect.v2.domain.engine.ApprovalManager
import com.bizconnect.v2.domain.engine.TaskEntity as DomainTaskEntity
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * FCM service that handles push notifications and remote messages from BizConnect server.
 * Manages SMS approval notifications and setting updates from web interface.
 */
@AndroidEntryPoint
class BizConnectFcmService : FirebaseMessagingService() {

    companion object {
        private const val TAG = "BizConnectFcmService"
        private const val TYPE_SEND_SMS = "send_sms"
        private const val TYPE_SEND_SMS_BATCH = "send_sms_batch"
        private const val TYPE_SETTING_UPDATE = "setting_update"
        private const val TYPE_SYNC_REQUEST = "sync_request"
    }

    @Inject
    lateinit var taskDao: TaskDao

    @Inject
    lateinit var appPreferences: AppPreferences

    @Inject
    lateinit var approvalManager: ApprovalManager

    @Inject
    lateinit var api: BizConnectApi

    private val scope = CoroutineScope(Dispatchers.Default)

    /**
     * Called when FCM token is generated or refreshed.
     * Uploads token to server for future push notifications.
     */
    override fun onNewToken(token: String) {
        super.onNewToken(token)
        Log.d(TAG, "FCM token refreshed: ${token.take(20)}...")

        scope.launch {
            try {
                appPreferences.saveFcmToken(token)
                uploadTokenToServer(token)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to save FCM token", e)
            }
        }
    }

    /**
     * Called when a message is received from FCM.
     * Handles data-only messages from BizConnect server.
     */
    override fun onMessageReceived(message: RemoteMessage) {
        super.onMessageReceived(message)

        Log.d(TAG, "Message received from: ${message.from}")

        // Handle data-only messages (preferred over notification messages)
        if (message.data.isNotEmpty()) {
            val messageType = message.data["type"]
            Log.d(TAG, "Message type: $messageType")

            when (messageType) {
                TYPE_SEND_SMS -> handleSingleSmsRequest(message.data)
                TYPE_SEND_SMS_BATCH -> handleBatchSmsRequest(message.data)
                TYPE_SETTING_UPDATE -> handleSettingUpdate(message.data)
                TYPE_SYNC_REQUEST -> handleSyncRequest()
                else -> Log.w(TAG, "Unknown message type: $messageType")
            }
        }
    }

    /**
     * Handles single SMS approval request from web interface.
     * Checks for duplicates and shows notification for user approval.
     */
    private fun handleSingleSmsRequest(data: Map<String, String>) {
        scope.launch {
            try {
                val taskId = data["taskId"] ?: return@launch
                val customerId = data["customerId"] ?: return@launch
                val customerName = data["customerName"] ?: return@launch
                val customerPhone = data["customerPhone"] ?: return@launch
                val message = data["message"] ?: return@launch
                val scheduledTime = data["scheduledTime"]?.toLongOrNull() ?: System.currentTimeMillis()

                // Check if task already exists (duplicate prevention)
                val existingTask = taskDao.getById(taskId)
                if (existingTask != null) {
                    Log.w(TAG, "Task already exists, skipping: $taskId")
                    return@launch
                }

                // Create task entity
                val task = TaskEntity(
                    id = taskId,
                    userId = appPreferences.getUserId() ?: "",
                    customerId = customerId,
                    customerName = customerName,
                    customerPhone = customerPhone,
                    messageContent = message,
                    scheduledAt = scheduledTime,
                    status = "PENDING",
                    type = "SMS",
                    retryCount = 0,
                    createdAt = System.currentTimeMillis(),
                    updatedAt = System.currentTimeMillis()
                )

                // Save task locally
                taskDao.insert(task)

                // Check if auto-approve is enabled
                val autoApprove = approvalManager.isAutoApproveEnabled()
                if (autoApprove) {
                    Log.d(TAG, "Auto-approve enabled, approving task: $taskId")
                    approvalManager.handleApproval(taskId)
                } else {
                    Log.d(TAG, "Requesting approval for task: $taskId")
                    approvalManager.requestApproval(task.toDomainEntity())
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to handle single SMS request", e)
            }
        }
    }

    /**
     * Handles batch SMS approval request from web interface.
     * Shows batch approval notification for multiple tasks.
     */
    private fun handleBatchSmsRequest(data: Map<String, String>) {
        scope.launch {
            try {
                val tasksJson = data["tasks"] ?: return@launch
                val priority = data["priority"] ?: "NORMAL"

                // Parse tasks from JSON
                val tasks = parseBatchTasksJson(tasksJson)
                if (tasks.isEmpty()) {
                    Log.w(TAG, "No tasks in batch request")
                    return@launch
                }

                // Check for duplicates and insert new tasks
                val newTasks = mutableListOf<TaskEntity>()
                for (task in tasks) {
                    val existing = taskDao.getById(task.id)
                    if (existing == null) {
                        taskDao.insert(task)
                        newTasks.add(task)
                    }
                }

                if (newTasks.isEmpty()) {
                    Log.w(TAG, "All tasks already exist")
                    return@launch
                }

                // Check if auto-approve is enabled
                val autoApprove = approvalManager.isAutoApproveEnabled()
                if (autoApprove) {
                    val taskIds = newTasks.map { it.id }
                    Log.d(TAG, "Auto-approve enabled, approving batch: ${taskIds.size} tasks")
                    approvalManager.handleBatchApproval(taskIds)
                } else {
                    Log.d(TAG, "Requesting approval for batch: ${newTasks.size} tasks")
                    approvalManager.requestBatchApproval(newTasks.map { it.toDomainEntity() })
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to handle batch SMS request", e)
            }
        }
    }

    /**
     * Handles setting update from web interface.
     * Downloads latest settings and updates local preferences.
     */
    private fun handleSettingUpdate(data: Map<String, String>) {
        scope.launch {
            try {
                Log.d(TAG, "Fetching updated settings from server")
                val response = api.getSettings()

                if (response.isSuccessful) {
                    val settings = response.body()
                    if (settings != null) {
                        appPreferences.saveSettings(
                            autoApprove = settings.autoApprove,
                            autoApproveThreshold = settings.autoApproveThreshold,
                            dailyLimit = settings.dailyLimit,
                            notificationsEnabled = settings.notificationsEnabled,
                            soundEnabled = settings.soundEnabled,
                            vibrateEnabled = settings.vibrateEnabled
                        )
                        Log.d(TAG, "Settings updated successfully")
                    }
                } else {
                    Log.e(TAG, "Failed to fetch settings: ${response.code()}")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to handle setting update", e)
            }
        }
    }

    /**
     * Handles sync request from server.
     * Triggers bidirectional data synchronization.
     */
    private fun handleSyncRequest() {
        scope.launch {
            try {
                Log.d(TAG, "Sync request received, triggering sync service")
                SyncService.enqueueSync(applicationContext)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to handle sync request", e)
            }
        }
    }

    /**
     * Uploads FCM token to server for future push notifications.
     */
    private suspend fun uploadTokenToServer(token: String) {
        try {
            val request = com.bizconnect.v2.data.remote.api.dto.UpdateFcmTokenRequest(
                token = token,
                deviceId = android.provider.Settings.Secure.getString(
                    contentResolver,
                    android.provider.Settings.Secure.ANDROID_ID
                ) ?: "unknown",
                deviceModel = android.os.Build.MODEL,
                osVersion = android.os.Build.VERSION.RELEASE
            )

            val response = api.updateFcmToken(request)
            if (response.isSuccessful) {
                Log.d(TAG, "FCM token uploaded successfully")
            } else {
                Log.e(TAG, "Failed to upload FCM token: ${response.code()}")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Exception uploading FCM token", e)
        }
    }

    /**
     * Parses batch tasks from JSON string.
     * Expected format: [{"id":"...","customerId":"...","message":"...","scheduledTime":...}, ...]
     */
    private fun parseBatchTasksJson(json: String): List<TaskEntity> {
        return try {
            // Simple JSON parsing for batch tasks
            val tasks = mutableListOf<TaskEntity>()
            val taskPattern = """"id"\s*:\s*"([^"]+)".*?"customerId"\s*:\s*"([^"]+)".*?"message"\s*:\s*"([^"]+)".*?"scheduledTime"\s*:\s*(\d+)""".toRegex(RegexOption.DOT_MATCHES_ALL)

            var remaining = json
            while (true) {
                val match = taskPattern.find(remaining) ?: break
                val (id, customerId, message, scheduledTime) = match.destructured

                tasks.add(
                    TaskEntity(
                        id = id,
                        userId = appPreferences.getUserId() ?: "",
                        customerId = customerId,
                        customerName = "Web Task",
                        customerPhone = "",
                        messageContent = message,
                        scheduledAt = scheduledTime.toLong(),
                        status = "PENDING",
                        type = "SMS",
                        retryCount = 0,
                        createdAt = System.currentTimeMillis(),
                        updatedAt = System.currentTimeMillis()
                    )
                )

                remaining = remaining.substring(match.range.last + 1)
            }

            tasks
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse batch tasks JSON", e)
            emptyList()
        }
    }

    /**
     * Convert data layer TaskEntity to domain TaskEntity for ApprovalManager
     */
    private fun TaskEntity.toDomainEntity(): DomainTaskEntity {
        return DomainTaskEntity(
            id = id,
            recipientAddress = customerPhone,
            messageBody = messageContent,
            imageUri = imageUrl,
            priority = priority,
            status = status,
            createdAt = createdAt,
            updatedAt = updatedAt,
            retryCount = retryCount,
            maxRetries = maxRetries,
            customerName = customerName,
            message = messageContent
        )
    }
}
