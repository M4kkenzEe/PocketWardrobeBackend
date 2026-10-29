package com.example.routes

import com.example.auth.EnvironmentConfig
import com.example.database.data.model.ClotheTable
import com.example.database.data.model.DailyQuotaTable
import com.example.database.data.model.EmailVerificationCodeTable
import com.example.database.data.model.LookDraftTable
import com.example.database.data.model.LookItemTable
import com.example.database.data.model.LookTable
import com.example.database.data.model.PasswordResetCodeTable
import com.example.database.data.model.PendingEmailVerificationTable
import com.example.database.data.model.RevokedTokenTable
import com.example.database.data.model.SharedLookTable
import com.example.database.data.model.UserClotheTable
import com.example.database.data.model.UserLookTable
import com.example.database.data.model.UserTable
import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import io.ktor.server.application.*
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.SchemaUtils
import org.jetbrains.exposed.v1.jdbc.transactions.transaction

fun Application.configureDatabase() {
    log.info("Configuring database with HikariCP connection pooling...")
    log.info("Database URL: ${EnvironmentConfig.getDatabaseUrl()}")

    // Configure HikariCP connection pool
    val config = HikariConfig().apply {
        jdbcUrl = EnvironmentConfig.getDatabaseUrl()
        driverClassName = EnvironmentConfig.dbDriver
        username = EnvironmentConfig.dbUser
        password = EnvironmentConfig.dbPassword

        // Connection pool settings
        maximumPoolSize = EnvironmentConfig.dbPoolSize
        connectionTimeout = EnvironmentConfig.dbConnectionTimeout
        idleTimeout = EnvironmentConfig.dbIdleTimeout
        maxLifetime = EnvironmentConfig.dbMaxLifetime

        // Pool name for logging
        poolName = "PocketWardrobePool"

        // Validation and health check
        connectionTestQuery = "SELECT 1"
        isAutoCommit = true

        // Performance optimizations
        leakDetectionThreshold = 60000 // 60 seconds
    }

    val dataSource = HikariDataSource(config)

    // Connect Exposed to HikariCP data source
    Database.connect(dataSource)

    log.info("✓ HikariCP connection pool configured:")
    log.info("  - Max pool size: ${EnvironmentConfig.dbPoolSize}")
    log.info("  - Connection timeout: ${EnvironmentConfig.dbConnectionTimeout}ms")
    log.info("  - Idle timeout: ${EnvironmentConfig.dbIdleTimeout}ms")
    log.info("  - Max lifetime: ${EnvironmentConfig.dbMaxLifetime}ms")

    // Create database schema
    transaction {
        SchemaUtils.create(
            UserTable,
            ClotheTable,
            UserClotheTable,
            LookTable,
            UserLookTable,
            LookItemTable,
            SharedLookTable,
            RevokedTokenTable,
            DailyQuotaTable,
            PasswordResetCodeTable,
            EmailVerificationCodeTable,
            PendingEmailVerificationTable,
            LookDraftTable
        )
        // Add new columns to existing tables (safe, idempotent)
        exec("ALTER TABLE looks ADD COLUMN IF NOT EXISTS generated_by_ai BOOLEAN DEFAULT FALSE NOT NULL")
        exec("ALTER TABLE clothes ADD COLUMN IF NOT EXISTS brand VARCHAR(100)")
        exec("ALTER TABLE clothes ADD COLUMN IF NOT EXISTS colors TEXT")
        exec("ALTER TABLE clothes ADD COLUMN IF NOT EXISTS occasion VARCHAR(100)")
        exec("ALTER TABLE clothes ADD COLUMN IF NOT EXISTS root_clothe_id INT NULL")
        exec("CREATE INDEX IF NOT EXISTS idx_clothes_root_clothe_id ON clothes(root_clothe_id)")
        exec("ALTER TABLE users ADD COLUMN IF NOT EXISTS is_pro BOOLEAN NOT NULL DEFAULT FALSE")
        exec("ALTER TABLE users ADD COLUMN IF NOT EXISTS pro_until TIMESTAMP NULL")
        exec("ALTER TABLE users ADD COLUMN IF NOT EXISTS token_version BIGINT NOT NULL DEFAULT 0")
        // email_verified: existing users default TRUE (they already registered and are active).
        // New users are created with FALSE (set by UserRepositoryImpl) — restore that default after migration.
        exec("ALTER TABLE users ADD COLUMN IF NOT EXISTS email_verified BOOLEAN NOT NULL DEFAULT TRUE")
        exec("ALTER TABLE users ALTER COLUMN email_verified SET DEFAULT FALSE")
        // created_at: NULL for users registered before this column existed — backfilling now() would be a false date.
        exec("ALTER TABLE users ADD COLUMN IF NOT EXISTS created_at TIMESTAMP NULL")
    }

    log.info("✓ Database configured successfully")
}
