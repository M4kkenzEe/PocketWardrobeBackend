package com.example.admin

import com.example.database.data.model.User
import com.example.database.domain.model.UserWithClothesCount
import com.example.testutils.FakeEmailService
import com.example.testutils.InMemoryUserRepository
import com.example.testutils.testAdminModule
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.server.testing.*
import kotlinx.datetime.Clock
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.days

class AdminUsersRouteTest {

    private val secret = "test-admin-secret"
    private val validBody = """{"email":"tester@example.com","password":"Passw0rdOk"}"""

    private fun legacyUser(id: Int, email: String, proUntil: kotlinx.datetime.Instant? = null) = User(
        userId = id,
        username = "user$id",
        email = email,
        passwordHash = "secret-hash-$id",
        gender = null,
        proUntil = proUntil,
        createdAt = null
    )

    private fun ApplicationTestBuilder.setUp(
        userRepo: InMemoryUserRepository = InMemoryUserRepository(),
        adminSecret: String? = secret,
        emailService: FakeEmailService = FakeEmailService()
    ) {
        application { testAdminModule(userRepo, adminSecret, emailService) }
    }

    private suspend fun HttpResponse.json() = Json.parseToJsonElement(bodyAsText())

    // --- access control ---------------------------------------------------------------------

    @Test
    fun `GET without secret header returns 403`() = testApplication {
        setUp()
        assertEquals(HttpStatusCode.Forbidden, client.get("/admin/users").status)
    }

    @Test
    fun `GET with wrong secret returns 403`() = testApplication {
        setUp()
        val response = client.get("/admin/users") { header("X-Admin-Secret", "wrong") }
        assertEquals(HttpStatusCode.Forbidden, response.status)
    }

    @Test
    fun `POST without secret header returns 403 and creates nothing`() = testApplication {
        val userRepo = InMemoryUserRepository()
        setUp(userRepo)

        val response = client.post("/admin/users") {
            contentType(ContentType.Application.Json)
            setBody(validBody)
        }

        assertEquals(HttpStatusCode.Forbidden, response.status)
        assertTrue(userRepo.findAllWithClothesCount().isEmpty())
    }

    @Test
    fun `POST with wrong secret returns 403`() = testApplication {
        setUp()
        val response = client.post("/admin/users") {
            header("X-Admin-Secret", "wrong")
            contentType(ContentType.Application.Json)
            setBody(validBody)
        }
        assertEquals(HttpStatusCode.Forbidden, response.status)
    }

    @Test
    fun `GET returns 503 when admin secret is not configured`() = testApplication {
        setUp(adminSecret = null)
        assertEquals(HttpStatusCode.ServiceUnavailable, client.get("/admin/users").status)
    }

    @Test
    fun `POST returns 503 when admin secret is not configured even if a header is sent`() = testApplication {
        setUp(adminSecret = null)
        val response = client.post("/admin/users") {
            header("X-Admin-Secret", secret)
            contentType(ContentType.Application.Json)
            setBody(validBody)
        }
        assertEquals(HttpStatusCode.ServiceUnavailable, response.status)
    }

    // --- GET /admin/users -------------------------------------------------------------------

    @Test
    fun `GET returns users with id email createdAt isPro and clothesCount`() = testApplication {
        val userRepo = InMemoryUserRepository()
        userRepo.seed(legacyUser(1, "old@example.com"), clothesCount = 3)
        userRepo.seed(legacyUser(2, "pro@example.com", proUntil = Clock.System.now().plus(5.days)), clothesCount = 0)
        setUp(userRepo)

        val response = client.get("/admin/users") { header("X-Admin-Secret", secret) }

        assertEquals(HttpStatusCode.OK, response.status)
        val users = response.json().jsonArray
        assertEquals(2, users.size)
        val legacy = users[0].jsonObject
        assertEquals(1, legacy.getValue("id").jsonPrimitive.int)
        assertEquals("old@example.com", legacy.getValue("email").jsonPrimitive.content)
        assertEquals(JsonNull, legacy.getValue("createdAt"))
        assertFalse(legacy.getValue("isPro").jsonPrimitive.boolean)
        assertEquals(3, legacy.getValue("clothesCount").jsonPrimitive.int)
    }

    @Test
    fun `GET returns legacy user with null email as null in json`() = testApplication {
        val userRepo = InMemoryUserRepository()
        userRepo.seedUserWithoutEmail(
            UserWithClothesCount(
                userId = 7,
                email = null,
                createdAt = null,
                isPro = false,
                proUntil = null,
                clothesCount = 2
            )
        )
        userRepo.seed(legacyUser(1, "old@example.com"))
        setUp(userRepo)

        val response = client.get("/admin/users") { header("X-Admin-Secret", secret) }

        assertEquals(HttpStatusCode.OK, response.status)
        val users = response.json().jsonArray
        assertEquals(2, users.size)
        val withoutEmail = users[1].jsonObject
        assertEquals(7, withoutEmail.getValue("id").jsonPrimitive.int)
        assertEquals(JsonNull, withoutEmail.getValue("email"))
        assertEquals(2, withoutEmail.getValue("clothesCount").jsonPrimitive.int)
    }

    @Test
    fun `GET reports isPro true for an active pro trial`() = testApplication {
        val userRepo = InMemoryUserRepository()
        userRepo.seed(legacyUser(1, "pro@example.com", proUntil = Clock.System.now().plus(5.days)))
        setUp(userRepo)

        val users = client.get("/admin/users") { header("X-Admin-Secret", secret) }.json().jsonArray

        assertTrue(users[0].jsonObject.getValue("isPro").jsonPrimitive.boolean)
    }

    @Test
    fun `GET never exposes password hash`() = testApplication {
        val userRepo = InMemoryUserRepository()
        userRepo.seed(legacyUser(1, "old@example.com"))
        setUp(userRepo)

        val body = client.get("/admin/users") { header("X-Admin-Secret", secret) }.bodyAsText()

        assertFalse(body.contains("secret-hash-1"))
        assertFalse(body.contains("passwordHash", ignoreCase = true))
        assertFalse(body.contains("password", ignoreCase = true))
    }

    // --- POST /admin/users ------------------------------------------------------------------

    @Test
    fun `POST creates user and returns 201 with list item shape`() = testApplication {
        setUp()

        val response = client.post("/admin/users") {
            header("X-Admin-Secret", secret)
            contentType(ContentType.Application.Json)
            setBody(validBody)
        }

        assertEquals(HttpStatusCode.Created, response.status)
        val created = response.json().jsonObject
        assertEquals(setOf("id", "email", "createdAt", "isPro", "clothesCount"), created.keys)
        assertEquals("tester@example.com", created.getValue("email").jsonPrimitive.content)
        assertNotNull(created.getValue("createdAt").jsonPrimitive.content)
        assertFalse(created.getValue("isPro").jsonPrimitive.boolean)
        assertEquals(0, created.getValue("clothesCount").jsonPrimitive.int)
    }

    @Test
    fun `POST response never contains password or hash`() = testApplication {
        setUp()

        val body = client.post("/admin/users") {
            header("X-Admin-Secret", secret)
            contentType(ContentType.Application.Json)
            setBody(validBody)
        }.bodyAsText()

        assertFalse(body.contains("Passw0rdOk"))
        assertFalse(body.contains("password", ignoreCase = true))
    }

    @Test
    fun `POST creates verified user without trial`() = testApplication {
        val userRepo = InMemoryUserRepository()
        setUp(userRepo)

        client.post("/admin/users") {
            header("X-Admin-Secret", secret)
            contentType(ContentType.Application.Json)
            setBody(validBody)
        }

        val stored = userRepo.findUserByEmail("tester@example.com")
        assertNotNull(stored)
        assertTrue(stored.emailVerified)
        assertFalse(stored.isPro)
        assertEquals(null, stored.proUntil)
    }

    @Test
    fun `POST does not send a verification email`() = testApplication {
        val emailService = FakeEmailService()
        setUp(emailService = emailService)

        client.post("/admin/users") {
            header("X-Admin-Secret", secret)
            contentType(ContentType.Application.Json)
            setBody(validBody)
        }

        assertTrue(emailService.sentVerifications().isEmpty())
    }

    @Test
    fun `POST with duplicate email returns 409`() = testApplication {
        val userRepo = InMemoryUserRepository()
        userRepo.seed(legacyUser(1, "tester@example.com"))
        setUp(userRepo)

        val response = client.post("/admin/users") {
            header("X-Admin-Secret", secret)
            contentType(ContentType.Application.Json)
            setBody(validBody)
        }

        assertEquals(HttpStatusCode.Conflict, response.status)
    }

    @Test
    fun `POST with invalid email returns 400`() = testApplication {
        setUp()
        val response = client.post("/admin/users") {
            header("X-Admin-Secret", secret)
            contentType(ContentType.Application.Json)
            setBody("""{"email":"not-an-email","password":"Passw0rdOk"}""")
        }
        assertEquals(HttpStatusCode.BadRequest, response.status)
    }

    @Test
    fun `POST with weak password returns 400`() = testApplication {
        setUp()
        val response = client.post("/admin/users") {
            header("X-Admin-Secret", secret)
            contentType(ContentType.Application.Json)
            setBody("""{"email":"tester@example.com","password":"weak"}""")
        }
        assertEquals(HttpStatusCode.BadRequest, response.status)
    }

    @Test
    fun `created user appears in the list with a createdAt date`() = testApplication {
        setUp()
        client.post("/admin/users") {
            header("X-Admin-Secret", secret)
            contentType(ContentType.Application.Json)
            setBody(validBody)
        }

        val users: JsonArray = client.get("/admin/users") { header("X-Admin-Secret", secret) }.json().jsonArray

        assertEquals(1, users.size)
        val createdAt = users[0].jsonObject.getValue("createdAt")
        assertTrue(createdAt !is JsonNull)
    }

    @Test
    fun `created user can log in through the public login endpoint`() = testApplication {
        setUp()
        client.post("/admin/users") {
            header("X-Admin-Secret", secret)
            contentType(ContentType.Application.Json)
            setBody(validBody)
        }

        val login = client.post("/api/v1/login") {
            contentType(ContentType.Application.Json)
            setBody("""{"login":"tester@example.com","password":"Passw0rdOk"}""")
        }

        assertEquals(HttpStatusCode.OK, login.status)
        assertTrue((login.json() as JsonObject).containsKey("accessToken"))
    }
}
