package com.example.auth

import com.example.auth.model.RefreshRequest
import com.example.auth.service.JWTConfig
import com.example.database.data.model.User
import com.example.testutils.FakeEmailService
import com.example.testutils.FakePasswordResetRepository
import com.example.testutils.InMemoryUserRepository
import com.example.testutils.bodyJson
import com.example.testutils.postJson
import com.example.testutils.postLogout
import com.example.testutils.postRefresh
import com.example.testutils.testAuthModule
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.plugins.ratelimit.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.testing.*
import kotlinx.serialization.json.jsonPrimitive
import org.mindrot.jbcrypt.BCrypt
import plugins.configureForwardedHeaders
import plugins.configureRateLimiting
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

/**
 * S-33.1: all buckets are registered by the production functions with the production limits
 * (EnvironmentConfig), so a 429 here is the 429 users get. Every request comes from one IP,
 * like hundreds of mobile users behind one CGNAT address.
 */
class AuthRateLimitTest {

    private val authLimit = EnvironmentConfig.rateLimitAuth
    private val refreshLimit = EnvironmentConfig.rateLimitRefresh
    private val registrationLimit = EnvironmentConfig.rateLimitRegistration
    private val userRepo = InMemoryUserRepository()
    private val emailService = FakeEmailService()

    private fun ApplicationTestBuilder.productionLimits() = application {
        testAuthModule(
            passwordResetRepo = FakePasswordResetRepository(),
            userRepo = userRepo,
            emailService = emailService,
            authRateLimit = authLimit,
            refreshRateLimit = refreshLimit,
            registrationRateLimit = registrationLimit
        )
    }

    private fun seededUser(userId: Int): User =
        User(userId = userId, username = "user$userId", email = "user$userId@example.com", passwordHash = "hash", gender = null)
            .also { userRepo.seed(it) }

    private fun refreshTokenOf(user: User): String = JWTConfig.generateTokenPair(user).refreshToken

    private suspend fun ApplicationTestBuilder.login() =
        postJson("/login", """{"login":"victim@example.com","password":"Guess1234"}""")

    private suspend fun ApplicationTestBuilder.exhaustLoginBucket() {
        repeat(authLimit) { login() }
        assertEquals(HttpStatusCode.TooManyRequests, login().status, "precondition: login bucket is exhausted")
    }

    /** Only the production plugins plus a handler that reads the body again, as /refresh does. */
    private fun Application.productionRateLimitingWithRefreshProbe() {
        install(ContentNegotiation) { json() }
        configureForwardedHeaders()
        configureRateLimiting()
        routing {
            rateLimit(RateLimitName("refresh")) {
                post("/probe") {
                    call.receive<RefreshRequest>()
                    call.respond(HttpStatusCode.OK)
                }
            }
        }
    }

    private suspend fun ApplicationTestBuilder.startRegistration(email: String) =
        postJson("/auth/registration/start", """{"email":"$email"}""")

    @Test
    fun `refresh for many users behind one IP is never throttled`() = testApplication {
        productionLimits()
        val users = (1..maxOf(authLimit, refreshLimit) + 1).map(::seededUser)

        users.forEach { user -> assertEquals(HttpStatusCode.OK, postRefresh(refreshTokenOf(user)).status) }
    }

    @Test
    fun `login beyond the auth limit from one IP gets 429`() = testApplication {
        productionLimits()
        repeat(authLimit) { assertEquals(HttpStatusCode.Unauthorized, login().status) }

        assertEquals(HttpStatusCode.TooManyRequests, login().status)
    }

    @Test
    fun `one user's refresh beyond the refresh limit gets 429`() = testApplication {
        productionLimits()
        val refreshToken = refreshTokenOf(seededUser(1))
        repeat(refreshLimit) { assertNotEquals(HttpStatusCode.TooManyRequests, postRefresh(refreshToken).status) }

        assertEquals(HttpStatusCode.TooManyRequests, postRefresh(refreshToken).status)
    }

    @Test
    fun `garbage refresh tokens are throttled per IP without touching real users`() = testApplication {
        productionLimits()
        repeat(refreshLimit) { attempt -> assertEquals(HttpStatusCode.Unauthorized, postRefresh("garbage-$attempt").status) }
        assertEquals(HttpStatusCode.TooManyRequests, postRefresh("garbage-more").status)

        assertEquals(HttpStatusCode.OK, postRefresh(refreshTokenOf(seededUser(1))).status)
    }

    @Test
    fun `old tokens replayed after a password reset do not throttle the new session`() = testApplication {
        productionLimits()
        val email = "victim@example.com"
        userRepo.seed(User(userId = 1, username = "victim", email = email, passwordHash = BCrypt.hashpw("OldPass12", BCrypt.gensalt(4)), gender = null))
        val stolen = postJson("/login", """{"login":"$email","password":"OldPass12"}""").bodyJson().getValue("refreshToken").jsonPrimitive.content
        postJson("/auth/forgot-password", """{"email":"$email"}""")
        val code = emailService.sentCodes().last().second
        postJson("/auth/reset-password", """{"email":"$email","code":"$code","newPassword":"NewPass12"}""")
        val fresh = postJson("/login", """{"login":"$email","password":"NewPass12"}""").bodyJson().getValue("refreshToken").jsonPrimitive.content
        repeat(refreshLimit) { assertEquals(HttpStatusCode.Unauthorized, postRefresh(stolen).status) }
        assertEquals(HttpStatusCode.TooManyRequests, postRefresh(stolen).status, "precondition: the thief drained the old-version bucket")

        assertEquals(HttpStatusCode.OK, postRefresh(fresh).status)
    }

    @Test
    fun `exhausted login bucket does not block refresh or logout`() = testApplication {
        productionLimits()
        val user = seededUser(1)
        exhaustLoginBucket()

        assertEquals(HttpStatusCode.OK, postRefresh(refreshTokenOf(user)).status)
        assertEquals(HttpStatusCode.NoContent, postLogout(refreshTokenOf(user)).status)
    }

    @Test
    fun `password recovery stays in the login bucket`() = testApplication {
        productionLimits()
        exhaustLoginBucket()

        val forgot = postJson("/auth/forgot-password", """{"email":"victim@example.com"}""")
        val reset = postJson("/auth/reset-password", """{"email":"victim@example.com","code":"123456","newPassword":"NewPass123"}""")

        assertEquals(HttpStatusCode.TooManyRequests, forgot.status)
        assertEquals(HttpStatusCode.TooManyRequests, reset.status)
    }

    @Test
    fun `signups behind one IP do not consume the login budget`() = testApplication {
        productionLimits()
        repeat(authLimit + 1) { index -> assertEquals(HttpStatusCode.OK, startRegistration("new$index@example.com").status) }

        assertEquals(HttpStatusCode.Unauthorized, login().status)
    }

    @Test
    fun `exhausted login bucket does not block registration`() = testApplication {
        productionLimits()
        exhaustLoginBucket()

        assertEquals(HttpStatusCode.OK, startRegistration("new@example.com").status)
    }

    @Test
    fun `registration beyond its limit from one IP gets 429`() = testApplication {
        productionLimits()
        repeat(registrationLimit) { index -> startRegistration("new$index@example.com") }

        assertEquals(HttpStatusCode.TooManyRequests, postJson("/register", """{"email":"late@example.com","password":"SecurePass1"}""").status)
    }

    @Test
    fun `production rate limiting keys refresh by user and keeps the body readable for the handler`() = testApplication {
        application { productionRateLimitingWithRefreshProbe() }
        suspend fun probe(user: User) = client.post("/probe") {
            contentType(ContentType.Application.Json)
            setBody("""{"refreshToken":"${refreshTokenOf(user)}"}""")
        }
        val throttledUser = seededUser(1)
        repeat(refreshLimit) { assertEquals(HttpStatusCode.OK, probe(throttledUser).status) }
        assertEquals(HttpStatusCode.TooManyRequests, probe(throttledUser).status)

        assertEquals(HttpStatusCode.OK, probe(seededUser(2)).status, "another user on the same IP has its own bucket")
    }
}
