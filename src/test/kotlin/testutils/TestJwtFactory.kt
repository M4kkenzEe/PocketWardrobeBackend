package com.example.testutils

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import java.util.Date
import java.util.UUID

object TestJwtFactory {

    private val algorithm = Algorithm.HMAC256(TEST_JWT_SECRET)

    fun generateAccessToken(
        userId: Int,
        username: String = "testuser"
    ): String = JWT.create()
        .withAudience(TEST_JWT_AUDIENCE)
        .withIssuer(TEST_JWT_ISSUER)
        .withClaim("userId", userId)
        .withClaim("username", username)
        .withClaim("type", "access")
        .withExpiresAt(Date(System.currentTimeMillis() + 900_000L))
        .withJWTId(UUID.randomUUID().toString())
        .sign(algorithm)
}
