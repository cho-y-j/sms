package com.bizconnect.v2.service

import android.os.Build
import android.telephony.SmsManager
import android.util.Log
import com.bizconnect.v2.data.local.db.dao.TaskDao
import com.bizconnect.v2.data.local.db.entity.TaskEntity
import com.bizconnect.v2.data.preferences.AppPreferences
import com.bizconnect.v2.data.remote.TokenManager
import com.bizconnect.v2.data.remote.api.BizConnectApi
import com.bizconnect.v2.domain.engine.ApprovalManager
import com.bizconnect.v2.domain.engine.TaskEntity as DomainTaskEntity
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
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
        private const val TYPE_WEB_SMS_BATCH = "web_sms_batch"
        private const val TYPE_SETTING_UPDATE = "setting_update"
        private const val TYPE_SYNC_REQUEST = "sync_request"

        private const val SERVER_BASE_URL = "https://sm.on1.kr"
        private const val SMS_SEND_DELAY_MS = 3000L  // 3초 간격 (20/min throttle)
    }

    @Inject
    lateinit var taskDao: TaskDao

    @Inject
    lateinit var appPreferences: AppPreferences

    @Inject
    lateinit var approvalManager: ApprovalManager

    @Inject
    lateinit var api: BizConnectApi

    @Inject
    lateinit var tokenManager: TokenManager

    private val scope = CoroutineScope(Dispatchers.Default)

    override fun onDestroy() {
        super.onDestroy()
        scope.cancel()
    }

    /**
     * Called when FCM token is generated or refreshed.
     * Uploads token to server for future push notifications.
     */
    override fun onNewToken(token: String) {
        super.onNewToken(token)
        Log.d(TAG, "FCM token refreshed")

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
                TYPE_WEB_SMS_BATCH -> handleWebSmsBatch(message.data)
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
     * Handles web-originated SMS batch sending.
     * Fetches pending messages from server, sends each via SmsManager,
     * and reports status back to server after each send.
     */
    private fun handleWebSmsBatch(data: Map<String, String>) {
        scope.launch {
            val jobId = data["jobId"]
            val count = data["count"]?.toIntOrNull() ?: 0

            if (jobId.isNullOrEmpty()) {
                Log.e(TAG, "web_sms_batch: missing jobId")
                return@launch
            }

            Log.d(TAG, "web_sms_batch: jobId=$jobId, expected count=$count")

            try {
                // Step 1: FCM 데이터에 메시지가 포함되어 있으면 바로 사용 (서버 재호출 불필요)
                val messagesJson = data["messages"]
                val pendingMessages: List<JSONObject> = if (!messagesJson.isNullOrBlank()) {
                    try {
                        val arr = JSONArray(messagesJson)
                        (0 until arr.length()).map { arr.getJSONObject(it) }
                    } catch (e: Exception) {
                        Log.w(TAG, "web_sms_batch: failed to parse inline messages, fetching from server")
                        fetchPendingMessages(jobId)
                    }
                } else {
                    fetchPendingMessages(jobId)
                }

                if (pendingMessages.isEmpty()) {
                    Log.w(TAG, "web_sms_batch: no pending messages for jobId=$jobId")
                    return@launch
                }

                Log.d(TAG, "web_sms_batch: fetched ${pendingMessages.size} pending messages")

                val smsManager = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    applicationContext.getSystemService(SmsManager::class.java)
                } else {
                    @Suppress("DEPRECATION")
                    SmsManager.getDefault()
                }

                // Step 2: Send each message with 3-second delay
                for ((index, msg) in pendingMessages.withIndex()) {
                    val msgId = msg.getString("id")
                    val phone = msg.getString("phone")
                    val body = msg.getString("message")

                    if (phone.isBlank() || body.isBlank()) {
                        Log.w(TAG, "web_sms_batch: skipping message $msgId - empty phone or body")
                        reportSmsStatus(msgId, "failed", "Empty phone number or message body")
                        continue
                    }

                    // Add delay between messages (skip delay for the first one)
                    if (index > 0) {
                        delay(SMS_SEND_DELAY_MS)
                    }

                    try {
                        // Split long messages and send
                        val parts = smsManager.divideMessage(body)
                        if (parts.size == 1) {
                            smsManager.sendTextMessage(phone, null, body, null, null)
                        } else {
                            smsManager.sendMultipartTextMessage(phone, null, parts, null, null)
                        }

                        Log.d(TAG, "web_sms_batch: sent SMS ${index + 1}/${pendingMessages.size} to $phone (id=$msgId)")
                        try { reportSmsStatus(msgId, "sent", null) } catch (_: Exception) { Log.w(TAG, "Status report failed for $msgId (sent)") }
                    } catch (e: Exception) {
                        val errorMsg = e.message ?: "Unknown send error"
                        Log.e(TAG, "web_sms_batch: failed to send SMS to $phone (id=$msgId)", e)
                        try { reportSmsStatus(msgId, "failed", errorMsg) } catch (_: Exception) { Log.w(TAG, "Status report failed for $msgId (failed)") }
                    }
                }

                Log.d(TAG, "web_sms_batch: completed jobId=$jobId")
            } catch (e: Exception) {
                Log.e(TAG, "web_sms_batch: fatal error processing jobId=$jobId", e)
            }
        }
    }

    /**
     * Fetches pending SMS messages from server for a given job.
     * GET /api/user/sms/pending?jobId={jobId}
     * Uses TokenManager for automatic 401 retry with token refresh.
     */
    private fun fetchPendingMessages(jobId: String): List<JSONObject> {
        val requestBuilder = Request.Builder()
            .url("$SERVER_BASE_URL/api/user/sms/pending?jobId=$jobId")
            .get()

        val response = tokenManager.executeAuthenticated(requestBuilder)
        return response.use { resp ->
            if (!resp.isSuccessful) {
                Log.e(TAG, "fetchPendingMessages: HTTP ${resp.code} for jobId=$jobId")
                return emptyList()
            }

            val bodyStr = resp.body?.string()
            if (bodyStr.isNullOrEmpty()) {
                Log.e(TAG, "fetchPendingMessages: empty response body for jobId=$jobId")
                return emptyList()
            }

            try {
                val jsonArray = JSONArray(bodyStr)
                val messages = mutableListOf<JSONObject>()
                for (i in 0 until jsonArray.length()) {
                    messages.add(jsonArray.getJSONObject(i))
                }
                messages
            } catch (e: Exception) {
                // Try parsing as object with "data" or "messages" key
                try {
                    val jsonObj = JSONObject(bodyStr)
                    val jsonArray = if (jsonObj.has("data")) jsonObj.getJSONArray("data")
                        else jsonObj.getJSONArray("messages")
                    val messages = mutableListOf<JSONObject>()
                    for (i in 0 until jsonArray.length()) {
                        messages.add(jsonArray.getJSONObject(i))
                    }
                    messages
                } catch (e2: Exception) {
                    Log.e(TAG, "fetchPendingMessages: failed to parse response", e2)
                    emptyList()
                }
            }
        }
    }

    /**
     * Reports SMS send status back to server.
     * PUT /api/user/sms/{id}/status
     * Uses TokenManager for automatic 401 retry with token refresh.
     */
    private fun reportSmsStatus(
        messageId: String,
        status: String,
        error: String?
    ) {
        try {
            val json = JSONObject().apply {
                put("status", status)
                if (error != null) {
                    put("error", error)
                }
            }

            val requestBody = json.toString()
                .toRequestBody("application/json".toMediaType())

            val requestBuilder = Request.Builder()
                .url("$SERVER_BASE_URL/api/user/sms/$messageId/status")
                .put(requestBody)

            val response = tokenManager.executeAuthenticated(requestBuilder)
            response.use { resp ->
                if (!resp.isSuccessful) {
                    Log.w(TAG, "reportSmsStatus: HTTP ${resp.code} for id=$messageId")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "reportSmsStatus: failed for id=$messageId", e)
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
