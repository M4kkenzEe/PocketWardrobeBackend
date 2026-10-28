package com.example.database.domain.repository

import com.example.database.data.model.PasswordResetCode

interface PasswordResetRepository {
    suspend fun createCode(userId: Int, code: String, ttlMinutes: Int = 15): PasswordResetCode
    /** Returns the code even if it is expired or used: telling those apart from a wrong code is the caller's job. */
    suspend fun findCode(userId: Int, code: String): PasswordResetCode?
    suspend fun markUsed(id: Int)
    suspend fun deleteExpiredCodes()
    suspend fun deleteUserCodes(userId: Int)
}
