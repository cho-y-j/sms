package com.bizconnect.v2.data.repository

import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import com.bizconnect.v2.data.local.db.dao.ConversationDao
import com.bizconnect.v2.data.local.db.dao.MessageDao
import com.bizconnect.v2.data.local.db.entity.ConversationEntity
import com.bizconnect.v2.data.local.db.entity.MessageEntity
import com.bizconnect.v2.domain.model.Conversation
import com.bizconnect.v2.domain.model.Message
import androidx.paging.map
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

class MessageRepository @Inject constructor(
    private val conversationDao: ConversationDao,
    private val messageDao: MessageDao
) {
    fun getAllConversations(): Flow<List<Conversation>> =
        conversationDao.getAll().map { conversations ->
            conversations.map { it.toDomain() }
        }

    fun getActiveConversations(): Flow<List<Conversation>> =
        conversationDao.getActiveConversations().map { conversations ->
            conversations.map { it.toDomain() }
        }

    fun getArchivedConversations(): Flow<List<Conversation>> =
        conversationDao.getArchivedConversations().map { conversations ->
            conversations.map { it.toDomain() }
        }

    fun getMessages(threadId: Long): Flow<PagingData<Message>> =
        Pager(
            config = PagingConfig(pageSize = 20),
            pagingSourceFactory = {
                messageDao.getByThread(threadId)
            }
        ).flow.map { pagingData ->
            pagingData.map { it.toDomain() }
        }

    fun getConversationMessages(threadId: Long): Flow<List<Message>> =
        messageDao.getByThreadFlow(threadId).map { messages ->
            messages.map { it.toDomain() }
        }

    suspend fun insertMessage(message: Message): Long =
        messageDao.insert(message.toEntity())

    suspend fun insertMessages(messages: List<Message>) =
        messageDao.insertAll(messages.map { it.toEntity() })

    suspend fun updateMessage(message: Message) =
        messageDao.update(message.toEntity())

    suspend fun deleteMessage(id: Long) =
        messageDao.deleteById(id)

    suspend fun deleteConversation(threadId: Long) {
        messageDao.deleteByThread(threadId)
        conversationDao.deleteById(threadId)
    }

    suspend fun markConversationAsRead(threadId: Long) =
        conversationDao.markAsRead(threadId)

    suspend fun markMessageAsRead(id: Long) =
        messageDao.markMessageAsRead(id)

    fun getUnreadMessages(): Flow<List<Message>> =
        messageDao.getUnreadMessages().map { messages ->
            messages.map { it.toDomain() }
        }

    fun searchMessages(query: String): Flow<List<Message>> =
        messageDao.search(query).map { messages ->
            messages.map { it.toDomain() }
        }

    fun searchConversations(query: String): Flow<List<Conversation>> =
        conversationDao.search(query).map { conversations ->
            conversations.map { it.toDomain() }
        }

    fun getUnreadCount(): Flow<Int> =
        conversationDao.getUnreadCount()

    suspend fun insertOrUpdateConversation(conversation: Conversation) =
        conversationDao.insert(conversation.toEntity())

    suspend fun updateConversation(conversation: Conversation) =
        conversationDao.update(conversation.toEntity())

    suspend fun deleteConversationEntity(conversation: Conversation) =
        conversationDao.delete(conversation.toEntity())

    suspend fun pinConversation(threadId: Long) =
        conversationDao.pin(threadId)

    suspend fun unpinConversation(threadId: Long) =
        conversationDao.unpin(threadId)

    suspend fun muteConversation(threadId: Long) =
        conversationDao.mute(threadId)

    suspend fun unmuteConversation(threadId: Long) =
        conversationDao.unmute(threadId)

    suspend fun blockConversation(threadId: Long) =
        conversationDao.block(threadId)

    suspend fun unblockConversation(threadId: Long) =
        conversationDao.unblock(threadId)

    suspend fun archiveConversation(threadId: Long) =
        conversationDao.archive(threadId)

    suspend fun unarchiveConversation(threadId: Long) =
        conversationDao.unarchive(threadId)

    suspend fun updateDraft(threadId: Long, draftText: String?) =
        conversationDao.updateDraft(threadId, draftText)

    suspend fun lockMessage(id: Long) =
        messageDao.lockMessage(id)

    suspend fun unlockMessage(id: Long) =
        messageDao.unlockMessage(id)

    fun getLockedMessages(): Flow<List<Message>> =
        messageDao.getLockedMessages().map { messages ->
            messages.map { it.toDomain() }
        }

    private fun ConversationEntity.toDomain() = Conversation(
        threadId = threadId,
        recipientAddress = recipientAddress,
        recipientName = recipientName,
        snippet = snippet,
        snippetType = snippetType,
        messageCount = messageCount,
        unreadCount = unreadCount,
        lastMessageTimestamp = lastMessageTimestamp,
        isPinned = isPinned,
        isMuted = isMuted,
        isBlocked = isBlocked,
        isArchived = isArchived,
        draftText = draftText,
        photoUri = photoUri
    )

    private fun Conversation.toEntity() = ConversationEntity(
        threadId = threadId,
        recipientAddress = recipientAddress,
        recipientName = recipientName,
        snippet = snippet,
        snippetType = snippetType,
        messageCount = messageCount,
        unreadCount = unreadCount,
        lastMessageTimestamp = lastMessageTimestamp,
        isPinned = isPinned,
        isMuted = isMuted,
        isBlocked = isBlocked,
        isArchived = isArchived,
        draftText = draftText,
        photoUri = photoUri
    )

    private fun MessageEntity.toDomain() = Message(
        id = id,
        threadId = threadId,
        address = address,
        body = body,
        timestamp = timestamp,
        type = type,
        read = read,
        seen = seen,
        status = status,
        isMms = isMms,
        mmsSubject = mmsSubject,
        mmsContentType = mmsContentType,
        attachmentPath = attachmentPath,
        attachmentMimeType = attachmentMimeType,
        simSlot = simSlot,
        isLocked = isLocked
    )

    private fun Message.toEntity() = MessageEntity(
        id = id,
        threadId = threadId,
        address = address,
        body = body,
        timestamp = timestamp,
        type = type,
        read = read,
        seen = seen,
        status = status,
        isMms = isMms,
        mmsSubject = mmsSubject,
        mmsContentType = mmsContentType,
        attachmentPath = attachmentPath,
        attachmentMimeType = attachmentMimeType,
        simSlot = simSlot,
        isLocked = isLocked
    )
}
