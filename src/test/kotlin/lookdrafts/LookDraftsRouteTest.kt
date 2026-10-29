package com.example.lookdrafts

import com.example.testutils.FakeRecommendationService
import com.example.testutils.InMemoryLooks
import com.example.testutils.TestJwtFactory
import com.example.testutils.testLookDraftsModule
import io.ktor.client.HttpClient
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.server.testing.*
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.File
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class LookDraftsRouteTest {

    private val owner = 1
    private val stranger = 2
    private val anchor = 100
    private val ownedClotheIds = setOf(100, 101, 102, 103)

    private val looksDirectory: File = Files.createTempDirectory("looks-test").toFile()
    private val looks = InMemoryLooks()
    private val recommendation = FakeRecommendationService(looks)

    @AfterTest
    fun cleanUp() {
        looksDirectory.deleteRecursively()
    }

    private fun withDraftsApp(block: suspend ApplicationTestBuilder.() -> Unit) = testApplication {
        application {
            testLookDraftsModule(looks, recommendation, ownedClotheIds, looksDirectory)
        }
        block()
    }

    private suspend fun HttpClient.postDrafts(anchorClotheId: Int = anchor, userId: Int = owner) =
        post("/api/v1/looks/drafts") {
            bearerAuth(TestJwtFactory.generateAccessToken(userId))
            contentType(ContentType.Application.Json)
            setBody("""{"anchorClotheId": $anchorClotheId}""")
        }

    private suspend fun HttpClient.authorized(method: HttpMethod, path: String, userId: Int = owner) =
        request(path) {
            this.method = method
            bearerAuth(TestJwtFactory.generateAccessToken(userId))
        }

    private suspend fun HttpResponse.json(): JsonObject = Json.parseToJsonElement(bodyAsText()).jsonObject

    private suspend fun HttpResponse.lookIds(): List<Int> =
        json().getValue("looks").jsonArray.map { it.jsonObject.getValue("lookId").jsonPrimitive.int }

    private suspend fun HttpClient.listedLookIds(userId: Int = owner): List<Int> {
        val response = authorized(HttpMethod.Get, "/api/v1/looks", userId)
        return Json.parseToJsonElement(response.bodyAsText()).jsonArray.map { it.jsonObject.getValue("id").jsonPrimitive.int }
    }

    private fun twoAnchorLooks() {
        recommendation.anchorClotheSets = listOf(listOf(100, 101, 102), listOf(100, 101, 103))
    }

    // POST /looks/drafts

    @Test
    fun `anchor owned by someone else gets 404 and RecommendationService is never called`() = withDraftsApp {
        twoAnchorLooks()

        val response = client.postDrafts(anchorClotheId = 999)

        assertEquals(HttpStatusCode.NotFound, response.status)
        assertEquals("clothe_not_found", response.json().getValue("error").jsonPrimitive.content)
        assertEquals(0, recommendation.totalCallCount)
    }

    @Test
    fun `not_enough_for_anchor from RecommendationService is passed through with missing categories`() = withDraftsApp {
        recommendation.generateFromItemFailure =
            HttpStatusCode.UnprocessableEntity to """{"error": "not_enough_for_anchor", "missing": ["bottom", "shoes"]}"""

        val response = client.postDrafts()

        assertEquals(HttpStatusCode.UnprocessableEntity, response.status)
        val body = response.json()
        assertEquals("not_enough_for_anchor", body.getValue("error").jsonPrimitive.content)
        assertEquals(listOf("bottom", "shoes"), body.getValue("missing").jsonArray.map { it.jsonPrimitive.content })
    }

    @Test
    fun `unsupported_category from RecommendationService is passed through`() = withDraftsApp {
        recommendation.generateFromItemFailure =
            HttpStatusCode.UnprocessableEntity to """{"error": "unsupported_category"}"""

        val response = client.postDrafts()

        assertEquals(HttpStatusCode.UnprocessableEntity, response.status)
        assertEquals("""{"error":"unsupported_category"}""", response.bodyAsText())
    }

    @Test
    fun `clothe_not_found from RecommendationService is passed through as 404`() = withDraftsApp {
        recommendation.generateFromItemFailure = HttpStatusCode.NotFound to """{"error": "clothe_not_found"}"""

        val response = client.postDrafts()

        assertEquals(HttpStatusCode.NotFound, response.status)
        assertEquals("""{"error":"clothe_not_found"}""", response.bodyAsText())
    }

    @Test
    fun `unreachable RecommendationService gets 503`() = withDraftsApp {
        recommendation.isUnreachable = true

        val response = client.postDrafts()

        assertEquals(HttpStatusCode.ServiceUnavailable, response.status)
        assertEquals("""{"error":"AI service unavailable"}""", response.bodyAsText())
    }

    @Test
    fun `RecommendationService 500 gets 503`() = withDraftsApp {
        recommendation.generateFromItemFailure = HttpStatusCode.InternalServerError to "boom"

        assertEquals(HttpStatusCode.ServiceUnavailable, client.postDrafts().status)
    }

    @Test
    fun `malformed body gets 400`() = withDraftsApp {
        val response = client.post("/api/v1/looks/drafts") {
            bearerAuth(TestJwtFactory.generateAccessToken(owner))
            contentType(ContentType.Application.Json)
            setBody("""{"anchor": "x"}""")
        }

        assertEquals(HttpStatusCode.BadRequest, response.status)
    }

    @Test
    fun `request without token gets 401`() = withDraftsApp {
        val response = client.post("/api/v1/looks/drafts") {
            contentType(ContentType.Application.Json)
            setBody("""{"anchorClotheId": $anchor}""")
        }

        assertEquals(HttpStatusCode.Unauthorized, response.status)
    }

    @Test
    fun `generation renders drafts as materialized drafts with urls`() = withDraftsApp {
        twoAnchorLooks()

        val response = client.postDrafts()

        assertEquals(HttpStatusCode.OK, response.status)
        val body = response.json()
        assertEquals(false, body.getValue("allSaved").jsonPrimitive.boolean)
        val items = body.getValue("looks").jsonArray.map { it.jsonObject }
        assertEquals(2, items.size)
        items.forEach { item ->
            val lookId = item.getValue("lookId").jsonPrimitive.int
            assertTrue(item.getValue("url").jsonPrimitive.content.endsWith(".png"))
            assertEquals(item.getValue("url").jsonPrimitive.content, looks.urlOf(lookId))
        }
        assertEquals(listOf(true, true), recommendation.materializeDraftFlags.toList())
        assertEquals(2, looks.draftCount())
        assertEquals(2, looksDirectory.listFiles().orEmpty().size)
    }

    @Test
    fun `all looks already saved returns empty list and allSaved`() = withDraftsApp {
        recommendation.allSaved = true

        val response = client.postDrafts()

        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals("""{"looks":[],"allSaved":true}""", response.bodyAsText())
    }

    @Test
    fun `repeated call reuses unsaved drafts without new rows or renders`() = withDraftsApp {
        twoAnchorLooks()
        val firstLookIds = client.postDrafts().lookIds()
        val lookCountAfterFirst = looks.lookCount()
        val rendersAfterFirst = recommendation.renderCallCount()

        val secondLookIds = client.postDrafts().lookIds()

        assertEquals(firstLookIds, secondLookIds)
        assertEquals(lookCountAfterFirst, looks.lookCount())
        assertEquals(2, looks.draftCount())
        assertEquals(rendersAfterFirst, recommendation.renderCallCount())
        assertEquals(2, recommendation.callCount("/materialize-look"))
    }

    @Test
    fun `reused draft is matched by clothe set regardless of item order`() = withDraftsApp {
        recommendation.anchorClotheSets = listOf(listOf(100, 101, 102))
        val firstLookIds = client.postDrafts().lookIds()
        recommendation.anchorClotheSets = listOf(listOf(102, 100, 101))

        assertEquals(firstLookIds, client.postDrafts().lookIds())
    }

    @Test
    fun `reused draft whose render failed is rendered again`() = withDraftsApp {
        recommendation.anchorClotheSets = listOf(listOf(100, 101, 102))
        recommendation.rendersFail = true
        val firstResponse = client.postDrafts()
        val lookId = firstResponse.lookIds().single()
        assertEquals("", looks.urlOf(lookId))
        recommendation.rendersFail = false

        val secondResponse = client.postDrafts()

        assertEquals(listOf(lookId), secondResponse.lookIds())
        assertTrue(looks.urlOf(lookId).endsWith(".png"))
        assertEquals(1, looks.draftCount())
    }

    @Test
    fun `drafts of another user are not reused`() = withDraftsApp {
        recommendation.anchorClotheSets = listOf(listOf(100, 101, 102))
        val ownerLookId = client.postDrafts(userId = owner).lookIds().single()

        val strangerLookId = client.postDrafts(userId = stranger).lookIds().single()

        assertNotEquals(ownerLookId, strangerLookId)
        assertEquals(2, looks.draftCount())
    }

    // GET /looks, GET /looks/byId

    @Test
    fun `drafts are absent from the looks list and appear after save`() = withDraftsApp {
        twoAnchorLooks()
        val lookId = client.postDrafts().lookIds().first()
        assertEquals(emptyList(), client.listedLookIds())

        client.authorized(HttpMethod.Post, "/api/v1/looks/$lookId/save")

        assertEquals(listOf(lookId), client.listedLookIds())
    }

    @Test
    fun `byId returns own draft with isDraft true`() = withDraftsApp {
        twoAnchorLooks()
        val lookId = client.postDrafts().lookIds().first()

        val response = client.authorized(HttpMethod.Get, "/api/v1/looks/byId/$lookId")

        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals(true, response.json().getValue("isDraft").jsonPrimitive.boolean)
    }

    @Test
    fun `byId does not give another user a draft`() = withDraftsApp {
        twoAnchorLooks()
        val lookId = client.postDrafts().lookIds().first()

        val response = client.authorized(HttpMethod.Get, "/api/v1/looks/byId/$lookId", userId = stranger)

        assertNotEquals(HttpStatusCode.OK, response.status)
    }

    @Test
    fun `byId returns saved look with isDraft false`() = withDraftsApp {
        val lookId = looks.createSavedLook(owner, setOf(100, 101))

        val response = client.authorized(HttpMethod.Get, "/api/v1/looks/byId/$lookId")

        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals(false, response.json().getValue("isDraft").jsonPrimitive.boolean)
    }

    @Test
    fun `byId returns a draft as saved after save`() = withDraftsApp {
        twoAnchorLooks()
        val lookId = client.postDrafts().lookIds().first()
        client.authorized(HttpMethod.Post, "/api/v1/looks/$lookId/save")

        val response = client.authorized(HttpMethod.Get, "/api/v1/looks/byId/$lookId")

        assertEquals(false, response.json().getValue("isDraft").jsonPrimitive.boolean)
    }

    // POST /looks/{id}/save

    @Test
    fun `save puts the draft into my looks and returns its id`() = withDraftsApp {
        twoAnchorLooks()
        val lookId = client.postDrafts().lookIds().first()

        val response = client.authorized(HttpMethod.Post, "/api/v1/looks/$lookId/save")

        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals(lookId, response.json().getValue("lookId").jsonPrimitive.int)
        assertEquals(1, looks.userLookCount(owner))
    }

    @Test
    fun `repeated save returns the same id without a second user look`() = withDraftsApp {
        twoAnchorLooks()
        val lookId = client.postDrafts().lookIds().first()
        client.authorized(HttpMethod.Post, "/api/v1/looks/$lookId/save")

        val response = client.authorized(HttpMethod.Post, "/api/v1/looks/$lookId/save")

        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals(lookId, response.json().getValue("lookId").jsonPrimitive.int)
        assertEquals(1, looks.userLookCount(owner))
    }

    @Test
    fun `save returns the existing look when the same clothes are already saved`() = withDraftsApp {
        recommendation.anchorClotheSets = listOf(listOf(100, 101, 102))
        val draftLookId = client.postDrafts().lookIds().single()
        val existingLookId = looks.createSavedLook(owner, setOf(102, 101, 100))

        val response = client.authorized(HttpMethod.Post, "/api/v1/looks/$draftLookId/save")

        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals(existingLookId, response.json().getValue("lookId").jsonPrimitive.int)
        assertEquals(1, looks.userLookCount(owner))
    }

    @Test
    fun `save of another user's draft gets 404`() = withDraftsApp {
        twoAnchorLooks()
        val lookId = client.postDrafts().lookIds().first()

        val response = client.authorized(HttpMethod.Post, "/api/v1/looks/$lookId/save", userId = stranger)

        assertEquals(HttpStatusCode.NotFound, response.status)
        assertEquals("look_not_found", response.json().getValue("error").jsonPrimitive.content)
        assertEquals(0, looks.userLookCount(stranger))
    }

    @Test
    fun `save of a missing look gets 404`() = withDraftsApp {
        val response = client.authorized(HttpMethod.Post, "/api/v1/looks/12345/save")

        assertEquals(HttpStatusCode.NotFound, response.status)
    }

    // POST /looks/{id}/share

    @Test
    fun `a draft cannot be shared`() = withDraftsApp {
        twoAnchorLooks()
        val lookId = client.postDrafts().lookIds().first()

        val response = client.authorized(HttpMethod.Post, "/api/v1/looks/$lookId/share")

        assertNotEquals(HttpStatusCode.Created, response.status)
        assertTrue(response.status.value in 400..499)
    }

    @Test
    fun `a saved draft can be shared`() = withDraftsApp {
        twoAnchorLooks()
        val lookId = client.postDrafts().lookIds().first()
        client.authorized(HttpMethod.Post, "/api/v1/looks/$lookId/save")

        val response = client.authorized(HttpMethod.Post, "/api/v1/looks/$lookId/share")

        assertEquals(HttpStatusCode.Created, response.status)
    }

    // The 404 handler keeps JSON error codes, but every other 404 body stays the generic one

    @Test
    fun `plain-text 404 of an existing endpoint still gets the generic body`() = withDraftsApp {
        val response = client.authorized(HttpMethod.Delete, "/api/v1/looks/12345")

        assertEquals(HttpStatusCode.NotFound, response.status)
        assertEquals("Not found", response.json().getValue("error").jsonPrimitive.content)
    }

    @Test
    fun `unknown endpoint still gets the generic 404 body`() = withDraftsApp {
        val response = client.authorized(HttpMethod.Get, "/api/v1/looks/unknown/path")

        assertEquals(HttpStatusCode.NotFound, response.status)
        assertEquals("Not found", response.json().getValue("error").jsonPrimitive.content)
    }
}
