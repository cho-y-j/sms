package com.bizconnect.server.database

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.transactions.transaction
import org.slf4j.LoggerFactory
import com.bizconnect.server.database.schema.UsersTable
import com.bizconnect.server.database.schema.SmsMessagesTable
import com.bizconnect.server.database.schema.SubscriptionsTable
import com.bizconnect.server.database.schema.PaymentsTable
import com.bizconnect.server.database.schema.CreditBalancesTable
import com.bizconnect.server.database.schema.DailyUsageTable
import com.bizconnect.server.database.schema.AdminUsersTable
import com.bizconnect.server.database.schema.AppConfigTable

private val logger = LoggerFactory.getLogger("Database")

fun initializeDatabase() {
    val dbUrl = System.getenv("DB_URL") ?: "jdbc:postgresql://localhost:5432/bizconnect"
    val dbUser = System.getenv("DB_USER") ?: "postgres"
    val dbPassword = System.getenv("DB_PASSWORD") ?: "postgres"

    logger.info("Initializing database: $dbUrl")

    val config = HikariConfig().apply {
        jdbcUrl = dbUrl
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

    Database.connect(dataSource)

    transaction {
        SchemaUtils.create(
            UsersTable, SmsMessagesTable,
            SubscriptionsTable, PaymentsTable, CreditBalancesTable,
            DailyUsageTable, AdminUsersTable, AppConfigTable
        )
        logger.info("Database tables created")
    }
}
