package com.bizconnect.v2.data.repository

import com.bizconnect.v2.data.local.db.BizConnectDatabase
import com.bizconnect.v2.data.local.db.entity.TaskEntity
import com.bizconnect.v2.domain.model.Task
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.util.UUID

class TaskRepository(private val database: BizConnectDatabase) {
    private val taskDao = database.taskDao()

    fun getPendingTasks(): Flow<List<Task>> =
        taskDao.getPendingTasks().map { tasks ->
            tasks.map { it.toDomain() }
        }

    fun getTasksByStatus(status: String): Flow<List<Task>> =
        taskDao.getByStatus(status).map { tasks ->
            tasks.map { it.toDomain() }
        }

    suspend fun getTaskById(id: String): Task? =
        taskDao.getById(id)?.toDomain()

    suspend fun getTasksByUser(userId: String, limit: Int = 100): List<Task> =
        taskDao.getByUser(userId, limit).map { it.toDomain() }

    suspend fun createTask(
        userId: String,
        customerId: String?,
        customerPhone: String,
        customerName: String?,
        messageContent: String,
        type: String,
        priority: Int = 0,
        imageUrl: String? = null,
        isMms: Boolean = false,
        scheduledAt: Long? = null
    ): String {
        val id = UUID.randomUUID().toString()
        val task = Task(
            id = id,
            userId = userId,
            customerId = customerId,
            customerPhone = customerPhone,
            customerName = customerName,
            messageContent = messageContent,
            type = type,
            status = Task.STATUS_PENDING,
            priority = priority,
            imageUrl = imageUrl,
            isMms = isMms,
            scheduledAt = scheduledAt
        )
        taskDao.insert(task.toEntity())
        return id
    }

    suspend fun updateTaskStatus(id: String, status: String, errorMessage: String? = null) {
        if (errorMessage != null) {
            taskDao.updateStatusWithError(id, status, errorMessage)
        } else {
            taskDao.updateStatus(id, status)
        }
    }

    suspend fun updateTask(task: Task) =
        taskDao.update(task.toEntity())

    suspend fun deleteTask(task: Task) =
        taskDao.delete(task.toEntity())

    suspend fun deleteTaskById(id: String) =
        taskDao.deleteById(id)

    fun getScheduledTasks(): Flow<List<Task>> =
        taskDao.getScheduledTasks().map { tasks ->
            tasks.map { it.toDomain() }
        }

    suspend fun getDueScheduledTasks(currentTime: Long): List<Task> =
        taskDao.getDueScheduledTasks(currentTime).map { it.toDomain() }

    fun getPendingCount(): Flow<Int> =
        taskDao.getPendingCount()

    suspend fun markTaskAsNotified(id: String) =
        taskDao.markAsNotified(id)

    suspend fun incrementRetryCount(id: String) =
        taskDao.incrementRetryCount(id)

    suspend fun getRecentCompleted(userId: String): List<Task> =
        taskDao.getRecentCompleted(userId).map { it.toDomain() }

    fun getFailedCount(userId: String): Flow<Int> =
        taskDao.getFailedCount(userId)

    suspend fun getTasksByCustomer(customerId: String): List<Task> =
        taskDao.getByCustomer(customerId).map { it.toDomain() }

    suspend fun getHighPriorityTasks(): List<Task> =
        taskDao.getHighPriorityTasks().map { it.toDomain() }

    private fun TaskEntity.toDomain() = Task(
        id = id,
        userId = userId,
        customerId = customerId,
        customerPhone = customerPhone,
        customerName = customerName,
        messageContent = messageContent,
        type = type,
        status = status,
        priority = priority,
        imageUrl = imageUrl,
        localImagePath = localImagePath,
        isMms = isMms,
        scheduledAt = scheduledAt,
        createdAt = createdAt,
        updatedAt = updatedAt,
        completedAt = completedAt,
        errorMessage = errorMessage,
        retryCount = retryCount,
        maxRetries = maxRetries,
        isNotified = isNotified
    )

    private fun Task.toEntity() = TaskEntity(
        id = id,
        userId = userId,
        customerId = customerId,
        customerPhone = customerPhone,
        customerName = customerName,
        messageContent = messageContent,
        type = type,
        status = status,
        priority = priority,
        imageUrl = imageUrl,
        localImagePath = localImagePath,
        isMms = isMms,
        scheduledAt = scheduledAt,
        createdAt = createdAt,
        updatedAt = updatedAt,
        completedAt = completedAt,
        errorMessage = errorMessage,
        retryCount = retryCount,
        maxRetries = maxRetries,
        isNotified = isNotified
    )
}
