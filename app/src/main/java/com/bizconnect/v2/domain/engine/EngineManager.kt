package com.bizconnect.v2.domain.engine

import android.net.Uri
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Main engine manager that orchestrates all messaging engines.
 * Provides unified API for:
 * - Single message sending (SMS/LMS/MMS)
 * - Bulk queue processing
 * - Message scheduling
 * - Template processing
 * - Queue statistics and monitoring
 *
 * Thread-safe singleton for application-wide message management.
 */
@Singleton
class EngineManager @Inject constructor(
    private val smsEngine: SmsEngine,
    private val queueEngine: QueueEngine,
    private val scheduleEngine: ScheduleEngine,
    private val templateEngine: TemplateEngine,
    private val mmsHelper: MmsHelper
) {
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    companion object {
        private const val TAG = "EngineManager"
    }

    /**
     * Send a single message with automatic type detection
     */
    suspend fun sendMessage(
        address: String,
        body: String,
        imageUri: Uri? = null
    ): SendResult {
        return smsEngine.sendMessage(address, body, imageUri)
    }

    /**
     * Send a single SMS (short message)
     */
    suspend fun sendSms(address: String, body: String): SendResult {
        return smsEngine.sendSms(address, body)
    }

    /**
     * Send a single LMS (long message)
     */
    suspend fun sendLms(address: String, body: String): SendResult {
        return smsEngine.sendLms(address, body)
    }

    /**
     * Send a single MMS (with image)
     */
    suspend fun sendMms(
        address: String,
        body: String,
        imageUri: Uri
    ): SendResult {
        return smsEngine.sendMms(address, body, imageUri)
    }

    /**
     * Send templated message with variable substitution
     */
    suspend fun sendTemplatedMessage(
        address: String,
        template: String,
        context: TemplateEngine.TemplateContext,
        imageUri: Uri? = null
    ): SendResult {
        val processedBody = templateEngine.process(template, context)
        return smsEngine.sendMessage(address, processedBody, imageUri)
    }

    /**
     * Enqueue a single message for bulk sending
     */
    suspend fun enqueueMessage(
        address: String,
        body: String,
        imageUri: Uri? = null,
        priority: Int = 0
    ) {
        val task = TaskEntity(
            id = java.util.UUID.randomUUID().toString(),
            recipientAddress = address,
            messageBody = body,
            imageUri = imageUri?.toString(),
            priority = priority
        )
        queueEngine.enqueue(task)
    }

    /**
     * Enqueue multiple messages for bulk sending
     */
    suspend fun enqueueBatch(messages: List<BulkMessageData>) {
        val tasks = messages.map { msg ->
            TaskEntity(
                id = java.util.UUID.randomUUID().toString(),
                recipientAddress = msg.address,
                messageBody = msg.body,
                imageUri = msg.imageUri?.toString(),
                priority = msg.priority ?: 0
            )
        }
        queueEngine.enqueueBatch(tasks)
    }

    /**
     * Start queue processing
     */
    fun startQueue() {
        queueEngine.startProcessing()
    }

    /**
     * Pause queue processing
     */
    fun pauseQueue() {
        queueEngine.pauseProcessing()
    }

    /**
     * Resume queue processing
     */
    fun resumeQueue() {
        queueEngine.resumeProcessing()
    }

    /**
     * Stop queue and cancel all pending messages
     */
    suspend fun stopQueue() {
        queueEngine.pauseProcessing()
    }

    /**
     * Get real-time queue state
     */
    fun getQueueState(): StateFlow<QueueState> {
        return queueEngine.queueState
    }

    /**
     * Get queue statistics
     */
    fun getQueueStats() = queueEngine.getQueueStats()

    /**
     * Schedule a message for future delivery
     */
    suspend fun scheduleMessage(
        address: String,
        body: String,
        scheduledTime: Long,
        imageUri: Uri? = null,
        recurring: Boolean = false,
        recurringPattern: String = ""
    ) {
        val message = ScheduledMessageEntity(
            id = java.util.UUID.randomUUID().toString(),
            recipientAddress = address,
            messageBody = body,
            imageUri = imageUri?.toString(),
            scheduledTime = scheduledTime,
            isRecurring = recurring,
            recurringPattern = recurringPattern
        )
        scheduleEngine.scheduleMessage(message)
    }

    /**
     * Cancel a scheduled message
     */
    suspend fun cancelScheduledMessage(messageId: String) {
        scheduleEngine.cancelSchedule(messageId)
    }

    /**
     * Process template and get preview
     */
    fun getTemplatePreview(
        template: String,
        context: TemplateEngine.TemplateContext
    ): String {
        return templateEngine.process(template, context)
    }

    /**
     * Validate template
     */
    fun validateTemplate(template: String): TemplateValidationResult {
        return templateEngine.validate(template)
    }

    /**
     * Get supported template variables
     */
    fun getSupportedVariables(): List<TemplateVariable> {
        return templateEngine.getSupportedVariables()
    }

    /**
     * Compress image for MMS sending
     */
    suspend fun compressImageForMms(uri: Uri): Uri {
        return mmsHelper.compressImage(uri)
    }

    /**
     * Validate image before MMS sending
     */
    fun validateImageForMms(uri: Uri): ImageValidationResult {
        return mmsHelper.validateImage(uri)
    }

    /**
     * Get image file size
     */
    fun getImageSize(uri: Uri): Long {
        return mmsHelper.getImageSize(uri)
    }

    /**
     * Determine message type based on content
     */
    fun getMessageType(body: String, hasImage: Boolean): MessageType {
        return smsEngine.getMessageType(body, hasImage)
    }

    /**
     * Check user daily send limit
     */
    suspend fun checkDailyLimit(userId: String): DailyLimitStatus {
        return smsEngine.checkDailyLimit(userId)
    }

    /**
     * Restore scheduled message alarms (call after boot)
     */
    suspend fun restoreSchedules() {
        scheduleEngine.restoreAlarms()
    }

    /**
     * Clean up resources
     */
    fun destroy() {
        queueEngine.destroy()
        scope.cancel()
    }
}

/**
 * Data class for bulk message sending
 */
data class BulkMessageData(
    val address: String,
    val body: String,
    val imageUri: Uri? = null,
    val priority: Int? = null,
    val metadata: Map<String, String>? = null
)

/**
 * Builder for convenient message creation
 */
class MessageBuilder {
    private var address: String? = null
    private var body: String? = null
    private var imageUri: Uri? = null
    private var templateMode: Boolean = false
    private var templateContext: TemplateEngine.TemplateContext? = null

    fun to(address: String) = apply { this.address = address }

    fun body(body: String) = apply { this.body = body }

    fun image(uri: Uri) = apply { this.imageUri = uri }

    fun template(template: String, context: TemplateEngine.TemplateContext) = apply {
        this.body = template
        this.templateMode = true
        this.templateContext = context
    }

    fun build(): BulkMessageData {
        require(!address.isNullOrBlank()) { "Address is required" }
        require(!body.isNullOrBlank()) { "Body is required" }

        return BulkMessageData(
            address = address!!,
            body = body!!,
            imageUri = imageUri,
            metadata = mapOf(
                "templateMode" to templateMode.toString()
            )
        )
    }
}

/**
 * Batch message builder for bulk operations
 */
class BatchMessageBuilder {
    private val messages = mutableListOf<BulkMessageData>()

    fun addMessage(address: String, body: String, imageUri: Uri? = null): BatchMessageBuilder {
        messages.add(BulkMessageData(address, body, imageUri))
        return this
    }

    fun addMessages(vararg data: BulkMessageData): BatchMessageBuilder {
        messages.addAll(data)
        return this
    }

    fun build(): List<BulkMessageData> {
        return messages
    }

    fun clear(): BatchMessageBuilder {
        messages.clear()
        return this
    }
}

/**
 * Typical usage patterns
 */
object EngineUsageExamples {

    /**
     * Example: Send single message
     */
    suspend fun exampleSingleMessage(manager: EngineManager) {
        val result = manager.sendMessage(
            address = "01012345678",
            body = "안녕하세요. 테스트 메시지입니다."
        )
        if (result.success) {
            println("Message sent successfully")
        } else {
            println("Send failed: ${result.errorMessage}")
        }
    }

    /**
     * Example: Send templated message
     */
    suspend fun exampleTemplatedMessage(manager: EngineManager) {
        val context = TemplateEngine.TemplateContext(
            customerName = "김철수",
            companyName = "비즈커넥트",
            customFields = mapOf("주문번호" to "ORD-12345")
        )

        val template = "안녕하세요 {고객명}님! 주문번호 {커스텀:주문번호}가 확인되었습니다."

        val result = manager.sendTemplatedMessage(
            address = "01012345678",
            template = template,
            context = context
        )
    }

    /**
     * Example: Bulk sending with queue
     */
    suspend fun exampleBulkSending(manager: EngineManager) {
        val messages = listOf(
            BulkMessageData("01012345678", "메시지 1"),
            BulkMessageData("01012345679", "메시지 2"),
            BulkMessageData("01012345680", "메시지 3")
        )

        manager.enqueueBatch(messages)
        manager.startQueue()
    }

    /**
     * Example: Schedule message
     */
    suspend fun exampleScheduledMessage(manager: EngineManager) {
        val tomorrowAt9Am = System.currentTimeMillis() + (24 * 60 * 60 * 1000)

        manager.scheduleMessage(
            address = "01012345678",
            body = "내일 아침 예약 메시지입니다",
            scheduledTime = tomorrowAt9Am
        )
    }

    /**
     * Example: Recurring message
     */
    suspend fun exampleRecurringMessage(manager: EngineManager) {
        val dailyAt9Am = System.currentTimeMillis() + (9 * 60 * 60 * 1000)

        manager.scheduleMessage(
            address = "01012345678",
            body = "매일 9시 정기 알림입니다",
            scheduledTime = dailyAt9Am,
            recurring = true,
            recurringPattern = "DAILY"
        )
    }

    /**
     * Example: Template validation
     */
    suspend fun exampleValidateTemplate(manager: EngineManager) {
        val template = "{고객명}님께 {날짜}에 배송됩니다"

        val result = manager.validateTemplate(template)
        if (result.isValid) {
            println("Template is valid")
            println("Variables found: ${result.variableCount}")
        } else {
            println("Template errors: ${result.errors}")
        }
    }

    /**
     * Example: Monitor queue progress
     */
    suspend fun exampleMonitorQueue(manager: EngineManager) {
        manager.getQueueStats().collect { stats ->
            println("Pending: ${stats.pendingCount}, Completed: ${stats.completedCount}, Failed: ${stats.failedCount}")
        }
    }
}
