package com.example.auth

import org.slf4j.Logger

object ConfigValidator {
    fun validateConfiguration(log: Logger) {
        log.info("=" .repeat(70))
        log.info("Validating environment configuration...")
        log.info("=" .repeat(70))

        try {
            // Trigger validation by accessing all required properties
            // This will throw IllegalStateException if any required config is missing

            log.info("Checking JWT configuration...")
            EnvironmentConfig.jwtSecret
            EnvironmentConfig.jwtIssuer
            EnvironmentConfig.jwtAudience
            EnvironmentConfig.jwtExpiresIn
            log.info("✓ JWT configuration is valid")

            log.info("Checking database configuration...")
            EnvironmentConfig.dbUrl
            EnvironmentConfig.dbUser
            EnvironmentConfig.dbPassword
            EnvironmentConfig.dbName
            log.info("✓ Database configuration is valid")
            log.info("  Database URL: ${EnvironmentConfig.getDatabaseUrl()}")

            log.info("Checking server configuration...")
            log.info("  Server will start at: ${EnvironmentConfig.getServerBaseUrl()}")

            log.info("Checking external services configuration...")
            EnvironmentConfig.removeBgServiceUrl
            EnvironmentConfig.analysisServiceUrl
            log.info("✓ External services configuration is valid")
            log.info("  RemoveBG Service: ${EnvironmentConfig.removeBgServiceUrl}")
            log.info("  Analysis Service: ${EnvironmentConfig.analysisServiceUrl}")

            log.info("Checking file storage configuration...")
            log.info("  Uploads directory: ${EnvironmentConfig.uploadsDirectory}")
            log.info("  Looks directory: ${EnvironmentConfig.looksDirectory}")

            log.info("=" .repeat(70))
            log.info("✓ Configuration validation successful!")
            log.info("=" .repeat(70))

        } catch (e: IllegalStateException) {
            log.error("=" .repeat(70))
            log.error("✗ Configuration validation failed!")
            log.error("=" .repeat(70))
            log.error("Error: ${e.message}")
            log.error("")
            log.error("Please check your .env file in the project root directory.")
            log.error("See .env.example for reference configuration.")
            log.error("")
            log.error("Required environment variables:")
            log.error("  - JWT_SECRET (minimum 32 characters)")
            log.error("  - JWT_ISSUER")
            log.error("  - JWT_AUDIENCE")
            log.error("  - JWT_EXPIRES_IN")
            log.error("  - DB_URL")
            log.error("  - DB_USER")
            log.error("  - DB_PASSWORD")
            log.error("  - DB_NAME")
            log.error("  - REMOVE_BG_SERVICE_URL")
            log.error("  - ANALYSIS_SERVICE_URL")
            log.error("=" .repeat(70))
            throw e
        } catch (e: Exception) {
            log.error("Unexpected error during configuration validation: ${e.message}", e)
            throw e
        }
    }
}
