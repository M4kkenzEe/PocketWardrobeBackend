package com.example.auth

import com.example.auth.service.JWTConfig
import com.example.database.data.model.User
import com.example.database.domain.model.UserWithClothesCount
import com.example.database.domain.repository.UserRepository
import com.example.testutils.FakePasswordResetRepository
import com.example.testutils.FakeRevokedTokenRepository
import com.example.testutils.testAuthModule
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.testing.*
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Regression test for: refresh token issued before password reset must be rejected afterwards.
 *
 * VULNERABILITY: /reset-password updated the password hash but did not increment tokenVersion,
 * so any previously issued refresh token remained valid for up to 30 days.
 *
 * This test would have FAILED before the fix (old token was accepted) and PASSES after it.
 */
class PasswordResetSessionInvalidationTest {

    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun `refresh token issued before password reset is rejected after reset`() = testApplication {
        var storedTokenVersion = 0L

        val baseUser = User(
            userId = 1,
            username = "victim",
            email = "victim@example.com",
            passwordHash = "hash",
            gender = null,
            tokenVersion = storedTokenVersion
        )

        // Stateful repo — incrementTokenVersion actually changes the stored version
        val statefulUserRepo = object : UserRepository {
            override suspend fun findUserById(userId: Int) = baseUser.copy(tokenVersion = storedTokenVersion)
            override suspend fun incrementTokenVersion(userId: Int) { storedTokenVersion++ }
            override suspend fun register(user: User, provisionedByAdmin: Boolean): User? = null
            override suspend fun findAllWithClothesCount(): List<UserWithClothesCount> = emptyList()
            override suspend fun findUserByUsername(userName: String): User? = null
            override suspend fun findUserByEmail(email: String): User? = null
            override suspend fun findUserByEmailIgnoreCase(email: String): User? = null
            override suspend fun authenticate(username: String, password: String): User? = null
            override suspend fun updatePassword(userId: Int, newPasswordHash: String) {}
            override suspend fun setEmailVerified(userId: Int) {}
        }

        application {
            testAuthModule(
                passwordResetRepo = FakePasswordResetRepository(),
                userRepo = statefulUserRepo,
                revokedTokenRepo = FakeRevokedTokenRepository()
            )
        }

        // Old refresh token — carries tokenVersion = 0 (before password reset)
        val oldRefreshToken = JWTConfig.generateTokenPair(baseUser.copy(tokenVersion = 0L)).refreshToken

        // Simulate password reset: tokenVersion incremented to 1
        statefulUserRepo.incrementTokenVersion(baseUser.userId)

        val client = createClient {
            install(ContentNegotiation) { json(json) }
        }

        val response = client.post("/api/v1/refresh") {
            contentType(ContentType.Application.Json)
            setBody("""{"refreshToken":"$oldRefreshToken"}""")
        }

        assertEquals(
            HttpStatusCode.Unauthorized,
            response.status,
            "Refresh token issued before password reset must be rejected (401) after the password was reset"
        )
    }

    @Test
    fun `refresh token issued after password reset is valid`() = testApplication {
        var storedTokenVersion = 0L

        val baseUser = User(
            userId = 2,
            username = "victim2",
            email = "victim2@example.com",
            passwordHash = "hash",
            gender = null,
            tokenVersion = storedTokenVersion
        )

        val statefulUserRepo = object : UserRepository {
            override suspend fun findUserById(userId: Int) = baseUser.copy(tokenVersion = storedTokenVersion)
            override suspend fun incrementTokenVersion(userId: Int) { storedTokenVersion++ }
            override suspend fun register(user: User, provisionedByAdmin: Boolean): User? = null
            override suspend fun findAllWithClothesCount(): List<UserWithClothesCount> = emptyList()
            override suspend fun findUserByUsername(userName: String): User? = null
            override suspend fun findUserByEmail(email: String): User? = null
            override suspend fun findUserByEmailIgnoreCase(email: String): User? = null
            override suspend fun authenticate(username: String, password: String): User? = null
            override suspend fun updatePassword(userId: Int, newPasswordHash: String) {}
            override suspend fun setEmailVerified(userId: Int) {}
        }

        application {
            testAuthModule(
                passwordResetRepo = FakePasswordResetRepository(),
                userRepo = statefulUserRepo,
                revokedTokenRepo = FakeRevokedTokenRepository()
            )
        }

        // Simulate password reset
        statefulUserRepo.incrementTokenVersion(baseUser.userId)

        // New refresh token issued AFTER password reset — carries tokenVersion = 1
        val newRefreshToken = JWTConfig.generateTokenPair(baseUser.copy(tokenVersion = storedTokenVersion)).refreshToken

        val client = createClient {
            install(ContentNegotiation) { json(json) }
        }

        val response = client.post("/api/v1/refresh") {
            contentType(ContentType.Application.Json)
            setBody("""{"refreshToken":"$newRefreshToken"}""")
        }

        assertEquals(
            HttpStatusCode.OK,
            response.status,
            "Refresh token issued after password reset must be accepted"
        )
    }
}
