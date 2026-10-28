package com.example.auth

import com.example.auth.service.JWTConfig
import com.example.database.data.model.User
import com.auth0.jwt.JWT
import com.example.testutils.FakeEmailService
import com.example.testutils.FakePasswordResetRepository
import com.example.testutils.FakeRevokedTokenRepository
import com.example.testutils.InMemoryUserRepository
import com.example.testutils.TestJwtFactory
import com.example.testutils.testAuthModule
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.testing.*
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.mindrot.jbcrypt.BCrypt
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * EPIC-20 threat model through the real routes: login → (theft) → forgot + reset → /refresh.
 * Before the fix /reset-password changed the hash without bumping tokenVersion, so a stolen
 * refresh token stayed valid for up to 30 days.
 */
class PasswordResetSessionInvalidationTest {

    private val json = Json { ignoreUnknownKeys = true }

    private val victimEmail = "owner@example.com"
    private val oldPassword = "OldPass12"
    private val userRepo = InMemoryUserRepository().apply {
        seed(User(userId = 1, username = "owner", email = victimEmail, passwordHash = BCrypt.hashpw(oldPassword, BCrypt.gensalt(4)), gender = null))
    }
    private val emailService = FakeEmailService()

    private fun ApplicationTestBuilder.module() = application {
        testAuthModule(
            passwordResetRepo = FakePasswordResetRepository(),
            userRepo = userRepo,
            revokedTokenRepo = FakeRevokedTokenRepository(),
            emailService = emailService
        )
    }

    private suspend fun ApplicationTestBuilder.postJson(path: String, body: String): HttpResponse =
        client.post("/api/v1$path") {
            contentType(ContentType.Application.Json)
            setBody(body)
        }

    private suspend fun refreshTokenOf(response: HttpResponse): String {
        val body = response.bodyAsText()
        assertEquals(HttpStatusCode.OK, response.status, body)
        return json.parseToJsonElement(body).jsonObject.getValue("refreshToken").jsonPrimitive.content
    }

    private suspend fun ApplicationTestBuilder.login(): String {
        return refreshTokenOf(postJson("/login", """{"login":"$victimEmail","password":"$oldPassword"}"""))
    }

    private suspend fun ApplicationTestBuilder.refresh(refreshToken: String): HttpResponse =
        postJson("/refresh", """{"refreshToken":"$refreshToken"}""")

    /** Runs the whole recovery flow and returns the refresh token of the session it opens. */
    private suspend fun ApplicationTestBuilder.resetPassword(): String {
        postJson("/auth/forgot-password", """{"email":"$victimEmail"}""")
        val code = emailService.sentCodes().last().second
        return refreshTokenOf(postJson("/auth/reset-password", """{"email":"$victimEmail","code":"$code","newPassword":"NewPass12"}"""))
    }

    @Test
    fun `stolen refresh token from a real login is rejected after the password reset`() = testApplication {
        module()
        val stolen = login()

        resetPassword()

        assertEquals(HttpStatusCode.Unauthorized, refresh(stolen).status)
    }

    @Test
    fun `token the thief rotated before the reset is rejected after it`() = testApplication {
        module()
        val stolen = login()
        val rotatedByThief = refreshTokenOf(refresh(stolen))

        resetPassword()

        assertEquals(HttpStatusCode.Unauthorized, refresh(rotatedByThief).status, "Rotation must not outlive the reset")
    }

    @Test
    fun `session opened by the reset carries the new version and refreshes normally`() = testApplication {
        module()

        val fromReset = resetPassword()

        assertEquals(1L, JWT.decode(fromReset).getClaim("tokenVersion").asLong())
        assertEquals(HttpStatusCode.OK, refresh(fromReset).status)
    }

    @Test
    fun `legacy refresh token without the version claim works while the version is still 0`() = testApplication {
        module()

        val response = refresh(TestJwtFactory.legacyRefreshTokenWithoutVersion(userId = 1))

        assertEquals(HttpStatusCode.OK, response.status)
    }

    @Test
    fun `legacy refresh token without the version claim is rejected after a reset`() = testApplication {
        module()
        val legacy = TestJwtFactory.legacyRefreshTokenWithoutVersion(userId = 1)

        resetPassword()

        assertEquals(HttpStatusCode.Unauthorized, refresh(legacy).status)
    }

    @Test
    fun `refresh token of a user that does not exist gets no new pair`() = testApplication {
        module()

        val response = refresh(JWTConfig.generateTokenPair(baseUserFor(userId = 999)).refreshToken)

        assertEquals(HttpStatusCode.Unauthorized, response.status)
    }

    @Test
    fun `wrong reset code does not end the victim's sessions`() = testApplication {
        module()
        val session = login()
        postJson("/auth/forgot-password", """{"email":"$victimEmail"}""")
        val wrongCode = if (emailService.sentCodes().last().second == "000000") "111111" else "000000"

        val rejected = postJson("/auth/reset-password", """{"email":"$victimEmail","code":"$wrongCode","newPassword":"NewPass12"}""")

        assertEquals(HttpStatusCode.BadRequest, rejected.status)
        assertEquals(HttpStatusCode.OK, refresh(session).status, "Anyone who knows the email must not be able to log the victim out")
    }

    private fun baseUserFor(userId: Int) = User(userId = userId, username = "ghost", email = "ghost@example.com", passwordHash = "", gender = null)
}
