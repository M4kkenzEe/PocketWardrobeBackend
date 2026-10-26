package com.example.testutils

import com.example.database.data.model.EmailVerificationCode
import com.example.database.domain.repository.EmailVerificationRepository

class FakeEmailVerificationRepository : EmailVerificationRepository {

    private val codes = mutableListOf<EmailVerificationCode>()

    fun addCode(code: EmailVerificationCode) {
        codes.add(code)
    }

    fun getCodes(): List<EmailVerificationCode> = codes.toList()

    override suspend fun createCode(userId: Int, codeHash: String, ttlMinutes: Int): EmailVerificationCode {
        val now = System.currentTimeMillis()
        val newCode = EmailVerificationCode(
            id = (codes.maxOfOrNull { it.id } ?: 0) + 1,
            userId = userId,
            codeHash = codeHash,
            expiresAt = now + ttlMinutes * 60_000L,
            isUsed = false,
            createdAt = now
        )
        codes.add(newCode)
        return newCode
    }

    override suspend fun findValidCode(userId: Int, codeHash: String): EmailVerificationCode? {
        val now = System.currentTimeMillis()
        return codes.firstOrNull {
            it.userId == userId && it.codeHash == codeHash && !it.isUsed && it.expiresAt > now
        }
    }

    override suspend fun markUsed(id: Int) {
        val index = codes.indexOfFirst { it.id == id }
        if (index >= 0) {
            codes[index] = codes[index].copy(isUsed = true)
        }
    }

    override suspend fun deleteUserCodes(userId: Int) {
        codes.removeAll { it.userId == userId }
    }

    override suspend fun getLatestCode(userId: Int): EmailVerificationCode? {
        return codes.filter { it.userId == userId }.maxByOrNull { it.createdAt }
    }
}
