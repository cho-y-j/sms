package com.bizconnect.v2.data.local.db.dao

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.bizconnect.v2.data.local.db.BizConnectDatabase
import com.bizconnect.v2.data.model.Task
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class TaskDaoTest {

    private lateinit var database: BizConnectDatabase
    private lateinit var dao: TaskDao

    @Before
    fun setup() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            BizConnectDatabase::class.java
        ).allowMainThreadQueries().build()

        dao = database.taskDao()
    }

    @After
    fun teardown() {
        database.close()
    }

    @Test
    fun insertAndGetTask() {
        val task = Task(
            id = UUID.randomUUID().toString(),
            conversationId = "conv-1",
            title = "Follow up with customer",
            description = "Call customer Kim Min-jun",
            dueDate = System.currentTimeMillis(),
            isCompleted = false,
            createdAt = System.currentTimeMillis()
        )

        dao.insert(task)

        val retrieved = dao.getTaskById(task.id)
        assertNotNull(retrieved)
        assertEquals(task.title, retrieved?.title)
    }

    @Test
    fun getTasksByConversation() {
        val convId = "conv-1"

        repeat(3) { index ->
            dao.insert(
                Task(
                    id = "task-$index",
                    conversationId = convId,
                    title = "Task $index",
                    description = "Description $index",
                    dueDate = System.currentTimeMillis() + index * 86400000,
                    isCompleted = false,
                    createdAt = System.currentTimeMillis()
                )
            )
        }

        val tasks = dao.getTasksByConversation(convId)

        assertEquals(3, tasks.size)
    }

    @Test
    fun getPendingTasks() {
        val convId = "conv-1"

        dao.insert(
            Task(
                id = "task-pending",
                conversationId = convId,
                title = "Pending task",
                description = "Not done yet",
                dueDate = System.currentTimeMillis(),
                isCompleted = false,
                createdAt = System.currentTimeMillis()
            )
        )

        dao.insert(
            Task(
                id = "task-completed",
                conversationId = convId,
                title = "Completed task",
                description = "Already done",
                dueDate = System.currentTimeMillis(),
                isCompleted = true,
                createdAt = System.currentTimeMillis()
            )
        )

        val pendingTasks = dao.getPendingTasks()

        assertEquals(1, pendingTasks.size)
        assertEquals("task-pending", pendingTasks[0].id)
    }

    @Test
    fun getTasksByDueDate() {
        val now = System.currentTimeMillis()
        val tomorrow = now + 86400000
        val nextWeek = now + 604800000

        dao.insert(
            Task(
                id = "task-today",
                conversationId = "conv-1",
                title = "Due today",
                description = "Today",
                dueDate = now,
                isCompleted = false,
                createdAt = now
            )
        )

        dao.insert(
            Task(
                id = "task-tomorrow",
                conversationId = "conv-1",
                title = "Due tomorrow",
                description = "Tomorrow",
                dueDate = tomorrow,
                isCompleted = false,
                createdAt = now
            )
        )

        dao.insert(
            Task(
                id = "task-future",
                conversationId = "conv-1",
                title = "Due next week",
                description = "Next week",
                dueDate = nextWeek,
                isCompleted = false,
                createdAt = now
            )
        )

        val upcomingTasks = dao.getUpcomingTasks(now, tomorrow + 1)

        assertEquals(2, upcomingTasks.size)
    }

    @Test
    fun markTaskAsCompleted() {
        val task = Task(
            id = "task-complete",
            conversationId = "conv-1",
            title = "Task to complete",
            description = "Complete this",
            dueDate = System.currentTimeMillis(),
            isCompleted = false,
            createdAt = System.currentTimeMillis()
        )

        dao.insert(task)
        dao.markAsCompleted("task-complete")

        val updated = dao.getTaskById("task-complete")
        assertTrue(updated?.isCompleted ?: false)
    }

    @Test
    fun deleteTask() {
        val task = Task(
            id = "task-delete",
            conversationId = "conv-1",
            title = "Task to delete",
            description = "Delete this",
            dueDate = System.currentTimeMillis(),
            isCompleted = false,
            createdAt = System.currentTimeMillis()
        )

        dao.insert(task)
        assertEquals(1, dao.getTasksByConversation("conv-1").size)

        dao.delete("task-delete")

        assertEquals(0, dao.getTasksByConversation("conv-1").size)
    }

    @Test
    fun getOverdueTasks() {
        val now = System.currentTimeMillis()

        dao.insert(
            Task(
                id = "task-overdue",
                conversationId = "conv-1",
                title = "Overdue task",
                description = "This was due yesterday",
                dueDate = now - 86400000,
                isCompleted = false,
                createdAt = now
            )
        )

        dao.insert(
            Task(
                id = "task-ontime",
                conversationId = "conv-1",
                title = "On time task",
                description = "Due tomorrow",
                dueDate = now + 86400000,
                isCompleted = false,
                createdAt = now
            )
        )

        val overdueTasks = dao.getOverdueTasks(now)

        assertEquals(1, overdueTasks.size)
        assertEquals("task-overdue", overdueTasks[0].id)
    }

    @Test
    fun updateTask() {
        val original = Task(
            id = "task-update",
            conversationId = "conv-1",
            title = "Original title",
            description = "Original description",
            dueDate = System.currentTimeMillis(),
            isCompleted = false,
            createdAt = System.currentTimeMillis()
        )

        dao.insert(original)

        val updated = original.copy(
            title = "Updated title",
            description = "Updated description"
        )

        dao.update(updated)

        val retrieved = dao.getTaskById("task-update")
        assertEquals("Updated title", retrieved?.title)
        assertEquals("Updated description", retrieved?.description)
    }
}

interface TaskDao {
    fun insert(task: Task)
    fun update(task: Task)
    fun getTaskById(id: String): Task?
    fun getTasksByConversation(conversationId: String): List<Task>
    fun getPendingTasks(): List<Task>
    fun getUpcomingTasks(startDate: Long, endDate: Long): List<Task>
    fun getOverdueTasks(currentTime: Long): List<Task>
    fun markAsCompleted(taskId: String)
    fun delete(taskId: String)
}
