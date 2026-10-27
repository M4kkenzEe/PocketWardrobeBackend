package com.example.auth

import com.example.testutils.FakeUserRepository
import com.example.testutils.TestJwtFactory
import com.example.testutils.testLooksEmailVerificationModule
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.testing.*
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertNotEquals

class GenerateLookEmailVerifiedTest {

    @Test
    fun `POST looks-generate для неверифицированного пользователя не блокируется по email`() = testApplication {
        val unverifiedUserRepo = FakeUserRepository(emailVerified = false)

        application {
            testLooksEmailVerificationModule(userRepo = unverifiedUserRepo)
        }

        val client = createClient {
            install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
        }

        val token = TestJwtFactory.generateAccessToken(userId = 1)
        val response = client.post("/api/v1/looks/generate") {
            contentType(ContentType.Application.Json)
            bearerAuth(token)
            setBody("{}")
        }

        // Generation service is a stub here, so the call may fail downstream (503); the gate itself must be gone
        assertNotEquals(HttpStatusCode.Forbidden, response.status)
    }
}
