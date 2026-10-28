package com.example.database.data.repository

import com.example.database.data.model.PendingEmailVerification
import com.example.database.data.model.PendingEmailVerificationDao
import com.example.database.data.model.PendingEmailVerificationTable
import com.example.database.domain.repository.PendingEmailVerificationRepository
import com.example.database.suspendTransaction
import org.jetbrains.exposed.v1.core.*
import org.jetbrains.exposed.v1.core.SqlExpressionBuilder.plus
import org.jetbrains.exposed.v1.jdbc.update

class PendingEmailVerificationRepositoryImpl : PendingEmailVerificationRepository {

    override suspend fun replaceCode(email: String, codeHash: String, ttlMinutes: Int): PendingEmailVerification = suspendTransaction {
        PendingEmailVerificationDao.find { PendingEmailVerificationTable.email eq email }.forEach { it.delete() }
        val now = System.currentTimeMillis()
        PendingEmailVerificationDao.new {
            this.email = email
            this.codeHash = codeHash
            this.expiresAt = now + ttlMinutes * 60_000L
            this.attempts = 0
            this.createdAt = now
        }.toModel()
    }

    override suspend fun find(email: String): PendingEmailVerification? = suspendTransaction {
        PendingEmailVerificationDao.find { PendingEmailVerificationTable.email eq email }
            .firstOrNull()?.toModel()
    }

    override suspend fun claimAttempt(email: String, maxAttempts: Int): Boolean = suspendTransaction {
        val now = System.currentTimeMillis()
        PendingEmailVerificationTable.update({
            (PendingEmailVerificationTable.email eq email) and
                (PendingEmailVerificationTable.attempts less maxAttempts) and
                (PendingEmailVerificationTable.expiresAt greater now)
        }) {
            it[attempts] = attempts + 1
        } > 0
    }

    override suspend fun delete(email: String): Unit = suspendTransaction {
        PendingEmailVerificationDao.find { PendingEmailVerificationTable.email eq email }.forEach { it.delete() }
    }

    override suspend fun deleteExpired(): Unit = suspendTransaction {
        val now = System.currentTimeMillis()
        PendingEmailVerificationDao.find { PendingEmailVerificationTable.expiresAt less now }.forEach { it.delete() }
    }

    private fun PendingEmailVerificationDao.toModel() = PendingEmailVerification(
        id = id.value,
        email = email,
        codeHash = codeHash,
        expiresAt = expiresAt,
        attempts = attempts,
        createdAt = createdAt
    )
}
