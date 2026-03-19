package com.bizconnect.server.routes

import com.bizconnect.server.database.DatabaseFactory
import com.bizconnect.server.database.TasksTable
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
 * Task management routes with full security validation
 * - Input validation and SQL injection protection
 * - User ownership verification
 * - Pagination support
 * - Status validation
 */
fun Route.taskRoutes() {
    route("/api/tasks") {
        /**
         * GET /api/tasks
         * List tasks for authenticated user with pagination
         */
        get("/") {
            try {
                val principal = call.principal<JWTPrincipal>()
                val userId = principal?.payload?.getClaim("userId")?.asString()
                    ?: throw Exception("Unauthorized")

                val page = call.request.queryParameters["page"]?.toIntOrNull() ?: 1
                val limit = call.request.queryParameters["limit"]?.toIntOrNull() ?: 20

                val (validPage, validLimit) = InputValidator.validatePagination(page, limit)
                val offset = (validPage - 1) * validLimit

                val tasks = transaction {
                    TasksTable.selectAll().where { TasksTable.userId eq userId }
                        .limit(validLimit, offset.toLong())
                        .map { row ->
                            TaskDTO(
                                id = row[TasksTable.id],
                                title = row[TasksTable.title],
                                description = row[TasksTable.description],
                                status = row[TasksTable.status],
                                scheduledTime = row[TasksTable.scheduledTime],
                                completedTime = row[TasksTable.completedTime],
                                createdAt = row[TasksTable.createdAt]
                            )
                        }
                }

                call.respond(
                    HttpStatusCode.OK,
                    mapOf(
                        "tasks" to tasks,
                        "page" to validPage,
                        "limit" to validLimit,
                        "total" to tasks.size
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
         * GET /api/tasks/{id}
         * Get task by ID (only owner can view)
         */
        get("/{id}") {
            try {
                val principal = call.principal<JWTPrincipal>()
                val userId = principal?.payload?.getClaim("userId")?.asString()
                    ?: throw Exception("Unauthorized")

                val taskId = InputValidator.validateUUID(call.parameters["id"])

                val task = transaction {
                    TasksTable.selectAll().where {
                        (TasksTable.id eq taskId) and (TasksTable.userId eq userId)
                    }.firstOrNull()?.let { row ->
                        TaskDTO(
                            id = row[TasksTable.id],
                            title = row[TasksTable.title],
                            description = row[TasksTable.description],
                            status = row[TasksTable.status],
                            scheduledTime = row[TasksTable.scheduledTime],
                            completedTime = row[TasksTable.completedTime],
                            createdAt = row[TasksTable.createdAt]
                        )
                    }
                }

                if (task == null) {
                    call.respond(
                        HttpStatusCode.NotFound,
                        mapOf("error" to "Task not found")
                    )
                } else {
                    call.respond(HttpStatusCode.OK, task)
                }
            } catch (e: Exception) {
                call.respond(
                    HttpStatusCode.BadRequest,
                    mapOf("error" to (e.message ?: "Bad request"))
                )
            }
        }

        /**
         * POST /api/tasks
         * Create a new task
         */
        post("/") {
            try {
                val principal = call.principal<JWTPrincipal>()
                val userId = principal?.payload?.getClaim("userId")?.asString()
                    ?: throw Exception("Unauthorized")

                val request = call.receive<CreateTaskRequest>()

                // Input validation
                val title = InputValidator.validateName(request.title)
                val description = request.description?.let {
                    InputValidator.validateMessageContent(it, maxLength = 500)
                }

                // Check for SQL injection
                if (InputValidator.checkSqlInjection(title) ||
                    (description != null && InputValidator.checkSqlInjection(description))) {
                    throw IllegalArgumentException("Invalid input detected")
                }

                val taskId = UUID.randomUUID().toString()
                val now = System.currentTimeMillis()

                transaction {
                    TasksTable.insert {
                        it[id] = taskId
                        it[TasksTable.userId] = userId
                        it[TasksTable.title] = title
                        it[TasksTable.description] = description
                        it[status] = "pending"
                        it[createdAt] = now
                        it[updatedAt] = now
                    }
                }

                call.respond(
                    HttpStatusCode.Created,
                    mapOf(
                        "id" to taskId,
                        "title" to title,
                        "status" to "pending",
                        "createdAt" to now
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
                    mapOf("error" to "Failed to create task")
                )
            }
        }

        /**
         * PUT /api/tasks/{id}/status
         * Update task status
         */
        put("/{id}/status") {
            try {
                val principal = call.principal<JWTPrincipal>()
                val userId = principal?.payload?.getClaim("userId")?.asString()
                    ?: throw Exception("Unauthorized")

                val taskId = InputValidator.validateUUID(call.parameters["id"])
                val request = call.receive<UpdateTaskStatusRequest>()

                // Validate status
                val validStatuses = listOf("pending", "scheduled", "sent", "failed", "cancelled")
                if (!validStatuses.contains(request.status)) {
                    throw IllegalArgumentException("Invalid status")
                }

                transaction {
                    // Verify ownership
                    val task = TasksTable.selectAll().where {
                        (TasksTable.id eq taskId) and (TasksTable.userId eq userId)
                    }.firstOrNull()

                    if (task == null) {
                        throw Exception("Task not found or unauthorized")
                    }

                    TasksTable.update({ (TasksTable.id eq taskId) and (TasksTable.userId eq userId) }) {
                        it[status] = request.status
                        it[updatedAt] = System.currentTimeMillis()
                        if (request.status == "sent") {
                            it[completedTime] = System.currentTimeMillis()
                        }
                    }
                }

                call.respond(
                    HttpStatusCode.OK,
                    mapOf(
                        "message" to "Task status updated",
                        "status" to request.status
                    )
                )
            } catch (e: Exception) {
                call.respond(
                    HttpStatusCode.BadRequest,
                    mapOf("error" to (e.message ?: "Bad request"))
                )
            }
        }

        /**
         * DELETE /api/tasks/{id}
         * Cancel/delete task
         */
        delete("/{id}") {
            try {
                val principal = call.principal<JWTPrincipal>()
                val userId = principal?.payload?.getClaim("userId")?.asString()
                    ?: throw Exception("Unauthorized")

                val taskId = InputValidator.validateUUID(call.parameters["id"])

                transaction {
                    // Verify ownership
                    val task = TasksTable.selectAll().where {
                        (TasksTable.id eq taskId) and (TasksTable.userId eq userId)
                    }.firstOrNull()

                    if (task == null) {
                        throw Exception("Task not found or unauthorized")
                    }

                    TasksTable.update({ (TasksTable.id eq taskId) and (TasksTable.userId eq userId) }) {
                        it[status] = "cancelled"
                        it[updatedAt] = System.currentTimeMillis()
                    }
                }

                call.respond(
                    HttpStatusCode.OK,
                    mapOf("message" to "Task cancelled")
                )
            } catch (e: Exception) {
                call.respond(
                    HttpStatusCode.BadRequest,
                    mapOf("error" to (e.message ?: "Bad request"))
                )
            }
        }

        /**
         * POST /api/tasks/batch
         * Create multiple tasks at once
         */
        post("/batch") {
            try {
                val principal = call.principal<JWTPrincipal>()
                val userId = principal?.payload?.getClaim("userId")?.asString()
                    ?: throw Exception("Unauthorized")

                val request = call.receive<BatchCreateTaskRequest>()

                if (request.tasks.isEmpty() || request.tasks.size > 100) {
                    throw IllegalArgumentException("Must provide 1-100 tasks")
                }

                val createdTasks = mutableListOf<String>()
                val now = System.currentTimeMillis()

                transaction {
                    for (taskRequest in request.tasks) {
                        val title = InputValidator.validateName(taskRequest.title)
                        val taskId = UUID.randomUUID().toString()

                        TasksTable.insert {
                            it[id] = taskId
                            it[TasksTable.userId] = userId
                            it[TasksTable.title] = title
                            it[status] = "pending"
                            it[createdAt] = now
                            it[updatedAt] = now
                        }

                        createdTasks.add(taskId)
                    }
                }

                call.respond(
                    HttpStatusCode.Created,
                    mapOf(
                        "message" to "Tasks created",
                        "count" to createdTasks.size,
                        "taskIds" to createdTasks
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
                    mapOf("error" to "Failed to create tasks")
                )
            }
        }
    }
}

// DTOs
data class TaskDTO(
    val id: String,
    val title: String,
    val description: String?,
    val status: String,
    val scheduledTime: Long?,
    val completedTime: Long?,
    val createdAt: Long
)

data class CreateTaskRequest(
    val title: String,
    val description: String? = null
)

data class UpdateTaskStatusRequest(
    val status: String
)

data class BatchCreateTaskRequest(
    val tasks: List<BatchTaskItem>
)

data class BatchTaskItem(
    val title: String,
    val description: String? = null
)
