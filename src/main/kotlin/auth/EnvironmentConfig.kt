package com.example.auth

import io.github.cdimascio.dotenv.dotenv

object EnvironmentConfig {
    private val dotenv = dotenv {
        directory = "./"
        ignoreIfMalformed = true
        ignoreIfMissing = true  // Allow missing .env for Docker environments
    }

    // Helper function to read from dotenv or system environment
    private fun getEnv(key: String): String? = dotenv[key] ?: System.getenv(key)

    // JWT Configuration with validation
    val jwtSecret: String = getEnv("JWT_SECRET")?.also {
        require(it.isNotBlank()) { "JWT_SECRET cannot be blank" }
        require(it.length >= 32) { "JWT_SECRET must be at least 32 characters long for security" }
    } ?: throw IllegalStateException("JWT_SECRET is required in environment")

    val jwtIssuer: String = getEnv("JWT_ISSUER")?.also {
        require(it.isNotBlank()) { "JWT_ISSUER cannot be blank" }
    } ?: throw IllegalStateException("JWT_ISSUER is required in environment")

    val jwtAudience: String = getEnv("JWT_AUDIENCE")?.also {
        require(it.isNotBlank()) { "JWT_AUDIENCE cannot be blank" }
    } ?: throw IllegalStateException("JWT_AUDIENCE is required in environment")

    val jwtExpiresIn: Long = getEnv("JWT_EXPIRES_IN")?.toLongOrNull()?.also {
        require(it > 0) { "JWT_EXPIRES_IN must be positive" }
    } ?: throw IllegalStateException("JWT_EXPIRES_IN is required in environment")

    val jwtAccessExpiresIn: Long = getEnv("JWT_ACCESS_EXPIRES_IN")?.toLongOrNull()?.also {
        require(it > 0) { "JWT_ACCESS_EXPIRES_IN must be positive" }
    } ?: 900_000L // 15 minutes

    val jwtRefreshExpiresIn: Long = getEnv("JWT_REFRESH_EXPIRES_IN")?.toLongOrNull()?.also {
        require(it > 0) { "JWT_REFRESH_EXPIRES_IN must be positive" }
    } ?: 2_592_000_000L // 30 days

    // Database Configuration
    val dbUrl: String = getEnv("DB_URL")?.also {
        require(it.isNotBlank()) { "DB_URL cannot be blank" }
    } ?: throw IllegalStateException("DB_URL is required in environment")

    val dbDriver: String = getEnv("DB_DRIVER") ?: "org.postgresql.Driver"

    val dbUser: String = getEnv("DB_USER")?.also {
        require(it.isNotBlank()) { "DB_USER cannot be blank" }
    } ?: throw IllegalStateException("DB_USER is required in environment")

    val dbPassword: String = getEnv("DB_PASSWORD")
        ?: throw IllegalStateException("DB_PASSWORD is required in environment")

    val dbName: String = getEnv("DB_NAME")?.also {
        require(it.isNotBlank()) { "DB_NAME cannot be blank" }
    } ?: throw IllegalStateException("DB_NAME is required in environment")

    // Build full JDBC URL
    fun getDatabaseUrl(): String = "$dbUrl/$dbName"

    // Server Configuration
    val serverHost: String = getEnv("SERVER_HOST") ?: "localhost"
    val serverPort: Int = getEnv("SERVER_PORT")?.toIntOrNull() ?: 8080
    val serverScheme: String = getEnv("SERVER_SCHEME") ?: "https"

    // Build base URL for the server
    fun getServerBaseUrl(): String = "$serverScheme://$serverHost"

    // App Configuration (for deep links)
    val appScheme: String = getEnv("APP_SCHEME") ?: "pocketwardrobe"
    val appHost: String = getEnv("APP_HOST") ?: "share"

    fun getAppDeepLinkBase(): String = "http://$appScheme/$appHost"

    // External Services Configuration
    val removeBgServiceUrl: String = getEnv("REMOVE_BG_SERVICE_URL")?.also {
        require(it.isNotBlank()) { "REMOVE_BG_SERVICE_URL cannot be blank" }
    } ?: throw IllegalStateException("REMOVE_BG_SERVICE_URL is required in environment")

    val analysisServiceUrl: String = getEnv("ANALYSIS_SERVICE_URL")?.also {
        require(it.isNotBlank()) { "ANALYSIS_SERVICE_URL cannot be blank" }
    } ?: throw IllegalStateException("ANALYSIS_SERVICE_URL is required in environment")

    val lookGenerationServiceUrl: String = getEnv("LOOK_GENERATION_SERVICE_URL") ?: "http://localhost:8001"

    // File Storage Configuration
    val uploadsDirectory: String = getEnv("UPLOADS_DIRECTORY") ?: "uploads"
    val looksDirectory: String = getEnv("LOOKS_DIRECTORY") ?: "looks"

    // CORS Configuration
    val corsAllowedOrigins: String = getEnv("CORS_ALLOWED_ORIGINS") ?: "*"

    // Rate Limiting Configuration
    val rateLimitAuth: Int = getEnv("RATE_LIMIT_AUTH")?.toIntOrNull()?.also {
        require(it > 0) { "RATE_LIMIT_AUTH must be positive" }
    } ?: 10

    val rateLimitUpload: Int = getEnv("RATE_LIMIT_UPLOAD")?.toIntOrNull()?.also {
        require(it > 0) { "RATE_LIMIT_UPLOAD must be positive" }
    } ?: 20

    val rateLimitDefault: Int = getEnv("RATE_LIMIT_DEFAULT")?.toIntOrNull()?.also {
        require(it > 0) { "RATE_LIMIT_DEFAULT must be positive" }
    } ?: 100

    val rateLimitGenerate: Int = getEnv("RATE_LIMIT_GENERATE")?.toIntOrNull()?.also {
        require(it > 0) { "RATE_LIMIT_GENERATE must be positive" }
    } ?: 5

    val rateLimitAffiliate: Int = getEnv("RATE_LIMIT_AFFILIATE")?.toIntOrNull()?.also {
        require(it > 0) { "RATE_LIMIT_AFFILIATE must be positive" }
    } ?: 60

    // Database Connection Pool Configuration
    val dbPoolSize: Int = getEnv("DB_POOL_SIZE")?.toIntOrNull()?.also {
        require(it > 0) { "DB_POOL_SIZE must be positive" }
    } ?: 10

    val dbConnectionTimeout: Long = getEnv("DB_CONNECTION_TIMEOUT")?.toLongOrNull()?.also {
        require(it > 0) { "DB_CONNECTION_TIMEOUT must be positive" }
    } ?: 30000

    val dbIdleTimeout: Long = getEnv("DB_IDLE_TIMEOUT")?.toLongOrNull()?.also {
        require(it > 0) { "DB_IDLE_TIMEOUT must be positive" }
    } ?: 600000

    val dbMaxLifetime: Long = getEnv("DB_MAX_LIFETIME")?.toLongOrNull()?.also {
        require(it > 0) { "DB_MAX_LIFETIME must be positive" }
    } ?: 1800000

    // File Upload Validation Configuration
    val maxFileSizeMB: Int = getEnv("MAX_FILE_SIZE_MB")?.toIntOrNull()?.also {
        require(it > 0) { "MAX_FILE_SIZE_MB must be positive" }
    } ?: 10

    val allowedImageTypes: String = getEnv("ALLOWED_IMAGE_TYPES")
        ?: "image/jpeg,image/png,image/jpg"

    // Logging Configuration
    val logLevel: String = getEnv("LOG_LEVEL") ?: "INFO"

    // Affiliate / Admitad Configuration (optional — graceful degradation if not set)
    val admitadWbId: String? = getEnv("ADMITAD_WB_ID")?.takeIf { it.isNotBlank() }
    val admitadOzonId: String? = getEnv("ADMITAD_OZON_ID")?.takeIf { it.isNotBlank() }
    val admitadLamodaId: String? = getEnv("ADMITAD_LAMODA_ID")?.takeIf { it.isNotBlank() }

    // SMTP / Email Configuration
    val smtpHost: String = getEnv("SMTP_HOST")?.also {
        require(it.isNotBlank()) { "SMTP_HOST cannot be blank" }
    } ?: throw IllegalStateException("SMTP_HOST is required in environment")

    val smtpPort: Int = getEnv("SMTP_PORT")?.toIntOrNull()?.also {
        require(it > 0) { "SMTP_PORT must be positive" }
    } ?: throw IllegalStateException("SMTP_PORT is required in environment")

    val smtpUser: String = getEnv("SMTP_USER")?.also {
        require(it.isNotBlank()) { "SMTP_USER cannot be blank" }
    } ?: throw IllegalStateException("SMTP_USER is required in environment")

    val smtpPassword: String = getEnv("SMTP_PASSWORD")
        ?: throw IllegalStateException("SMTP_PASSWORD is required in environment")

    val smtpFrom: String = getEnv("SMTP_FROM")?.also {
        require(it.isNotBlank()) { "SMTP_FROM cannot be blank" }
    } ?: throw IllegalStateException("SMTP_FROM is required in environment")
}