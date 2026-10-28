package com.example.testutils

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import com.example.auth.EnvironmentConfig
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

    /**
     * Refresh token in the shape issued before the tokenVersion claim existed (a9f5060).
     * Signed with the app's real JWT settings, because /refresh verifies through JWTConfig.
     */
    fun legacyRefreshTokenWithoutVersion(userId: Int): String = JWT.create()
        .withAudience(EnvironmentConfig.jwtAudience)
        .withIssuer(EnvironmentConfig.jwtIssuer)
        .withClaim("userId", userId)
        .withClaim("type", "refresh")
        .withExpiresAt(Date(System.currentTimeMillis() + 3_600_000L))
        .withJWTId(UUID.randomUUID().toString())
        .sign(Algorithm.HMAC256(EnvironmentConfig.jwtSecret))
}
