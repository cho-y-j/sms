package com.bizconnect.v2.data.local.db.dao

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.bizconnect.v2.data.local.db.BizConnectDatabase
import com.bizconnect.v2.data.model.Conversation
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class ConversationDaoTest {

    private lateinit var database: BizConnectDatabase
    private lateinit var dao: ConversationDao

    @Before
    fun setup() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            BizConnectDatabase::class.java
        ).allowMainThreadQueries().build()

        dao = database.conversationDao()
    }

    @After
    fun teardown() {
        database.close()
    }

    @Test
    fun insertAndGetConversation() {
        val conversation = Conversation(
            id = UUID.randomUUID().toString(),
            customerId = "customer-1",
            customerName = "Kim Min-jun",
            customerPhone = "01012345678",
            lastMessage = "Hello there",
            lastMessageTime = System.currentTimeMillis(),
            unreadCount = 0,
            isPinned = false
        )

        dao.insert(conversation)

        val retrieved = dao.getConversationById(conversation.id)
        assertNotNull(retrieved)
        assertEquals(conversation.customerName, retrieved?.customerName)
    }

    @Test
    fun conversationsOrderedByPinnedThenTimestamp() {
        val now = System.currentTimeMillis()

        val conv1 = Conversation(
            id = "conv-1",
            customerId = "customer-1",
            customerName = "Alice",
            customerPhone = "01011111111",
            lastMessage = "Message 1",
            lastMessageTime = now - 5000,
            unreadCount = 0,
            isPinned = false
        )

        val conv2 = Conversation(
            id = "conv-2",
            customerId = "customer-2",
            customerName = "Bob",
            customerPhone = "01022222222",
            lastMessage = "Message 2",
            lastMessageTime = now,
            unreadCount = 0,
            isPinned = true
        )

        val conv3 = Conversation(
            id = "conv-3",
            customerId = "customer-3",
            customerName = "Charlie",
            customerPhone = "01033333333",
            lastMessage = "Message 3",
            lastMessageTime = now - 10000,
            unreadCount = 0,
            isPinned = true
        )

        dao.insert(conv1)
        dao.insert(conv2)
        dao.insert(conv3)

        val allConversations = dao.getAllConversations()

        assertEquals(3, allConversations.size)
        // Pinned conversations should come first, sorted by timestamp descending
        assertTrue(allConversations[0].isPinned)
        assertTrue(allConversations[1].isPinned)
        assertFalse(allConversations[2].isPinned)
    }

    @Test
    fun markConversationAsRead() {
        val conversation = Conversation(
            id = "conv-read-test",
            customerId = "customer-1",
            customerName = "Test User",
            customerPhone = "01012345678",
            lastMessage = "Unread message",
            lastMessageTime = System.currentTimeMillis(),
            unreadCount = 3,
            isPinned = false
        )

        dao.insert(conversation)
        dao.markAsRead("conv-read-test")

        val updated = dao.getConversationById("conv-read-test")
        assertEquals(0, updated?.unreadCount)
    }

    @Test
    fun searchConversations() {
        dao.insert(
            Conversation(
                id = "conv-1",
                customerId = "customer-1",
                customerName = "Kim Min-jun",
                customerPhone = "01012345678",
                lastMessage = "Coffee meeting?",
                lastMessageTime = System.currentTimeMillis(),
                unreadCount = 0,
                isPinned = false
            )
        )

        dao.insert(
            Conversation(
                id = "conv-2",
                customerId = "customer-2",
                customerName = "Park Ji-woo",
                customerPhone = "01087654321",
                lastMessage = "Project deadline",
                lastMessageTime = System.currentTimeMillis(),
                unreadCount = 0,
                isPinned = false
            )
        )

        val results = dao.searchConversations("Kim")

        assertEquals(1, results.size)
        assertEquals("Kim Min-jun", results[0].customerName)
    }

    @Test
    fun getUnreadCount() {
        dao.insert(
            Conversation(
                id = "conv-1",
                customerId = "customer-1",
                customerName = "User 1",
                customerPhone = "01011111111",
                lastMessage = "Message",
                lastMessageTime = System.currentTimeMillis(),
                unreadCount = 2,
                isPinned = false
            )
        )

        dao.insert(
            Conversation(
                id = "conv-2",
                customerId = "customer-2",
                customerName = "User 2",
                customerPhone = "01022222222",
                lastMessage = "Message",
                lastMessageTime = System.currentTimeMillis(),
                unreadCount = 3,
                isPinned = false
            )
        )

        val unreadCount = dao.getTotalUnreadCount()
        assertEquals(5, unreadCount)
    }

    @Test
    fun pinConversation() {
        val conversation = Conversation(
            id = "conv-pin",
            customerId = "customer-1",
            customerName = "Important Customer",
            customerPhone = "01012345678",
            lastMessage = "Important",
            lastMessageTime = System.currentTimeMillis(),
            unreadCount = 0,
            isPinned = false
        )

        dao.insert(conversation)
        dao.pinConversation("conv-pin", true)

        val pinned = dao.getConversationById("conv-pin")
        assertTrue(pinned?.isPinned ?: false)
    }

    @Test
    fun deleteConversation() {
        val conversation = Conversation(
            id = "conv-delete",
            customerId = "customer-1",
            customerName = "To Delete",
            customerPhone = "01012345678",
            lastMessage = "Message",
            lastMessageTime = System.currentTimeMillis(),
            unreadCount = 0,
            isPinned = false
        )

        dao.insert(conversation)
        assertTrue(dao.getConversationById("conv-delete") != null)

        dao.delete("conv-delete")

        assertEquals(null, dao.getConversationById("conv-delete"))
    }
}

interface ConversationDao {
    fun insert(conversation: Conversation)
    fun getConversationById(id: String): Conversation?
    fun getAllConversations(): List<Conversation>
    fun markAsRead(conversationId: String)
    fun searchConversations(query: String): List<Conversation>
    fun getTotalUnreadCount(): Int
    fun pinConversation(conversationId: String, isPinned: Boolean)
    fun delete(conversationId: String)
}
