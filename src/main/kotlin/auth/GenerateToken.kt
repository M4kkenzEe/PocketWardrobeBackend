package com.example.auth

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import io.ktor.server.application.Application
import java.util.Date


fun Application.generateToken(username: String): String {
    // Получаем настройки из конфигурации
    val jwtAudience = "jwt-audience"
    val jwtDomain = "http://jwt-provider-domain/"
    val jwtSecret = "secret"
    val jwtExpiresIn = 3600000L // 1 час

    return JWT.create()
        .withAudience(jwtAudience)
        .withIssuer(jwtDomain)
        .withClaim("username", username)
        .withExpiresAt(Date(System.currentTimeMillis() + jwtExpiresIn))
        .sign(Algorithm.HMAC256(jwtSecret))
}