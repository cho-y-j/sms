package com.bizconnect.server.routes

import com.bizconnect.server.database.CustomersTable
import com.bizconnect.server.database.CustomerGroupsTable
import com.bizconnect.server.security.InputValidator
import io.ktor.server.application.call
import io.ktor.server.routing.Route
import io.ktor.server.routing.route
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.put
import io.ktor.server.routing.delete
import io.ktor.server.request.receive
import io.ktor.http.HttpStatusCode
import io.ktor.server.response.respond
import io.ktor.server.auth.principal
import io.ktor.server.auth.jwt.JWTPrincipal
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.update
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.transactions.transaction
import java.util.UUID

/**
 * Customer management routes with full security
 */
fun Route.customerRoutes() {
    route("/api/customers") {
        /**
         * GET /api/customers
         * List customers with pagination
         */
        get("/") {
            try {
                val principal = call.principal<JWTPrincipal>()
                val userId = principal?.payload?.getClaim("userId")?.asString()
                    ?: throw Exception("Unauthorized")

                val page = call.request.queryParameters["page"]?.toIntOrNull() ?: 1
                val limit = call.request.queryParameters["limit"]?.toIntOrNull() ?: 20

                val (validPage, validLimit) = InputValidator.validatePagination(page, limit, maxLimit = 100)
                val offset = (validPage - 1) * validLimit

                val customers = transaction {
                    CustomersTable.selectAll().where { CustomersTable.userId eq userId }
                        .limit(validLimit, offset.toLong())
                        .map { row ->
                            CustomerDTO(
                                id = row[CustomersTable.id],
                                name = row[CustomersTable.name],
                                phone = row[CustomersTable.phone],
                                email = row[CustomersTable.email],
                                memo = row[CustomersTable.memo],
                                birthDate = row[CustomersTable.birthDate],
                                createdAt = row[CustomersTable.createdAt]
                            )
                        }
                }

                call.respond(
                    HttpStatusCode.OK,
                    mapOf(
                        "customers" to customers,
                        "page" to validPage,
                        "limit" to validLimit
                    )
                )
            } catch (e: Exception) {
                call.respond(
                    HttpStatusCode.Unauthorized,
                    mapOf("error" to "Unauthorized")
                )
            }
        }

        /**
         * GET /api/customers/{id}
         * Get customer by ID
         */
        get("/{id}") {
            try {
                val principal = call.principal<JWTPrincipal>()
                val userId = principal?.payload?.getClaim("userId")?.asString()
                    ?: throw Exception("Unauthorized")

                val customerId = InputValidator.validateUUID(call.parameters["id"])

                val customer = transaction {
                    CustomersTable.selectAll().where {
                        (CustomersTable.id eq customerId) and (CustomersTable.userId eq userId)
                    }.firstOrNull()?.let { row ->
                        CustomerDTO(
                            id = row[CustomersTable.id],
                            name = row[CustomersTable.name],
                            phone = row[CustomersTable.phone],
                            email = row[CustomersTable.email],
                            memo = row[CustomersTable.memo],
                            birthDate = row[CustomersTable.birthDate],
                            createdAt = row[CustomersTable.createdAt]
                        )
                    }
                }

                if (customer == null) {
                    call.respond(
                        HttpStatusCode.NotFound,
                        mapOf("error" to "Customer not found")
                    )
                } else {
                    call.respond(HttpStatusCode.OK, customer)
                }
            } catch (e: Exception) {
                call.respond(
                    HttpStatusCode.BadRequest,
                    mapOf("error" to (e.message ?: "Bad request"))
                )
            }
        }

        /**
         * POST /api/customers
         * Create new customer
         */
        post("/") {
            try {
                val principal = call.principal<JWTPrincipal>()
                val userId = principal?.payload?.getClaim("userId")?.asString()
                    ?: throw Exception("Unauthorized")

                val request = call.receive<CreateCustomerRequest>()

                // Input validation
                val name = InputValidator.validateName(request.name)
                val phone = InputValidator.validatePhoneNumber(request.phone)
                val email = request.email?.let { InputValidator.validateEmail(it) }

                // SQL injection checks
                if (InputValidator.checkSqlInjection(name) ||
                    (email != null && InputValidator.checkSqlInjection(email))) {
                    throw IllegalArgumentException("Invalid input detected")
                }

                val customerId = UUID.randomUUID().toString()
                val now = System.currentTimeMillis()

                transaction {
                    CustomersTable.insert {
                        it[id] = customerId
                        it[CustomersTable.userId] = userId
                        it[CustomersTable.name] = name
                        it[CustomersTable.phone] = phone
                        if (email != null) it[CustomersTable.email] = email
                        it[createdAt] = now
                        it[updatedAt] = now
                    }
                }

                call.respond(
                    HttpStatusCode.Created,
                    mapOf(
                        "id" to customerId,
                        "name" to name,
                        "phone" to phone,
                        "email" to (email ?: "")
                    )
                )
            } catch (e: IllegalArgumentException) {
                call.respond(
                    HttpStatusCode.BadRequest,
                    mapOf("error" to (e.message ?: "Invalid input"))
                )
            } catch (e: Exception) {
                call.respond(
                    HttpStatusCode.InternalServerError,
                    mapOf("error" to "Failed to create customer")
                )
            }
        }

        /**
         * PUT /api/customers/{id}
         * Update customer
         */
        put("/{id}") {
            try {
                val principal = call.principal<JWTPrincipal>()
                val userId = principal?.payload?.getClaim("userId")?.asString()
                    ?: throw Exception("Unauthorized")

                val customerId = InputValidator.validateUUID(call.parameters["id"])
                val request = call.receive<UpdateCustomerRequest>()

                transaction {
                    // Verify ownership
                    val customer = CustomersTable.selectAll().where {
                        (CustomersTable.id eq customerId) and (CustomersTable.userId eq userId)
                    }.firstOrNull() ?: throw Exception("Customer not found or unauthorized")

                    CustomersTable.update({ (CustomersTable.id eq customerId) and (CustomersTable.userId eq userId) }) {
                        if (request.name != null) it[CustomersTable.name] = InputValidator.validateName(request.name)
                        if (request.memo != null) it[CustomersTable.memo] = InputValidator.validateMessageContent(request.memo, 500)
                        if (request.birthDate != null) it[CustomersTable.birthDate] = request.birthDate
                        it[updatedAt] = System.currentTimeMillis()
                    }
                }

                call.respond(
                    HttpStatusCode.OK,
                    mapOf("message" to "Customer updated")
                )
            } catch (e: Exception) {
                call.respond(
                    HttpStatusCode.BadRequest,
                    mapOf("error" to (e.message ?: "Bad request"))
                )
            }
        }

        /**
         * DELETE /api/customers/{id}
         * Delete customer
         */
        delete("/{id}") {
            try {
                val principal = call.principal<JWTPrincipal>()
                val userId = principal?.payload?.getClaim("userId")?.asString()
                    ?: throw Exception("Unauthorized")

                val customerId = InputValidator.validateUUID(call.parameters["id"])

                transaction {
                    val customer = CustomersTable.selectAll().where {
                        (CustomersTable.id eq customerId) and (CustomersTable.userId eq userId)
                    }.firstOrNull() ?: throw Exception("Customer not found")

                    CustomersTable.deleteWhere {
                        (id eq customerId) and (CustomersTable.userId eq userId)
                    }
                }

                call.respond(
                    HttpStatusCode.OK,
                    mapOf("message" to "Customer deleted")
                )
            } catch (e: Exception) {
                call.respond(
                    HttpStatusCode.BadRequest,
                    mapOf("error" to (e.message ?: "Bad request"))
                )
            }
        }

        /**
         * GET /api/customers/birthdays
         * Get upcoming birthdays
         */
        get("/birthdays") {
            try {
                val principal = call.principal<JWTPrincipal>()
                val userId = principal?.payload?.getClaim("userId")?.asString()
                    ?: throw Exception("Unauthorized")

                val customers = transaction {
                    CustomersTable.selectAll().where {
                        (CustomersTable.userId eq userId) and (CustomersTable.birthDate.isNotNull())
                    }.map { row ->
                        BirthdayDTO(
                            id = row[CustomersTable.id],
                            name = row[CustomersTable.name],
                            birthDate = row[CustomersTable.birthDate]!!
                        )
                    }
                }

                call.respond(HttpStatusCode.OK, mapOf("birthdays" to customers))
            } catch (e: Exception) {
                call.respond(
                    HttpStatusCode.Unauthorized,
                    mapOf("error" to "Unauthorized")
                )
            }
        }

        /**
         * GET /api/customers/groups
         * List customer groups
         */
        get("/groups") {
            try {
                val principal = call.principal<JWTPrincipal>()
                val userId = principal?.payload?.getClaim("userId")?.asString()
                    ?: throw Exception("Unauthorized")

                val groups = transaction {
                    CustomerGroupsTable.selectAll().where { CustomerGroupsTable.userId eq userId }
                        .map { row ->
                            GroupDTO(
                                id = row[CustomerGroupsTable.id],
                                name = row[CustomerGroupsTable.name],
                                callbackEnabled = row[CustomerGroupsTable.callbackEnabled],
                                useAi = row[CustomerGroupsTable.useAi]
                            )
                        }
                }

                call.respond(HttpStatusCode.OK, mapOf("groups" to groups))
            } catch (e: Exception) {
                call.respond(
                    HttpStatusCode.Unauthorized,
                    mapOf("error" to "Unauthorized")
                )
            }
        }
    }
}

// DTOs
data class CustomerDTO(
    val id: String,
    val name: String,
    val phone: String,
    val email: String?,
    val memo: String?,
    val birthDate: String?,
    val createdAt: Long
)

data class CreateCustomerRequest(
    val name: String,
    val phone: String,
    val email: String? = null,
    val memo: String? = null,
    val birthDate: String? = null
)

data class UpdateCustomerRequest(
    val name: String? = null,
    val memo: String? = null,
    val birthDate: String? = null
)

data class BirthdayDTO(
    val id: String,
    val name: String,
    val birthDate: String
)

data class GroupDTO(
    val id: String,
    val name: String,
    val callbackEnabled: Boolean,
    val useAi: Boolean
)
