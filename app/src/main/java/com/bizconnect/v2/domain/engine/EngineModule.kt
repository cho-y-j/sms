package com.bizconnect.v2.domain.engine

import android.content.Context
import android.content.SharedPreferences
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Hilt dependency injection module for the messaging engine.
 * Provides all engine components and their dependencies.
 */
@Module
@InstallIn(SingletonComponent::class)
object EngineModule {

    private const val PREFERENCES_NAME = "bizconnect_preferences"

    @Singleton
    @Provides
    fun provideSharedPreferences(
        @ApplicationContext context: Context
    ): SharedPreferences {
        return context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
    }

    @Singleton
    @Provides
    fun provideEngineAppPreferences(
        sharedPreferences: SharedPreferences
    ): AppPreferences {
        return SharedPreferencesAppPreferences(sharedPreferences)
    }

    @Singleton
    @Provides
    fun provideEngineSmsLogDao(): SmsLogDao {
        return InMemorySmsLogDao()
    }

    @Singleton
    @Provides
    fun provideEngineDailyLimitDao(): DailyLimitDao {
        return InMemoryDailyLimitDao()
    }

    @Singleton
    @Provides
    fun provideMessageRepository(): MessageRepository {
        return InMemoryMessageRepository()
    }

    @Singleton
    @Provides
    fun provideScheduledMessageDao(): ScheduledMessageDao {
        return InMemoryScheduledMessageDao()
    }

    @Singleton
    @Provides
    fun provideTaskDao(): TaskDao {
        return InMemoryTaskDao()
    }

    @Singleton
    @Provides
    fun provideMmsHelper(
        @ApplicationContext context: Context
    ): MmsHelper {
        return MmsHelper(context)
    }

    @Singleton
    @Provides
    fun provideTemplateEngine(): TemplateEngine {
        return TemplateEngine()
    }

    @Singleton
    @Provides
    fun provideSmsEngine(
        @ApplicationContext context: Context,
        messageRepository: MessageRepository,
        smsLogDao: SmsLogDao,
        dailyLimitDao: DailyLimitDao,
        appPreferences: AppPreferences,
        mmsHelper: MmsHelper
    ): SmsEngine {
        return SmsEngine(
            context = context,
            messageRepository = messageRepository,
            smsLogDao = smsLogDao,
            dailyLimitDao = dailyLimitDao,
            appPreferences = appPreferences,
            mmsHelper = mmsHelper
        )
    }

    @Singleton
    @Provides
    fun provideQueueEngine(
        smsEngine: SmsEngine,
        taskDao: TaskDao,
        appPreferences: AppPreferences
    ): QueueEngine {
        return QueueEngine(
            smsEngine = smsEngine,
            taskDao = taskDao,
            appPreferences = appPreferences
        )
    }

    @Singleton
    @Provides
    fun provideScheduleEngine(
        @ApplicationContext context: Context,
        scheduledMessageDao: ScheduledMessageDao,
        queueEngine: QueueEngine,
        smsEngine: SmsEngine
    ): ScheduleEngine {
        return ScheduleEngine(
            context = context,
            scheduledMessageDao = scheduledMessageDao,
            queueEngine = queueEngine,
            smsEngine = smsEngine
        )
    }

    @Singleton
    @Provides
    fun provideEngineManager(
        smsEngine: SmsEngine,
        queueEngine: QueueEngine,
        scheduleEngine: ScheduleEngine,
        templateEngine: TemplateEngine,
        mmsHelper: MmsHelper
    ): EngineManager {
        return EngineManager(
            smsEngine = smsEngine,
            queueEngine = queueEngine,
            scheduleEngine = scheduleEngine,
            templateEngine = templateEngine,
            mmsHelper = mmsHelper
        )
    }
}

/**
 * In-memory implementations for engine domain interfaces
 */
class InMemoryMessageRepository : MessageRepository {
    private val messages = mutableMapOf<String, MessageEntity>()

    override suspend fun saveMessage(message: MessageEntity) {
        messages[message.id] = message
    }

    override suspend fun getMessageById(id: String): MessageEntity? {
        return messages[id]
    }

    override suspend fun deleteMessage(id: String) {
        messages.remove(id)
    }
}

class InMemorySmsLogDao : SmsLogDao {
    private val logs = mutableListOf<SmsLogEntry>()

    override suspend fun insert(log: SmsLogEntry) {
        logs.add(log)
    }

    override suspend fun insertAll(logs: List<SmsLogEntry>) {
        this.logs.addAll(logs)
    }

    override suspend fun getDailyCount(userId: String): Int {
        return logs.size
    }

    override suspend fun getMessagesByDate(startTime: Long, endTime: Long): List<SmsLogEntry> {
        return logs.filter { it.timestamp in startTime..endTime }
    }

    override suspend fun getMessagesByAddress(address: String): List<SmsLogEntry> {
        return logs.filter { it.address == address }
    }
}

class InMemoryDailyLimitDao : DailyLimitDao {
    private val limits = mutableMapOf<String, DailyLimit>()

    override suspend fun incrementCount(userId: String) {
        val current = limits[userId]
        if (current != null) {
            limits[userId] = current.copy(count = current.count + 1)
        }
    }

    override suspend fun resetDaily() {
        limits.clear()
    }

    override suspend fun getCurrentCount(userId: String): Int {
        return limits[userId]?.count ?: 0
    }

    override suspend fun getOrCreateLimit(userId: String): DailyLimit {
        return limits.getOrPut(userId) {
            DailyLimit(userId = userId, date = java.time.LocalDate.now().toString())
        }
    }
}

class InMemoryScheduledMessageDao : ScheduledMessageDao {
    private val messages = mutableMapOf<String, ScheduledMessageEntity>()

    override suspend fun insert(message: ScheduledMessageEntity) {
        messages[message.id] = message
    }

    override suspend fun update(message: ScheduledMessageEntity) {
        messages[message.id] = message
    }

    override suspend fun getMessageById(id: String): ScheduledMessageEntity? {
        return messages[id]
    }

    override suspend fun updateStatus(id: String, status: String) {
        messages[id]?.let {
            messages[id] = it.copy(status = status)
        }
    }

    override suspend fun getDueMessages(currentTime: Long): List<ScheduledMessageEntity> {
        return messages.values.filter {
            it.scheduledTime <= currentTime && it.status == "PENDING"
        }
    }

    override suspend fun getNextPendingMessage(): ScheduledMessageEntity? {
        return messages.values
            .filter { it.status == "PENDING" }
            .minByOrNull { it.scheduledTime }
    }

    override suspend fun getAllPendingMessages(): List<ScheduledMessageEntity> {
        return messages.values.filter { it.status == "PENDING" }
    }

    override suspend fun delete(id: String) {
        messages.remove(id)
    }
}

class InMemoryTaskDao : TaskDao {
    private val tasks = mutableMapOf<String, TaskEntity>()

    override suspend fun insert(task: TaskEntity) {
        tasks[task.id] = task
    }

    override suspend fun insertAll(tasks: List<TaskEntity>) {
        tasks.forEach { insert(it) }
    }

    override suspend fun updateStatus(taskId: String, status: String) {
        tasks[taskId]?.let {
            tasks[taskId] = it.copy(status = status, updatedAt = System.currentTimeMillis())
        }
    }

    override suspend fun updateAllPendingStatus(status: String) {
        tasks.values
            .filter { it.status == "PENDING" }
            .forEach { updateStatus(it.id, status) }
    }

    override suspend fun getPendingTasks(limit: Int): List<TaskEntity> {
        return tasks.values
            .filter { it.status == "PENDING" }
            .sortedWith(compareBy({ -it.priority }, { it.createdAt }))
            .take(limit)
    }

    override suspend fun getPendingCount(): Int {
        return tasks.values.count { it.status == "PENDING" }
    }

    override suspend fun delete(taskId: String) {
        tasks.remove(taskId)
    }

    override fun observeStats(): kotlinx.coroutines.flow.Flow<TaskStats> {
        return kotlinx.coroutines.flow.flowOf(
            TaskStats(
                pending = tasks.values.count { it.status == "PENDING" },
                completed = tasks.values.count { it.status == "COMPLETED" },
                failed = tasks.values.count { it.status == "FAILED" }
            )
        )
    }

    override suspend fun getTaskById(taskId: String): TaskEntity? {
        return tasks[taskId]
    }

    override suspend fun updateTask(task: TaskEntity) {
        tasks[task.id] = task
    }
}
