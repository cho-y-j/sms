package com.bizconnect.server.routes

import com.bizconnect.server.database.*
import com.bizconnect.server.models.*
import com.bizconnect.server.security.PasswordManager
import com.bizconnect.server.security.JwtManager
import io.ktor.server.application.call
import io.ktor.server.request.receiveText
import io.ktor.server.routing.Route
import io.ktor.server.routing.route
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.put
import io.ktor.server.request.receive
import io.ktor.http.HttpStatusCode
import io.ktor.server.response.respond
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.transactions.transaction
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

fun Route.adminRoutes(jwtManager: JwtManager, passwordManager: PasswordManager) {
    route("/api/admin") {

        // POST /login - admin login
        post("/login") {
            try {
                val body = call.receiveText()
                val json = kotlinx.serialization.json.Json.parseToJsonElement(body) as kotlinx.serialization.json.JsonObject
                val request = AdminLoginRequest(
                    json["username"]?.let { (it as kotlinx.serialization.json.JsonPrimitive).content } ?: "",
                    json["password"]?.let { (it as kotlinx.serialization.json.JsonPrimitive).content } ?: ""
                )

                val admin = transaction {
                    AdminUsersTable.selectAll()
                        .where { AdminUsersTable.username eq request.username }
                        .firstOrNull()
                }

                if (admin == null) {
                    call.respond(HttpStatusCode.Unauthorized, mapOf("error" to "Invalid credentials"))
                    return@post
                }

                if (!passwordManager.verifyPassword(request.password, admin[AdminUsersTable.passwordHash])) {
                    call.respond(HttpStatusCode.Unauthorized, mapOf("error" to "Invalid credentials"))
                    return@post
                }

                val adminId = admin[AdminUsersTable.id]
                val tokenPair = jwtManager.createTokenPair(adminId, "admin:${admin[AdminUsersTable.username]}")

                call.respond(HttpStatusCode.OK, mapOf(
                    "accessToken" to tokenPair.accessToken,
                    "refreshToken" to tokenPair.refreshToken,
                    "expiresIn" to tokenPair.expiresIn.toString(),
                    "role" to admin[AdminUsersTable.role]
                ))
            } catch (e: Exception) {
                call.respond(HttpStatusCode.BadRequest, mapOf("error" to (e.message ?: "Login failed")))
            }
        }

        // GET /dashboard - overview stats
        get("/dashboard") {
            try {
                val today = LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE)
                val monthStart = LocalDate.now().withDayOfMonth(1).format(DateTimeFormatter.ISO_LOCAL_DATE)

                val dashboard = transaction {
                    val totalUsers = UsersTable.selectAll().count().toInt()
                    val activeUsers = UsersTable.selectAll()
                        .where { UsersTable.isActive eq true }
                        .count().toInt()

                    val todayMessages = SmsLogsTable.selectAll()
                        .where { SmsLogsTable.sentAt greaterEq todayStartMillis() }
                        .count().toInt()

                    val monthlyRevenue = PaymentsTable.selectAll()
                        .where {
                            (PaymentsTable.status eq "completed") and
                            (PaymentsTable.createdAt greaterEq monthStartMillis())
                        }
                        .sumOf { it[PaymentsTable.amount] }.toDouble()

                    val recentPayments = PaymentsTable.selectAll()
                        .orderBy(PaymentsTable.createdAt, SortOrder.DESC)
                        .limit(10)
                        .map { row ->
                            PaymentResponse(
                                id = row[PaymentsTable.id],
                                amount = row[PaymentsTable.amount],
                                type = row[PaymentsTable.type],
                                status = row[PaymentsTable.status],
                                createdAt = Instant.ofEpochMilli(row[PaymentsTable.createdAt])
                                    .atZone(ZoneId.systemDefault()).toString()
                            )
                        }

                    AdminDashboard(
                        totalUsers = totalUsers,
                        activeUsers = activeUsers,
                        todayMessages = todayMessages,
                        monthlyRevenue = monthlyRevenue,
                        recentPayments = recentPayments
                    )
                }

                call.respond(HttpStatusCode.OK, dashboard)
            } catch (e: Exception) {
                call.respond(HttpStatusCode.InternalServerError, mapOf("error" to "Failed to load dashboard"))
            }
        }

        // GET /users - user list with pagination
        get("/users") {
            try {
                val page = call.request.queryParameters["page"]?.toIntOrNull() ?: 1
                val limit = call.request.queryParameters["limit"]?.toIntOrNull() ?: 20
                val offset = ((page - 1) * limit).toLong()

                val result = transaction {
                    val total = UsersTable.selectAll().count().toInt()
                    val users = UsersTable.selectAll()
                        .orderBy(UsersTable.createdAt, SortOrder.DESC)
                        .limit(limit, offset)
                        .map { row ->
                            val userId = row[UsersTable.id]

                            val subscription = SubscriptionsTable.selectAll()
                                .where { (SubscriptionsTable.userId eq userId) and (SubscriptionsTable.isActive eq true) }
                                .firstOrNull()

                            val creditBalance = CreditBalancesTable.selectAll()
                                .where { CreditBalancesTable.userId eq userId }
                                .firstOrNull()

                            val todayUsage = DailyUsageTable.selectAll()
                                .where {
                                    (DailyUsageTable.userId eq userId) and
                                    (DailyUsageTable.date eq LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE))
                                }
                                .firstOrNull()

                            UserDetail(
                                id = userId,
                                phoneNumber = row[UsersTable.phone],
                                email = row[UsersTable.email],
                                displayName = row[UsersTable.name],
                                status = if (row[UsersTable.isActive]) "active" else "suspended",
                                tier = subscription?.get(SubscriptionsTable.tier) ?: "free",
                                creditBalance = creditBalance?.get(CreditBalancesTable.balance) ?: 0.0,
                                todayUsage = todayUsage?.let {
                                    it[DailyUsageTable.smsCount] + it[DailyUsageTable.lmsCount] + it[DailyUsageTable.mmsCount]
                                } ?: 0,
                                createdAt = Instant.ofEpochMilli(row[UsersTable.createdAt])
                                    .atZone(ZoneId.systemDefault()).toString()
                            )
                        }

                    PaginatedResponse(data = users, total = total, page = page, pageSize = limit)
                }

                call.respond(HttpStatusCode.OK, result)
            } catch (e: Exception) {
                call.respond(HttpStatusCode.InternalServerError, mapOf("error" to "Failed to load users"))
            }
        }

        // GET /users/{id} - user detail
        get("/users/{id}") {
            try {
                val userId = call.parameters["id"]
                    ?: return@get call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Missing user id"))

                val userDetail = transaction {
                    val row = UsersTable.selectAll()
                        .where { UsersTable.id eq userId }
                        .firstOrNull() ?: return@transaction null

                    val subscription = SubscriptionsTable.selectAll()
                        .where { (SubscriptionsTable.userId eq userId) and (SubscriptionsTable.isActive eq true) }
                        .firstOrNull()

                    val creditBalance = CreditBalancesTable.selectAll()
                        .where { CreditBalancesTable.userId eq userId }
                        .firstOrNull()

                    val todayUsage = DailyUsageTable.selectAll()
                        .where {
                            (DailyUsageTable.userId eq userId) and
                            (DailyUsageTable.date eq LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE))
                        }
                        .firstOrNull()

                    UserDetail(
                        id = userId,
                        phoneNumber = row[UsersTable.phone],
                        email = row[UsersTable.email],
                        displayName = row[UsersTable.name],
                        status = if (row[UsersTable.isActive]) "active" else "suspended",
                        tier = subscription?.get(SubscriptionsTable.tier) ?: "free",
                        creditBalance = creditBalance?.get(CreditBalancesTable.balance) ?: 0.0,
                        todayUsage = todayUsage?.let {
                            it[DailyUsageTable.smsCount] + it[DailyUsageTable.lmsCount] + it[DailyUsageTable.mmsCount]
                        } ?: 0,
                        createdAt = Instant.ofEpochMilli(row[UsersTable.createdAt])
                            .atZone(ZoneId.systemDefault()).toString()
                    )
                }

                if (userDetail == null) {
                    call.respond(HttpStatusCode.NotFound, mapOf("error" to "User not found"))
                } else {
                    call.respond(HttpStatusCode.OK, userDetail)
                }
            } catch (e: Exception) {
                call.respond(HttpStatusCode.InternalServerError, mapOf("error" to "Failed to load user"))
            }
        }

        // PUT /users/{id}/status - change user status (active/suspended)
        put("/users/{id}/status") {
            try {
                val userId = call.parameters["id"]
                    ?: return@put call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Missing user id"))
                val request = call.receive<StatusUpdateRequest>()

                val validStatuses = listOf("active", "suspended")
                if (request.status !in validStatuses) {
                    call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Invalid status. Use: $validStatuses"))
                    return@put
                }

                transaction {
                    UsersTable.update({ UsersTable.id eq userId }) {
                        it[isActive] = request.status == "active"
                        it[updatedAt] = System.currentTimeMillis()
                    }
                }

                call.respond(HttpStatusCode.OK, mapOf("message" to "User status updated", "status" to request.status))
            } catch (e: Exception) {
                call.respond(HttpStatusCode.BadRequest, mapOf("error" to (e.message ?: "Failed to update status")))
            }
        }

        // PUT /users/{id}/tier - change subscription tier
        put("/users/{id}/tier") {
            try {
                val userId = call.parameters["id"]
                    ?: return@put call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Missing user id"))
                val request = call.receive<TierUpdateRequest>()

                val validTiers = listOf("free", "paid", "premium")
                if (request.tier !in validTiers) {
                    call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Invalid tier. Use: $validTiers"))
                    return@put
                }

                transaction {
                    // Deactivate current subscription
                    SubscriptionsTable.update({
                        (SubscriptionsTable.userId eq userId) and (SubscriptionsTable.isActive eq true)
                    }) {
                        it[isActive] = false
                        it[endDate] = System.currentTimeMillis()
                    }

                    // Create new subscription
                    val priceMap = mapOf("free" to 0, "paid" to 4900, "premium" to 9900)
                    SubscriptionsTable.insert {
                        it[id] = java.util.UUID.randomUUID().toString()
                        it[SubscriptionsTable.userId] = userId
                        it[tier] = request.tier
                        it[startDate] = System.currentTimeMillis()
                        it[isActive] = true
                        it[monthlyPrice] = priceMap[request.tier] ?: 0
                        it[createdAt] = System.currentTimeMillis()
                    }
                }

                call.respond(HttpStatusCode.OK, mapOf("message" to "User tier updated", "tier" to request.tier))
            } catch (e: Exception) {
                call.respond(HttpStatusCode.BadRequest, mapOf("error" to (e.message ?: "Failed to update tier")))
            }
        }

        // GET /payments - payment history
        get("/payments") {
            try {
                val page = call.request.queryParameters["page"]?.toIntOrNull() ?: 1
                val limit = call.request.queryParameters["limit"]?.toIntOrNull() ?: 20
                val offset = ((page - 1) * limit).toLong()

                val result = transaction {
                    val total = PaymentsTable.selectAll().count().toInt()
                    val payments = PaymentsTable.selectAll()
                        .orderBy(PaymentsTable.createdAt, SortOrder.DESC)
                        .limit(limit, offset)
                        .map { row ->
                            PaymentResponse(
                                id = row[PaymentsTable.id],
                                amount = row[PaymentsTable.amount],
                                type = row[PaymentsTable.type],
                                status = row[PaymentsTable.status],
                                createdAt = Instant.ofEpochMilli(row[PaymentsTable.createdAt])
                                    .atZone(ZoneId.systemDefault()).toString()
                            )
                        }

                    PaginatedResponse(data = payments, total = total, page = page, pageSize = limit)
                }

                call.respond(HttpStatusCode.OK, result)
            } catch (e: Exception) {
                call.respond(HttpStatusCode.InternalServerError, mapOf("error" to "Failed to load payments"))
            }
        }

        // GET /usage - usage statistics
        get("/usage") {
            try {
                val date = call.request.queryParameters["date"]
                    ?: LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE)

                val usageList = transaction {
                    DailyUsageTable.selectAll()
                        .where { DailyUsageTable.date eq date }
                        .map { row ->
                            UsageStats(
                                date = row[DailyUsageTable.date],
                                smsCount = row[DailyUsageTable.smsCount],
                                lmsCount = row[DailyUsageTable.lmsCount],
                                mmsCount = row[DailyUsageTable.mmsCount],
                                callbackCount = row[DailyUsageTable.callbackCount],
                                aiTokens = row[DailyUsageTable.aiTokens],
                                totalCost = row[DailyUsageTable.totalCost]
                            )
                        }
                }

                // Aggregate totals
                val totalSms = usageList.sumOf { it.smsCount }
                val totalLms = usageList.sumOf { it.lmsCount }
                val totalMms = usageList.sumOf { it.mmsCount }
                val totalCallback = usageList.sumOf { it.callbackCount }
                val totalAiTokens = usageList.sumOf { it.aiTokens }
                val totalCost = usageList.sumOf { it.totalCost }

                call.respond(HttpStatusCode.OK, mapOf(
                    "date" to date,
                    "userCount" to usageList.size,
                    "totalSms" to totalSms,
                    "totalLms" to totalLms,
                    "totalMms" to totalMms,
                    "totalCallback" to totalCallback,
                    "totalAiTokens" to totalAiTokens,
                    "totalCost" to totalCost
                ))
            } catch (e: Exception) {
                call.respond(HttpStatusCode.InternalServerError, mapOf("error" to "Failed to load usage"))
            }
        }

        // GET /config - app config
        get("/config") {
            try {
                val configs = transaction {
                    AppConfigTable.selectAll().map { row ->
                        AppConfigItem(
                            key = row[AppConfigTable.key],
                            value = row[AppConfigTable.value],
                            description = row[AppConfigTable.description]
                        )
                    }
                }

                call.respond(HttpStatusCode.OK, configs)
            } catch (e: Exception) {
                call.respond(HttpStatusCode.InternalServerError, mapOf("error" to "Failed to load config"))
            }
        }

        // PUT /config - update config
        put("/config") {
            try {
                val items = call.receive<List<AppConfigItem>>()

                transaction {
                    for (item in items) {
                        val updated = AppConfigTable.update({ AppConfigTable.key eq item.key }) {
                            it[value] = item.value
                            if (item.description != null) {
                                it[description] = item.description
                            }
                            it[updatedAt] = System.currentTimeMillis()
                        }

                        if (updated == 0) {
                            AppConfigTable.insert {
                                it[id] = java.util.UUID.randomUUID().toString()
                                it[key] = item.key
                                it[value] = item.value
                                it[description] = item.description
                                it[updatedAt] = System.currentTimeMillis()
                            }
                        }
                    }
                }

                call.respond(HttpStatusCode.OK, mapOf("message" to "Config updated"))
            } catch (e: Exception) {
                call.respond(HttpStatusCode.BadRequest, mapOf("error" to (e.message ?: "Failed to update config")))
            }
        }
    }
}

private fun todayStartMillis(): Long {
    return LocalDate.now()
        .atStartOfDay(ZoneId.systemDefault())
        .toInstant()
        .toEpochMilli()
}

private fun monthStartMillis(): Long {
    return LocalDate.now()
        .withDayOfMonth(1)
        .atStartOfDay(ZoneId.systemDefault())
        .toInstant()
        .toEpochMilli()
}
