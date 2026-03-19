package com.bizconnect.v2.di

import android.content.ContentResolver
import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import com.bizconnect.v2.data.local.db.BizConnectDatabase
import com.bizconnect.v2.data.local.db.dao.*
import com.bizconnect.v2.data.repository.ContactRepository
import com.bizconnect.v2.data.repository.MessageRepository
import com.bizconnect.v2.data.repository.SpamFilterRepository
import com.bizconnect.v2.util.DateTimeUtil
import com.bizconnect.v2.util.NotificationUtil
import com.bizconnect.v2.util.PermissionUtil
import com.bizconnect.v2.util.PhoneNumberUtil
import com.bizconnect.v2.util.SecurityUtil
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

@Module
@InstallIn(SingletonComponent::class)
object AppModule {
    @Provides
    @Singleton
    fun provideBizConnectDatabase(@ApplicationContext context: Context): BizConnectDatabase {
        return Room.databaseBuilder(
            context,
            BizConnectDatabase::class.java,
            "bizconnect_db"
        )
            .fallbackToDestructiveMigration()
            .addCallback(object : RoomDatabase.Callback() {
                override fun onCreate(db: SupportSQLiteDatabase) {
                    super.onCreate(db)
                    // 기본 카테고리
                    db.execSQL("INSERT OR IGNORE INTO categories (name, sortOrder, color, createdAt) VALUES ('가족', 1, ${0xFF4CAF50.toInt()}, ${System.currentTimeMillis()})")
                    db.execSQL("INSERT OR IGNORE INTO categories (name, sortOrder, color, createdAt) VALUES ('친구', 2, ${0xFF2196F3.toInt()}, ${System.currentTimeMillis()})")

                    // 기본 템플릿 (카테고리별 샘플)
                    val now = System.currentTimeMillis()
                    db.execSQL("INSERT OR IGNORE INTO message_templates (id, name, content, category, isMms, createdAt, updatedAt) VALUES (1, '인사말', '안녕하세요 %이름%님, 좋은 하루 보내세요!', 'general', 0, $now, $now)")
                    db.execSQL("INSERT OR IGNORE INTO message_templates (id, name, content, category, isMms, createdAt, updatedAt) VALUES (2, '콜백 기본', '%이름%님, 전화 주셔서 감사합니다. 확인 후 다시 연락드리겠습니다.', 'callback', 0, $now, $now)")
                    db.execSQL("INSERT OR IGNORE INTO message_templates (id, name, content, category, isMms, createdAt, updatedAt) VALUES (3, '프로모션 안내', '%이름%님, 특별 할인 이벤트를 안내드립니다! 자세한 내용은 아래 링크를 확인해주세요.', 'bulk', 0, $now, $now)")
                    db.execSQL("INSERT OR IGNORE INTO message_templates (id, name, content, category, isMms, createdAt, updatedAt) VALUES (4, '예약 확인', '%이름%님, %날짜% %시간% 예약이 확인되었습니다. 감사합니다.', 'scheduled', 0, $now, $now)")
                    db.execSQL("INSERT OR IGNORE INTO message_templates (id, name, content, category, isMms, createdAt, updatedAt) VALUES (5, '생일 축하', '%이름%님, 생일을 진심으로 축하드립니다! 행복한 하루 보내세요 🎂', 'scheduled', 0, $now, $now)")
                    db.execSQL("INSERT OR IGNORE INTO message_templates (id, name, content, category, isMms, createdAt, updatedAt) VALUES (6, '부재중 안내', '%이름%님, 부재중 전화를 확인했습니다. 곧 연락드리겠습니다.', 'callback', 0, $now, $now)")
                    db.execSQL("INSERT OR IGNORE INTO message_templates (id, name, content, category, isMms, createdAt, updatedAt) VALUES (7, '감사 인사', '%이름%님, 항상 이용해 주셔서 감사합니다. 좋은 하루 되세요!', 'general', 0, $now, $now)")
                    db.execSQL("INSERT OR IGNORE INTO message_templates (id, name, content, category, isMms, createdAt, updatedAt) VALUES (8, '미팅 안내', '%이름%님, %날짜% %시간%에 미팅이 예정되어 있습니다. 장소: %주소%', 'scheduled', 0, $now, $now)")
                }
            })
            .build()
    }

    @Provides
    @Singleton
    fun provideContentResolver(@ApplicationContext context: Context): ContentResolver {
        return context.contentResolver
    }

    @Provides
    @Singleton
    fun provideConversationDao(db: BizConnectDatabase): ConversationDao = db.conversationDao()

    @Provides
    @Singleton
    fun provideMessageDao(db: BizConnectDatabase): MessageDao = db.messageDao()

    @Provides
    @Singleton
    fun provideTaskDao(db: BizConnectDatabase): TaskDao = db.taskDao()

    @Provides
    @Singleton
    fun provideSpamFilterDao(db: BizConnectDatabase): SpamFilterDao = db.spamFilterDao()

    @Provides
    @Singleton
    fun provideSmsLogDao(db: BizConnectDatabase): SmsLogDao = db.smsLogDao()

    @Provides
    @Singleton
    fun provideDailyLimitDao(db: BizConnectDatabase): DailyLimitDao = db.dailyLimitDao()

    @Provides
    @Singleton
    fun provideContactDao(db: BizConnectDatabase): ContactDao = db.contactDao()

    @Provides
    @Singleton
    fun provideCustomerDao(db: BizConnectDatabase): CustomerDao = db.customerDao()

    @Provides
    @Singleton
    fun provideCallbackSettingDao(db: BizConnectDatabase): CallbackSettingDao = db.callbackSettingDao()

    @Provides
    @Singleton
    fun provideScheduledMessageDao(db: BizConnectDatabase): ScheduledMessageDao = db.scheduledMessageDao()

    @Provides
    @Singleton
    fun provideCategoryDao(db: BizConnectDatabase): CategoryDao = db.categoryDao()

    @Provides
    @Singleton
    fun provideMessageTemplateDao(db: BizConnectDatabase): MessageTemplateDao = db.messageTemplateDao()

    @Provides
    @Singleton
    fun provideAiUsageDao(db: BizConnectDatabase): AiUsageDao = db.aiUsageDao()

    @Provides
    @Singleton
    fun provideMessageRepository(
        conversationDao: ConversationDao,
        messageDao: MessageDao
    ): MessageRepository = MessageRepository(conversationDao, messageDao)

    @Provides
    @Singleton
    fun provideContactRepository(contactDao: ContactDao): ContactRepository = ContactRepository(contactDao)

    @Provides
    @Singleton
    fun provideSpamFilterRepository(spamFilterDao: SpamFilterDao): SpamFilterRepository = SpamFilterRepository(spamFilterDao)

    @Provides
    @Singleton
    fun provideDataStore(@ApplicationContext context: Context): DataStore<Preferences> {
        return context.dataStore
    }

    @Provides
    @Singleton
    fun providePhoneNumberUtil(): PhoneNumberUtil = PhoneNumberUtil()

    @Provides
    @Singleton
    fun provideNotificationUtil(
        @ApplicationContext context: Context
    ): NotificationUtil = NotificationUtil(context)

    @Provides
    @Singleton
    fun providePermissionUtil(
        @ApplicationContext context: Context
    ): PermissionUtil = PermissionUtil(context)

    @Provides
    @Singleton
    fun provideSecurityUtil(): SecurityUtil = SecurityUtil()

    @Provides
    @Singleton
    fun provideDateTimeUtil(): DateTimeUtil = DateTimeUtil
}
