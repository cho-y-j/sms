# BizConnect V2 - SMS/LMS/MMS Sending Engine

Complete, production-ready messaging engine for BizConnect V2, registered as the default SMS application. Full system-level access to SMS/MMS framework.

## Architecture Overview

### Core Components

#### 1. **SmsEngine.kt** - Core Sending Engine
The fundamental message transmission layer supporting three message types:
- **SMS**: Single-part messages ≤ 160 bytes (70 Korean characters)
- **LMS**: Multi-part messages > 160 bytes using multipart SMS or MMS framework
- **MMS**: Messages with image attachments using system MMS PDU framework

**Key Features:**
- Direct SmsManager API access (system default SMS app privilege)
- Automatic message type detection
- Daily send limit enforcement
- Delivery tracking via PendingIntent callbacks
- Support for multiple SIM cards (subscriptionId parameter)
- Comprehensive error handling with specific error codes
- Message logging to persistent database

**Usage Example:**
```kotlin
// Single SMS
val result = smsEngine.sendSms("01012345678", "Hello!")

// Long message (auto-splits across parts)
val result = smsEngine.sendLms("01012345678", "This is a very long message...")

// MMS with image
val result = smsEngine.sendMms("01012345678", "Check this image!", imageUri)

// Auto-detect type
val result = smsEngine.sendMessage("01012345678", body, imageUri)
```

#### 2. **QueueEngine.kt** - Bulk Sending Queue
Priority-based persistent queue for bulk message sending with real-time monitoring.

**Key Features:**
- Room database persistence (survives app restart)
- Priority-based message ordering
- Configurable throttle intervals between sends
- Pause/Resume capability (queue state preserved)
- Real-time queue statistics via Flow
- Proper coroutine cancellation handling
- Per-message retry logic (configurable max retries)
- Batch operations support

**Queue States:**
- `Idle`: No processing
- `Processing`: Active sending with progress (current/total)
- `Paused`: Halted but can resume
- `Completed`: All messages processed
- `Error`: Processing failed

**Usage Example:**
```kotlin
// Add single message
queueEngine.enqueue(TaskEntity(...))

// Add batch
queueEngine.enqueueBatch(listOf(...))

// Control processing
queueEngine.startProcessing()
queueEngine.pauseProcessing()
queueEngine.resumeProcessing()

// Monitor progress
queueEngine.queueState.collect { state ->
    when(state) {
        is QueueState.Processing -> println("${state.current}/${state.total}")
        is QueueState.Completed -> println("Done: ${state.successCount}/${state.successCount + state.failCount}")
    }
}
```

#### 3. **ScheduleEngine.kt** - Scheduled Delivery
Precise schedule-based message delivery with recurring support.

**Key Features:**
- AlarmManager for precise timing (exact or inexact based on API level)
- Boot persistence (restores alarms after device restart)
- Recurring message support (DAILY, WEEKLY, MONTHLY, YEARLY)
- Exponential backoff retry strategy
- Timezone-aware scheduling
- Maximum retry attempts with status tracking
- Automatic rescheduling for recurring messages

**Usage Example:**
```kotlin
// Single scheduled message
scheduleEngine.scheduleMessage(
    ScheduledMessageEntity(
        id = UUID.randomUUID().toString(),
        recipientAddress = "01012345678",
        messageBody = "Tomorrow at 9 AM",
        scheduledTime = tomorrowAt9Am
    )
)

// Recurring daily message
scheduleEngine.scheduleMessage(
    ScheduledMessageEntity(
        recipientAddress = "01012345678",
        messageBody = "Daily reminder",
        scheduledTime = dailyAt9Am,
        isRecurring = true,
        recurringPattern = "DAILY"
    )
)

// Cancel scheduled message
scheduleEngine.cancelSchedule(messageId)

// Restore alarms (call in BootCompleteReceiver)
scheduleEngine.restoreAlarms()
```

#### 4. **TemplateEngine.kt** - Variable Substitution
Process message templates with dynamic variable replacement for personalized messaging.

**Supported Variables:**
- `{고객명}` - Customer name
- `{전화번호}` - Phone number
- `{날짜}` - Date (YYYY-MM-DD)
- `{시간}` - Time (HH:mm)
- `{요일}` - Day of week (Korean)
- `{회사명}` - Company name
- `{담당자}` - Manager name
- `{시스템날짜}` - System date
- `{시스템시간}` - System time
- `{커스텀:key}` - Custom field (e.g., `{커스텀:주문번호}`)

**Usage Example:**
```kotlin
val template = "안녕하세요 {고객명}님! 주문 {커스텀:주문번호}가 {날짜}에 배송됩니다."

val context = TemplateEngine.TemplateContext(
    customerName = "김철수",
    customFields = mapOf("주문번호" to "ORD-12345")
)

val processed = templateEngine.process(template, context)
// Result: "안녕하세요 김철수님! 주문 ORD-12345가 2026-03-17에 배송됩니다."

// Validation
val validation = templateEngine.validate(template)
if (validation.isValid) {
    println("Variables found: ${validation.variableCount}")
} else {
    println("Errors: ${validation.errors}")
}
```

#### 5. **MmsHelper.kt** - MMS PDU Construction
Helper for constructing and managing MMS messages with image handling.

**Key Features:**
- MMS PDU composition with proper headers
- Image compression (auto-resize + quality adjustment)
- Size validation (max 300KB by default)
- Carrier-specific MMS configuration
- Saving sent MMS to conversation thread
- Image validation and dimension checking
- Cache management for compressed images

**Usage Example:**
```kotlin
// Compress image for sending
val compressedUri = mmsHelper.compressImage(imageUri, maxSizeBytes = 300 * 1024)

// Validate before sending
val validation = mmsHelper.validateImage(imageUri)
if (validation.isValid) {
    // Safe to send
} else {
    println("Error: ${validation.errorMessage}")
}

// Get image size
val sizeBytes = mmsHelper.getImageSize(imageUri)

// Build MMS PDU
val pdu = mmsHelper.buildMmsPdu(
    recipientAddress = "01012345678",
    body = "Message text",
    imageUri = imageUri
)
```

## Data Models

### Core Entities

**SendResult**
```kotlin
data class SendResult(
    val success: Boolean,
    val messageId: Long? = null,
    val messageType: MessageType,
    val errorMessage: String? = null,
    val errorCode: Int? = null
)
```

**TaskEntity** (Queue)
```kotlin
data class TaskEntity(
    val id: String,
    val recipientAddress: String,
    val messageBody: String,
    val imageUri: String? = null,
    val subscriptionId: Int = -1,
    val priority: Int = 0,
    val status: String = "PENDING",
    val createdAt: Long = System.currentTimeMillis(),
    val retryCount: Int = 0,
    val maxRetries: Int = 3
)
```

**ScheduledMessageEntity**
```kotlin
data class ScheduledMessageEntity(
    val id: String,
    val recipientAddress: String,
    val messageBody: String,
    val scheduledTime: Long,
    val isRecurring: Boolean = false,
    val recurringPattern: String = "", // DAILY, WEEKLY, MONTHLY, YEARLY
    val status: String = "PENDING", // PENDING, SENT, CANCELLED, FAILED
    val retryCount: Int = 0,
    val maxRetries: Int = 3
)
```

## Unified API: EngineManager

`EngineManager` provides a single interface for all messaging operations.

**Key Methods:**
```kotlin
// Single messages
suspend fun sendMessage(address: String, body: String, imageUri: Uri?): SendResult
suspend fun sendSms(address: String, body: String): SendResult
suspend fun sendLms(address: String, body: String): SendResult
suspend fun sendMms(address: String, body: String, imageUri: Uri): SendResult

// Templated messages
suspend fun sendTemplatedMessage(
    address: String,
    template: String,
    context: TemplateEngine.TemplateContext,
    imageUri: Uri? = null
): SendResult

// Queue operations
suspend fun enqueueMessage(address: String, body: String, imageUri: Uri? = null)
suspend fun enqueueBatch(messages: List<BulkMessageData>)
fun startQueue()
fun pauseQueue()
fun resumeQueue()
fun getQueueState(): StateFlow<QueueState>
fun getQueueStats(): Flow<QueueStats>

// Scheduling
suspend fun scheduleMessage(
    address: String,
    body: String,
    scheduledTime: Long,
    recurring: Boolean = false,
    recurringPattern: String = ""
): Unit

// Template processing
fun getTemplatePreview(template: String, context: TemplateEngine.TemplateContext): String
fun validateTemplate(template: String): TemplateEngine.TemplateValidationResult
fun getSupportedVariables(): List<TemplateVariable>

// MMS utilities
suspend fun compressImageForMms(uri: Uri): Uri
fun validateImageForMms(uri: Uri): ImageValidationResult
fun getImageSize(uri: Uri): Long

// Limits
suspend fun checkDailyLimit(userId: String): DailyLimitStatus
```

## Integration Guide

### 1. Setup with Hilt DI
All components are provided via Hilt dependency injection in `EngineModule.kt`.

```kotlin
@Module
@InstallIn(SingletonComponent::class)
object EngineModule {
    // Provides all engine components
}
```

### 2. Using EngineManager in Activities/ViewModels
```kotlin
@HiltViewModel
class MessagingViewModel @Inject constructor(
    private val engineManager: EngineManager
) : ViewModel() {
    suspend fun sendMessage(address: String, body: String) {
        val result = engineManager.sendMessage(address, body)
        if (result.success) {
            // Handle success
        }
    }
}
```

### 3. Real-Time Queue Monitoring
```kotlin
class QueueFragment @Inject constructor(
    private val engineManager: EngineManager
) {
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        viewLifecycleOwner.lifecycleScope.launch {
            engineManager.getQueueStats().collect { stats ->
                updateUI(stats)
            }
        }
    }
}
```

### 4. Restore Schedules After Boot
```kotlin
class BootCompleteReceiver : BroadcastReceiver() {
    @Inject lateinit var engineManager: EngineManager

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            CoroutineScope(Dispatchers.IO).launch {
                engineManager.restoreSchedules()
            }
        }
    }
}
```

## Database Schema

### sms_logs Table
Persists all sent messages for audit/history.
```
id (PRIMARY KEY)
address
body
messageType (SMS|LMS|MMS)
success
errorMessage
timestamp
subscriptionId
```

### daily_limits Table
Tracks daily send counts per user.
```
userId (PRIMARY KEY)
date (YYYY-MM-DD)
count
lastUpdated
```

## Configuration

App settings via `AppPreferences`:
```kotlin
// Daily send limit (default: 1000)
appPreferences.getDailyLimitCount()
appPreferences.setDailyLimitCount(5000)

// Queue throttle interval (default: 500ms)
appPreferences.getQueueThrottleMs()
appPreferences.setQueueThrottleMs(1000)

// Limit mode (PER_DAY, PER_HOUR, etc)
appPreferences.getLimitMode()
```

## AndroidManifest.xml Requirements

```xml
<!-- Default SMS app permissions -->
<uses-permission android:name="android.permission.SEND_SMS" />
<uses-permission android:name="android.permission.READ_SMS" />
<uses-permission android:name="android.permission.WRITE_SMS" />
<uses-permission android:name="android.permission.RECEIVE_SMS" />

<!-- MMS permissions -->
<uses-permission android:name="android.permission.INTERNET" />
<uses-permission android:name="android.permission.READ_CONTACTS" />
<uses-permission android:name="android.permission.ACCESS_NETWORK_STATE" />

<!-- Scheduling -->
<uses-permission android:name="android.permission.SCHEDULE_EXACT_ALARM" />
<uses-permission android:name="android.permission.SCHEDULE_EXACT_ALARM_CLOCK" />

<!-- System -->
<uses-permission android:name="android.permission.RECEIVE_BOOT_COMPLETED" />

<!-- Default SMS app role -->
<intent-filter android:priority="0">
    <action android:name="android.intent.action.SEND" />
    <category android:name="android.intent.category.DEFAULT" />
    <data android:mimeType="text/plain" />
</intent-filter>
```

## Error Handling

Each send operation returns `SendResult` with error codes:
- `0`: Success
- `-1`: Invalid input (empty address/body)
- `-2`: Daily limit reached
- `-3`: Permission denied
- `-4`: Send failed (network/carrier)
- `-5`: Unsupported (e.g., MMS on API < 21)

## Performance Considerations

- **Queue Processing**: Uses Dispatchers.IO with configurable throttle
- **Image Compression**: Async with automatic size reduction
- **Database**: Uses Room with proper indexing
- **Memory**: Scheduled messages held in memory, persisted to DB
- **Concurrency**: SupervisorJob + proper cancellation handling

## Testing

All engines are designed for testability:
- Mockable DAOs and repositories
- In-memory implementations provided for testing
- Flow-based statistics for reactive testing
- No hard dependencies on Android Context for business logic

## License & Notes

- Registered as default SMS app (system-level access)
- Korean carrier support built-in
- Production-ready code with no TODOs
- Comprehensive error handling
- Full coroutine support with cancellation
