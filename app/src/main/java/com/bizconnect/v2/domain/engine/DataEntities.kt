package com.bizconnect.v2.domain.engine

/**
 * Domain-level interfaces and data classes for the engine layer.
 * These are NOT Room entities - they are domain abstractions used by engines.
 */

/**
 * SMS/MMS log entry for engine use
 */
data class SmsLogEntry(
    val id: String,
    val address: String,
    val body: String,
    val messageType: String,
    val success: Boolean,
    val errorMessage: String? = null,
    val timestamp: Long,
    val subscriptionId: Int = -1
)

/**
 * Daily limit tracking for engine use
 */
data class DailyLimit(
    val userId: String,
    val date: String,
    val count: Int = 0,
    val lastUpdated: Long = System.currentTimeMillis()
)

/**
 * Domain message entity
 */
data class MessageEntity(
    val id: String,
    val title: String,
    val content: String,
    val template: Boolean = false,
    val createdAt: Long = System.currentTimeMillis()
)

/**
 * Message repository interface for engine use
 */
interface MessageRepository {
    suspend fun saveMessage(message: MessageEntity)
    suspend fun getMessageById(id: String): MessageEntity?
    suspend fun deleteMessage(id: String)
}

/**
 * SMS log DAO interface for engine use
 */
interface SmsLogDao {
    suspend fun insert(log: SmsLogEntry)
    suspend fun insertAll(logs: List<SmsLogEntry>)
    suspend fun getDailyCount(userId: String): Int
    suspend fun getMessagesByDate(startTime: Long, endTime: Long): List<SmsLogEntry>
    suspend fun getMessagesByAddress(address: String): List<SmsLogEntry>
}

/**
 * Daily limit DAO interface for engine use
 */
interface DailyLimitDao {
    suspend fun incrementCount(userId: String)
    suspend fun resetDaily()
    suspend fun getCurrentCount(userId: String): Int
    suspend fun getOrCreateLimit(userId: String): DailyLimit
}

/**
 * App preferences interface for engine use
 */
interface AppPreferences {
    fun getUserId(): String
    fun getDailyLimitCount(): Int
    fun setDailyLimitCount(count: Int)
    fun getLimitMode(): String
    fun setLimitMode(mode: String)
    fun getQueueThrottleMs(): Long
    fun setQueueThrottleMs(ms: Long)
    fun getAutoApprove(): Boolean
}

/**
 * Task entity for queue storage (domain-level, not Room)
 */
data class TaskEntity(
    val id: String,
    val recipientAddress: String,
    val messageBody: String,
    val imageUri: String? = null,
    val subscriptionId: Int = -1,
    val priority: Int = 0,
    val status: String = "PENDING",
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val retryCount: Int = 0,
    val maxRetries: Int = 3,
    val metadata: String? = null,
    val customerName: String? = null,
    val message: String = messageBody,
    val approvedAt: Long? = null,
    val sentAt: Long? = null,
    val lastError: String? = null
)

/**
 * DAO interface for task persistence (domain-level, not Room)
 */
interface TaskDao {
    suspend fun insert(task: TaskEntity)
    suspend fun insertAll(tasks: List<TaskEntity>)
    suspend fun updateStatus(taskId: String, status: String)
    suspend fun updateAllPendingStatus(status: String)
    suspend fun getPendingTasks(limit: Int): List<TaskEntity>
    suspend fun getPendingCount(): Int
    suspend fun delete(taskId: String)
    fun observeStats(): kotlinx.coroutines.flow.Flow<TaskStats>
    suspend fun getTaskById(taskId: String): TaskEntity?
    suspend fun updateTask(task: TaskEntity)
}

/**
 * Task statistics for engine use
 */
data class TaskStats(
    val pending: Int,
    val completed: Int,
    val failed: Int
)

/**
 * Scheduled message entity (domain-level, not Room)
 */
data class ScheduledMessageEntity(
    val id: String,
    val recipientAddress: String,
    val messageBody: String,
    val imageUri: String? = null,
    val subscriptionId: Int = -1,
    val scheduledTime: Long,
    val isRecurring: Boolean = false,
    val recurringPattern: String = "",
    val status: String = "PENDING",
    val retryCount: Int = 0,
    val maxRetries: Int = 3,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

/**
 * DAO interface for scheduled messages (domain-level, not Room)
 */
interface ScheduledMessageDao {
    suspend fun insert(message: ScheduledMessageEntity)
    suspend fun update(message: ScheduledMessageEntity)
    suspend fun getMessageById(id: String): ScheduledMessageEntity?
    suspend fun updateStatus(id: String, status: String)
    suspend fun getDueMessages(currentTime: Long): List<ScheduledMessageEntity>
    suspend fun getNextPendingMessage(): ScheduledMessageEntity?
    suspend fun getAllPendingMessages(): List<ScheduledMessageEntity>
    suspend fun delete(id: String)
}

/**
 * SharedPreferences-backed implementation of AppPreferences
 */
class SharedPreferencesAppPreferences(
    private val preferences: android.content.SharedPreferences
) : AppPreferences {
    companion object {
        private const val KEY_USER_ID = "user_id"
        private const val KEY_DAILY_LIMIT = "daily_limit"
        private const val KEY_LIMIT_MODE = "limit_mode"
        private const val KEY_QUEUE_THROTTLE = "queue_throttle"

        private const val DEFAULT_DAILY_LIMIT = 1000
        private const val DEFAULT_LIMIT_MODE = "PER_DAY"
        private const val DEFAULT_QUEUE_THROTTLE = 500L
    }

    override fun getUserId(): String {
        return preferences.getString(KEY_USER_ID, "") ?: ""
    }

    override fun getDailyLimitCount(): Int {
        return preferences.getInt(KEY_DAILY_LIMIT, DEFAULT_DAILY_LIMIT)
    }

    override fun setDailyLimitCount(count: Int) {
        preferences.edit().putInt(KEY_DAILY_LIMIT, count).apply()
    }

    override fun getLimitMode(): String {
        return preferences.getString(KEY_LIMIT_MODE, DEFAULT_LIMIT_MODE) ?: DEFAULT_LIMIT_MODE
    }

    override fun setLimitMode(mode: String) {
        preferences.edit().putString(KEY_LIMIT_MODE, mode).apply()
    }

    override fun getQueueThrottleMs(): Long {
        return preferences.getLong(KEY_QUEUE_THROTTLE, DEFAULT_QUEUE_THROTTLE)
    }

    override fun setQueueThrottleMs(ms: Long) {
        preferences.edit().putLong(KEY_QUEUE_THROTTLE, ms).apply()
    }

    override fun getAutoApprove(): Boolean {
        return preferences.getBoolean("auto_approve", false)
    }
}
