package com.example.admin

import com.example.testutils.InMemoryUserRepository
import com.example.testutils.testAdminModule
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.server.testing.*
import plugins.configureCors
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * The admin console is opened from file://, so browsers send "Origin: null".
 * Assumes CORS_ALLOWED_ORIGINS is unset or "*" (the default).
 */
class AdminCorsTest {

    private suspend fun ApplicationTestBuilder.preflight(origin: String) =
        client.options("/admin/users") {
            header(HttpHeaders.Origin, origin)
            header(HttpHeaders.AccessControlRequestMethod, "POST")
            header(HttpHeaders.AccessControlRequestHeaders, "x-admin-secret,content-type")
        }

    private fun ApplicationTestBuilder.setUp() {
        application {
            configureCors()
            testAdminModule(InMemoryUserRepository(), adminSecret = "test-admin-secret")
        }
    }

    @Test
    fun `preflight from a regular origin allows the admin secret header`() = testApplication {
        setUp()

        val response = preflight("http://localhost:3000")

        assertEquals(HttpStatusCode.OK, response.status)
        assertTrue(response.headers[HttpHeaders.AccessControlAllowHeaders].orEmpty().contains("X-Admin-Secret", ignoreCase = true))
    }

    @Test
    fun `preflight from file origin null is accepted`() = testApplication {
        setUp()

        val response = preflight("null")

        assertEquals(HttpStatusCode.OK, response.status)
        assertNotNull(response.headers[HttpHeaders.AccessControlAllowOrigin])
        assertTrue(response.headers[HttpHeaders.AccessControlAllowHeaders].orEmpty().contains("X-Admin-Secret", ignoreCase = true))
    }

    @Test
    fun `actual request from file origin null gets an allow-origin header`() = testApplication {
        setUp()

        val response = client.get("/admin/users") {
            header(HttpHeaders.Origin, "null")
            header("X-Admin-Secret", "test-admin-secret")
        }

        assertEquals(HttpStatusCode.OK, response.status)
        assertNotNull(response.headers[HttpHeaders.AccessControlAllowOrigin])
    }
}
