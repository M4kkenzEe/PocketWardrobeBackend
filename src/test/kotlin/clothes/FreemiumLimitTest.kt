package com.example.clothes

import com.example.testutils.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.client.request.forms.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.testing.*
import kotlinx.serialization.json.*
import kotlin.test.*

class FreemiumLimitTest {

    private val token get() = TestJwtFactory.generateAccessToken(TEST_USER_ID)

    private fun appTest(
        clotheCount: Int,
        isPro: Boolean,
        block: suspend ApplicationTestBuilder.() -> Unit
    ) = testApplication {
        application {
            testClothesModule(
                clotheRepo = FakeClotheRepository(),
                userRepo = FakeUserRepository(isPro = isPro),
                userClotheRepo = FakeUserClotheRepository(clotheCount = clotheCount)
            )
        }
        block()
    }

    private fun ApplicationTestBuilder.jsonClient() = createClient {
        install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
    }

    // ── POST /clothes ──────────────────────────────────────────────────────────

    @Test
    fun `POST clothes - free user at limit 100 gets 402 FREEMIUM_LIMIT`() =
        appTest(clotheCount = 100, isPro = false) {
            val resp = jsonClient().post("/api/v1/clothes") {
                bearerAuth(token)
                setBody(MultiPartFormDataContent(formData {
                    append("name", "test")
                    append("storeUrl", "https://example.com")
                }))
            }
            assertEquals(HttpStatusCode.PaymentRequired, resp.status)

            val body = Json.parseToJsonElement(resp.bodyAsText()).jsonObject
            assertEquals("FREEMIUM_LIMIT", body["error"]?.jsonPrimitive?.content)
            assertEquals(100, body["limit"]?.jsonPrimitive?.int)
            assertEquals(100, body["current"]?.jsonPrimitive?.int)
        }

    @Test
    fun `POST clothes - free user at 99 items passes freemium check`() =
        appTest(clotheCount = 99, isPro = false) {
            val resp = jsonClient().post("/api/v1/clothes") {
                bearerAuth(token)
                // Empty body → 400 "Missing required fields", not 402
                setBody(MultiPartFormDataContent(formData {}))
            }
            // Passes freemium gate, fails at missing form fields
            assertNotEquals(HttpStatusCode.PaymentRequired, resp.status)
        }

    @Test
    fun `POST clothes - pro user at 100 items is not blocked`() =
        appTest(clotheCount = 100, isPro = true) {
            val resp = jsonClient().post("/api/v1/clothes") {
                bearerAuth(token)
                setBody(MultiPartFormDataContent(formData {}))
            }
            assertNotEquals(HttpStatusCode.PaymentRequired, resp.status)
        }

    @Test
    fun `POST clothes - free user at 200 items gets 402 with correct counts`() =
        appTest(clotheCount = 200, isPro = false) {
            val resp = jsonClient().post("/api/v1/clothes") {
                bearerAuth(token)
                setBody(MultiPartFormDataContent(formData {
                    append("name", "x")
                    append("storeUrl", "https://x.com")
                }))
            }
            assertEquals(HttpStatusCode.PaymentRequired, resp.status)

            val body = Json.parseToJsonElement(resp.bodyAsText()).jsonObject
            assertEquals("FREEMIUM_LIMIT", body["error"]?.jsonPrimitive?.content)
            assertEquals(100, body["limit"]?.jsonPrimitive?.int)
            assertEquals(200, body["current"]?.jsonPrimitive?.int)
        }

    @Test
    fun `POST clothes - no auth returns 401 regardless of limit`() =
        appTest(clotheCount = 100, isPro = false) {
            val resp = jsonClient().post("/api/v1/clothes") {
                setBody(MultiPartFormDataContent(formData {}))
            }
            assertEquals(HttpStatusCode.Unauthorized, resp.status)
        }

    // ── GET /clothes/from_url ──────────────────────────────────────────────────

    @Test
    fun `GET clothes from_url - free user at limit 100 gets 402 FREEMIUM_LIMIT`() =
        appTest(clotheCount = 100, isPro = false) {
            val resp = jsonClient().get("/api/v1/clothes/from_url?url=https://example.com/img.jpg") {
                bearerAuth(token)
            }
            assertEquals(HttpStatusCode.PaymentRequired, resp.status)

            val body = Json.parseToJsonElement(resp.bodyAsText()).jsonObject
            assertEquals("FREEMIUM_LIMIT", body["error"]?.jsonPrimitive?.content)
            assertEquals(100, body["limit"]?.jsonPrimitive?.int)
            assertEquals(100, body["current"]?.jsonPrimitive?.int)
        }

    @Test
    fun `GET clothes from_url - pro user at 100 items is not blocked`() =
        appTest(clotheCount = 100, isPro = true) {
            val resp = jsonClient().get("/api/v1/clothes/from_url?url=https://example.com/img.jpg") {
                bearerAuth(token)
            }
            // Pro user passes freemium gate; image download from stub fails → 500
            assertNotEquals(HttpStatusCode.PaymentRequired, resp.status)
        }

    @Test
    fun `GET clothes from_url - missing url returns 400 not 402`() =
        appTest(clotheCount = 100, isPro = false) {
            val resp = jsonClient().get("/api/v1/clothes/from_url") { bearerAuth(token) }
            // Missing url check happens before freemium check in route
            assertNotEquals(HttpStatusCode.PaymentRequired, resp.status)
        }

    @Test
    fun `GET clothes from_url - no auth returns 401 regardless of limit`() =
        appTest(clotheCount = 100, isPro = false) {
            val resp = jsonClient().get("/api/v1/clothes/from_url?url=https://example.com/img.jpg")
            assertEquals(HttpStatusCode.Unauthorized, resp.status)
        }
}
