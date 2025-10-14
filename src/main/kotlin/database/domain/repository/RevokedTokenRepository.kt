package com.example.database.domain.repository

interface RevokedTokenRepository {
    suspend fun revokeToken(jti: String, userId: Int, expiresAt: Long)
    suspend fun isTokenRevoked(jti: String): Boolean
}
