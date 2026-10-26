package com.example.database.data.repository

import com.example.database.data.model.EmailVerificationCode
import com.example.database.data.model.EmailVerificationCodeDao
import com.example.database.data.model.EmailVerificationCodeTable
import com.example.database.domain.repository.EmailVerificationRepository
import com.example.database.suspendTransaction
import org.jetbrains.exposed.v1.core.*
import org.jetbrains.exposed.v1.core.dao.id.EntityID

class EmailVerificationRepositoryImpl : EmailVerificationRepository {

    override suspend fun createCode(userId: Int, codeHash: String, ttlMinutes: Int): EmailVerificationCode = suspendTransaction {
        val now = System.currentTimeMillis()
        EmailVerificationCodeDao.new {
            this.userId = EntityID(userId, com.example.database.data.model.UserTable)
            this.codeHash = codeHash
            this.expiresAt = now + ttlMinutes * 60_000L
            this.isUsed = false
            this.createdAt = now
        }.toModel()
    }

    override suspend fun findValidCode(userId: Int, codeHash: String): EmailVerificationCode? = suspendTransaction {
        val now = System.currentTimeMillis()
        EmailVerificationCodeDao.find {
            (EmailVerificationCodeTable.userId eq userId) and
            (EmailVerificationCodeTable.codeHash eq codeHash) and
            (EmailVerificationCodeTable.isUsed eq false) and
            (EmailVerificationCodeTable.expiresAt greater now)
        }.firstOrNull()?.toModel()
    }

    override suspend fun markUsed(id: Int): Unit = suspendTransaction {
        EmailVerificationCodeDao.findById(id)?.isUsed = true
    }

    override suspend fun deleteUserCodes(userId: Int): Unit = suspendTransaction {
        EmailVerificationCodeDao.find { EmailVerificationCodeTable.userId eq userId }
            .forEach { it.delete() }
    }

    override suspend fun getLatestCode(userId: Int): EmailVerificationCode? = suspendTransaction {
        EmailVerificationCodeDao.find { EmailVerificationCodeTable.userId eq userId }
            .orderBy(EmailVerificationCodeTable.createdAt to SortOrder.DESC)
            .limit(1)
            .firstOrNull()?.toModel()
    }

    private fun EmailVerificationCodeDao.toModel() = EmailVerificationCode(
        id = id.value,
        userId = userId.value,
        codeHash = codeHash,
        expiresAt = expiresAt,
        isUsed = isUsed,
        createdAt = createdAt
    )
}
