package com.example.testutils

import com.example.database.domain.repository.RevokedTokenRepository

class FakeRevokedTokenRepository : RevokedTokenRepository {
    private val revoked = mutableSetOf<String>()

    override suspend fun revokeToken(jti: String, userId: Int, expiresAt: Long) {
        revoked.add(jti)
    }

    override suspend fun isTokenRevoked(jti: String): Boolean = revoked.contains(jti)
}
