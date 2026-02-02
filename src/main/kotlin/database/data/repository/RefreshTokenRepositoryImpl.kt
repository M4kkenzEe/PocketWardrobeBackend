package com.example.database.data.repository

import com.example.database.data.model.*
import com.example.database.domain.repository.RefreshTokenRepository
import com.example.database.suspendTransaction
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.dao.id.EntityID

class RefreshTokenRepositoryImpl : RefreshTokenRepository {

    override suspend fun create(
        userId: Int,
        tokenHash: String,
        expiresAt: Long,
        userAgent: String?,
        ipAddress: String?
    ): RefreshToken = suspendTransaction {
        RefreshTokenDao.new {
            this.userId = EntityID(userId, UserTable)
            this.tokenHash = tokenHash
            this.expiresAt = expiresAt
            this.createdAt = System.currentTimeMillis()
            this.userAgent = userAgent
            this.ipAddress = ipAddress
        }.let(::daoToModel)
    }

    override suspend fun findByTokenHash(tokenHash: String): RefreshToken? = suspendTransaction {
        val now = System.currentTimeMillis()
        RefreshTokenDao.find {
            (RefreshTokenTable.tokenHash eq tokenHash) and
            (RefreshTokenTable.expiresAt greater now)
        }.firstOrNull()?.let(::daoToModel)
    }

    override suspend fun deleteByTokenHash(tokenHash: String): Boolean = suspendTransaction {
        val token = RefreshTokenDao.find {
            RefreshTokenTable.tokenHash eq tokenHash
        }.firstOrNull()
        token?.delete()
        token != null
    }

    override suspend fun deleteAllForUser(userId: Int): Int = suspendTransaction {
        val tokens = RefreshTokenDao.find {
            RefreshTokenTable.userId eq userId
        }.toList()
        val count = tokens.size
        tokens.forEach { it.delete() }
        count
    }

    override suspend fun deleteExpired(): Int = suspendTransaction {
        val now = System.currentTimeMillis()
        val expired = RefreshTokenDao.find {
            RefreshTokenTable.expiresAt less now
        }.toList()
        val count = expired.size
        expired.forEach { it.delete() }
        count
    }

    override suspend fun countActiveTokensForUser(userId: Int): Long = suspendTransaction {
        val now = System.currentTimeMillis()
        RefreshTokenDao.find {
            (RefreshTokenTable.userId eq userId) and
            (RefreshTokenTable.expiresAt greater now)
        }.count()
    }
}
