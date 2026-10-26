package com.example.database.domain.repository

import com.example.database.data.model.EmailVerificationCode

interface EmailVerificationRepository {
    suspend fun createCode(userId: Int, codeHash: String, ttlMinutes: Int = 15): EmailVerificationCode
    suspend fun findValidCode(userId: Int, codeHash: String): EmailVerificationCode?
    suspend fun markUsed(id: Int)
    suspend fun deleteUserCodes(userId: Int)
    suspend fun getLatestCode(userId: Int): EmailVerificationCode?
}
