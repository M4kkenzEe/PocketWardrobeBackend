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
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

class GenerateLookEmailVerifiedTest {

    @Test
    fun `POST looks-generate для неверифицированного пользователя возвращает 403 с error email_not_verified`() = testApplication {
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

        assertEquals(HttpStatusCode.Forbidden, response.status)
        val body = Json.parseToJsonElement(response.bodyAsText()).jsonObject
        assertEquals("email_not_verified", body["error"]?.jsonPrimitive?.content)
    }

    @Test
    fun `POST looks-generate для верифицированного пользователя не возвращает 403`() = testApplication {
        val verifiedUserRepo = FakeUserRepository(emailVerified = true)

        application {
            testLooksEmailVerificationModule(userRepo = verifiedUserRepo)
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

        // May succeed or fail for other reasons (no clothes, service unavailable)
        // but must NOT be 403 email_not_verified
        assertNotEquals(HttpStatusCode.Forbidden, response.status)
    }
}
