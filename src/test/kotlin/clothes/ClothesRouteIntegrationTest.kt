package com.example.clothes

import com.example.database.data.model.Clothe
import com.example.database.data.model.PaginatedClothesResponse
import com.example.testutils.*
import io.ktor.client.call.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.testing.*
import kotlinx.serialization.json.*
import kotlin.test.*

class ClothesRouteIntegrationTest {

    private lateinit var fakeRepo: FakeClotheRepository

    @BeforeTest
    fun setUp() {
        fakeRepo = FakeClotheRepository()
        fakeRepo.seed(TEST_USER_ID, *CLOTHES_SEED.toTypedArray())
    }

    private fun appTest(block: suspend ApplicationTestBuilder.() -> Unit) = testApplication {
        application { testClothesModule(clotheRepo = fakeRepo) }
        block()
    }

    private fun ApplicationTestBuilder.jsonClient() = createClient {
        install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
    }

    private val token get() = TestJwtFactory.generateAccessToken(TEST_USER_ID)

    // ========== Auth ==========

    @Test
    fun `GET clothes - no auth token returns 401`() = appTest {
        val resp = jsonClient().get("/api/v1/clothes")
        assertEquals(HttpStatusCode.Unauthorized, resp.status)
    }

    // ========== Backward compat — plain list ==========

    @Test
    fun `GET clothes - no params returns plain JsonArray sorted by id`() = appTest {
        val resp = jsonClient().get("/api/v1/clothes") { bearerAuth(token) }
        assertEquals(HttpStatusCode.OK, resp.status)

        val body = resp.body<JsonElement>()
        assertTrue(body is JsonArray, "Expected JsonArray, got ${body::class.simpleName}")
        assertEquals(CLOTHES_SEED.size, body.jsonArray.size)

        val ids = body.jsonArray.map { it.jsonObject["id"]!!.jsonPrimitive.int }
        assertEquals(ids.sorted(), ids)
    }

    // ========== Pagination ==========

    @Test
    fun `GET clothes - limit=3 returns paginated response with hasMore=true`() = appTest {
        val resp = jsonClient().get("/api/v1/clothes?limit=3") { bearerAuth(token) }
        assertEquals(HttpStatusCode.OK, resp.status)

        val body = resp.body<PaginatedClothesResponse>()
        assertEquals(3, body.data.size)
        assertTrue(body.hasMore)
        assertNotNull(body.nextCursor)
        assertEquals(body.data.last().id, body.nextCursor)
    }

    @Test
    fun `GET clothes - limit=100 returns all items with hasMore=false`() = appTest {
        val resp = jsonClient().get("/api/v1/clothes?limit=100") { bearerAuth(token) }
        assertEquals(HttpStatusCode.OK, resp.status)

        val body = resp.body<PaginatedClothesResponse>()
        assertEquals(CLOTHES_SEED.size, body.data.size)
        assertFalse(body.hasMore)
        assertNull(body.nextCursor)
    }

    @Test
    fun `GET clothes - cursor=128 returns only items with id greater than 128`() = appTest {
        val resp = jsonClient().get("/api/v1/clothes?limit=10&cursor=128") { bearerAuth(token) }
        assertEquals(HttpStatusCode.OK, resp.status)

        val body = resp.body<PaginatedClothesResponse>()
        assertTrue(body.data.all { it.id!! > 128 })
        assertEquals(listOf(129, 130, 131), body.data.map { it.id })
    }

    @Test
    fun `GET clothes - limit=200 is coerced to max 50`() {
        val bigSeed = (200..260).map { i ->
            Clothe(id = i, name = "item$i", imageUrl = "", storeUrl = "", category = "tops",
                material = "cotton", fit = "slim", season = "summer", styleTags = "casual",
                brand = null, colors = null)
        }
        val bigRepo = FakeClotheRepository()
        bigRepo.seed(TEST_USER_ID, *bigSeed.toTypedArray())

        testApplication {
            application { testClothesModule(clotheRepo = bigRepo) }
            val client = createClient { install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) } }
            val resp = client.get("/api/v1/clothes?limit=200") { bearerAuth(token) }
            assertEquals(HttpStatusCode.OK, resp.status)
            val body = resp.body<PaginatedClothesResponse>()
            assertTrue(body.data.size <= 50)
        }
    }

    // ========== Category filter ==========

    @Test
    fun `GET clothes - category=tops returns only tops`() = appTest {
        val resp = jsonClient().get("/api/v1/clothes?category=tops") { bearerAuth(token) }
        assertEquals(HttpStatusCode.OK, resp.status)

        val body = resp.body<PaginatedClothesResponse>()
        assertTrue(body.data.isNotEmpty())
        assertTrue(body.data.all { it.category == "tops" })
    }

    @Test
    fun `GET clothes - category=tops and category=pants applies OR logic`() = appTest {
        val resp = jsonClient().get("/api/v1/clothes?category=tops&category=pants") { bearerAuth(token) }
        assertEquals(HttpStatusCode.OK, resp.status)

        val body = resp.body<PaginatedClothesResponse>()
        assertTrue(body.data.isNotEmpty())
        assertTrue(body.data.all { it.category == "tops" || it.category == "pants" })
        val cats = body.data.map { it.category }.toSet()
        assertTrue("tops" in cats && "pants" in cats)
    }

    // ========== Season filter ==========

    @Test
    fun `GET clothes - season=summer returns only summer items`() = appTest {
        val resp = jsonClient().get("/api/v1/clothes?season=summer") { bearerAuth(token) }
        assertEquals(HttpStatusCode.OK, resp.status)

        val body = resp.body<PaginatedClothesResponse>()
        assertTrue(body.data.isNotEmpty())
        assertTrue(body.data.all { it.season == "summer" })
    }

    // ========== Material filter (LIKE) ==========

    @Test
    fun `GET clothes - material=cotton matches pure cotton and cotton+polyester`() = appTest {
        val resp = jsonClient().get("/api/v1/clothes?material=cotton") { bearerAuth(token) }
        assertEquals(HttpStatusCode.OK, resp.status)

        val body = resp.body<PaginatedClothesResponse>()
        val ids = body.data.map { it.id }.toSet()
        assertTrue(100 in ids, "Pure cotton item (id=100) must be included")
        assertTrue(101 in ids, "Mixed cotton item (id=101) must be included")
        assertFalse(ids.any { it in listOf(102, 103, 104) })
    }

    // ========== Fit filter ==========

    @Test
    fun `GET clothes - fit=slim returns only slim-fit items`() = appTest {
        val resp = jsonClient().get("/api/v1/clothes?fit=slim") { bearerAuth(token) }
        assertEquals(HttpStatusCode.OK, resp.status)

        val body = resp.body<PaginatedClothesResponse>()
        assertTrue(body.data.isNotEmpty())
        assertTrue(body.data.all { it.fit?.contains("slim", ignoreCase = true) == true })
    }

    // ========== Brand filter ==========

    @Test
    fun `GET clothes - brand=Nike returns only Nike items`() = appTest {
        val resp = jsonClient().get("/api/v1/clothes?brand=Nike") { bearerAuth(token) }
        assertEquals(HttpStatusCode.OK, resp.status)

        val body = resp.body<PaginatedClothesResponse>()
        assertTrue(body.data.isNotEmpty())
        assertTrue(body.data.all { it.brand == "Nike" })
    }

    // ========== Style filter (LIKE on styleTags) ==========

    @Test
    fun `GET clothes - style=casual returns items whose styleTags contain casual`() = appTest {
        val resp = jsonClient().get("/api/v1/clothes?style=casual") { bearerAuth(token) }
        assertEquals(HttpStatusCode.OK, resp.status)

        val body = resp.body<PaginatedClothesResponse>()
        assertTrue(body.data.isNotEmpty())
        assertTrue(body.data.all { it.styleTags?.contains("casual", ignoreCase = true) == true })
        assertFalse(body.data.any { it.id == 103 }, "Formal item (id=103) must be excluded")
    }

    // ========== Search query ==========

    @Test
    fun `GET clothes - q=shirt matches case-insensitively`() = appTest {
        val resp = jsonClient().get("/api/v1/clothes?q=shirt") { bearerAuth(token) }
        assertEquals(HttpStatusCode.OK, resp.status)

        val body = resp.body<PaginatedClothesResponse>()
        assertEquals(listOf(100), body.data.map { it.id })
    }

    @Test
    fun `GET clothes - q=SHIRT returns same result as lowercase`() = appTest {
        val client = jsonClient()
        val lower = client.get("/api/v1/clothes?q=shirt")  { bearerAuth(token) }.body<PaginatedClothesResponse>()
        val upper = client.get("/api/v1/clothes?q=SHIRT")  { bearerAuth(token) }.body<PaginatedClothesResponse>()
        assertEquals(lower.data.map { it.id }, upper.data.map { it.id })
    }

    @Test
    fun `GET clothes - q=nonexistent returns empty data`() = appTest {
        val resp = jsonClient().get("/api/v1/clothes?q=nonexistent") { bearerAuth(token) }
        assertEquals(HttpStatusCode.OK, resp.status)

        val body = resp.body<PaginatedClothesResponse>()
        assertTrue(body.data.isEmpty())
        assertFalse(body.hasMore)
        assertNull(body.nextCursor)
    }

    // ========== Color filter (in-memory Euclidean distance, applied in route) ==========

    @Test
    fun `GET clothes - white color filter returns only near-white items`() = appTest {
        // id=100: RGB(255,255,255) distance=0 → included
        // id=104: RGB(253,252,248) distance≈sqrt(4+9+49)≈7.9 → included within tolerance=30
        // id=101: RGB(0,0,0) distance≈441 → excluded
        val resp = jsonClient().get(
            "/api/v1/clothes?color_r=255&color_g=255&color_b=255&color_tolerance=30"
        ) { bearerAuth(token) }
        assertEquals(HttpStatusCode.OK, resp.status)

        val body = resp.body<PaginatedClothesResponse>()
        val ids = body.data.map { it.id }
        assertTrue(100 in ids)
        assertTrue(104 in ids)
        assertFalse(101 in ids)
        assertFalse(102 in ids)
    }

    @Test
    fun `GET clothes - partial color params without g and b means color filter is ignored`() = appTest {
        // parseColorParam returns null if any of r/g/b is missing → filter.color == null → no color filter
        val resp = jsonClient().get("/api/v1/clothes?limit=50&color_r=255") { bearerAuth(token) }
        assertEquals(HttpStatusCode.OK, resp.status)

        val body = resp.body<PaginatedClothesResponse>()
        assertEquals(CLOTHES_SEED.size, body.data.size)
    }

    // ========== Combined filters ==========

    @Test
    fun `GET clothes - category=tops and season=summer and q=shirt returns only matching item`() = appTest {
        val resp = jsonClient().get(
            "/api/v1/clothes?category=tops&season=summer&q=shirt"
        ) { bearerAuth(token) }
        assertEquals(HttpStatusCode.OK, resp.status)

        val body = resp.body<PaginatedClothesResponse>()
        assertEquals(listOf(100), body.data.map { it.id })
    }

    @Test
    fun `GET clothes - combined filter with no matching items returns empty data`() = appTest {
        // outerwear + summer: no item in seed has both
        val resp = jsonClient().get(
            "/api/v1/clothes?category=outerwear&season=summer"
        ) { bearerAuth(token) }
        assertEquals(HttpStatusCode.OK, resp.status)

        val body = resp.body<PaginatedClothesResponse>()
        assertTrue(body.data.isEmpty())
        assertFalse(body.hasMore)
        assertNull(body.nextCursor)
    }
}
