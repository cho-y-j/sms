package com.bizconnect.server.database

import com.bizconnect.server.security.PasswordManager
import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import java.util.UUID

/**
 * Database initialization with HikariCP connection pooling
 * Runs migrations and creates tables if they don't exist
 */
object DatabaseFactory {
    private var database: Database? = null

    fun init() {
        val dbHost = System.getenv("DB_HOST") ?: "localhost"
        val dbPort = System.getenv("DB_PORT") ?: "5432"
        val dbName = System.getenv("DB_NAME") ?: "bizconnect"
        val dbUser = System.getenv("DB_USER") ?: "bizconnect_user"
        val dbPassword = System.getenv("DB_PASSWORD") ?: "password"

        val jdbcUrl = "jdbc:postgresql://$dbHost:$dbPort/$dbName"

        val config = HikariConfig().apply {
            this.jdbcUrl = jdbcUrl
            username = dbUser
            password = dbPassword
            maximumPoolSize = 10
            minimumIdle = 2
            connectionTimeout = 30000
            idleTimeout = 600000
            maxLifetime = 1800000
            isAutoCommit = true
            leakDetectionThreshold = 15000
        }

        val dataSource = HikariDataSource(config)
        database = Database.connect(dataSource)

        // Create tables
        createTables()

        println("Database initialized successfully")
    }

    private fun createTables() {
        transaction {
            SchemaUtils.createMissingTablesAndColumns(
                UsersTable,
                ApiKeysTable,
                TokenBlacklistTable,
                IpBlacklistTable,
                TasksTable,
                CustomersTable,
                CustomerGroupsTable,
                SmsLogsTable,
                ScheduledMessagesTable,
                CallbackSettingsTable,
                DailyLimitsTable,
                AuditLogsTable,
                RefreshTokensTable,
                SubscriptionsTable,
                PaymentsTable,
                CreditBalancesTable,
                DailyUsageTable,
                AdminUsersTable,
                AppConfigTable
            )
        }

        // Create default admin if not exists
        transaction {
            val adminExists = AdminUsersTable.selectAll()
                .where { AdminUsersTable.username eq "admin" }
                .count() > 0
            if (!adminExists) {
                val passwordManager = PasswordManager()
                AdminUsersTable.insert {
                    it[id] = UUID.randomUUID().toString()
                    it[username] = "admin"
                    it[passwordHash] = passwordManager.hashPassword("Admin1234!")
                    it[role] = "superadmin"
                    it[createdAt] = System.currentTimeMillis()
                }
            }
        }

        // Create default app config
        transaction {
            val configs = listOf(
                Triple("sms_cost", "9.8", "단문 단가(원)"),
                Triple("lms_cost", "29.0", "장문 단가(원)"),
                Triple("mms_cost", "63.0", "MMS 단가(원)"),
                Triple("free_daily_limit", "50", "무료 일일 한도"),
                Triple("paid_daily_limit", "150", "유료 일일 한도"),
                Triple("monthly_subscription_price", "4900", "월 구독료(원)")
            )
            for ((key, value, desc) in configs) {
                val exists = AppConfigTable.selectAll()
                    .where { AppConfigTable.key eq key }
                    .count() > 0
                if (!exists) {
                    AppConfigTable.insert {
                        it[id] = UUID.randomUUID().toString()
                        it[AppConfigTable.key] = key
                        it[AppConfigTable.value] = value
                        it[description] = desc
                        it[updatedAt] = System.currentTimeMillis()
                    }
                }
            }
        }
    }

    fun getDatabase(): Database {
        return database ?: throw IllegalStateException("Database not initialized. Call init() first.")
    }
}
