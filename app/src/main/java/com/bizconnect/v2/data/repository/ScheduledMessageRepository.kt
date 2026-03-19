package com.bizconnect.v2.data.repository

import com.bizconnect.v2.data.local.db.BizConnectDatabase
import com.bizconnect.v2.data.local.db.entity.ScheduledMessageEntity
import com.bizconnect.v2.domain.model.ScheduledMessage
import com.google.gson.Gson
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.util.UUID

class ScheduledMessageRepository(private val database: BizConnectDatabase) {
    private val scheduledMessageDao = database.scheduledMessageDao()
    private val gson = Gson()

    fun getAll(): Flow<List<ScheduledMessage>> =
        scheduledMessageDao.getAll().map { messages ->
            messages.map { it.toDomain() }
        }

    fun getActive(): Flow<List<ScheduledMessage>> =
        scheduledMessageDao.getActive().map { messages ->
            messages.map { it.toDomain() }
        }

    suspend fun getNextScheduled(): ScheduledMessage? =
        scheduledMessageDao.getNextScheduled()?.toDomain()

    suspend fun getDueMessages(currentTime: Long): List<ScheduledMessage> =
        scheduledMessageDao.getDueMessages(currentTime).map { it.toDomain() }

    suspend fun getMessageById(id: String): ScheduledMessage? =
        scheduledMessageDao.getById(id)?.toDomain()

    fun getByUser(userId: String): Flow<List<ScheduledMessage>> =
        scheduledMessageDao.getByUser(userId).map { messages ->
            messages.map { it.toDomain() }
        }

    suspend fun createMessage(
        userId: String,
        recipients: List<String>,
        recipientNames: List<String>?,
        message: String,
        imageUrl: String? = null,
        isMms: Boolean = false,
        scheduledAt: Long,
        repeatType: String = ScheduledMessage.REPEAT_NONE
    ): String {
        val id = UUID.randomUUID().toString()
        val entity = ScheduledMessageEntity(
            id = id,
            userId = userId,
            recipients = gson.toJson(recipients),
            recipientNames = recipientNames?.let { gson.toJson(it) },
            message = message,
            imageUrl = imageUrl,
            isMms = isMms,
            scheduledAt = scheduledAt,
            repeatType = repeatType
        )
        scheduledMessageDao.insert(entity)
        return id
    }

    suspend fun insertMessage(message: ScheduledMessage) =
        scheduledMessageDao.insert(message.toEntity())

    suspend fun insertMessages(messages: List<ScheduledMessage>) =
        scheduledMessageDao.insertAll(messages.map { it.toEntity() })

    suspend fun updateMessage(message: ScheduledMessage) =
        scheduledMessageDao.update(message.toEntity())

    suspend fun deleteMessage(message: ScheduledMessage) =
        scheduledMessageDao.delete(message.toEntity())

    suspend fun deleteMessageById(id: String) =
        scheduledMessageDao.deleteById(id)

    suspend fun deactivateMessage(id: String) =
        scheduledMessageDao.deactivate(id)

    suspend fun activateMessage(id: String) =
        scheduledMessageDao.activate(id)

    suspend fun updateLastSentAt(id: String) =
        scheduledMessageDao.updateLastSentAt(id, System.currentTimeMillis())

    fun getActiveCount(): Flow<Int> =
        scheduledMessageDao.getActiveCount()

    suspend fun getRecurringMessages(): List<ScheduledMessage> =
        scheduledMessageDao.getRecurringMessages().map { it.toDomain() }

    suspend fun getOneTimeMessages(currentTime: Long): List<ScheduledMessage> =
        scheduledMessageDao.getOneTimeMessages(currentTime).map { it.toDomain() }

    private fun ScheduledMessageEntity.toDomain(): ScheduledMessage {
        val recipientList: List<String> = try {
            gson.fromJson(recipients, Array<String>::class.java).toList()
        } catch (e: Exception) {
            emptyList()
        }

        val recipientNameList: List<String>? = try {
            recipientNames?.let { gson.fromJson(it, Array<String>::class.java).toList() }
        } catch (e: Exception) {
            null
        }

        return ScheduledMessage(
            id = id,
            userId = userId,
            recipients = recipientList,
            recipientNames = recipientNameList,
            message = message,
            imageUrl = imageUrl,
            localImagePath = localImagePath,
            isMms = isMms,
            scheduledAt = scheduledAt,
            repeatType = repeatType,
            isActive = isActive,
            lastSentAt = lastSentAt,
            createdAt = createdAt
        )
    }

    private fun ScheduledMessage.toEntity() = ScheduledMessageEntity(
        id = id,
        userId = userId,
        recipients = gson.toJson(recipients),
        recipientNames = recipientNames?.let { gson.toJson(it) },
        message = message,
        imageUrl = imageUrl,
        localImagePath = localImagePath,
        isMms = isMms,
        scheduledAt = scheduledAt,
        repeatType = repeatType,
        isActive = isActive,
        lastSentAt = lastSentAt,
        createdAt = createdAt
    )
}
