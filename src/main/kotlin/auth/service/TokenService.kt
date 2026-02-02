package com.example.auth.service

import com.auth0.jwt.JWT
import com.auth0.jwt.JWTVerifier
import com.auth0.jwt.algorithms.Algorithm
import com.example.auth.EnvironmentConfig
import com.example.database.data.model.User
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.*

class TokenService {

    private val algorithm = Algorithm.HMAC256(EnvironmentConfig.jwtSecret)
    private val issuer = EnvironmentConfig.jwtIssuer
    private val audience = EnvironmentConfig.jwtAudience

    companion object {
        private const val ACCESS_TOKEN_VALIDITY_MS = 15 * 60 * 1000L  // 15 minutes
        private const val REFRESH_TOKEN_VALIDITY_MS = 7 * 24 * 60 * 60 * 1000L  // 7 days
        private const val REFRESH_TOKEN_BYTES = 32  // 256 bits
    }

    /**
     * Generates a short-lived JWT access token
     * @return Pair of (token string, expires_in in seconds)
     */
    fun generateAccessToken(user: User): Pair<String, Long> {
        val expiresAt = System.currentTimeMillis() + ACCESS_TOKEN_VALIDITY_MS

        val token = JWT.create()
            .withAudience(audience)
            .withIssuer(issuer)
            .withSubject(user.userId.toString())
            .withClaim("userId", user.userId)
            .withClaim("username", user.username)
            .withIssuedAt(Date())
            .withExpiresAt(Date(expiresAt))
            .withJWTId(UUID.randomUUID().toString())
            .sign(algorithm)

        return Pair(token, ACCESS_TOKEN_VALIDITY_MS / 1000)  // Return seconds
    }

    /**
     * Generates a cryptographically secure random refresh token
     * @return Pair of (raw token for cookie, expiresAt timestamp in milliseconds)
     */
    fun generateRefreshToken(): Pair<String, Long> {
        val secureRandom = SecureRandom()
        val tokenBytes = ByteArray(REFRESH_TOKEN_BYTES)
        secureRandom.nextBytes(tokenBytes)

        val rawToken = Base64.getUrlEncoder().withoutPadding().encodeToString(tokenBytes)
        val expiresAt = System.currentTimeMillis() + REFRESH_TOKEN_VALIDITY_MS

        return Pair(rawToken, expiresAt)
    }

    /**
     * Hashes a refresh token using SHA-256
     * Only the hash should be stored in the database
     */
    fun hashToken(token: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val hashBytes = digest.digest(token.toByteArray(Charsets.UTF_8))
        return hashBytes.joinToString("") { "%02x".format(it) }
    }

    /**
     * JWT verifier for access token validation
     */
    fun verifier(): JWTVerifier = JWT.require(algorithm)
        .withAudience(audience)
        .withIssuer(issuer)
        .build()

    /**
     * Gets the refresh token validity in milliseconds
     */
    fun getRefreshTokenValidityMs(): Long = REFRESH_TOKEN_VALIDITY_MS

    /**
     * Gets the access token validity in milliseconds
     */
    fun getAccessTokenValidityMs(): Long = ACCESS_TOKEN_VALIDITY_MS
}
