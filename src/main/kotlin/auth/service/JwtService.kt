package com.example.auth.service

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import com.example.auth.EnvironmentConfig
import com.example.auth.model.AuthResponse
import java.util.Date
import java.util.UUID

class JwtService {
    fun generateToken(userId: String): AuthResponse {
        val expiresAt = Date(System.currentTimeMillis() + EnvironmentConfig.jwtExpiresIn)

        val token = JWT.create()
            .withIssuer(EnvironmentConfig.jwtIssuer)
            .withAudience(EnvironmentConfig.jwtAudience)
            .withSubject(userId)
            .withClaim("userId", userId)
            .withIssuedAt(Date())
            .withExpiresAt(expiresAt)
            .withJWTId(UUID.randomUUID().toString())
            .sign(Algorithm.HMAC256(EnvironmentConfig.jwtSecret))

        return AuthResponse(token, expiresAt.time)
    }
}