package com.example.clothes

import com.example.database.data.model.AvailableFiltersResponse
import com.example.testutils.*
import io.ktor.client.call.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.testing.*
import kotlinx.serialization.json.*
import kotlin.test.*

class ClothesFiltersRouteIntegrationTest {

    private lateinit var fakeRepo: FakeClotheRepository

    @BeforeTest
    fun setUp() {
        fakeRepo = FakeClotheRepository()
        fakeRepo.seed(TEST_USER_ID, *CLOTHES_SEED.toTypedArray())
    }

    private val token get() = TestJwtFactory.generateAccessToken(TEST_USER_ID)

    private fun withApp(block: suspend ApplicationTestBuilder.() -> Unit) = testApplication {
        application { testClothesModule(clotheRepo = fakeRepo) }
        block()
    }

    private fun ApplicationTestBuilder.jsonClient() = createClient {
        install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
    }

    // ========== Auth ==========

    @Test
    fun `GET clothes-filters - no auth token returns 401`() = withApp {
        val resp = jsonClient().get("/api/v1/clothes/filters")
        assertEquals(HttpStatusCode.Unauthorized, resp.status)
    }

    @Test
    fun `GET clothes-filters - returns 200 with valid token`() = withApp {
        val resp = jsonClient().get("/api/v1/clothes/filters") { bearerAuth(token) }
        assertEquals(HttpStatusCode.OK, resp.status)
    }

    // ========== Filter values ==========

    @Test
    fun `GET clothes-filters - categories are distinct and sorted`() = withApp {
        val body = jsonClient().get("/api/v1/clothes/filters") { bearerAuth(token) }
            .body<AvailableFiltersResponse>()
        assertEquals(listOf("outerwear", "pants", "tops"), body.categories)
    }

    @Test
    fun `GET clothes-filters - materials are split by plus sign and sorted`() = withApp {
        val body = jsonClient().get("/api/v1/clothes/filters") { bearerAuth(token) }
            .body<AvailableFiltersResponse>()
        // Seed: cotton, cotton + polyester, denim, wool, linen → split+trim+distinct+sort
        assertEquals(listOf("cotton", "denim", "linen", "polyester", "wool"), body.materials)
    }

    @Test
    fun `GET clothes-filters - fits are split by hyphen and sorted`() = withApp {
        val body = jsonClient().get("/api/v1/clothes/filters") { bearerAuth(token) }
            .body<AvailableFiltersResponse>()
        // Seed fits: slim, slim, regular, loose, relaxed (items 128-131 add more slim/regular/loose)
        assertEquals(listOf("loose", "regular", "relaxed", "slim"), body.fits)
    }

    @Test
    fun `GET clothes-filters - seasons are distinct and sorted`() = withApp {
        val body = jsonClient().get("/api/v1/clothes/filters") { bearerAuth(token) }
            .body<AvailableFiltersResponse>()
        assertEquals(listOf("all-season", "summer", "winter"), body.seasons)
    }

    @Test
    fun `GET clothes-filters - styles are split by comma and sorted`() = withApp {
        val body = jsonClient().get("/api/v1/clothes/filters") { bearerAuth(token) }
            .body<AvailableFiltersResponse>()
        // Seed styleTags: casual,minimalist / casual,streetwear / casual / formal,classic / boho,casual
        // items 128-131 add: casual / casual / boho / formal
        assertEquals(listOf("boho", "casual", "classic", "formal", "minimalist", "streetwear"), body.styles)
    }

    @Test
    fun `GET clothes-filters - brands exclude nulls and are sorted`() = withApp {
        val body = jsonClient().get("/api/v1/clothes/filters") { bearerAuth(token) }
            .body<AvailableFiltersResponse>()
        // Seed brands: Nike, Adidas, null, Zara, HM, Nike, null, HM, Zara → distinct non-null sorted
        assertEquals(listOf("Adidas", "HM", "Nike", "Zara"), body.brands)
    }

    @Test
    fun `GET clothes-filters - colors contains only items with non-null colors`() = withApp {
        val body = jsonClient().get("/api/v1/clothes/filters") { bearerAuth(token) }
            .body<AvailableFiltersResponse>()
        // Items 100-104 have colors; items 128-131 have null colors
        assertEquals(5, body.colors.size)
    }

    @Test
    fun `GET clothes-filters - empty wardrobe returns all empty lists`() {
        val emptyRepo = FakeClotheRepository()
        testApplication {
            application { testClothesModule(clotheRepo = emptyRepo) }
            val client = createClient { install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) } }
            val body = client.get("/api/v1/clothes/filters") { bearerAuth(token) }
                .body<AvailableFiltersResponse>()
            assertTrue(body.categories.isEmpty())
            assertTrue(body.materials.isEmpty())
            assertTrue(body.fits.isEmpty())
            assertTrue(body.seasons.isEmpty())
            assertTrue(body.styles.isEmpty())
            assertTrue(body.brands.isEmpty())
            assertTrue(body.colors.isEmpty())
        }
    }
}
