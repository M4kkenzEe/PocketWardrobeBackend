package com.example.auth

import com.example.database.data.model.PasswordResetCode
import com.example.database.data.model.User
import com.example.testutils.FakePasswordResetRepository
import com.example.testutils.FakeUserRepository
import com.example.testutils.testAuthModule
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.testing.*
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PasswordResetOtpCleanupTest {

    @Test
    fun `forgot-password вызывает deleteExpiredCodes даже для незарегистрированного email`() = testApplication {
        val repo = FakePasswordResetRepository()

        application {
            testAuthModule(passwordResetRepo = repo)
        }

        val client = createClient {
            install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
        }

        client.post("/api/v1/auth/forgot-password") {
            contentType(ContentType.Application.Json)
            setBody("""{"email":"nobody@test.com"}""")
        }

        assertTrue(repo.deleteExpiredCodesCalled,
            "deleteExpiredCodes must be called on every forgot-password request")
    }

    @Test
    fun `forgot-password удаляет expired коды из репозитория`() = testApplication {
        val repo = FakePasswordResetRepository()
        val now = System.currentTimeMillis()

        repo.addCode(
            PasswordResetCode(
                id = 1,
                userId = 99,
                code = "111111",
                expiresAt = now - 60_000L,
                isUsed = false,
                createdAt = now - 3_600_000L
            )
        )

        val userEmail = "active@test.com"
        val user = User(userId = 99, username = "active", email = userEmail, passwordHash = "", gender = null)

        application {
            testAuthModule(
                passwordResetRepo = repo,
                userRepo = FakeUserRepository(emailUser = user)
            )
        }

        val client = createClient {
            install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
        }

        client.post("/api/v1/auth/forgot-password") {
            contentType(ContentType.Application.Json)
            setBody("""{"email":"$userEmail"}""")
        }

        assertTrue(
            repo.getCodes().none { it.expiresAt <= System.currentTimeMillis() },
            "No expired codes should remain in the repository after forgot-password"
        )
    }

    @Test
    fun `findValidCode возвращает null для expired кода`() = runTest {
        val repo = FakePasswordResetRepository()
        val now = System.currentTimeMillis()

        repo.addCode(
            PasswordResetCode(
                id = 1,
                userId = 1,
                code = "999999",
                expiresAt = now - 1L,
                isUsed = false,
                createdAt = now - 1_000L
            )
        )

        val result = repo.findValidCode(userId = 1, code = "999999")

        assertNull(result, "Expired code must not be returned as valid by findValidCode")
    }
}
