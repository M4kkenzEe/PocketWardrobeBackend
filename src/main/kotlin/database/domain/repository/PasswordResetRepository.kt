package com.example.database.domain.repository

import com.example.database.data.model.PasswordResetCode

interface PasswordResetRepository {
    suspend fun createCode(userId: Int, code: String, ttlMinutes: Int = 15): PasswordResetCode
    suspend fun findValidCode(userId: Int, code: String): PasswordResetCode?
    suspend fun markUsed(id: Int)
    suspend fun deleteExpiredCodes()
    suspend fun deleteUserCodes(userId: Int)
}
