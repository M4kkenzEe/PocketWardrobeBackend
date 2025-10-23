package com.example.auth

import io.github.cdimascio.dotenv.dotenv

object EnvironmentConfig {
    private val dotenv = dotenv {
        directory = "./"
        ignoreIfMalformed = true
        ignoreIfMissing = true
    }

    val jwtSecret = dotenv["JWT_SECRET"] ?: "fallback_secret"
    val jwtIssuer = dotenv["JWT_ISSUER"] ?: "https://default-issuer.com"
    val jwtAudience = dotenv["JWT_AUDIENCE"] ?: "default-audience"
    val jwtExpiresIn = dotenv["JWT_EXPIRES_IN"]?.toLongOrNull() ?: 3_600_000L
    val analysisServiceUrl = dotenv["ANALYSIS_SERVICE_URL"] ?: "http://localhost:8088/"
}