package com.example.database.domain.repository

import com.example.database.data.model.SharedLook
import com.example.database.data.model.SharedLookDetails

interface SharedLookRepository {
    /**
     * Creates a share token for a look
     * @param lookId ID of the look to share
     * @param ownerId ID of the user who owns the look (for validation)
     * @return Generated share token (UUID)
     */
    suspend fun createShareToken(lookId: Int, ownerId: Int): String

    /**
     * Gets look details by share token (public access)
     * @param shareToken The share token
     * @return SharedLookDetails if token is valid, null otherwise
     */
    suspend fun getLookByShareToken(shareToken: String): SharedLookDetails?

    /**
     * Revokes a share token (only owner can do this)
     * @param shareToken The token to revoke
     * @param ownerId ID of the user requesting revocation
     * @return true if revoked, false if not found or user is not owner
     */
    suspend fun revokeShareToken(shareToken: String, ownerId: Int): Boolean

    /**
     * Gets all active share tokens for a look
     * @param lookId ID of the look
     * @param ownerId ID of the user requesting (must be owner)
     * @return List of share tokens
     */
    suspend fun getShareTokensForLook(lookId: Int, ownerId: Int): List<SharedLook>
}
