package com.example.testutils

import com.example.database.data.model.PendingEmailVerification
import com.example.database.domain.repository.PendingEmailVerificationRepository

class FakePendingEmailVerificationRepository : PendingEmailVerificationRepository {

    private val rows = mutableMapOf<String, PendingEmailVerification>()
    private var nextId = 1

    fun seed(row: PendingEmailVerification) {
        rows[row.email] = row
    }

    fun get(email: String): PendingEmailVerification? = rows[email]

    override suspend fun replaceCode(email: String, codeHash: String, ttlMinutes: Int): PendingEmailVerification {
        val now = System.currentTimeMillis()
        val row = PendingEmailVerification(
            id = nextId++,
            email = email,
            codeHash = codeHash,
            expiresAt = now + ttlMinutes * 60_000L,
            attempts = 0,
            createdAt = now
        )
        rows[email] = row
        return row
    }

    override suspend fun find(email: String): PendingEmailVerification? = rows[email]

    override suspend fun claimAttempt(email: String, maxAttempts: Int): Boolean {
        val row = rows[email] ?: return false
        if (row.attempts >= maxAttempts || row.expiresAt <= System.currentTimeMillis()) return false
        rows[email] = row.copy(attempts = row.attempts + 1)
        return true
    }

    override suspend fun delete(email: String) {
        rows.remove(email)
    }

    override suspend fun deleteExpired() {
        val now = System.currentTimeMillis()
        rows.values.removeAll { it.expiresAt < now }
    }
}
