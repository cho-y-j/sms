# BizConnect V2 - Room Database & Repository Layer

Complete implementation of the database layer and repository pattern for BizConnect V2 (Default SMS App).

## Directory Structure

```
com/bizconnect/v2/
├── data/
│   ├── local/
│   │   └── db/
│   │       ├── entity/                    # Room Entities
│   │       │   ├── ConversationEntity.kt
│   │       │   ├── MessageEntity.kt
│   │       │   ├── ContactEntity.kt
│   │       │   ├── CustomerEntity.kt
│   │       │   ├── TaskEntity.kt
│   │       │   ├── SmsLogEntity.kt
│   │       │   ├── ScheduledMessageEntity.kt
│   │       │   ├── SpamFilterEntity.kt
│   │       │   ├── CallbackSettingEntity.kt
│   │       │   └── DailyLimitEntity.kt
│   │       ├── dao/                      # Data Access Objects
│   │       │   ├── ConversationDao.kt
│   │       │   ├── MessageDao.kt
│   │       │   ├── ContactDao.kt
│   │       │   ├── CustomerDao.kt
│   │       │   ├── TaskDao.kt
│   │       │   ├── SmsLogDao.kt
│   │       │   ├── ScheduledMessageDao.kt
│   │       │   ├── SpamFilterDao.kt
│   │       │   ├── CallbackSettingDao.kt
│   │       │   └── DailyLimitDao.kt
│   │       ├── BizConnectDatabase.kt      # Room Database
│   │       └── Converters.kt             # Type Converters
│   ├── preferences/
│   │   └── AppPreferences.kt             # DataStore Preferences
│   └── repository/                       # Repository Pattern
│       ├── MessageRepository.kt
│       ├── ContactRepository.kt
│       ├── CustomerRepository.kt
│       ├── TaskRepository.kt
│       ├── SmsLogRepository.kt
│       ├── ScheduledMessageRepository.kt
│       ├── SpamFilterRepository.kt
│       ├── CallbackSettingRepository.kt
│       ├── DailyLimitRepository.kt
│       └── RepositoryProvider.kt
└── domain/
    └── model/                            # Domain Models
        ├── Conversation.kt
        ├── Message.kt
        ├── Contact.kt
        ├── Customer.kt
        ├── Task.kt
        ├── SmsLog.kt
        ├── ScheduledMessage.kt
        └── CallbackSetting.kt
```

## 1. Room Entities

### ConversationEntity.kt
- Represents SMS/MMS conversations (threads)
- Primary key: `threadId`
- Indexed: `threadId`, `isPinned`, `lastMessageTimestamp`
- Supports pinning, muting, blocking, archiving

### MessageEntity.kt
- Represents individual SMS/MMS messages
- Primary key: `id` (auto-generated)
- Foreign key: `threadId` → ConversationEntity
- Indexed: `threadId`, `timestamp`
- Supports message locking
- Types: inbox (1), sent (2), draft (3), outbox (4), failed (5), queued (6)
- Status: none (-1), complete (0), pending (32), failed (64)

### ContactEntity.kt
- Cached system contacts
- Primary key: `id`
- Indexed: `phoneNumber`, `normalizedNumber`
- Stores phone photos

### CustomerEntity.kt
- Business CRM customers (from web platform)
- Primary key: `id` (UUID)
- Indexed: `userId`, `normalizedPhone`, `groupId`
- Supports birthdays, anniversaries, groups, callback settings

### TaskEntity.kt
- SMS sending tasks from web platform
- Primary key: `id` (UUID)
- Indexed: `userId`, `status`, `scheduledAt`
- Types: send_sms, send_mms, callback_ended, callback_missed, callback_busy
- Status: pending, processing, completed, failed, cancelled
- Supports retry logic and priority

### SmsLogEntity.kt
- Complete SMS/MMS send history and analytics
- Primary key: `id` (UUID)
- Indexed: `userId`, `taskId`, `sentAt`
- Tracks sent messages for compliance and analytics
- Supports sync with server

### ScheduledMessageEntity.kt
- Recurring and one-time scheduled messages
- Primary key: `id` (UUID)
- Indexed: `userId`, `scheduledAt`
- Supports repeat types: none, daily, weekly, monthly, yearly
- Recipients stored as JSON

### SpamFilterEntity.kt
- Spam filtering rules
- Primary key: `id` (auto-generated)
- Indexed: `type`, `isActive`
- Types: number, keyword, pattern
- Active filters can be toggled

### CallbackSettingEntity.kt
- Auto-callback settings for business use
- Primary key: `userId`
- Supports: on-end, on-missed, on-busy auto-replies
- Business card image support
- Throttle interval (ms)

### DailyLimitEntity.kt
- Track daily SMS send limits
- Primary keys: `userId`, `date` (yyyy-MM-dd)
- Limit modes: safe (199/day), max (499/day)
- For compliance and rate limiting

## 2. Data Access Objects (DAOs)

All DAOs use Flow for reactive queries and suspend functions for one-off queries.

### ConversationDao.kt
```kotlin
fun getAll(): Flow<List<ConversationEntity>>          // Ordered by pinned, lastTimestamp
fun getById(threadId): Flow<ConversationEntity?>
fun search(query): Flow<List<ConversationEntity>>
fun getUnreadCount(): Flow<Int>
suspend fun markAsRead(threadId)
suspend fun pin/unpin/mute/unmute/block/archive(threadId)
suspend fun updateDraft(threadId, text)
suspend fun insert/update/delete()
```

### MessageDao.kt
```kotlin
fun getByThread(threadId): PagingSource<Int, MessageEntity>  // For paging
fun getByThreadFlow(threadId): Flow<List<MessageEntity>>
suspend fun getById(id): MessageEntity?
suspend fun getLatestByThread(threadId): MessageEntity?
fun search(query): Flow<List<MessageEntity>>
fun getUnreadMessages(): Flow<List<MessageEntity>>
suspend fun markAsRead(threadId)
suspend fun markMessageAsRead(id)
suspend fun deleteByThread(threadId)
suspend fun lockMessage(id)
suspend fun unlockMessage(id)
```

### ContactDao.kt
```kotlin
fun getAll(): Flow<List<ContactEntity>>
suspend fun getByPhone(phone): ContactEntity?
fun getByName(name): Flow<List<ContactEntity>>
fun search(query): Flow<List<ContactEntity>>
fun getContactsWithPhoto(): Flow<List<ContactEntity>>
suspend fun insert/update/delete()
```

### CustomerDao.kt
```kotlin
fun getAll(userId): Flow<List<CustomerEntity>>
suspend fun getByPhone(phone): CustomerEntity?
fun getByGroup(groupId): Flow<List<CustomerEntity>>
fun getBirthdaysToday(userId, monthDay): Flow<List<CustomerEntity>>
fun getAnniversariesToday(userId, monthDay): Flow<List<CustomerEntity>>
fun search(userId, query): Flow<List<CustomerEntity>>
suspend fun getUnsyncedCustomers(userId): List<CustomerEntity>
suspend fun softDelete(id)
suspend fun markAsSynced(id)
```

### TaskDao.kt
```kotlin
fun getPendingTasks(): Flow<List<TaskEntity>>
fun getByStatus(status): Flow<List<TaskEntity>>
fun getScheduledTasks(): Flow<List<TaskEntity>>
suspend fun getDueScheduledTasks(currentTime): List<TaskEntity>
fun getPendingCount(): Flow<Int>
suspend fun updateStatus(id, status, errorMessage?)
suspend fun incrementRetryCount(id)
suspend fun getHighPriorityTasks(): List<TaskEntity>
```

### SmsLogDao.kt
```kotlin
fun getAll(userId): PagingSource<Int, SmsLogEntity>    // For paging
fun getAllFlow(userId): Flow<List<SmsLogEntity>>
fun getByDate(userId, startDate, endDate): Flow<List<SmsLogEntity>>
fun getTodayCount(userId, date): Flow<Int>
suspend fun getDailyCount(userId, date): Int
fun getFailedLogs(userId): Flow<List<SmsLogEntity>>
suspend fun getUnsyncedLogs(userId): List<SmsLogEntity>
suspend fun markLogAsSynced(id)
suspend fun getRecentRecipients(userId, limit): List<String>
```

### ScheduledMessageDao.kt
```kotlin
fun getAll(): Flow<List<ScheduledMessageEntity>>
fun getActive(): Flow<List<ScheduledMessageEntity>>
suspend fun getNextScheduled(): ScheduledMessageEntity?
suspend fun getDueMessages(currentTime): List<ScheduledMessageEntity>
suspend fun deactivate(id)
suspend fun activate(id)
suspend fun updateLastSentAt(id)
suspend fun getRecurringMessages(): List<ScheduledMessageEntity>
```

### SpamFilterDao.kt
```kotlin
fun getAll(): Flow<List<SpamFilterEntity>>
suspend fun getActiveFilters(): List<SpamFilterEntity>
fun getByType(type): Flow<List<SpamFilterEntity>>
suspend fun isNumberBlocked(address): Boolean
suspend fun isKeywordBlocked(body): Boolean
suspend fun isSpam(address, body): Boolean
suspend fun enable/disable(id)
```

### CallbackSettingDao.kt
```kotlin
fun get(userId): Flow<CallbackSettingEntity?>
suspend fun getSync(userId): CallbackSettingEntity?
suspend fun updateAutoCallbackEnabled(userId, enabled)
suspend fun updateOnEnd(userId, enabled, message)
suspend fun updateOnMissed(userId, enabled, message)
suspend fun updateOnBusy(userId, enabled, message)
suspend fun updateBusinessCard(userId, enabled, imageUrl)
```

### DailyLimitDao.kt
```kotlin
suspend fun get(userId, date): DailyLimitEntity?
fun getFlow(userId, date): Flow<DailyLimitEntity?>
suspend fun incrementCount(userId, date)
suspend fun setCount(userId, date, count)
suspend fun getSentCount(userId, date): Int
suspend fun getLimitMode(userId, date): String
fun getLast30Days(userId): Flow<List<DailyLimitEntity>>
```

## 3. BizConnectDatabase.kt

Main Room Database class with:
- All 10 entities defined
- All 10 DAOs exposed
- Type converters for complex types
- Singleton pattern with getInstance()
- In-memory database support for testing

```kotlin
@Database(
    entities = [
        ConversationEntity::class,
        MessageEntity::class,
        ContactEntity::class,
        CustomerEntity::class,
        TaskEntity::class,
        SmsLogEntity::class,
        ScheduledMessageEntity::class,
        SpamFilterEntity::class,
        CallbackSettingEntity::class,
        DailyLimitEntity::class
    ],
    version = 1,
    exportSchema = true
)
```

## 4. Type Converters (Converters.kt)

Handles complex type conversions:
- Date ↔ Long
- List<String> ↔ JSON String
- Map<String, String> ↔ JSON String
- List<Int> ↔ JSON String

## 5. Domain Models

Clean domain models separate from database entities:

- **Conversation.kt** - Thread metadata
- **Message.kt** - Individual messages with status constants
- **Contact.kt** - System contacts
- **Customer.kt** - CRM customers
- **Task.kt** - SMS sending tasks with status/type constants
- **SmsLog.kt** - Message history
- **ScheduledMessage.kt** - Recurring messages with repeat types
- **CallbackSetting.kt** - Auto-reply settings

## 6. Repository Layer

### MessageRepository.kt
- getAllConversations()
- getMessages(threadId) with paging
- insertMessage/updateMessage/deleteMessage
- markConversationAsRead/markMessageAsRead
- searchMessages/searchConversations
- pinConversation/unpinConversation
- muteConversation/unmuteConversation
- blockConversation/unblockConversation
- archiveConversation/unarchiveConversation
- lockMessage/unlockMessage

### ContactRepository.kt
- getAllContacts()
- getContactByPhone(phone)
- searchContacts(query)
- insertContact/updateContact/deleteContact
- deleteAllContacts()
- getContactsWithPhoto()
- updateLastUpdated()
- getOldContacts(olderThan)

### CustomerRepository.kt
- getAllCustomers(userId)
- getCustomerByPhone(phone)
- getCustomersByGroup(groupId)
- getBirthdayCustomers(userId, monthDay)
- getAnniversaryCustomers(userId, monthDay)
- searchCustomers(userId, query)
- addCustomer/updateCustomer/deleteCustomer
- softDeleteCustomer(id)
- getUnsyncedCustomers(userId)
- markCustomerAsSynced(id)

### TaskRepository.kt
- getPendingTasks()
- getTasksByStatus(status)
- createTask()
- updateTaskStatus(id, status, errorMessage?)
- getScheduledTasks()
- getDueScheduledTasks(currentTime)
- getRecentCompleted(userId)
- getHighPriorityTasks()
- getTasksByCustomer(customerId)

### SmsLogRepository.kt
- getAll(userId) with paging
- getAllFlow(userId)
- getByDate(userId, startDate, endDate)
- getTodayCount/getDailyCount()
- createLog()
- getUnsyncedLogs(userId)
- markLogAsSynced(id)
- getRecentSent(userId, limit)
- getFailedLogs(userId)
- getRecentRecipients(userId, limit)
- getLogsByTask/getLogsByType()

### ScheduledMessageRepository.kt
- getAll()
- getActive()
- getNextScheduled()
- getDueMessages(currentTime)
- createMessage()
- getRecurringMessages()
- getOneTimeMessages(currentTime)
- deactivateMessage(id)
- activateMessage(id)
- updateLastSentAt(id)

### SpamFilterRepository.kt
- getAll()
- getActiveFilters()
- getByType(type)
- insertFilter(type, value)
- updateFilter/deleteFilter()
- enableFilter/disableFilter()
- isNumberBlocked(address)
- isKeywordBlocked(body)
- isSpam(address, body)
- addNumberFilter/addKeywordFilter/addPatternFilter()

### CallbackSettingRepository.kt
- get(userId): Flow<CallbackSetting?>
- getSync(userId): CallbackSetting?
- insert/update/delete()
- updateAutoCallbackEnabled()
- updateOnEnd/updateOnMissed/updateOnBusy()
- updateBusinessCard()
- isAutoCallbackEnabled/isOnEndEnabled()
- getOrCreateDefault(userId)

### DailyLimitRepository.kt
- get/insert/update/delete()
- incrementCount(userId, date)
- setCount(userId, date, count)
- getTodayDate(): String
- getOrCreateToday(userId, limitMode)
- incrementTodayCount(userId)
- canSendToday(userId, limitMode): Boolean
- getRemainingToday(userId, limitMode): Int

## 7. AppPreferences.kt (DataStore)

Preferences for app configuration:

**UI Settings:**
- `fontSize`: Int (14, 16, 18, 20, 24)
- `darkMode`: Boolean
- `hideArchived`: Boolean
- `threadViewMode`: String (condensed, expanded)

**User & Server:**
- `userId`: String?
- `phoneNumber`: String?
- `serverUrl`: String?
- `fcmToken`: String?

**SMS Settings:**
- `dailyLimitMode`: String (safe=199, max=499)
- `autoApprove`: Boolean
- `defaultSimSlot`: Int

**Notifications:**
- `notificationEnabled`: Boolean
- `soundEnabled`: Boolean
- `vibrationEnabled`: Boolean

**Spam & Security:**
- `spamFilterEnabled`: Boolean

**Backup:**
- `autoBackupEnabled`: Boolean
- `lastBackupTime`: String?
- `messageRetentionDays`: Int (default 90)

**Metadata:**
- `lastSyncTime`: String?
- `appVersion`: String?
- `onboardingCompleted`: Boolean

## 8. RepositoryProvider.kt

Central provider for all repositories with lazy initialization:

```kotlin
val messageRepository: MessageRepository
val contactRepository: ContactRepository
val customerRepository: CustomerRepository
val taskRepository: TaskRepository
val smsLogRepository: SmsLogRepository
val scheduledMessageRepository: ScheduledMessageRepository
val spamFilterRepository: SpamFilterRepository
val callbackSettingRepository: CallbackSettingRepository
val dailyLimitRepository: DailyLimitRepository
val preferences: AppPreferences
```

Singleton pattern for easy injection.

## Usage Examples

### Get all conversations
```kotlin
val repositories = RepositoryProvider.getInstance(context)
repositories.messageRepository.getAllConversations()
    .collect { conversations ->
        // Update UI
    }
```

### Send a message
```kotlin
val taskId = repositories.taskRepository.createTask(
    userId = "user123",
    customerPhone = "+1234567890",
    customerName = "John Doe",
    messageContent = "Hello!",
    type = Task.TYPE_SEND_SMS
)
```

### Get customer birthdays today
```kotlin
val today = "03-17"  // MM-dd format
repositories.customerRepository.getBirthdayCustomers("user123", today)
    .collect { birthdays ->
        // Send birthday messages
    }
```

### Check daily limit
```kotlin
val canSend = repositories.dailyLimitRepository.canSendToday(
    userId = "user123",
    limitMode = "safe"  // 199/day or 499/day
)
```

### Spam filtering
```kotlin
val isSpam = repositories.spamFilterRepository.isSpam(
    address = "+1234567890",
    body = "Check out this link..."
)
```

## Dependencies Required

```gradle
implementation "androidx.room:room-runtime:2.5.2"
implementation "androidx.room:room-ktx:2.5.2"
kapt "androidx.room:room-compiler:2.5.2"

implementation "androidx.datastore:datastore-preferences:1.0.0"

implementation "androidx.paging:paging-runtime-ktx:3.1.1"

implementation "com.google.code.gson:gson:2.10.1"

implementation "org.jetbrains.kotlinx:kotlinx-coroutines-core:1.7.1"
implementation "org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.1"
```

## Key Features

✅ Complete CRUD operations for all entities
✅ Reactive queries using Flow
✅ Paging support for large datasets
✅ Soft delete support for customers
✅ Sync tracking (unsyncedAt, syncedAt)
✅ Complex queries (spam filtering, birthday/anniversary search)
✅ Type-safe database access
✅ Singleton pattern for repositories
✅ DataStore for preferences
✅ Proper entity relationships with foreign keys
✅ Comprehensive indexing for performance
✅ JSON serialization for complex types
✅ Retry logic for tasks
✅ Daily limit tracking
✅ SMS history and analytics

## Database Version

Current version: **1**

For future migrations, add Migration classes to the database builder.

## Schema Export

`exportSchema = true` enables Room to export the database schema to JSON for version control.

Schema location: `app/schemas/`
