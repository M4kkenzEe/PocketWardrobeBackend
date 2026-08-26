package com.example.testutils

import com.example.database.data.model.PasswordResetCode
import com.example.database.domain.repository.PasswordResetRepository

class FakePasswordResetRepository : PasswordResetRepository {

    private val codes = mutableListOf<PasswordResetCode>()
    var deleteExpiredCodesCalled = false
        private set

    fun addCode(code: PasswordResetCode) {
        codes.add(code)
    }

    fun getCodes(): List<PasswordResetCode> = codes.toList()

    override suspend fun createCode(userId: Int, code: String, ttlMinutes: Int): PasswordResetCode {
        val now = System.currentTimeMillis()
        val newCode = PasswordResetCode(
            id = (codes.maxOfOrNull { it.id } ?: 0) + 1,
            userId = userId,
            code = code,
            expiresAt = now + ttlMinutes * 60_000L,
            isUsed = false,
            createdAt = now
        )
        codes.add(newCode)
        return newCode
    }

    override suspend fun findValidCode(userId: Int, code: String): PasswordResetCode? {
        val now = System.currentTimeMillis()
        return codes.firstOrNull {
            it.userId == userId && it.code == code && !it.isUsed && it.expiresAt > now
        }
    }

    override suspend fun markUsed(id: Int) {
        val index = codes.indexOfFirst { it.id == id }
        if (index >= 0) {
            codes[index] = codes[index].copy(isUsed = true)
        }
    }

    override suspend fun deleteExpiredCodes() {
        deleteExpiredCodesCalled = true
        val now = System.currentTimeMillis()
        codes.removeAll { it.expiresAt < now }
    }

    override suspend fun deleteUserCodes(userId: Int) {
        codes.removeAll { it.userId == userId }
    }
}
