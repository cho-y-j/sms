package com.bizconnect.v2.domain.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.UUID

class QueueEngineTest {

    private lateinit var queueEngine: QueueEngine

    @Before
    fun setup() {
        queueEngine = QueueEngine()
    }

    @Test
    fun enqueueTask() {
        val task = Task(
            id = UUID.randomUUID().toString(),
            type = TaskType.SEND_MESSAGE,
            priority = TaskPriority.NORMAL,
            payload = "Test message"
        )

        val taskId = queueEngine.enqueue(task)

        assertTrue(taskId.isNotBlank())
        assertEquals(1, queueEngine.getQueueSize())
    }

    @Test
    fun processTasksInPriorityOrder() {
        queueEngine.enqueue(
            Task(
                id = "task1",
                type = TaskType.SEND_MESSAGE,
                priority = TaskPriority.LOW,
                payload = "Low priority"
            )
        )
        queueEngine.enqueue(
            Task(
                id = "task2",
                type = TaskType.SYNC_DATA,
                priority = TaskPriority.CRITICAL,
                payload = "Critical priority"
            )
        )
        queueEngine.enqueue(
            Task(
                id = "task3",
                type = TaskType.SEND_MESSAGE,
                priority = TaskPriority.NORMAL,
                payload = "Normal priority"
            )
        )

        val task1 = queueEngine.dequeue()
        assertEquals("task2", task1?.id)

        val task2 = queueEngine.dequeue()
        assertEquals("task3", task2?.id)

        val task3 = queueEngine.dequeue()
        assertEquals("task1", task3?.id)
    }

    @Test
    fun pauseAndResumeProcessing() {
        queueEngine.enqueue(
            Task(
                id = "task1",
                type = TaskType.SEND_MESSAGE,
                priority = TaskPriority.NORMAL,
                payload = "Message 1"
            )
        )

        queueEngine.pause()
        assertTrue(queueEngine.isPaused())

        queueEngine.resume()
        assertFalse(queueEngine.isPaused())
    }

    @Test
    fun cancelSpecificTask() {
        val taskId = queueEngine.enqueue(
            Task(
                id = "task-to-cancel",
                type = TaskType.SEND_MESSAGE,
                priority = TaskPriority.NORMAL,
                payload = "Will be cancelled"
            )
        )

        assertTrue(queueEngine.cancelTask(taskId))
        assertEquals(0, queueEngine.getQueueSize())
    }

    @Test
    fun cancelAllTasks() {
        queueEngine.enqueue(
            Task(
                id = "task1",
                type = TaskType.SEND_MESSAGE,
                priority = TaskPriority.NORMAL,
                payload = "Message 1"
            )
        )
        queueEngine.enqueue(
            Task(
                id = "task2",
                type = TaskType.SYNC_DATA,
                priority = TaskPriority.NORMAL,
                payload = "Sync 1"
            )
        )

        queueEngine.cancelAll()

        assertEquals(0, queueEngine.getQueueSize())
    }

    @Test
    fun retryFailedTask() {
        val task = Task(
            id = "task-retry",
            type = TaskType.SEND_MESSAGE,
            priority = TaskPriority.NORMAL,
            payload = "Message"
        )

        queueEngine.enqueue(task)
        val dequeuedTask = queueEngine.dequeue()

        val retried = queueEngine.retryTask(dequeuedTask!!)
        assertTrue(retried)
        assertEquals(1, queueEngine.getQueueSize())
    }

    @Test
    fun maxRetryLimit() {
        val task = Task(
            id = "task-max-retry",
            type = TaskType.SEND_MESSAGE,
            priority = TaskPriority.NORMAL,
            payload = "Message",
            retryCount = 3
        )

        val retried = queueEngine.retryTask(task)

        assertFalse(retried)
    }

    @Test
    fun queueStatisticsUpdate() {
        queueEngine.enqueue(
            Task(
                id = "task1",
                type = TaskType.SEND_MESSAGE,
                priority = TaskPriority.NORMAL,
                payload = "Message 1"
            )
        )
        queueEngine.enqueue(
            Task(
                id = "task2",
                type = TaskType.SYNC_DATA,
                priority = TaskPriority.HIGH,
                payload = "Sync 1"
            )
        )

        val stats = queueEngine.getStatistics()

        assertEquals(2, stats.totalTasks)
        assertEquals(1, stats.highPriorityTasks)
    }
}

enum class TaskType {
    SEND_MESSAGE,
    SYNC_DATA,
    DOWNLOAD_ATTACHMENT,
    BACKUP_DATA
}

enum class TaskPriority(val value: Int) {
    LOW(1),
    NORMAL(2),
    HIGH(3),
    CRITICAL(4)
}

data class Task(
    val id: String,
    val type: TaskType,
    val priority: TaskPriority,
    val payload: String,
    val retryCount: Int = 0,
    val maxRetries: Int = 3
)

data class QueueStatistics(
    val totalTasks: Int,
    val highPriorityTasks: Int,
    val averageWaitTime: Long
)

class QueueEngine {
    private val taskQueue = PriorityQueue<Task>(compareBy { it.priority.value }.reversed())
    private var paused = false
    private var processedCount = 0

    fun enqueue(task: Task): String {
        taskQueue.add(task)
        return task.id
    }

    fun dequeue(): Task? {
        return if (!paused && taskQueue.isNotEmpty()) {
            processedCount++
            taskQueue.poll()
        } else {
            null
        }
    }

    fun pause() {
        paused = true
    }

    fun resume() {
        paused = false
    }

    fun isPaused(): Boolean = paused

    fun cancelTask(taskId: String): Boolean {
        return taskQueue.removeIf { it.id == taskId }
    }

    fun cancelAll() {
        taskQueue.clear()
    }

    fun retryTask(task: Task): Boolean {
        if (task.retryCount < task.maxRetries) {
            val retryTask = task.copy(retryCount = task.retryCount + 1)
            taskQueue.add(retryTask)
            return true
        }
        return false
    }

    fun getQueueSize(): Int = taskQueue.size

    fun getStatistics(): QueueStatistics {
        val highPriorityCount = taskQueue.count { it.priority.value >= TaskPriority.HIGH.value }
        return QueueStatistics(
            totalTasks = taskQueue.size,
            highPriorityTasks = highPriorityCount,
            averageWaitTime = 0L
        )
    }
}

class PriorityQueue<T>(private val comparator: Comparator<T>) {
    private val elements = mutableListOf<T>()

    fun add(element: T) {
        elements.add(element)
        elements.sortWith(comparator)
    }

    fun poll(): T? = if (elements.isNotEmpty()) elements.removeAt(0) else null

    fun peek(): T? = elements.firstOrNull()

    fun isNotEmpty() = elements.isNotEmpty()

    fun removeIf(predicate: (T) -> Boolean) = elements.removeIf(predicate)

    fun clear() = elements.clear()

    fun count(predicate: (T) -> Boolean) = elements.count(predicate)

    val size: Int get() = elements.size
}
