package com.example.auth

import com.auth0.jwt.JWT
import com.example.auth.service.JWTConfig
import com.example.auth.model.LoginResponse
import com.example.database.data.model.User
import com.example.testutils.FakePasswordResetRepository
import com.example.testutils.FakeRevokedTokenRepository
import com.example.testutils.FakeUserRepository
import com.example.testutils.testAuthModule
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.testing.*
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Unit tests for refresh token rotation (Task F).
 * Requires .env with JWT_SECRET, JWT_ISSUER, JWT_AUDIENCE (same as ConfigValidatorTest).
 */
class RefreshTokenRotationTest {

    private val testUser = User(
        userId = 1,
        username = "testuser",
        email = "test@example.com",
        passwordHash = "hash",
        gender = null
    )

    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun `after successful refresh, old refresh token JTI is revoked`() = testApplication {
        val fakeRevoked = FakeRevokedTokenRepository()

        application {
            testAuthModule(
                passwordResetRepo = FakePasswordResetRepository(),
                userRepo = FakeUserRepository(),
                revokedTokenRepo = fakeRevoked
            )
        }

        val oldPair = JWTConfig.generateTokenPair(testUser)
        val oldJti = JWT.decode(oldPair.refreshToken).id

        val client = createClient {
            install(ContentNegotiation) { json(json) }
        }

        val response = client.post("/api/v1/refresh") {
            contentType(ContentType.Application.Json)
            setBody("""{"refreshToken":"${oldPair.refreshToken}"}""")
        }

        assertEquals(HttpStatusCode.OK, response.status, "First /refresh must succeed")
        assertTrue(
            fakeRevoked.isTokenRevoked(oldJti),
            "Old refresh token JTI must be revoked after successful /refresh"
        )
    }

    @Test
    fun `after successful refresh, using old token again returns 401`() = testApplication {
        val fakeRevoked = FakeRevokedTokenRepository()

        application {
            testAuthModule(
                passwordResetRepo = FakePasswordResetRepository(),
                userRepo = FakeUserRepository(),
                revokedTokenRepo = fakeRevoked
            )
        }

        val oldPair = JWTConfig.generateTokenPair(testUser)

        val client = createClient {
            install(ContentNegotiation) { json(json) }
        }

        val firstResponse = client.post("/api/v1/refresh") {
            contentType(ContentType.Application.Json)
            setBody("""{"refreshToken":"${oldPair.refreshToken}"}""")
        }
        assertEquals(HttpStatusCode.OK, firstResponse.status, "First /refresh must succeed")

        val secondResponse = client.post("/api/v1/refresh") {
            contentType(ContentType.Application.Json)
            setBody("""{"refreshToken":"${oldPair.refreshToken}"}""")
        }
        assertEquals(
            HttpStatusCode.Unauthorized,
            secondResponse.status,
            "Old refresh token must be rejected (401) after rotation"
        )
    }

    @Test
    fun `new refresh token obtained from refresh is valid`() = testApplication {
        val fakeRevoked = FakeRevokedTokenRepository()

        application {
            testAuthModule(
                passwordResetRepo = FakePasswordResetRepository(),
                userRepo = FakeUserRepository(),
                revokedTokenRepo = fakeRevoked
            )
        }

        val oldPair = JWTConfig.generateTokenPair(testUser)

        val client = createClient {
            install(ContentNegotiation) { json(json) }
        }

        val firstResponse = client.post("/api/v1/refresh") {
            contentType(ContentType.Application.Json)
            setBody("""{"refreshToken":"${oldPair.refreshToken}"}""")
        }
        assertEquals(HttpStatusCode.OK, firstResponse.status, "First /refresh must succeed")

        val newTokens = json.decodeFromString<LoginResponse>(firstResponse.bodyAsText())

        val secondResponse = client.post("/api/v1/refresh") {
            contentType(ContentType.Application.Json)
            setBody("""{"refreshToken":"${newTokens.refreshToken}"}""")
        }
        assertEquals(
            HttpStatusCode.OK,
            secondResponse.status,
            "New refresh token obtained after rotation must be valid"
        )
    }
}
