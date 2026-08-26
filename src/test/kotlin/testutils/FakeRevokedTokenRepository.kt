package com.example.testutils

import com.example.database.domain.repository.RevokedTokenRepository

class FakeRevokedTokenRepository : RevokedTokenRepository {
    override suspend fun revokeToken(jti: String, userId: Int, expiresAt: Long) {}
    override suspend fun isTokenRevoked(jti: String): Boolean = false
}
