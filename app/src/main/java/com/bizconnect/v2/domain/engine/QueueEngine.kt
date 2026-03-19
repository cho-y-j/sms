package com.bizconnect.v2.domain.engine

import android.net.Uri
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

/**
 * Priority-based message queue for bulk sending.
 * Features:
 * - Persistent queue (backed by Room DB)
 * - Priority-based processing
 * - Configurable throttle intervals
 * - Pause/Resume capability
 * - Real-time queue statistics
 * - Proper cancellation handling
 */
class QueueEngine @Inject constructor(
    private val smsEngine: SmsEngine,
    private val taskDao: TaskDao,
    private val appPreferences: AppPreferences
) {
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private var processingJob: Job? = null
    private var isProcessing = false
    private var isPaused = false

    private val _queueState = MutableStateFlow<QueueState>(QueueState.Idle)
    val queueState: StateFlow<QueueState> = _queueState.asStateFlow()

    private var successCount = 0
    private var failCount = 0

    companion object {
        private const val TAG = "QueueEngine"
        const val DEFAULT_THROTTLE_MS = 500L // 500ms between sends
        const val BATCH_SIZE = 50
    }

    /**
     * Add a single task to the queue
     */
    suspend fun enqueue(task: TaskEntity) = withContext(Dispatchers.IO) {
        val taskWithTimestamp = task.copy(
            createdAt = System.currentTimeMillis(),
            status = "PENDING"
        )
        taskDao.insert(taskWithTimestamp)
    }

    /**
     * Add a batch of tasks to the queue
     */
    suspend fun enqueueBatch(tasks: List<TaskEntity>) = withContext(Dispatchers.IO) {
        val tasksWithTimestamp = tasks.map { task ->
            task.copy(
                createdAt = System.currentTimeMillis(),
                status = "PENDING"
            )
        }
        taskDao.insertAll(tasksWithTimestamp)
    }

    /**
     * Enqueue a list of tasks (alias for enqueueBatch, used by ApprovalManager)
     */
    suspend fun enqueueTasks(tasks: List<TaskEntity>) {
        enqueueBatch(tasks)
    }

    /**
     * Start processing the queue
     * Processes tasks in priority order with throttling
     */
    fun startProcessing() {
        if (isProcessing) return

        isProcessing = true
        isPaused = false
        successCount = 0
        failCount = 0

        processingJob = scope.launch {
            processQueue()
        }
    }

    /**
     * Pause queue processing (can be resumed)
     */
    fun pauseProcessing() {
        isPaused = true
        _queueState.value = QueueState.Paused(
            remaining = runBlocking { taskDao.getPendingCount() }
        )
    }

    /**
     * Resume paused queue processing
     */
    fun resumeProcessing() {
        if (!isPaused) return
        isPaused = false
    }

    /**
     * Cancel a specific task
     */
    suspend fun cancelTask(taskId: String) = withContext(Dispatchers.IO) {
        taskDao.updateStatus(taskId, "CANCELLED")
    }

    /**
     * Cancel all pending tasks
     */
    suspend fun cancelAll() = withContext(Dispatchers.IO) {
        taskDao.updateAllPendingStatus("CANCELLED")
    }

    /**
     * Get real-time queue statistics
     */
    fun getQueueStats(): Flow<QueueStats> {
        return taskDao.observeStats().map { stats ->
            QueueStats(
                pendingCount = stats.pending,
                processingCount = if (isProcessing && !isPaused) 1 else 0,
                completedCount = stats.completed,
                failedCount = stats.failed,
                totalCount = stats.pending + stats.completed + stats.failed
            )
        }
    }

    /**
     * Stop processing and clean up resources
     */
    fun stop() {
        processingJob?.cancel()
        isProcessing = false
        isPaused = false
        _queueState.value = QueueState.Idle
    }

    /**
     * Destroy the engine and cancel all operations
     */
    fun destroy() {
        stop()
        scope.cancel()
    }

    /**
     * Core queue processing loop
     */
    private suspend fun processQueue() = withContext(Dispatchers.IO) {
        try {
            while (isProcessing) {
                // Handle pause
                while (isPaused) {
                    delay(100)
                }

                // Fetch next batch of pending tasks
                val pendingTasks = taskDao.getPendingTasks(BATCH_SIZE)

                if (pendingTasks.isEmpty()) {
                    // Queue is empty
                    isProcessing = false
                    _queueState.value = QueueState.Completed(successCount, failCount)
                    break
                }

                // Process each task
                for (task in pendingTasks) {
                    if (!isProcessing) break

                    while (isPaused) {
                        delay(100)
                    }

                    updateState(task, pendingTasks.size)

                    val success = processTask(task)

                    if (success) {
                        taskDao.updateStatus(task.id, "COMPLETED")
                        successCount++
                    } else {
                        taskDao.updateStatus(task.id, "FAILED")
                        failCount++
                    }

                    applyThrottle()
                }
            }
        } catch (e: Exception) {
            isProcessing = false
            _queueState.value = QueueState.Error("Queue processing error: ${e.message}")
            e.printStackTrace()
        }
    }

    /**
     * Process a single task - determines message type and sends appropriately
     */
    private suspend fun processTask(task: TaskEntity): Boolean = withContext(Dispatchers.IO) {
        return@withContext try {
            val result = when {
                task.imageUri != null && task.imageUri.isNotBlank() -> {
                    smsEngine.sendMms(
                        address = task.recipientAddress,
                        body = task.messageBody,
                        imageUri = Uri.parse(task.imageUri),
                        subscriptionId = task.subscriptionId
                    )
                }
                task.messageBody.toByteArray(Charsets.UTF_8).size > SmsEngine.SMS_MAX_BYTES -> {
                    smsEngine.sendLms(
                        address = task.recipientAddress,
                        body = task.messageBody,
                        subscriptionId = task.subscriptionId
                    )
                }
                else -> {
                    smsEngine.sendSms(
                        address = task.recipientAddress,
                        body = task.messageBody,
                        subscriptionId = task.subscriptionId
                    )
                }
            }

            result.success
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Apply throttle delay between sends based on configuration
     */
    private suspend fun applyThrottle() {
        val throttleMs = appPreferences.getQueueThrottleMs()
        if (throttleMs > 0) {
            delay(throttleMs)
        }
    }

    /**
     * Update queue state during processing
     */
    private fun updateState(current: TaskEntity, remaining: Int) {
        if (isProcessing && !isPaused) {
            _queueState.value = QueueState.Processing(
                current = successCount + failCount + 1,
                total = successCount + failCount + remaining,
                currentTask = current
            )
        }
    }
}

/**
 * Queue processing states
 */
sealed class QueueState {
    object Idle : QueueState()

    data class Processing(
        val current: Int,
        val total: Int,
        val currentTask: TaskEntity
    ) : QueueState()

    data class Paused(val remaining: Int) : QueueState()

    data class Completed(
        val successCount: Int,
        val failCount: Int
    ) : QueueState()

    data class Error(val message: String) : QueueState()
}

/**
 * Queue statistics
 */
data class QueueStats(
    val pendingCount: Int,
    val processingCount: Int,
    val completedCount: Int,
    val failedCount: Int,
    val totalCount: Int
) {
    val successRate: Float
        get() = if (totalCount == 0) 0f else completedCount.toFloat() / totalCount
}

