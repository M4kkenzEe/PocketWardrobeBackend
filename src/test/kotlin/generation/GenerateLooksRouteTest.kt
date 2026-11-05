package com.example.looks

import com.example.testutils.FakeRecommendationService
import com.example.testutils.InMemoryLooks
import com.example.testutils.TestJwtFactory
import com.example.testutils.testLookDraftsModule
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.server.testing.*
import kotlinx.serialization.json.*
import java.net.SocketTimeoutException
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals

class GenerateLooksRouteTest {
    @Test
    fun `confirmed shortage preserves categories for both methods`() {
        listOf(
            listOf("top"), listOf("bottom"), listOf("shoes"),
            listOf("bottom", "shoes"), listOf("top", "bottom", "shoes")
        ).forEach { missing ->
            checkBothMethods("""{"error":"not_enough_clothes","missing":${Json.encodeToString(missing)}}""",
                HttpStatusCode.BadRequest, HttpStatusCode.BadRequest, missing)
        }
    }

    @Test
    fun `absent unknown or empty missing yields general shortage`() {
        listOf("", ",\"missing\":[]", ",\"missing\":[\"hat\"]", ",\"missing\":[\"top\",\"hat\"]").forEach {
            checkBothMethods("""{"error":"not_enough_clothes"$it}""",
                HttpStatusCode.BadRequest, HttpStatusCode.BadRequest, emptyList())
        }
    }

    @Test
    fun `missing categories are ordered and deduplicated`() =
        checkBothMethods("""{"error":"not_enough_clothes","missing":["shoes","top","top"]}""",
            HttpStatusCode.BadRequest, HttpStatusCode.BadRequest, listOf("top", "shoes"))

    @Test
    fun `other client server and malformed errors remain service errors`() {
        listOf(
            HttpStatusCode.NotFound to """{"error":"user_not_found"}""",
            HttpStatusCode.BadRequest to """{"error":"user_not_found"}""",
            HttpStatusCode.InternalServerError to """{"error":"not_enough_clothes"}""",
            HttpStatusCode.BadRequest to "not json"
        ).forEach { (status, body) ->
            checkBothMethods(body, status, HttpStatusCode.ServiceUnavailable)
        }
    }

    @Test
    fun `timeout remains service error`() =
        checkBothMethods("", HttpStatusCode.OK, HttpStatusCode.ServiceUnavailable,
            exception = SocketTimeoutException("timed out"))

    @Test
    fun `empty successful generation stays successful`() =
        checkBothMethods("""{"looks":[]}""", HttpStatusCode.OK, HttpStatusCode.OK)

    @Test
    fun `both methods require authentication`() = testApplication {
        val looks = InMemoryLooks()
        val recommendation = FakeRecommendationService(looks)
        val directory = Files.createTempDirectory("generation-auth").toFile()
        application {
            testLookDraftsModule(looks, recommendation, emptySet(), directory, enableOrdinaryGeneration = true)
        }
        listOf(HttpMethod.Get, HttpMethod.Post).forEach { method ->
            assertEquals(HttpStatusCode.Unauthorized, client.request("/api/v1/looks/generate") {
                this.method = method
            }.status)
        }
        assertEquals(0, recommendation.totalCallCount)
        directory.deleteRecursively()
    }

    private fun checkBothMethods(
        body: String,
        upstreamStatus: HttpStatusCode,
        expectedStatus: HttpStatusCode,
        missing: List<String>? = null,
        exception: Exception? = null
    ) = testApplication {
        val looks = InMemoryLooks()
        val recommendation = FakeRecommendationService(looks).apply {
            generateResponse = upstreamStatus to body
            generateException = exception
        }
        val directory = Files.createTempDirectory("generation-test").toFile()
        application {
            testLookDraftsModule(looks, recommendation, emptySet(), directory, enableOrdinaryGeneration = true)
        }
        try {
            listOf(HttpMethod.Get, HttpMethod.Post).forEach { method ->
                val response = client.request("/api/v1/looks/generate") {
                    this.method = method
                    bearerAuth(TestJwtFactory.generateAccessToken(1))
                    contentType(ContentType.Application.Json)
                    setBody("{}")
                }
                assertEquals(expectedStatus, response.status, "$method upstream=$body")
                val payload = Json.parseToJsonElement(response.bodyAsText()).jsonObject
                when (expectedStatus) {
                    HttpStatusCode.BadRequest -> {
                        assertEquals("not_enough_clothes", payload.getValue("error").jsonPrimitive.content)
                        assertEquals(missing, payload.getValue("missing").jsonArray.map { it.jsonPrimitive.content })
                    }
                    HttpStatusCode.OK -> assertEquals(emptyList(), payload.getValue("look_ids").jsonArray.toList())
                    else -> assertEquals("AI service unavailable", payload.getValue("error").jsonPrimitive.content)
                }
            }
            assertEquals(0, recommendation.renderCallCount())
            assertEquals(0, looks.lookCount())
        } finally {
            directory.deleteRecursively()
        }
    }
}
