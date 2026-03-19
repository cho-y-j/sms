package com.bizconnect.server.models

import kotlinx.serialization.Serializable

@Serializable
data class HealthResponse(
    val status: String,
    val version: String,
    val timestamp: Long
)

@Serializable
data class MessageRequest(
    val recipients: List<String>,
    val messageBody: String,
    val messageType: String = "SMS", // SMS, LMS, MMS
    val scheduledTime: Long? = null
)

@Serializable
data class MessageResponse(
    val id: Long,
    val status: String,
    val message: String,
    val timestamp: Long
)

@Serializable
data class LoginRequest(
    val phoneNumber: String,
    val password: String
)

@Serializable
data class LoginResponse(
    val accessToken: String,
    val refreshToken: String,
    val expiresIn: Long,
    val user: UserInfo
)

@Serializable
data class UserInfo(
    val id: Long,
    val phoneNumber: String,
    val email: String,
    val displayName: String,
    val status: String
)

@Serializable
data class ErrorResponse(
    val error: String,
    val message: String,
    val timestamp: Long
)

@Serializable
data class PaginatedResponse<T>(
    val data: List<T>,
    val total: Int,
    val page: Int,
    val pageSize: Int
)

// Auth
@Serializable
data class SignupRequest(
    val phoneNumber: String,
    val email: String,
    val password: String,
    val displayName: String
)

@Serializable
data class AuthResponse(
    val token: String,
    val userId: String,
    val displayName: String,
    val tier: String
)

// Subscription
@Serializable
data class SubscriptionInfo(
    val tier: String,
    val isActive: Boolean,
    val startDate: String,
    val endDate: String?
)

@Serializable
data class SubscriptionChangeRequest(
    val tier: String
)

// Payment
@Serializable
data class PaymentRequest(
    val amount: Int,
    val type: String,
    val description: String
)

@Serializable
data class PaymentResponse(
    val id: String,
    val amount: Int,
    val type: String,
    val status: String,
    val createdAt: String
)

// Usage
@Serializable
data class UsageStats(
    val date: String,
    val smsCount: Int,
    val lmsCount: Int,
    val mmsCount: Int,
    val callbackCount: Int,
    val aiTokens: Int,
    val totalCost: Double
)

@Serializable
data class UsageSummary(
    val todayUsage: UsageStats?,
    val monthlyTotal: UsageStats,
    val dailyLimit: Int,
    val remaining: Int
)

// Admin
@Serializable
data class AdminLoginRequest(
    val username: String,
    val password: String
)

@Serializable
data class AdminDashboard(
    val totalUsers: Int,
    val activeUsers: Int,
    val todayMessages: Int,
    val monthlyRevenue: Double,
    val recentPayments: List<PaymentResponse>
)

@Serializable
data class UserDetail(
    val id: String,
    val phoneNumber: String,
    val email: String,
    val displayName: String,
    val status: String,
    val tier: String,
    val creditBalance: Double,
    val todayUsage: Int,
    val createdAt: String
)

// Config
@Serializable
data class AppConfigItem(
    val key: String,
    val value: String,
    val description: String?
)

// Status update
@Serializable
data class StatusUpdateRequest(
    val status: String
)

// Tier update
@Serializable
data class TierUpdateRequest(
    val tier: String
)

// Usage record
@Serializable
data class UsageRecordRequest(
    val smsCount: Int = 0,
    val lmsCount: Int = 0,
    val mmsCount: Int = 0,
    val callbackCount: Int = 0,
    val aiTokens: Int = 0,
    val totalCost: Double = 0.0
)
