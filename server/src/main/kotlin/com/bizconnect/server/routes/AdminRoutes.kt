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
import io.ktor.server.routing.delete
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.deleteWhere
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
                val body = call.receiveText()
                val json = kotlinx.serialization.json.Json.parseToJsonElement(body) as kotlinx.serialization.json.JsonObject
                val statusValue = json["status"]?.let { (it as kotlinx.serialization.json.JsonPrimitive).content } ?: ""

                val validStatuses = listOf("active", "suspended")
                if (statusValue !in validStatuses) {
                    call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Invalid status. Use: $validStatuses"))
                    return@put
                }

                transaction {
                    UsersTable.update({ UsersTable.id eq userId }) {
                        it[isActive] = statusValue == "active"
                        it[updatedAt] = System.currentTimeMillis()
                    }
                }

                call.respond(HttpStatusCode.OK, mapOf("message" to "User status updated", "status" to statusValue))
            } catch (e: Exception) {
                call.respond(HttpStatusCode.BadRequest, mapOf("error" to (e.message ?: "Failed to update status")))
            }
        }

        // PUT /users/{id}/tier - change subscription tier
        put("/users/{id}/tier") {
            try {
                val userId = call.parameters["id"]
                    ?: return@put call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Missing user id"))
                val body = call.receiveText()
                val json = kotlinx.serialization.json.Json.parseToJsonElement(body) as kotlinx.serialization.json.JsonObject
                val tierValue = json["tier"]?.let { (it as kotlinx.serialization.json.JsonPrimitive).content } ?: ""

                val validTiers = listOf("free", "paid", "premium")
                if (tierValue !in validTiers) {
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
                        it[tier] = tierValue
                        it[startDate] = System.currentTimeMillis()
                        it[isActive] = true
                        it[monthlyPrice] = priceMap[tierValue] ?: 0
                        it[createdAt] = System.currentTimeMillis()
                    }
                }

                call.respond(HttpStatusCode.OK, mapOf("message" to "User tier updated", "tier" to tierValue))
            } catch (e: Exception) {
                call.respond(HttpStatusCode.BadRequest, mapOf("error" to (e.message ?: "Failed to update tier")))
            }
        }

        // DELETE /users/{id} - 회원 삭제
        delete("/users/{id}") {
            try {
                val userId = call.parameters["id"]
                    ?: return@delete call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Missing user id"))

                transaction {
                    // 관련 데이터 모두 삭제 (FK 순서 주의)
                    PendingSmsTable.deleteWhere { PendingSmsTable.userId eq userId }
                    SmsSendJobsTable.deleteWhere { SmsSendJobsTable.userId eq userId }
                    UserContactsTable.deleteWhere { UserContactsTable.userId eq userId }
                    UserCategoriesTable.deleteWhere { UserCategoriesTable.userId eq userId }
                    MessageTemplatesTable.deleteWhere { MessageTemplatesTable.userId eq userId }
                    CreditBalancesTable.deleteWhere { CreditBalancesTable.userId eq userId }
                    SubscriptionsTable.deleteWhere { SubscriptionsTable.userId eq userId }
                    SmsLogsTable.deleteWhere { SmsLogsTable.userId eq userId }
                    PaymentsTable.deleteWhere { PaymentsTable.userId eq userId }
                    NicePaymentsTable.deleteWhere { NicePaymentsTable.userId eq userId }
                    DailyUsageTable.deleteWhere { DailyUsageTable.userId eq userId }
                    RefreshTokensTable.deleteWhere { RefreshTokensTable.userId eq userId }
                    UsersTable.deleteWhere { UsersTable.id eq userId }
                }

                call.respond(HttpStatusCode.OK, mapOf("message" to "회원이 삭제되었습니다"))
            } catch (e: Exception) {
                call.respond(HttpStatusCode.BadRequest, mapOf("error" to (e.message ?: "Failed to delete user")))
            }
        }

        // PUT /users/{id}/password - 관리자 비밀번호 강제 변경
        put("/users/{id}/password") {
            try {
                val userId = call.parameters["id"]
                    ?: return@put call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Missing user id"))
                val body = call.receiveText()
                val json = kotlinx.serialization.json.Json.parseToJsonElement(body) as kotlinx.serialization.json.JsonObject
                val newPassword = json["password"]?.let { (it as kotlinx.serialization.json.JsonPrimitive).content } ?: ""

                if (newPassword.length < 4) {
                    call.respond(HttpStatusCode.BadRequest, mapOf("error" to "비밀번호는 4자 이상이어야 합니다"))
                    return@put
                }

                transaction {
                    UsersTable.update({ UsersTable.id eq userId }) {
                        it[passwordHash] = passwordManager.hashPassword(newPassword)
                        it[updatedAt] = System.currentTimeMillis()
                        it[loginAttempts] = 0
                        it[lockedUntil] = null
                    }
                }

                call.respond(HttpStatusCode.OK, mapOf("message" to "비밀번호가 변경되었습니다"))
            } catch (e: Exception) {
                call.respond(HttpStatusCode.BadRequest, mapOf("error" to (e.message ?: "Failed to change password")))
            }
        }

        // POST /users/{id}/credit - 관리자 크레딧 수동 충전/차감
        post("/users/{id}/credit") {
            try {
                val userId = call.parameters["id"]
                    ?: return@post call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Missing user id"))
                val body = call.receiveText()
                val json = kotlinx.serialization.json.Json.parseToJsonElement(body) as kotlinx.serialization.json.JsonObject
                val amount = json["amount"]?.let { (it as kotlinx.serialization.json.JsonPrimitive).content.toDoubleOrNull() } ?: 0.0
                val reason = json["reason"]?.let { (it as kotlinx.serialization.json.JsonPrimitive).content } ?: "관리자 수동 조정"

                transaction {
                    val existing = CreditBalancesTable.selectAll()
                        .where { CreditBalancesTable.userId eq userId }
                        .firstOrNull()

                    if (existing != null) {
                        val currentBalance = existing[CreditBalancesTable.balance]
                        CreditBalancesTable.update({ CreditBalancesTable.userId eq userId }) {
                            it[balance] = (currentBalance + amount).coerceAtLeast(0.0)
                            it[updatedAt] = System.currentTimeMillis()
                        }
                    } else {
                        CreditBalancesTable.insert {
                            it[id] = java.util.UUID.randomUUID().toString()
                            it[CreditBalancesTable.userId] = userId
                            it[balance] = amount.coerceAtLeast(0.0)
                            it[updatedAt] = System.currentTimeMillis()
                        }
                    }

                    // 결제 기록
                    PaymentsTable.insert {
                        it[id] = java.util.UUID.randomUUID().toString()
                        it[PaymentsTable.userId] = userId
                        it[PaymentsTable.amount] = amount.toInt()
                        it[type] = "admin_adjust"
                        it[status] = "completed"
                        it[description] = reason
                        it[createdAt] = System.currentTimeMillis()
                    }
                }

                call.respond(HttpStatusCode.OK, mapOf("message" to "크레딧이 조정되었습니다", "amount" to amount.toString()))
            } catch (e: Exception) {
                call.respond(HttpStatusCode.BadRequest, mapOf("error" to (e.message ?: "Failed to adjust credit")))
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
                    "userCount" to usageList.size.toString(),
                    "totalSms" to totalSms.toString(),
                    "totalLms" to totalLms.toString(),
                    "totalMms" to totalMms.toString(),
                    "totalCallback" to totalCallback.toString(),
                    "totalAiTokens" to totalAiTokens.toString(),
                    "totalCost" to totalCost.toString()
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

        // ===== Admin Template Categories =====
        get("/template-categories") {
            try {
                val categories = transaction {
                    AdminTemplateCategoriesTable.selectAll()
                        .orderBy(AdminTemplateCategoriesTable.sortOrder, SortOrder.ASC)
                        .map { row ->
                            mapOf(
                                "id" to row[AdminTemplateCategoriesTable.id],
                                "name" to row[AdminTemplateCategoriesTable.name],
                                "icon" to row[AdminTemplateCategoriesTable.icon],
                                "sortOrder" to row[AdminTemplateCategoriesTable.sortOrder].toString(),
                                "createdAt" to row[AdminTemplateCategoriesTable.createdAt].toString()
                            )
                        }
                }
                call.respond(HttpStatusCode.OK, mapOf("data" to categories))
            } catch (e: Exception) {
                call.respond(HttpStatusCode.InternalServerError, mapOf("error" to "Failed to load categories"))
            }
        }

        post("/template-categories") {
            try {
                val body = call.receiveText()
                val json = kotlinx.serialization.json.Json.parseToJsonElement(body) as kotlinx.serialization.json.JsonObject
                val name = json["name"]?.let { (it as kotlinx.serialization.json.JsonPrimitive).content } ?: ""
                val icon = json["icon"]?.let { (it as kotlinx.serialization.json.JsonPrimitive).content } ?: "📋"
                val sortOrder = json["sortOrder"]?.let { (it as kotlinx.serialization.json.JsonPrimitive).content.toIntOrNull() } ?: 0

                if (name.isBlank()) {
                    call.respond(HttpStatusCode.BadRequest, mapOf("error" to "name required"))
                    return@post
                }

                val id = java.util.UUID.randomUUID().toString()
                transaction {
                    AdminTemplateCategoriesTable.insert {
                        it[AdminTemplateCategoriesTable.id] = id
                        it[AdminTemplateCategoriesTable.name] = name
                        it[AdminTemplateCategoriesTable.icon] = icon
                        it[AdminTemplateCategoriesTable.sortOrder] = sortOrder
                        it[createdAt] = System.currentTimeMillis()
                    }
                }
                call.respond(HttpStatusCode.Created, mapOf("id" to id, "message" to "카테고리가 생성되었습니다"))
            } catch (e: Exception) {
                call.respond(HttpStatusCode.BadRequest, mapOf("error" to (e.message ?: "Failed to create category")))
            }
        }

        delete("/template-categories/{id}") {
            try {
                val catId = call.parameters["id"]
                    ?: return@delete call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Missing id"))
                transaction {
                    // 해당 카테고리의 템플릿도 삭제
                    AdminTemplatesTable.deleteWhere { AdminTemplatesTable.categoryId eq catId }
                    AdminTemplateCategoriesTable.deleteWhere { AdminTemplateCategoriesTable.id eq catId }
                }
                call.respond(HttpStatusCode.OK, mapOf("message" to "카테고리가 삭제되었습니다"))
            } catch (e: Exception) {
                call.respond(HttpStatusCode.BadRequest, mapOf("error" to (e.message ?: "Failed to delete category")))
            }
        }

        // ===== Admin Templates =====
        get("/templates") {
            try {
                val categoryId = call.request.queryParameters["categoryId"]
                val templates = transaction {
                    val query = if (categoryId != null) {
                        AdminTemplatesTable.selectAll().where { AdminTemplatesTable.categoryId eq categoryId }
                    } else {
                        AdminTemplatesTable.selectAll()
                    }
                    query.orderBy(AdminTemplatesTable.sortOrder, SortOrder.ASC)
                        .map { row ->
                            mapOf(
                                "id" to row[AdminTemplatesTable.id],
                                "categoryId" to row[AdminTemplatesTable.categoryId],
                                "title" to row[AdminTemplatesTable.title],
                                "content" to row[AdminTemplatesTable.content],
                                "sortOrder" to row[AdminTemplatesTable.sortOrder].toString(),
                                "createdAt" to row[AdminTemplatesTable.createdAt].toString(),
                                "updatedAt" to row[AdminTemplatesTable.updatedAt].toString()
                            )
                        }
                }
                call.respond(HttpStatusCode.OK, mapOf("data" to templates))
            } catch (e: Exception) {
                call.respond(HttpStatusCode.InternalServerError, mapOf("error" to "Failed to load templates"))
            }
        }

        post("/templates") {
            try {
                val body = call.receiveText()
                val json = kotlinx.serialization.json.Json.parseToJsonElement(body) as kotlinx.serialization.json.JsonObject
                val categoryId = json["categoryId"]?.let { (it as kotlinx.serialization.json.JsonPrimitive).content } ?: ""
                val title = json["title"]?.let { (it as kotlinx.serialization.json.JsonPrimitive).content } ?: ""
                val content = json["content"]?.let { (it as kotlinx.serialization.json.JsonPrimitive).content } ?: ""
                val sortOrder = json["sortOrder"]?.let { (it as kotlinx.serialization.json.JsonPrimitive).content.toIntOrNull() } ?: 0

                if (categoryId.isBlank() || title.isBlank() || content.isBlank()) {
                    call.respond(HttpStatusCode.BadRequest, mapOf("error" to "categoryId, title, content required"))
                    return@post
                }

                val id = java.util.UUID.randomUUID().toString()
                transaction {
                    AdminTemplatesTable.insert {
                        it[AdminTemplatesTable.id] = id
                        it[AdminTemplatesTable.categoryId] = categoryId
                        it[AdminTemplatesTable.title] = title
                        it[AdminTemplatesTable.content] = content
                        it[AdminTemplatesTable.sortOrder] = sortOrder
                        it[createdAt] = System.currentTimeMillis()
                        it[updatedAt] = System.currentTimeMillis()
                    }
                }
                call.respond(HttpStatusCode.Created, mapOf("id" to id, "message" to "템플릿이 생성되었습니다"))
            } catch (e: Exception) {
                call.respond(HttpStatusCode.BadRequest, mapOf("error" to (e.message ?: "Failed to create template")))
            }
        }

        put("/templates/{id}") {
            try {
                val tplId = call.parameters["id"]
                    ?: return@put call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Missing id"))
                val body = call.receiveText()
                val json = kotlinx.serialization.json.Json.parseToJsonElement(body) as kotlinx.serialization.json.JsonObject

                transaction {
                    AdminTemplatesTable.update({ AdminTemplatesTable.id eq tplId }) {
                        json["title"]?.let { v -> it[title] = (v as kotlinx.serialization.json.JsonPrimitive).content }
                        json["content"]?.let { v -> it[content] = (v as kotlinx.serialization.json.JsonPrimitive).content }
                        json["categoryId"]?.let { v -> it[categoryId] = (v as kotlinx.serialization.json.JsonPrimitive).content }
                        json["sortOrder"]?.let { v -> it[sortOrder] = (v as kotlinx.serialization.json.JsonPrimitive).content.toInt() }
                        it[updatedAt] = System.currentTimeMillis()
                    }
                }
                call.respond(HttpStatusCode.OK, mapOf("message" to "템플릿이 수정되었습니다"))
            } catch (e: Exception) {
                call.respond(HttpStatusCode.BadRequest, mapOf("error" to (e.message ?: "Failed to update template")))
            }
        }

        delete("/templates/{id}") {
            try {
                val tplId = call.parameters["id"]
                    ?: return@delete call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Missing id"))
                transaction {
                    AdminTemplatesTable.deleteWhere { AdminTemplatesTable.id eq tplId }
                }
                call.respond(HttpStatusCode.OK, mapOf("message" to "템플릿이 삭제되었습니다"))
            } catch (e: Exception) {
                call.respond(HttpStatusCode.BadRequest, mapOf("error" to (e.message ?: "Failed to delete template")))
            }
        }

        // ===== SMS Monitoring =====

        // GET /sms-logs - paginated SMS logs with error details
        get("/sms-logs") {
            try {
                val page = call.request.queryParameters["page"]?.toIntOrNull() ?: 1
                val limit = call.request.queryParameters["limit"]?.toIntOrNull() ?: 20
                val statusFilter = call.request.queryParameters["status"] ?: "all"
                val dateFrom = call.request.queryParameters["dateFrom"]
                val dateTo = call.request.queryParameters["dateTo"]
                val offset = ((page - 1) * limit).toLong()

                val result = transaction {
                    // Build conditions list
                    val conditions = mutableListOf<Op<Boolean>>()

                    if (statusFilter != "all") {
                        conditions.add(Op.build { SmsLogsTable.status eq statusFilter })
                    }

                    if (dateFrom != null) {
                        val fromMillis = try {
                            LocalDate.parse(dateFrom).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
                        } catch (_: Exception) { null }
                        if (fromMillis != null) {
                            conditions.add(Op.build { SmsLogsTable.sentAt greaterEq fromMillis })
                        }
                    }

                    if (dateTo != null) {
                        val toMillis = try {
                            LocalDate.parse(dateTo).plusDays(1).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
                        } catch (_: Exception) { null }
                        if (toMillis != null) {
                            conditions.add(Op.build { SmsLogsTable.sentAt less toMillis })
                        }
                    }

                    val query = if (conditions.isEmpty()) {
                        SmsLogsTable.selectAll()
                    } else {
                        SmsLogsTable.selectAll().where {
                            conditions.reduce { acc, op -> acc and op }
                        }
                    }

                    val total = query.count().toInt()
                    val logs = query
                        .orderBy(SmsLogsTable.sentAt, SortOrder.DESC)
                        .limit(limit, offset)
                        .map { row ->
                            mapOf(
                                "id" to row[SmsLogsTable.id],
                                "userId" to row[SmsLogsTable.userId],
                                "recipientPhone" to row[SmsLogsTable.recipientPhone],
                                "messagePreview" to row[SmsLogsTable.messageContent].take(50),
                                "status" to row[SmsLogsTable.status],
                                "errorCode" to (row[SmsLogsTable.errorCode] ?: ""),
                                "errorMessage" to (row[SmsLogsTable.errorMessage] ?: ""),
                                "wideshotSendCode" to (row[SmsLogsTable.wideshotSendCode] ?: ""),
                                "sentAt" to row[SmsLogsTable.sentAt].toString()
                            )
                        }

                    mapOf(
                        "data" to logs,
                        "total" to total.toString(),
                        "page" to page.toString(),
                        "pageSize" to limit.toString()
                    )
                }

                call.respond(HttpStatusCode.OK, result)
            } catch (e: Exception) {
                call.respond(HttpStatusCode.InternalServerError, mapOf("error" to "Failed to load SMS logs"))
            }
        }

        // GET /sms-errors - recent failed SMS grouped by error code
        get("/sms-errors") {
            try {
                val result = transaction {
                    val failedLogs = SmsLogsTable.selectAll()
                        .where { SmsLogsTable.status eq "failed" }
                        .orderBy(SmsLogsTable.sentAt, SortOrder.DESC)
                        .toList()

                    // Group by error code
                    val grouped = failedLogs.groupBy { it[SmsLogsTable.errorCode] ?: "UNKNOWN" }
                    grouped.map { (code, rows) ->
                        mapOf(
                            "errorCode" to code,
                            "errorMessage" to (rows.firstOrNull()?.get(SmsLogsTable.errorMessage) ?: ""),
                            "count" to rows.size.toString(),
                            "lastOccurred" to (rows.maxByOrNull { it[SmsLogsTable.sentAt] }?.get(SmsLogsTable.sentAt)?.toString() ?: "")
                        )
                    }.sortedByDescending { it["count"]?.toIntOrNull() ?: 0 }
                }

                call.respond(HttpStatusCode.OK, mapOf("data" to result))
            } catch (e: Exception) {
                call.respond(HttpStatusCode.InternalServerError, mapOf("error" to "Failed to load SMS errors"))
            }
        }

        // GET /sms-stats - success/failure rates for last 7 days
        get("/sms-stats") {
            try {
                val result = transaction {
                    val days = (0..6).map { offset ->
                        LocalDate.now().minusDays(offset.toLong())
                    }.reversed()

                    days.map { date ->
                        val dayStart = date.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
                        val dayEnd = date.plusDays(1).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()

                        val sent = SmsLogsTable.selectAll()
                            .where {
                                (SmsLogsTable.sentAt greaterEq dayStart) and
                                (SmsLogsTable.sentAt less dayEnd) and
                                (SmsLogsTable.status eq "sent")
                            }.count().toInt()

                        val failed = SmsLogsTable.selectAll()
                            .where {
                                (SmsLogsTable.sentAt greaterEq dayStart) and
                                (SmsLogsTable.sentAt less dayEnd) and
                                (SmsLogsTable.status eq "failed")
                            }.count().toInt()

                        val total = sent + failed
                        val successRate = if (total > 0) ((sent.toDouble() / total) * 100).toInt().toString() else "0"

                        mapOf(
                            "date" to date.format(DateTimeFormatter.ISO_LOCAL_DATE),
                            "sent" to sent.toString(),
                            "failed" to failed.toString(),
                            "total" to total.toString(),
                            "successRate" to successRate
                        )
                    }
                }

                call.respond(HttpStatusCode.OK, mapOf("data" to result))
            } catch (e: Exception) {
                call.respond(HttpStatusCode.InternalServerError, mapOf("error" to "Failed to load SMS stats"))
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
