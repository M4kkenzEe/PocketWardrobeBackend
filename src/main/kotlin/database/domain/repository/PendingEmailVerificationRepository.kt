package com.example.database.domain.repository

import com.example.database.data.model.PendingEmailVerification

interface PendingEmailVerificationRepository {
    suspend fun replaceCode(email: String, codeHash: String, ttlMinutes: Int = 15): PendingEmailVerification
    suspend fun find(email: String): PendingEmailVerification?

    /** True if an attempt was taken: the code exists, is unexpired and has fewer than [maxAttempts] attempts used. */
    suspend fun claimAttempt(email: String, maxAttempts: Int): Boolean
    suspend fun delete(email: String)
    suspend fun deleteExpired()
}
