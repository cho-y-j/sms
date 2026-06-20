package com.bizconnect.v2.data.local.db

import androidx.room.Database
import androidx.room.RoomDatabase
import com.bizconnect.v2.data.local.db.entity.ConversationEntity
import com.bizconnect.v2.data.local.db.entity.MessageEntity
import com.bizconnect.v2.data.local.db.entity.TaskEntity
import com.bizconnect.v2.data.local.db.entity.SpamFilterEntity
import com.bizconnect.v2.data.local.db.entity.SmsLogEntity
import com.bizconnect.v2.data.local.db.entity.DailyLimitEntity
import com.bizconnect.v2.data.local.db.entity.ContactEntity
import com.bizconnect.v2.data.local.db.entity.CustomerEntity
import com.bizconnect.v2.data.local.db.entity.CallbackSettingEntity
import com.bizconnect.v2.data.local.db.entity.ScheduledMessageEntity
import com.bizconnect.v2.data.local.db.entity.CategoryEntity
import com.bizconnect.v2.data.local.db.entity.ContactCategoryEntity
import com.bizconnect.v2.data.local.db.entity.MessageTemplateEntity
import com.bizconnect.v2.data.local.db.entity.AiUsageEntity
import com.bizconnect.v2.data.local.db.dao.*

@Database(
    entities = [
        ConversationEntity::class,
        MessageEntity::class,
        TaskEntity::class,
        SpamFilterEntity::class,
        SmsLogEntity::class,
        DailyLimitEntity::class,
        ContactEntity::class,
        CustomerEntity::class,
        CallbackSettingEntity::class,
        ScheduledMessageEntity::class,
        CategoryEntity::class,
        ContactCategoryEntity::class,
        MessageTemplateEntity::class,
        AiUsageEntity::class
    ],
    version = 9,
    exportSchema = false
)
abstract class BizConnectDatabase : RoomDatabase() {
    abstract fun conversationDao(): ConversationDao
    abstract fun messageDao(): MessageDao
    abstract fun taskDao(): TaskDao
    abstract fun spamFilterDao(): SpamFilterDao
    abstract fun smsLogDao(): SmsLogDao
    abstract fun dailyLimitDao(): DailyLimitDao
    abstract fun contactDao(): ContactDao
    abstract fun customerDao(): CustomerDao
    abstract fun callbackSettingDao(): CallbackSettingDao
    abstract fun scheduledMessageDao(): ScheduledMessageDao
    abstract fun categoryDao(): CategoryDao
    abstract fun messageTemplateDao(): MessageTemplateDao
    abstract fun aiUsageDao(): AiUsageDao
}
