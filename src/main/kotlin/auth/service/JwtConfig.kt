package com.example.auth.service

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import com.auth0.jwt.interfaces.DecodedJWT
import com.example.auth.EnvironmentConfig
import com.example.database.data.model.User
import java.util.Date
import java.util.UUID

data class TokenPair(val accessToken: String, val refreshToken: String, val expiresAt: Long)

object JWTConfig {
    private val secret = EnvironmentConfig.jwtSecret
    private val issuer = EnvironmentConfig.jwtIssuer
    private val audience = EnvironmentConfig.jwtAudience
    private val accessValidityMs = EnvironmentConfig.jwtAccessExpiresIn
    private val refreshValidityMs = EnvironmentConfig.jwtRefreshExpiresIn
    private val algorithm = Algorithm.HMAC256(secret)

    fun generateTokenPair(user: User): TokenPair {
        val accessExpiresAt = System.currentTimeMillis() + accessValidityMs

        val accessToken = JWT.create()
            .withAudience(audience)
            .withIssuer(issuer)
            .withClaim("userId", user.userId)
            .withClaim("username", user.username)
            .withClaim("type", "access")
            .withExpiresAt(Date(accessExpiresAt))
            .withJWTId(UUID.randomUUID().toString())
            .sign(algorithm)

        val refreshToken = JWT.create()
            .withAudience(audience)
            .withIssuer(issuer)
            .withClaim("userId", user.userId)
            .withClaim("type", "refresh")
            .withExpiresAt(Date(System.currentTimeMillis() + refreshValidityMs))
            .withJWTId(UUID.randomUUID().toString())
            .sign(algorithm)

        return TokenPair(accessToken, refreshToken, accessExpiresAt)
    }

    fun verifyRefreshToken(token: String): DecodedJWT? {
        return try {
            val decoded = JWT.require(algorithm)
                .withAudience(audience)
                .withIssuer(issuer)
                .withClaim("type", "refresh")
                .build()
                .verify(token)
            decoded
        } catch (_: Exception) {
            null
        }
    }

    fun verifier() = JWT.require(algorithm)
        .withAudience(audience)
        .withIssuer(issuer)
        .withClaim("type", "access")
        .build()
}
