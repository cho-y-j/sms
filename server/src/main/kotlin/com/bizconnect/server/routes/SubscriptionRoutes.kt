package com.bizconnect.server.routes

import com.bizconnect.server.database.*
import com.bizconnect.server.models.*
import io.ktor.server.application.call
import io.ktor.server.routing.Route
import io.ktor.server.routing.route
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.request.receive
import io.ktor.http.HttpStatusCode
import io.ktor.server.response.respond
import io.ktor.server.auth.principal
import io.ktor.server.auth.jwt.JWTPrincipal
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.transactions.transaction
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.UUID

fun Route.subscriptionRoutes() {
    route("/api/subscription") {

        // GET / - get current subscription
        get {
            try {
                val principal = call.principal<JWTPrincipal>()
                val userId = principal?.payload?.getClaim("userId")?.asString()
                    ?: return@get call.respond(HttpStatusCode.Unauthorized, mapOf("error" to "Unauthorized"))

                val subscription = transaction {
                    SubscriptionsTable.selectAll()
                        .where { (SubscriptionsTable.userId eq userId) and (SubscriptionsTable.isActive eq true) }
                        .firstOrNull()
                }

                if (subscription == null) {
                    call.respond(HttpStatusCode.OK, SubscriptionInfo(
                        tier = "free",
                        isActive = true,
                        startDate = "",
                        endDate = null
                    ))
                } else {
                    call.respond(HttpStatusCode.OK, SubscriptionInfo(
                        tier = subscription[SubscriptionsTable.tier],
                        isActive = subscription[SubscriptionsTable.isActive],
                        startDate = Instant.ofEpochMilli(subscription[SubscriptionsTable.startDate])
                            .atZone(ZoneId.systemDefault()).toLocalDate().toString(),
                        endDate = subscription[SubscriptionsTable.endDate]?.let {
                            Instant.ofEpochMilli(it).atZone(ZoneId.systemDefault()).toLocalDate().toString()
                        }
                    ))
                }
            } catch (e: Exception) {
                call.respond(HttpStatusCode.InternalServerError, mapOf("error" to "Failed to load subscription"))
            }
        }

        // POST /change - change tier
        post("/change") {
            try {
                val principal = call.principal<JWTPrincipal>()
                val userId = principal?.payload?.getClaim("userId")?.asString()
                    ?: return@post call.respond(HttpStatusCode.Unauthorized, mapOf("error" to "Unauthorized"))

                val request = call.receive<SubscriptionChangeRequest>()

                val validTiers = listOf("free", "paid", "premium")
                if (request.tier !in validTiers) {
                    call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Invalid tier. Use: $validTiers"))
                    return@post
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
                        it[id] = UUID.randomUUID().toString()
                        it[SubscriptionsTable.userId] = userId
                        it[tier] = request.tier
                        it[startDate] = System.currentTimeMillis()
                        it[isActive] = true
                        it[monthlyPrice] = priceMap[request.tier] ?: 0
                        it[createdAt] = System.currentTimeMillis()
                    }

                    // Record payment if paid tier
                    if (request.tier != "free") {
                        PaymentsTable.insert {
                            it[id] = UUID.randomUUID().toString()
                            it[PaymentsTable.userId] = userId
                            it[amount] = priceMap[request.tier] ?: 0
                            it[type] = "subscription"
                            it[status] = "completed"
                            it[description] = "${request.tier} 구독 시작"
                            it[createdAt] = System.currentTimeMillis()
                        }
                    }
                }

                call.respond(HttpStatusCode.OK, mapOf("message" to "Subscription changed", "tier" to request.tier))
            } catch (e: Exception) {
                call.respond(HttpStatusCode.BadRequest, mapOf("error" to (e.message ?: "Failed to change subscription")))
            }
        }

        // GET /credit - get credit balance
        get("/credit") {
            try {
                val principal = call.principal<JWTPrincipal>()
                val userId = principal?.payload?.getClaim("userId")?.asString()
                    ?: return@get call.respond(HttpStatusCode.Unauthorized, mapOf("error" to "Unauthorized"))

                val balance = transaction {
                    CreditBalancesTable.selectAll()
                        .where { CreditBalancesTable.userId eq userId }
                        .firstOrNull()?.get(CreditBalancesTable.balance) ?: 0.0
                }

                call.respond(HttpStatusCode.OK, mapOf("balance" to balance))
            } catch (e: Exception) {
                call.respond(HttpStatusCode.InternalServerError, mapOf("error" to "Failed to load balance"))
            }
        }

        // POST /charge - charge credits
        post("/charge") {
            try {
                val principal = call.principal<JWTPrincipal>()
                val userId = principal?.payload?.getClaim("userId")?.asString()
                    ?: return@post call.respond(HttpStatusCode.Unauthorized, mapOf("error" to "Unauthorized"))

                val request = call.receive<PaymentRequest>()

                if (request.amount <= 0) {
                    call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Amount must be positive"))
                    return@post
                }

                val paymentId = UUID.randomUUID().toString()

                transaction {
                    // Record payment
                    PaymentsTable.insert {
                        it[id] = paymentId
                        it[PaymentsTable.userId] = userId
                        it[amount] = request.amount
                        it[type] = "credit_charge"
                        it[status] = "completed"
                        it[description] = request.description
                        it[createdAt] = System.currentTimeMillis()
                    }

                    // Update or create credit balance
                    val existing = CreditBalancesTable.selectAll()
                        .where { CreditBalancesTable.userId eq userId }
                        .firstOrNull()

                    if (existing != null) {
                        val currentBalance = existing[CreditBalancesTable.balance]
                        CreditBalancesTable.update({ CreditBalancesTable.userId eq userId }) {
                            it[balance] = currentBalance + request.amount
                            it[updatedAt] = System.currentTimeMillis()
                        }
                    } else {
                        CreditBalancesTable.insert {
                            it[id] = UUID.randomUUID().toString()
                            it[CreditBalancesTable.userId] = userId
                            it[balance] = request.amount.toDouble()
                            it[updatedAt] = System.currentTimeMillis()
                        }
                    }
                }

                call.respond(HttpStatusCode.OK, mapOf(
                    "message" to "Credits charged",
                    "paymentId" to paymentId,
                    "amount" to request.amount
                ))
            } catch (e: Exception) {
                call.respond(HttpStatusCode.BadRequest, mapOf("error" to (e.message ?: "Failed to charge credits")))
            }
        }

        // GET /usage - get usage stats
        get("/usage") {
            try {
                val principal = call.principal<JWTPrincipal>()
                val userId = principal?.payload?.getClaim("userId")?.asString()
                    ?: return@get call.respond(HttpStatusCode.Unauthorized, mapOf("error" to "Unauthorized"))

                val today = LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE)
                val monthStart = LocalDate.now().withDayOfMonth(1)

                val result = transaction {
                    // Today's usage
                    val todayUsage = DailyUsageTable.selectAll()
                        .where { (DailyUsageTable.userId eq userId) and (DailyUsageTable.date eq today) }
                        .firstOrNull()?.let { row ->
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

                    // Monthly total
                    val monthlyRows = DailyUsageTable.selectAll()
                        .where {
                            (DailyUsageTable.userId eq userId) and
                            (DailyUsageTable.date greaterEq monthStart.format(DateTimeFormatter.ISO_LOCAL_DATE))
                        }
                        .toList()

                    val monthlyTotal = UsageStats(
                        date = monthStart.format(DateTimeFormatter.ISO_LOCAL_DATE),
                        smsCount = monthlyRows.sumOf { it[DailyUsageTable.smsCount] },
                        lmsCount = monthlyRows.sumOf { it[DailyUsageTable.lmsCount] },
                        mmsCount = monthlyRows.sumOf { it[DailyUsageTable.mmsCount] },
                        callbackCount = monthlyRows.sumOf { it[DailyUsageTable.callbackCount] },
                        aiTokens = monthlyRows.sumOf { it[DailyUsageTable.aiTokens] },
                        totalCost = monthlyRows.sumOf { it[DailyUsageTable.totalCost] }
                    )

                    // Get daily limit based on subscription
                    val subscription = SubscriptionsTable.selectAll()
                        .where { (SubscriptionsTable.userId eq userId) and (SubscriptionsTable.isActive eq true) }
                        .firstOrNull()

                    val tier = subscription?.get(SubscriptionsTable.tier) ?: "free"

                    // Get limit from config
                    val limitKey = if (tier == "free") "free_daily_limit" else "paid_daily_limit"
                    val dailyLimit = AppConfigTable.selectAll()
                        .where { AppConfigTable.key eq limitKey }
                        .firstOrNull()?.get(AppConfigTable.value)?.toIntOrNull() ?: 50

                    val todayTotal = todayUsage?.let {
                        it.smsCount + it.lmsCount + it.mmsCount
                    } ?: 0

                    UsageSummary(
                        todayUsage = todayUsage,
                        monthlyTotal = monthlyTotal,
                        dailyLimit = dailyLimit,
                        remaining = (dailyLimit - todayTotal).coerceAtLeast(0)
                    )
                }

                call.respond(HttpStatusCode.OK, result)
            } catch (e: Exception) {
                call.respond(HttpStatusCode.InternalServerError, mapOf("error" to "Failed to load usage"))
            }
        }

        // POST /usage/record - record usage (called by app)
        post("/usage/record") {
            try {
                val principal = call.principal<JWTPrincipal>()
                val userId = principal?.payload?.getClaim("userId")?.asString()
                    ?: return@post call.respond(HttpStatusCode.Unauthorized, mapOf("error" to "Unauthorized"))

                val request = call.receive<UsageRecordRequest>()
                val today = LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE)

                transaction {
                    val existing = DailyUsageTable.selectAll()
                        .where { (DailyUsageTable.userId eq userId) and (DailyUsageTable.date eq today) }
                        .firstOrNull()

                    if (existing != null) {
                        DailyUsageTable.update({
                            (DailyUsageTable.userId eq userId) and (DailyUsageTable.date eq today)
                        }) {
                            it[smsCount] = existing[smsCount] + request.smsCount
                            it[lmsCount] = existing[lmsCount] + request.lmsCount
                            it[mmsCount] = existing[mmsCount] + request.mmsCount
                            it[callbackCount] = existing[callbackCount] + request.callbackCount
                            it[aiTokens] = existing[aiTokens] + request.aiTokens
                            it[totalCost] = existing[totalCost] + request.totalCost
                        }
                    } else {
                        DailyUsageTable.insert {
                            it[id] = UUID.randomUUID().toString()
                            it[DailyUsageTable.userId] = userId
                            it[date] = today
                            it[smsCount] = request.smsCount
                            it[lmsCount] = request.lmsCount
                            it[mmsCount] = request.mmsCount
                            it[callbackCount] = request.callbackCount
                            it[aiTokens] = request.aiTokens
                            it[totalCost] = request.totalCost
                        }
                    }

                    // Deduct from credit balance
                    if (request.totalCost > 0) {
                        val balance = CreditBalancesTable.selectAll()
                            .where { CreditBalancesTable.userId eq userId }
                            .firstOrNull()

                        if (balance != null) {
                            val currentBalance = balance[CreditBalancesTable.balance]
                            CreditBalancesTable.update({ CreditBalancesTable.userId eq userId }) {
                                it[CreditBalancesTable.balance] = (currentBalance - request.totalCost).coerceAtLeast(0.0)
                                it[updatedAt] = System.currentTimeMillis()
                            }
                        }
                    }
                }

                call.respond(HttpStatusCode.OK, mapOf("message" to "Usage recorded"))
            } catch (e: Exception) {
                call.respond(HttpStatusCode.BadRequest, mapOf("error" to (e.message ?: "Failed to record usage")))
            }
        }
    }
}
