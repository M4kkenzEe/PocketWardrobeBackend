package com.example.database.data.repository

import com.example.database.data.model.PasswordResetCode
import com.example.database.data.model.PasswordResetCodeDao
import com.example.database.data.model.PasswordResetCodeTable
import com.example.database.domain.repository.PasswordResetRepository
import com.example.database.suspendTransaction
import org.jetbrains.exposed.v1.core.*
import org.jetbrains.exposed.v1.core.dao.id.EntityID

class PasswordResetRepositoryImpl : PasswordResetRepository {

    override suspend fun createCode(userId: Int, code: String, ttlMinutes: Int): PasswordResetCode = suspendTransaction {
        val now = System.currentTimeMillis()
        val dao = PasswordResetCodeDao.new {
            this.userId = EntityID(userId, com.example.database.data.model.UserTable)
            this.code = code
            this.expiresAt = now + ttlMinutes * 60_000L
            this.isUsed = false
            this.createdAt = now
        }
        dao.toModel()
    }

    override suspend fun findValidCode(userId: Int, code: String): PasswordResetCode? = suspendTransaction {
        val now = System.currentTimeMillis()
        PasswordResetCodeDao.find {
            (PasswordResetCodeTable.userId eq userId) and
            (PasswordResetCodeTable.code eq code) and
            (PasswordResetCodeTable.isUsed eq false) and
            (PasswordResetCodeTable.expiresAt greater now)
        }.firstOrNull()?.toModel()
    }

    override suspend fun markUsed(id: Int): Unit = suspendTransaction {
        PasswordResetCodeDao.findById(id)?.isUsed = true
    }

    override suspend fun deleteExpiredCodes(): Unit = suspendTransaction {
        val now = System.currentTimeMillis()
        PasswordResetCodeDao.find { PasswordResetCodeTable.expiresAt less now }
            .forEach { it.delete() }
    }

    override suspend fun deleteUserCodes(userId: Int): Unit = suspendTransaction {
        PasswordResetCodeDao.find { PasswordResetCodeTable.userId eq userId }
            .forEach { it.delete() }
    }

    private fun PasswordResetCodeDao.toModel() = PasswordResetCode(
        id = id.value,
        userId = userId.value,
        code = code,
        expiresAt = expiresAt,
        isUsed = isUsed,
        createdAt = createdAt
    )
}
