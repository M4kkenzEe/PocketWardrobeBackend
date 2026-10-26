package com.example.auth

import com.example.database.data.model.EmailVerificationCode
import com.example.database.data.model.User
import com.example.testutils.FakeEmailService
import com.example.testutils.FakeEmailVerificationRepository
import com.example.testutils.FakePasswordResetRepository
import com.example.testutils.FakeUserRepository
import com.example.testutils.TEST_JWT_AUDIENCE
import com.example.testutils.TEST_JWT_ISSUER
import com.example.testutils.TEST_JWT_SECRET
import com.example.testutils.TestJwtFactory
import com.example.testutils.testAuthModule
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.testing.*
import kotlinx.serialization.json.Json
import java.security.MessageDigest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class EmailVerificationTest {

    private fun sha256(input: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        return digest.digest(input.toByteArray()).joinToString("") { "%02x".format(it) }
    }

    @Test
    fun `verify-email с правильным кодом возвращает 200`() = testApplication {
        val userId = 42
        val code = "123456"
        val emailVerificationRepo = FakeEmailVerificationRepository()
        val now = System.currentTimeMillis()
        emailVerificationRepo.addCode(
            EmailVerificationCode(
                id = 1,
                userId = userId,
                codeHash = sha256(code),
                expiresAt = now + 15 * 60_000L,
                isUsed = false,
                createdAt = now
            )
        )

        val userRepo = FakeUserRepository(emailVerified = false)

        application {
            testAuthModule(
                passwordResetRepo = FakePasswordResetRepository(),
                userRepo = userRepo,
                emailVerificationRepo = emailVerificationRepo
            )
        }

        val client = createClient {
            install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
        }

        val token = TestJwtFactory.generateAccessToken(userId)
        val response = client.post("/api/v1/auth/verify-email") {
            contentType(ContentType.Application.Json)
            bearerAuth(token)
            setBody("""{"code":"$code"}""")
        }

        assertEquals(HttpStatusCode.OK, response.status)
    }

    @Test
    fun `verify-email с неверным кодом возвращает 400`() = testApplication {
        val userId = 42
        val emailVerificationRepo = FakeEmailVerificationRepository()
        val userRepo = FakeUserRepository(emailVerified = false)

        application {
            testAuthModule(
                passwordResetRepo = FakePasswordResetRepository(),
                userRepo = userRepo,
                emailVerificationRepo = emailVerificationRepo
            )
        }

        val client = createClient {
            install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
        }

        val token = TestJwtFactory.generateAccessToken(userId)
        val response = client.post("/api/v1/auth/verify-email") {
            contentType(ContentType.Application.Json)
            bearerAuth(token)
            setBody("""{"code":"000000"}""")
        }

        assertEquals(HttpStatusCode.BadRequest, response.status)
    }

    @Test
    fun `verify-email с истёкшим кодом возвращает 400`() = testApplication {
        val userId = 42
        val code = "999999"
        val emailVerificationRepo = FakeEmailVerificationRepository()
        val now = System.currentTimeMillis()
        emailVerificationRepo.addCode(
            EmailVerificationCode(
                id = 1,
                userId = userId,
                codeHash = sha256(code),
                expiresAt = now - 1L,  // already expired
                isUsed = false,
                createdAt = now - 3_600_000L
            )
        )

        val userRepo = FakeUserRepository(emailVerified = false)

        application {
            testAuthModule(
                passwordResetRepo = FakePasswordResetRepository(),
                userRepo = userRepo,
                emailVerificationRepo = emailVerificationRepo
            )
        }

        val client = createClient {
            install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
        }

        val token = TestJwtFactory.generateAccessToken(userId)
        val response = client.post("/api/v1/auth/verify-email") {
            contentType(ContentType.Application.Json)
            bearerAuth(token)
            setBody("""{"code":"$code"}""")
        }

        assertEquals(HttpStatusCode.BadRequest, response.status)
    }

    @Test
    fun `resend-verification работает для неверифицированного пользователя`() = testApplication {
        val userId = 42
        val emailVerificationRepo = FakeEmailVerificationRepository()
        val emailService = FakeEmailService()
        val userEmail = "test@test.com"
        val userRepo = FakeUserRepository(
            emailVerified = false,
            emailUser = User(userId = userId, username = "testuser", email = userEmail, passwordHash = "", gender = null, emailVerified = false)
        )

        application {
            testAuthModule(
                passwordResetRepo = FakePasswordResetRepository(),
                userRepo = userRepo,
                emailService = emailService,
                emailVerificationRepo = emailVerificationRepo
            )
        }

        val client = createClient {
            install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
        }

        val token = TestJwtFactory.generateAccessToken(userId)
        val response = client.post("/api/v1/auth/resend-verification") {
            bearerAuth(token)
        }

        assertEquals(HttpStatusCode.OK, response.status)
        val sentVerifications = emailService.sentVerifications()
        assertTrue(sentVerifications.isNotEmpty(), "Verification email must be sent")
        assertEquals(userEmail, sentVerifications.first().first)
    }

    @Test
    fun `resend-verification rate limit — второй запрос в течение 60 секунд возвращает 429`() = testApplication {
        val userId = 42
        val emailVerificationRepo = FakeEmailVerificationRepository()
        val now = System.currentTimeMillis()
        emailVerificationRepo.addCode(
            EmailVerificationCode(
                id = 1,
                userId = userId,
                codeHash = sha256("111111"),
                expiresAt = now + 15 * 60_000L,
                isUsed = false,
                createdAt = now - 10_000L  // created 10 seconds ago
            )
        )
        val userRepo = FakeUserRepository(emailVerified = false)

        application {
            testAuthModule(
                passwordResetRepo = FakePasswordResetRepository(),
                userRepo = userRepo,
                emailVerificationRepo = emailVerificationRepo
            )
        }

        val client = createClient {
            install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
        }

        val token = TestJwtFactory.generateAccessToken(userId)
        val response = client.post("/api/v1/auth/resend-verification") {
            bearerAuth(token)
        }

        assertEquals(HttpStatusCode.TooManyRequests, response.status)
    }

    @Test
    fun `verify-email без токена возвращает 401`() = testApplication {
        application {
            testAuthModule(passwordResetRepo = FakePasswordResetRepository())
        }

        val client = createClient {
            install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
        }

        val response = client.post("/api/v1/auth/verify-email") {
            contentType(ContentType.Application.Json)
            setBody("""{"code":"123456"}""")
        }

        assertEquals(HttpStatusCode.Unauthorized, response.status)
    }

    @Test
    fun `verify-email успешно вызывает setEmailVerified`() = testApplication {
        val userId = 42
        val code = "654321"
        val emailVerificationRepo = FakeEmailVerificationRepository()
        val now = System.currentTimeMillis()
        emailVerificationRepo.addCode(
            EmailVerificationCode(
                id = 1,
                userId = userId,
                codeHash = sha256(code),
                expiresAt = now + 15 * 60_000L,
                isUsed = false,
                createdAt = now
            )
        )

        val userRepo = FakeUserRepository(emailVerified = false)

        application {
            testAuthModule(
                passwordResetRepo = FakePasswordResetRepository(),
                userRepo = userRepo,
                emailVerificationRepo = emailVerificationRepo
            )
        }

        val client = createClient {
            install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
        }

        val token = TestJwtFactory.generateAccessToken(userId)
        client.post("/api/v1/auth/verify-email") {
            contentType(ContentType.Application.Json)
            bearerAuth(token)
            setBody("""{"code":"$code"}""")
        }

        assertTrue(userRepo.isEmailVerified, "setEmailVerified must be called after successful verification")
    }

    @Test
    fun `EmailVerificationUseCase sendCode создаёт код и отправляет email`() = testApplication {
        val userId = 99
        val user = com.example.database.data.model.User(
            userId = userId,
            username = "newuser",
            email = "newuser@example.com",
            passwordHash = "",
            gender = null,
            emailVerified = false
        )
        val emailVerificationRepo = FakeEmailVerificationRepository()
        val emailService = FakeEmailService()
        val userRepo = FakeUserRepository()

        val useCase = com.example.usecases.EmailVerificationUseCase(
            emailVerificationRepository = emailVerificationRepo,
            userRepository = userRepo,
            emailService = emailService
        )

        useCase.sendCode(user)

        assertTrue(emailVerificationRepo.getCodes().isNotEmpty(), "Verification code must be created")
        assertTrue(emailService.sentVerifications().isNotEmpty(), "Verification email must be sent")
        assertEquals("newuser@example.com", emailService.sentVerifications().first().first)
    }

    @Test
    fun `resend-verification работает после истечения cooldown`() = testApplication {
        val userId = 42
        val emailVerificationRepo = FakeEmailVerificationRepository()
        val now = System.currentTimeMillis()
        // Code created 70 seconds ago — cooldown has elapsed
        emailVerificationRepo.addCode(
            EmailVerificationCode(
                id = 1,
                userId = userId,
                codeHash = sha256("111111"),
                expiresAt = now + 15 * 60_000L,
                isUsed = false,
                createdAt = now - 70_000L
            )
        )
        val emailService = FakeEmailService()
        val userRepo = FakeUserRepository(
            emailVerified = false,
            emailUser = com.example.database.data.model.User(userId = userId, username = "testuser", email = "test@test.com", passwordHash = "", gender = null, emailVerified = false)
        )

        application {
            testAuthModule(
                passwordResetRepo = FakePasswordResetRepository(),
                userRepo = userRepo,
                emailService = emailService,
                emailVerificationRepo = emailVerificationRepo
            )
        }

        val client = createClient {
            install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
        }

        val token = TestJwtFactory.generateAccessToken(userId)
        val response = client.post("/api/v1/auth/resend-verification") {
            bearerAuth(token)
        }

        assertEquals(HttpStatusCode.OK, response.status)
        assertTrue(emailService.sentVerifications().isNotEmpty(), "New verification email must be sent after cooldown")
    }
}
