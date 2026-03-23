package com.bizconnect.server.database

import org.jetbrains.exposed.sql.Table

/**
 * Database schema using Jetbrains Exposed ORM
 * All queries use parameterized statements to prevent SQL injection
 */

object UsersTable : Table("users") {
    val id = varchar("id", 36)
    val email = varchar("email", 254).uniqueIndex()
    val passwordHash = varchar("password_hash", 255)
    val name = varchar("name", 100)
    val phone = varchar("phone", 20).uniqueIndex()
    val fcmToken = varchar("fcm_token", 500).nullable()
    val apiKeyHash = varchar("api_key_hash", 255).nullable()
    val role = varchar("role", 50).default("user")
    val isActive = bool("is_active").default(true)
    val loginAttempts = integer("login_attempts").default(0)
    val lockedUntil = long("locked_until").nullable()
    val lastLoginAt = long("last_login_at").nullable()
    val lastLoginIp = varchar("last_login_ip", 100).nullable()
    val createdAt = long("created_at")
    val updatedAt = long("updated_at")

    override val primaryKey = PrimaryKey(id)
}

object ApiKeysTable : Table("api_keys") {
    val id = varchar("id", 36)
    val userId = varchar("user_id", 36).references(UsersTable.id)
    val keyHash = varchar("key_hash", 255).uniqueIndex()
    val name = varchar("name", 100)
    val permissions = varchar("permissions", 500) // JSON array
    val rateLimitPerMinute = integer("rate_limit_per_minute").default(100)
    val isActive = bool("is_active").default(true)
    val lastUsedAt = long("last_used_at").nullable()
    val expiresAt = long("expires_at").nullable()
    val rotatedAt = long("rotated_at").nullable()
    val createdAt = long("created_at")

    override val primaryKey = PrimaryKey(id)
}

object TokenBlacklistTable : Table("token_blacklist") {
    val id = varchar("id", 36)
    val token = varchar("token", 500).uniqueIndex()
    val expiresAt = long("expires_at")
    val revokedAt = long("revoked_at")

    override val primaryKey = PrimaryKey(id)
}

object IpBlacklistTable : Table("ip_blacklist") {
    val id = varchar("id", 36)
    val ipAddress = varchar("ip_address", 45).uniqueIndex()
    val reason = varchar("reason", 255)
    val blockedAt = long("blocked_at")
    val expiresAt = long("expires_at").nullable()

    override val primaryKey = PrimaryKey(id)
}

object TasksTable : Table("tasks") {
    val id = varchar("id", 36)
    val userId = varchar("user_id", 36).references(UsersTable.id)
    val title = varchar("title", 255)
    val description = text("description").nullable()
    val status = varchar("status", 50).default("pending") // pending, scheduled, sent, failed, cancelled
    val scheduledTime = long("scheduled_time").nullable()
    val completedTime = long("completed_time").nullable()
    val retryCount = integer("retry_count").default(0)
    val errorMessage = text("error_message").nullable()
    val createdAt = long("created_at")
    val updatedAt = long("updated_at")
    val externalTaskId = varchar("external_task_id", 255).nullable()

    override val primaryKey = PrimaryKey(id)
}

object CustomersTable : Table("customers") {
    val id = varchar("id", 36)
    val userId = varchar("user_id", 36).references(UsersTable.id)
    val name = varchar("name", 100)
    val phone = varchar("phone", 20)
    val email = varchar("email", 254).nullable()
    val memo = text("memo").nullable()
    val groupId = varchar("group_id", 36).nullable()
    val tags = varchar("tags", 500).nullable() // JSON array
    val birthDate = varchar("birth_date", 10).nullable() // YYYY-MM-DD
    val lastContactedAt = long("last_contacted_at").nullable()
    val isActive = bool("is_active").default(true)
    val createdAt = long("created_at")
    val updatedAt = long("updated_at")
    val externalId = varchar("external_id", 255).nullable()

    override val primaryKey = PrimaryKey(id)
}

object CustomerGroupsTable : Table("customer_groups") {
    val id = varchar("id", 36)
    val userId = varchar("user_id", 36).references(UsersTable.id)
    val name = varchar("name", 100)
    val callbackEnabled = bool("callback_enabled").default(false)
    val useAi = bool("use_ai").default(false)
    val createdAt = long("created_at")
    val updatedAt = long("updated_at")

    override val primaryKey = PrimaryKey(id)
}

object SmsLogsTable : Table("sms_logs") {
    val id = varchar("id", 36)
    val userId = varchar("user_id", 36).references(UsersTable.id)
    val customerId = varchar("customer_id", 36).nullable()
    val recipientPhone = varchar("recipient_phone", 20)
    val messageContent = text("message_content")
    val characterCount = integer("character_count")
    val segmentCount = integer("segment_count").default(1)
    val status = varchar("status", 50).default("sent") // sent, delivered, failed, bounced
    val result = varchar("result", 500).nullable()
    val sentAt = long("sent_at")
    val deliveredAt = long("delivered_at").nullable()
    val errorCode = varchar("error_code", 50).nullable()
    val errorMessage = text("error_message").nullable()
    val wideshotSendCode = varchar("wideshot_send_code", 50).nullable()
    val requestedAt = long("requested_at")
    val externalId = varchar("external_id", 255).nullable()

    override val primaryKey = PrimaryKey(id)
}

object ScheduledMessagesTable : Table("scheduled_messages") {
    val id = varchar("id", 36)
    val userId = varchar("user_id", 36).references(UsersTable.id)
    val customerId = varchar("customer_id", 36).nullable()
    val recipientPhone = varchar("recipient_phone", 20)
    val messageContent = text("message_content")
    val scheduledTime = long("scheduled_time")
    val sentTime = long("sent_time").nullable()
    val status = varchar("status", 50).default("pending") // pending, sent, cancelled, failed
    val createdAt = long("created_at")
    val updatedAt = long("updated_at")
    val externalId = varchar("external_id", 255).nullable()

    override val primaryKey = PrimaryKey(id)
}

object CallbackSettingsTable : Table("callback_settings") {
    val id = varchar("id", 36)
    val userId = varchar("user_id", 36).references(UsersTable.id)
    val groupId = varchar("group_id", 36).nullable()
    val webhookUrl = varchar("webhook_url", 500)
    val webhookSecret = varchar("webhook_secret", 255)
    val events = varchar("events", 500) // JSON array: [sms_sent, sms_delivered, etc]
    val isActive = bool("is_active").default(true)
    val lastTriggeredAt = long("last_triggered_at").nullable()
    val failureCount = integer("failure_count").default(0)
    val createdAt = long("created_at")
    val updatedAt = long("updated_at")

    override val primaryKey = PrimaryKey(id)
}

object DailyLimitsTable : Table("daily_limits") {
    val id = varchar("id", 36)
    val userId = varchar("user_id", 36).references(UsersTable.id)
    val date = varchar("date", 10) // YYYY-MM-DD
    val charactersSent = integer("characters_sent").default(0)
    val messagesSent = integer("messages_sent").default(0)
    val remainingCharacters = integer("remaining_characters")
    val remainingMessages = integer("remaining_messages")
    val resetAt = long("reset_at")
    val updatedAt = long("updated_at")

    override val primaryKey = PrimaryKey(id)
}

object AuditLogsTable : Table("audit_logs") {
    val id = varchar("id", 36)
    val userId = varchar("user_id", 36).nullable()
    val action = varchar("action", 100)
    val details = text("details").nullable()
    val ipAddress = varchar("ip_address", 45)
    val userAgent = varchar("user_agent", 500).nullable()
    val severity = varchar("severity", 20).default("INFO") // INFO, WARNING, CRITICAL
    val timestamp = long("timestamp")

    override val primaryKey = PrimaryKey(id)

    init {
        index(false, userId, timestamp)
        index(false, action, timestamp)
    }
}

object RefreshTokensTable : Table("refresh_tokens") {
    val id = varchar("id", 36)
    val token = varchar("token", 500).uniqueIndex()
    val userId = varchar("user_id", 36).references(UsersTable.id)
    val isUsed = bool("is_used").default(false)
    val usedAt = long("used_at").nullable()
    val expiresAt = long("expires_at")
    val createdAt = long("created_at")

    override val primaryKey = PrimaryKey(id)
}

// 구독 관리
object SubscriptionsTable : Table("subscriptions") {
    val id = varchar("id", 36)
    val userId = varchar("user_id", 36).references(UsersTable.id)
    val tier = varchar("tier", 20) // "free", "paid", "premium"
    val startDate = long("start_date")
    val endDate = long("end_date").nullable()
    val isActive = bool("is_active").default(true)
    val monthlyPrice = integer("monthly_price").default(0) // won
    val createdAt = long("created_at")

    override val primaryKey = PrimaryKey(id)
}

// 결제 내역
object PaymentsTable : Table("payments") {
    val id = varchar("id", 36)
    val userId = varchar("user_id", 36).references(UsersTable.id)
    val amount = integer("amount") // won
    val type = varchar("type", 30) // "subscription", "credit_charge"
    val status = varchar("status", 20) // "pending", "completed", "failed", "refunded"
    val description = varchar("description", 500)
    val transactionId = varchar("transaction_id", 100).nullable()
    val createdAt = long("created_at")

    override val primaryKey = PrimaryKey(id)
}

// 충전 잔액
object CreditBalancesTable : Table("credit_balances") {
    val id = varchar("id", 36)
    val userId = varchar("user_id", 36).references(UsersTable.id).uniqueIndex()
    val balance = double("balance").default(0.0)
    val updatedAt = long("updated_at")

    override val primaryKey = PrimaryKey(id)
}

// 사용량 통계 (일별)
object DailyUsageTable : Table("daily_usage") {
    val id = varchar("id", 36)
    val userId = varchar("user_id", 36).references(UsersTable.id)
    val date = varchar("date", 10) // "2026-03-19"
    val smsCount = integer("sms_count").default(0)
    val lmsCount = integer("lms_count").default(0)
    val mmsCount = integer("mms_count").default(0)
    val callbackCount = integer("callback_count").default(0)
    val aiTokens = integer("ai_tokens").default(0)
    val totalCost = double("total_cost").default(0.0)

    override val primaryKey = PrimaryKey(id)

    init {
        index(false, userId, date)
    }
}

// 관리자
object AdminUsersTable : Table("admin_users") {
    val id = varchar("id", 36)
    val username = varchar("username", 50).uniqueIndex()
    val passwordHash = varchar("password_hash", 255)
    val role = varchar("role", 20).default("admin") // "admin", "superadmin"
    val createdAt = long("created_at")

    override val primaryKey = PrimaryKey(id)
}

// NICE Pay 결제 상세
object NicePaymentsTable : Table("nice_payments") {
    val id = varchar("id", 36)
    val orderId = varchar("order_id", 100).uniqueIndex()
    val userId = varchar("user_id", 36).nullable()
    val tid = varchar("tid", 100).nullable()
    val amount = integer("amount")
    val goodsName = varchar("goods_name", 200)
    val payMethod = varchar("pay_method", 30).default("card")
    val status = varchar("status", 30).default("ready") // ready, paid, failed, cancelled, partialCancelled
    val paymentType = varchar("payment_type", 30).nullable() // subscription, credit_charge
    val returnUrl = varchar("return_url", 500)
    val buyerName = varchar("buyer_name", 100).nullable()
    val buyerEmail = varchar("buyer_email", 254).nullable()
    val buyerTel = varchar("buyer_tel", 20).nullable()
    val authToken = varchar("auth_token", 500).nullable()
    val approveNo = varchar("approve_no", 100).nullable()
    val balanceAmt = integer("balance_amt").nullable()
    val channel = varchar("channel", 20).nullable()
    val cardInfo = text("card_info").nullable() // JSON
    val resultCode = varchar("result_code", 20).nullable()
    val resultMsg = varchar("result_msg", 500).nullable()
    val paidAt = long("paid_at").nullable()
    val cancelledAt = long("cancelled_at").nullable()
    val createdAt = long("created_at")
    val updatedAt = long("updated_at")

    override val primaryKey = PrimaryKey(id)
}

// 발송 작업 (배치 단위)
object SmsSendJobsTable : Table("sms_send_jobs") {
    val id = varchar("id", 36)
    val userId = varchar("user_id", 36).references(UsersTable.id)
    val totalCount = integer("total_count")
    val sentCount = integer("sent_count").default(0)
    val failedCount = integer("failed_count").default(0)
    val status = varchar("status", 20).default("queued") // queued, processing, completed, cancelled
    val sendMethod = varchar("send_method", 20).default("phone")
    val isAdMessage = bool("is_ad_message").default(false)
    val messageTemplate = text("message_template")
    val etaMinutes = integer("eta_minutes").default(0)
    val createdAt = long("created_at")
    val updatedAt = long("updated_at")

    override val primaryKey = PrimaryKey(id)
}

// 웹에서 폰 발송 대기 메시지
object PendingSmsTable : Table("pending_sms") {
    val id = varchar("id", 36)
    val userId = varchar("user_id", 36).references(UsersTable.id)
    val jobId = varchar("job_id", 36).nullable()
    val recipientPhone = varchar("recipient_phone", 20)
    val recipientName = varchar("recipient_name", 100).nullable()
    val messageContent = text("message_content")
    val sendMethod = varchar("send_method", 20).default("phone")
    val status = varchar("status", 20).default("pending")
    val errorMessage = text("error_message").nullable()
    val createdAt = long("created_at")
    val processedAt = long("processed_at").nullable()

    override val primaryKey = PrimaryKey(id)

    init {
        index(false, userId, status)
        index(false, jobId)
    }
}

// 카테고리 관리
object UserCategoriesTable : Table("user_categories") {
    val id = varchar("id", 36)
    val userId = varchar("user_id", 36).references(UsersTable.id)
    val name = varchar("name", 50)
    val color = varchar("color", 7).default("#6366f1")
    val sortOrder = integer("sort_order").default(0)
    val createdAt = long("created_at")

    override val primaryKey = PrimaryKey(id)

    init {
        index(false, userId)
    }
}

// 메시지 템플릿
object MessageTemplatesTable : Table("message_templates") {
    val id = varchar("id", 36)
    val userId = varchar("user_id", 36).references(UsersTable.id)
    val title = varchar("title", 100)
    val content = text("content")
    val category = varchar("category", 50).nullable()
    val isFromPhone = bool("is_from_phone").default(false)
    val createdAt = long("created_at")
    val updatedAt = long("updated_at")

    override val primaryKey = PrimaryKey(id)
}

// 사용자 연락처 (웹 동기화용)
object UserContactsTable : Table("user_contacts") {
    val id = varchar("id", 36)
    val userId = varchar("user_id", 36).references(UsersTable.id)
    val name = varchar("name", 100)
    val phone = varchar("phone", 20)
    val email = varchar("email", 254).nullable()
    val company = varchar("company", 100).nullable()
    val memo = text("memo").nullable()
    val category = varchar("category", 50).nullable()
    val tags = varchar("tags", 500).nullable()
    val lastContactDate = long("last_contact_date").nullable()
    val totalMessageCount = integer("total_message_count").default(0)
    val notes = text("notes").nullable()
    val birthday = varchar("birthday", 10).nullable()       // MM-DD or YYYY-MM-DD
    val anniversary = varchar("anniversary", 10).nullable()  // MM-DD or YYYY-MM-DD
    val createdAt = long("created_at")
    val updatedAt = long("updated_at")

    override val primaryKey = PrimaryKey(id)

    init {
        index(false, userId)
    }
}

// 사용자 SMS 이력 (앱→서버 동기화)
object UserSmsHistoryTable : Table("user_sms_history") {
    val id = varchar("id", 36)
    val userId = varchar("user_id", 36).references(UsersTable.id)
    val threadId = long("thread_id")
    val recipientPhone = varchar("recipient_phone", 20)
    val recipientName = varchar("recipient_name", 100).nullable()
    val body = text("body").nullable()
    val timestamp = long("timestamp")
    val type = varchar("type", 10) // sent, received
    val isMms = bool("is_mms").default(false)
    val createdAt = long("created_at")
    override val primaryKey = PrimaryKey(id)
    init {
        index(false, userId, recipientPhone)
        index(false, userId, timestamp)
    }
}

// SMS 인증 코드
object VerificationCodesTable : Table("verification_codes") {
    val id = varchar("id", 36)
    val phone = varchar("phone", 20)
    val code = varchar("code", 6)
    val purpose = varchar("purpose", 30) // signup, login_device
    val expiresAt = long("expires_at")
    val verified = bool("verified").default(false)
    val attempts = integer("attempts").default(0)
    val createdAt = long("created_at")
    override val primaryKey = PrimaryKey(id)
    init { index(false, phone) }
}

// 관리자 제공 템플릿 카테고리
object AdminTemplateCategoriesTable : Table("admin_template_categories") {
    val id = varchar("id", 36)
    val name = varchar("name", 50)
    val icon = varchar("icon", 10).default("📋")
    val sortOrder = integer("sort_order").default(0)
    val createdAt = long("created_at")
    override val primaryKey = PrimaryKey(id)
}

// 관리자 제공 템플릿
object AdminTemplatesTable : Table("admin_templates") {
    val id = varchar("id", 36)
    val categoryId = varchar("category_id", 36)
    val title = varchar("title", 100)
    val content = text("content")
    val sortOrder = integer("sort_order").default(0)
    val createdAt = long("created_at")
    val updatedAt = long("updated_at")
    override val primaryKey = PrimaryKey(id)
}

// 예약 문자
object ScheduledSmsTable : Table("scheduled_sms") {
    val id = varchar("id", 36)
    val userId = varchar("user_id", 36).references(UsersTable.id)
    val recipientPhone = varchar("recipient_phone", 20)
    val recipientName = varchar("recipient_name", 100).nullable()
    val messageContent = text("message_content")
    val scheduledAt = long("scheduled_at")
    val sendMethod = varchar("send_method", 20).default("phone")
    val status = varchar("status", 20).default("scheduled") // scheduled, sent, failed, cancelled
    val createdAt = long("created_at")

    override val primaryKey = PrimaryKey(id)

    init {
        index(false, userId, status)
        index(false, scheduledAt)
    }
}

// 인증된 기기
object TrustedDevicesTable : Table("trusted_devices") {
    val id = varchar("id", 36)
    val userId = varchar("user_id", 36).references(UsersTable.id)
    val deviceToken = varchar("device_token", 64)
    val deviceName = varchar("device_name", 200) // "Chrome / Windows" etc
    val platform = varchar("platform", 10).default("web") // web, app
    val lastUsedAt = long("last_used_at")
    val createdAt = long("created_at")
    override val primaryKey = PrimaryKey(id)
    init {
        index(false, userId)
        index(true, deviceToken) // unique
    }
}

// 앱 설정 (서버에서 관리)
object AppConfigTable : Table("app_config") {
    val id = varchar("id", 36)
    val key = varchar("key", 100).uniqueIndex()
    val value = varchar("value", 500)
    val description = varchar("description", 200).nullable()
    val updatedAt = long("updated_at")

    override val primaryKey = PrimaryKey(id)
}
