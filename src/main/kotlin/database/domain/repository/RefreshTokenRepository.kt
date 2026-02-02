package com.example.database.domain.repository

import com.example.database.data.model.RefreshToken

interface RefreshTokenRepository {
    /**
     * Stores a new refresh token hash for a user
     * @return The created RefreshToken record
     */
    suspend fun create(
        userId: Int,
        tokenHash: String,
        expiresAt: Long,
        userAgent: String? = null,
        ipAddress: String? = null
    ): RefreshToken

    /**
     * Finds a refresh token by its hash
     * @return RefreshToken if found and not expired, null otherwise
     */
    suspend fun findByTokenHash(tokenHash: String): RefreshToken?

    /**
     * Deletes a specific refresh token by hash
     * @return true if deleted, false if not found
     */
    suspend fun deleteByTokenHash(tokenHash: String): Boolean

    /**
     * Deletes all refresh tokens for a user (logout from all devices)
     * @return Number of tokens deleted
     */
    suspend fun deleteAllForUser(userId: Int): Int

    /**
     * Deletes expired refresh tokens (cleanup job)
     * @return Number of tokens deleted
     */
    suspend fun deleteExpired(): Int

    /**
     * Counts active refresh tokens for a user (for security limits)
     */
    suspend fun countActiveTokensForUser(userId: Int): Long
}
