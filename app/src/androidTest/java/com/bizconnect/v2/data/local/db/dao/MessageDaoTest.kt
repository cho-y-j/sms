package com.bizconnect.v2.data.local.db.dao

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.bizconnect.v2.data.local.db.BizConnectDatabase
import com.bizconnect.v2.data.model.Message
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class MessageDaoTest {

    private lateinit var database: BizConnectDatabase
    private lateinit var dao: MessageDao

    @Before
    fun setup() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            BizConnectDatabase::class.java
        ).allowMainThreadQueries().build()

        dao = database.messageDao()
    }

    @After
    fun teardown() {
        database.close()
    }

    @Test
    fun insertAndGetMessage() {
        val message = Message(
            id = UUID.randomUUID().toString(),
            conversationId = "conv-1",
            senderName = "Kim Min-jun",
            body = "Hello there",
            timestamp = System.currentTimeMillis(),
            isIncoming = true,
            isRead = false
        )

        dao.insert(message)

        val retrieved = dao.getMessageById(message.id)
        assertNotNull(retrieved)
        assertEquals(message.body, retrieved?.body)
    }

    @Test
    fun getMessagesByConversation() {
        val convId = "conv-1"

        repeat(5) { index ->
            dao.insert(
                Message(
                    id = "msg-$index",
                    conversationId = convId,
                    senderName = "Sender $index",
                    body = "Message body $index",
                    timestamp = System.currentTimeMillis() + index * 1000,
                    isIncoming = index % 2 == 0,
                    isRead = false
                )
            )
        }

        val messages = dao.getMessagesByConversation(convId)

        assertEquals(5, messages.size)
    }

    @Test
    fun messagesOrderedByTimestamp() {
        val now = System.currentTimeMillis()
        val convId = "conv-1"

        dao.insert(
            Message(
                id = "msg-1",
                conversationId = convId,
                senderName = "User A",
                body = "First message",
                timestamp = now,
                isIncoming = true,
                isRead = false
            )
        )

        dao.insert(
            Message(
                id = "msg-2",
                conversationId = convId,
                senderName = "User B",
                body = "Second message",
                timestamp = now + 1000,
                isIncoming = false,
                isRead = false
            )
        )

        dao.insert(
            Message(
                id = "msg-3",
                conversationId = convId,
                senderName = "User A",
                body = "Third message",
                timestamp = now + 2000,
                isIncoming = true,
                isRead = false
            )
        )

        val messages = dao.getMessagesByConversation(convId)

        assertEquals("First message", messages[0].body)
        assertEquals("Second message", messages[1].body)
        assertEquals("Third message", messages[2].body)
    }

    @Test
    fun markMessageAsRead() {
        val message = Message(
            id = "msg-read",
            conversationId = "conv-1",
            senderName = "Sender",
            body = "Message",
            timestamp = System.currentTimeMillis(),
            isIncoming = true,
            isRead = false
        )

        dao.insert(message)
        dao.markAsRead("msg-read")

        val updated = dao.getMessageById("msg-read")
        assertTrue(updated?.isRead ?: false)
    }

    @Test
    fun deleteMessage() {
        val message = Message(
            id = "msg-delete",
            conversationId = "conv-1",
            senderName = "Sender",
            body = "To delete",
            timestamp = System.currentTimeMillis(),
            isIncoming = true,
            isRead = false
        )

        dao.insert(message)
        assertEquals(1, dao.getMessagesByConversation("conv-1").size)

        dao.delete("msg-delete")

        assertEquals(0, dao.getMessagesByConversation("conv-1").size)
    }

    @Test
    fun deleteMessagesOlderThan() {
        val now = System.currentTimeMillis()
        val convId = "conv-1"
        val cutoffTime = now - 5000

        dao.insert(
            Message(
                id = "msg-old",
                conversationId = convId,
                senderName = "Sender",
                body = "Old message",
                timestamp = now - 10000,
                isIncoming = true,
                isRead = false
            )
        )

        dao.insert(
            Message(
                id = "msg-new",
                conversationId = convId,
                senderName = "Sender",
                body = "New message",
                timestamp = now,
                isIncoming = true,
                isRead = false
            )
        )

        dao.deleteMessagesOlderThan(cutoffTime)

        val remaining = dao.getMessagesByConversation(convId)
        assertEquals(1, remaining.size)
        assertEquals("New message", remaining[0].body)
    }

    @Test
    fun searchMessages() {
        val convId = "conv-1"

        dao.insert(
            Message(
                id = "msg-1",
                conversationId = convId,
                senderName = "Sender",
                body = "This is a test message",
                timestamp = System.currentTimeMillis(),
                isIncoming = true,
                isRead = false
            )
        )

        dao.insert(
            Message(
                id = "msg-2",
                conversationId = convId,
                senderName = "Sender",
                body = "This is another message",
                timestamp = System.currentTimeMillis(),
                isIncoming = true,
                isRead = false
            )
        )

        val results = dao.searchMessages("test")

        assertEquals(1, results.size)
        assertEquals("This is a test message", results[0].body)
    }

    @Test
    fun getUnreadMessagesCount() {
        val convId = "conv-1"

        dao.insert(
            Message(
                id = "msg-1",
                conversationId = convId,
                senderName = "Sender",
                body = "Unread 1",
                timestamp = System.currentTimeMillis(),
                isIncoming = true,
                isRead = false
            )
        )

        dao.insert(
            Message(
                id = "msg-2",
                conversationId = convId,
                senderName = "Sender",
                body = "Read",
                timestamp = System.currentTimeMillis(),
                isIncoming = true,
                isRead = true
            )
        )

        val unreadCount = dao.getUnreadMessagesCount(convId)
        assertEquals(1, unreadCount)
    }
}

interface MessageDao {
    fun insert(message: Message)
    fun getMessageById(id: String): Message?
    fun getMessagesByConversation(conversationId: String): List<Message>
    fun markAsRead(messageId: String)
    fun delete(messageId: String)
    fun deleteMessagesOlderThan(timestamp: Long)
    fun searchMessages(query: String): List<Message>
    fun getUnreadMessagesCount(conversationId: String): Int
}
