package com.bizconnect.server.database.schema

import org.jetbrains.exposed.dao.id.LongIdTable
import org.jetbrains.exposed.sql.Column
import org.jetbrains.exposed.sql.javatime.datetime
import java.time.LocalDateTime

object UsersTable : LongIdTable("users") {
    val phoneNumber: Column<String> = varchar("phone_number", 20).uniqueIndex()
    val email: Column<String> = varchar("email", 255).uniqueIndex()
    val passwordHash: Column<String> = varchar("password_hash", 255)
    val displayName: Column<String> = varchar("display_name", 255)
    val status: Column<String> = varchar("status", 50).default("active") // active, inactive, suspended
    val createdAt: Column<LocalDateTime> = datetime("created_at").default(LocalDateTime.now())
    val updatedAt: Column<LocalDateTime> = datetime("updated_at").default(LocalDateTime.now())
}

object SmsMessagesTable : LongIdTable("sms_messages") {
    val userId: Column<Long> = long("user_id").references(UsersTable.id)
    val phoneNumber: Column<String> = varchar("phone_number", 20)
    val messageBody: Column<String> = text("message_body")
    val messageType: Column<String> = varchar("message_type", 50) // SMS, LMS, MMS
    val isIncoming: Column<Boolean> = bool("is_incoming")
    val status: Column<String> = varchar("status", 50) // pending, sent, delivered, failed
    val sendAttempts: Column<Int> = integer("send_attempts").default(0)
    val maxAttempts: Column<Int> = integer("max_attempts").default(3)
    val createdAt: Column<LocalDateTime> = datetime("created_at").default(LocalDateTime.now())
    val sentAt: Column<LocalDateTime?> = datetime("sent_at").nullable()
    val deliveredAt: Column<LocalDateTime?> = datetime("delivered_at").nullable()
    val failureReason: Column<String?> = varchar("failure_reason", 500).nullable()
}

// 구독 관리
object SubscriptionsTable : LongIdTable("subscriptions") {
    val userId: Column<Long> = long("user_id").references(UsersTable.id)
    val tier: Column<String> = varchar("tier", 20) // "free", "paid", "premium"
    val startDate: Column<LocalDateTime> = datetime("start_date")
    val endDate: Column<LocalDateTime?> = datetime("end_date").nullable()
    val isActive: Column<Boolean> = bool("is_active").default(true)
    val monthlyPrice: Column<Int> = integer("monthly_price").default(0)
    val createdAt: Column<LocalDateTime> = datetime("created_at").default(LocalDateTime.now())
}

// 결제 내역
object PaymentsTable : LongIdTable("payments") {
    val userId: Column<Long> = long("user_id").references(UsersTable.id)
    val amount: Column<Int> = integer("amount")
    val type: Column<String> = varchar("type", 30)
    val status: Column<String> = varchar("status", 20)
    val description: Column<String> = varchar("description", 500)
    val transactionId: Column<String?> = varchar("transaction_id", 100).nullable()
    val createdAt: Column<LocalDateTime> = datetime("created_at").default(LocalDateTime.now())
}

// 충전 잔액
object CreditBalancesTable : LongIdTable("credit_balances") {
    val userId: Column<Long> = long("user_id").references(UsersTable.id).uniqueIndex()
    val balance: Column<Double> = double("balance").default(0.0)
    val updatedAt: Column<LocalDateTime> = datetime("updated_at").default(LocalDateTime.now())
}

// 사용량 통계 (일별)
object DailyUsageTable : LongIdTable("daily_usage") {
    val userId: Column<Long> = long("user_id").references(UsersTable.id)
    val date: Column<String> = varchar("date", 10)
    val smsCount: Column<Int> = integer("sms_count").default(0)
    val lmsCount: Column<Int> = integer("lms_count").default(0)
    val mmsCount: Column<Int> = integer("mms_count").default(0)
    val callbackCount: Column<Int> = integer("callback_count").default(0)
    val aiTokens: Column<Int> = integer("ai_tokens").default(0)
    val totalCost: Column<Double> = double("total_cost").default(0.0)
}

// 관리자
object AdminUsersTable : LongIdTable("admin_users") {
    val username: Column<String> = varchar("username", 50).uniqueIndex()
    val passwordHash: Column<String> = varchar("password_hash", 255)
    val role: Column<String> = varchar("role", 20).default("admin")
    val createdAt: Column<LocalDateTime> = datetime("created_at").default(LocalDateTime.now())
}

// 앱 설정 (서버에서 관리)
object AppConfigTable : LongIdTable("app_config") {
    val key: Column<String> = varchar("key", 100).uniqueIndex()
    val value: Column<String> = varchar("value", 500)
    val description: Column<String?> = varchar("description", 200).nullable()
    val updatedAt: Column<LocalDateTime> = datetime("updated_at").default(LocalDateTime.now())
}
