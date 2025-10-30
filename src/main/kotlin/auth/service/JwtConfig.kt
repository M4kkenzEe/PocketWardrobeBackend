package com.example.auth.service

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import com.example.auth.EnvironmentConfig
import com.example.database.data.model.User
import java.util.Date
import java.util.UUID

object JWTConfig {
    private val secret = EnvironmentConfig.jwtSecret
    private val issuer = EnvironmentConfig.jwtIssuer
    private val audience = EnvironmentConfig.jwtAudience
    private val validityMs = EnvironmentConfig.jwtExpiresIn
    private val algorithm = Algorithm.HMAC256(secret)

    fun generateToken(user: User): String = JWT.create()
        .withAudience(audience)
        .withIssuer(issuer)
        .withClaim("userId", user.userId)
        .withClaim("username", user.username)
        .withExpiresAt(Date(System.currentTimeMillis() + validityMs))
        .withJWTId(UUID.randomUUID().toString())
        .sign(algorithm)

    fun verifier() = JWT.require(algorithm)
        .withAudience(audience)
        .withIssuer(issuer)
        .build()
}