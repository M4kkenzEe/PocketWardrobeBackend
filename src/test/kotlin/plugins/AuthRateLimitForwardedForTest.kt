package plugins

import com.example.auth.EnvironmentConfig
import com.example.testutils.FakePasswordResetRepository
import com.example.testutils.testAuthModule
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.server.plugins.ratelimit.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.testing.*
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

/**
 * EPIC-39: nginx appends the real client IP to whatever X-Forwarded-For the client sent,
 * so only the last value can be trusted as the rate limit key.
 */
class AuthRateLimitForwardedForTest {

    private val limit = 3
    private val realClientIp = "203.0.113.7"

    private fun ApplicationTestBuilder.module() = application {
        testAuthModule(passwordResetRepo = FakePasswordResetRepository(), authRateLimit = limit)
    }

    private suspend fun ApplicationTestBuilder.login(forwardedFor: String? = null): HttpResponse =
        client.post("/api/v1/login") {
            contentType(ContentType.Application.Json)
            forwardedFor?.let { header(HttpHeaders.XForwardedFor, it) }
            setBody("""{"login":"victim@example.com","password":"Guess1234"}""")
        }

    private fun spoofed(attempt: Int, realIp: String = realClientIp) = "$attempt.$attempt.$attempt.$attempt, $realIp"

    @Test
    fun `spoofed first X-Forwarded-For values on login share the real client's bucket`() = testApplication {
        module()
        repeat(limit) { attempt -> assertNotEquals(HttpStatusCode.TooManyRequests, login(spoofed(attempt + 1)).status) }

        val response = login(spoofed(limit + 1))

        assertEquals(HttpStatusCode.TooManyRequests, response.status)
    }

    @Test
    fun `another real client behind nginx keeps its own bucket`() = testApplication {
        module()
        repeat(limit + 1) { attempt -> login(spoofed(attempt + 1)) }

        val response = login(spoofed(attempt = 1, realIp = "198.51.100.9"))

        assertNotEquals(HttpStatusCode.TooManyRequests, response.status)
    }

    @Test
    fun `without X-Forwarded-For the bucket is the connection address`() = testApplication {
        module()
        repeat(limit) { login() }

        assertEquals(HttpStatusCode.TooManyRequests, login().status)
        assertNotEquals(HttpStatusCode.TooManyRequests, login(spoofed(attempt = 1)).status, "A forwarded client is a different bucket")
    }

    @Test
    fun `spoofed X-Forwarded-For does not bypass the limit on register`() = testApplication {
        module()
        suspend fun register(attempt: Int) = client.post("/api/v1/register") {
            contentType(ContentType.Application.Json)
            header(HttpHeaders.XForwardedFor, spoofed(attempt))
            setBody("""{"email":"new@example.com","password":"SecurePass1"}""")
        }
        repeat(limit) { attempt -> register(attempt + 1) }

        assertEquals(HttpStatusCode.TooManyRequests, register(limit + 1).status)
    }

    @Test
    fun `production rate limiting applies the configured auth limit per real client`() = testApplication {
        application {
            configureForwardedHeaders()
            configureRateLimiting()
            routing { rateLimit(RateLimitName("auth")) { post("/probe") { call.respond(HttpStatusCode.OK) } } }
        }
        suspend fun probe(attempt: Int) = client.post("/probe") { header(HttpHeaders.XForwardedFor, spoofed(attempt)) }
        repeat(EnvironmentConfig.rateLimitAuth) { attempt -> assertEquals(HttpStatusCode.OK, probe(attempt + 1).status) }

        assertEquals(HttpStatusCode.TooManyRequests, probe(EnvironmentConfig.rateLimitAuth + 1).status)
    }
}
