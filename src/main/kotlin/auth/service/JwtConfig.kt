package com.example.auth.service

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import com.example.database.data.model.User
import java.util.Date
import java.util.UUID

object JWTConfig {
    private const val secret = "your-jwt-secret" // Replace with a secure secret in production
    private const val issuer = "http://localhost:8080"
    private const val audience = "wardrobe-users"
    private const val validityMs = 36_000_000 // 10 hours
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