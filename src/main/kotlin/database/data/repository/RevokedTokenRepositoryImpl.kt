package com.example.database.data.repository

import com.example.database.data.model.RevokedTokenDao
import com.example.database.data.model.RevokedTokenTable
import com.example.database.data.model.UserTable
import com.example.database.domain.repository.RevokedTokenRepository
import com.example.database.suspendTransaction
import org.jetbrains.exposed.v1.core.dao.id.EntityID

class RevokedTokenRepositoryImpl : RevokedTokenRepository {

    override suspend fun revokeToken(jti: String, userId: Int, expiresAt: Long) = suspendTransaction {
        RevokedTokenDao.new {
            this.jti = jti
            this.userId = EntityID(userId, UserTable)
            this.expiresAt = expiresAt
        }
        Unit
    }

    override suspend fun isTokenRevoked(jti: String): Boolean = suspendTransaction {
        RevokedTokenDao.find { RevokedTokenTable.jti eq jti }.firstOrNull() != null
    }
}
