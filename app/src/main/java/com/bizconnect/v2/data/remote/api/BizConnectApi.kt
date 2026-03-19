package com.bizconnect.v2.data.remote.api

import com.bizconnect.v2.data.remote.api.dto.*
import retrofit2.Response
import retrofit2.http.*

interface BizConnectApi {
    // ===== Authentication =====
    @POST("api/auth/register")
    suspend fun register(@Body request: RegisterRequest): Response<AuthResponse>

    @POST("api/auth/login")
    suspend fun login(@Body request: LoginRequest): Response<AuthResponse>

    @POST("api/auth/refresh")
    suspend fun refreshToken(@Body request: RefreshRequest): Response<AuthResponse>

    @POST("api/auth/logout")
    suspend fun logout(): Response<Unit>

    // ===== Tasks =====
    @GET("api/tasks")
    suspend fun getTasks(@Query("status") status: String? = null): Response<List<TaskDto>>

    @POST("api/tasks")
    suspend fun createTask(@Body task: CreateTaskRequest): Response<TaskDto>

    @POST("api/tasks/batch")
    suspend fun createBatchTasks(@Body tasks: BatchTaskRequest): Response<List<TaskDto>>

    @PUT("api/tasks/{id}/status")
    suspend fun updateTaskStatus(
        @Path("id") id: String,
        @Body status: UpdateStatusRequest
    ): Response<TaskDto>

    // ===== Customers =====
    @GET("api/customers")
    suspend fun getCustomers(): Response<List<CustomerDto>>

    @POST("api/customers")
    suspend fun createCustomer(@Body customer: CreateCustomerRequest): Response<CustomerDto>

    @PUT("api/customers/{id}")
    suspend fun updateCustomer(
        @Path("id") id: String,
        @Body customer: UpdateCustomerRequest
    ): Response<CustomerDto>

    @DELETE("api/customers/{id}")
    suspend fun deleteCustomer(@Path("id") id: String): Response<Unit>

    @POST("api/customers/import")
    suspend fun importCustomers(@Body customers: List<CreateCustomerRequest>): Response<ImportResult>

    @GET("api/customers/birthdays")
    suspend fun getBirthdays(): Response<List<CustomerDto>>

    // ===== SMS =====
    @POST("api/sms/send")
    suspend fun sendSmsFromWeb(@Body request: SendSmsRequest): Response<SendSmsResponse>

    @GET("api/sms/logs")
    suspend fun getSmsLogs(
        @Query("page") page: Int,
        @Query("limit") limit: Int
    ): Response<PaginatedResponse<SmsLogDto>>

    @GET("api/sms/stats")
    suspend fun getSmsStats(): Response<SmsStatsDto>

    @GET("api/sms/daily-limit")
    suspend fun getDailyLimit(): Response<DailyLimitDto>

    // ===== FCM =====
    @PUT("api/fcm/token")
    suspend fun updateFcmToken(@Body request: UpdateFcmTokenRequest): Response<Unit>

    // ===== Settings =====
    @GET("api/settings")
    suspend fun getSettings(): Response<SettingsDto>

    @PUT("api/settings")
    suspend fun updateSettings(@Body settings: UpdateSettingsRequest): Response<SettingsDto>

    @PUT("api/settings/callback")
    suspend fun updateCallbackSettings(@Body settings: UpdateCallbackRequest): Response<Unit>
}
