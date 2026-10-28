package com.example.auth

import com.example.auth.service.JWTConfig
import com.example.database.data.model.PendingEmailVerification
import com.example.database.data.model.User
import com.example.routes.configureSecurity
import com.example.testutils.FakeEmailService
import com.example.testutils.FakePasswordResetRepository
import com.example.testutils.FakePendingEmailVerificationRepository
import com.example.testutils.InMemoryUserRepository
import com.example.testutils.assertSessionResponse
import com.example.testutils.bodyJson
import com.example.testutils.postJson
import com.example.testutils.testAuthModule
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.server.auth.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.testing.*
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.security.MessageDigest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class RegistrationFlowTest {

    private val email = "new.user@example.com"
    private val password = "SecurePass1"
    private val userRepo = InMemoryUserRepository()
    private val pendingRepo = FakePendingEmailVerificationRepository()
    private val emailService = FakeEmailService()

    private fun ApplicationTestBuilder.module() = application {
        testAuthModule(
            passwordResetRepo = FakePasswordResetRepository(),
            userRepo = userRepo,
            emailService = emailService,
            pendingEmailVerificationRepo = pendingRepo
        )
    }

    private suspend fun ApplicationTestBuilder.start(forEmail: String = email) =
        postJson("/auth/registration/start", """{"email":"$forEmail"}""")

    private suspend fun ApplicationTestBuilder.verify(code: String, forEmail: String = email) =
        postJson("/auth/registration/verify", """{"email":"$forEmail","code":"$code"}""")

    private suspend fun ApplicationTestBuilder.register(verificationToken: String?, forEmail: String = email): HttpResponse {
        val tokenField = verificationToken?.let { ""","verificationToken":"$it"""" } ?: ""
        return postJson("/register", """{"email":"$forEmail","password":"$password","gender":"OTHER"$tokenField}""")
    }

    private fun registerBody(
        verificationToken: String,
        forEmail: String = email,
        password: String = this.password,
        gender: String = "OTHER",
        username: String? = null
    ): String {
        val usernameField = username?.let { ""","username":"$it"""" } ?: ""
        return """{"email":"$forEmail","password":"$password","gender":"$gender","verificationToken":"$verificationToken"$usernameField}"""
    }

    private fun sentCode(forEmail: String = email): String =
        emailService.sentVerifications().last { it.first == forEmail }.second

    private fun wrongCodeFor(code: String) = if (code == "000000") "111111" else "000000"

    private suspend fun ApplicationTestBuilder.verifiedToken(forEmail: String = email): String {
        start(forEmail)
        val response = verify(sentCode(forEmail), forEmail)
        assertEquals(HttpStatusCode.OK, response.status)
        return Json.parseToJsonElement(response.bodyAsText()).jsonObject.getValue("verificationToken").jsonPrimitive.content
    }

    private fun sha256(input: String): String =
        MessageDigest.getInstance("SHA-256").digest(input.toByteArray()).joinToString("") { "%02x".format(it) }

    private fun existingUser(userEmail: String) = User(
        userId = 7,
        username = "existing",
        email = userEmail,
        passwordHash = "hash",
        gender = null
    )

    // ---- start ----

    @Test
    fun `start sends a code to the email and creates no user`() = testApplication {
        module()

        val response = start()

        assertEquals(HttpStatusCode.OK, response.status)
        assertTrue(response.bodyJson().getValue("message").jsonPrimitive.content.isNotBlank())
        assertEquals(6, sentCode().length)
        assertNull(userRepo.findUserByEmail(email), "no account may exist before registration is completed")
    }

    @Test
    fun `start for an email that already has an account returns 409`() = testApplication {
        userRepo.seed(existingUser(email))
        module()

        assertEquals(HttpStatusCode.Conflict, start().status)
        assertTrue(emailService.sentVerifications().isEmpty())
    }

    @Test
    fun `second start within the cooldown returns 429`() = testApplication {
        module()
        start()

        assertEquals(HttpStatusCode.TooManyRequests, start().status)
    }

    @Test
    fun `start after the cooldown sends a fresh code`() = testApplication {
        val oldCreatedAt = System.currentTimeMillis() - 61_000L
        pendingRepo.seed(
            PendingEmailVerification(1, email, sha256("123456"), oldCreatedAt + 900_000L, 0, oldCreatedAt)
        )
        module()

        assertEquals(HttpStatusCode.OK, start().status)
        assertEquals(1, emailService.sentVerifications().size)
    }

    @Test
    fun `start with a malformed email returns 400`() = testApplication {
        module()

        assertEquals(HttpStatusCode.BadRequest, start("not-an-email").status)
    }

    // ---- verify ----

    @Test
    fun `verify with a wrong code returns 400`() = testApplication {
        module()
        start()

        assertEquals(HttpStatusCode.BadRequest, verify(wrongCodeFor(sentCode())).status)
    }

    @Test
    fun `verify with an expired code returns 400`() = testApplication {
        val now = System.currentTimeMillis()
        pendingRepo.seed(PendingEmailVerification(1, email, sha256("123456"), now - 1_000L, 0, now - 900_000L))
        module()

        assertEquals(HttpStatusCode.BadRequest, verify("123456").status)
    }

    @Test
    fun `fifth wrong attempt invalidates the code even if the right one is sent next`() = testApplication {
        module()
        start()
        val code = sentCode()

        repeat(5) { assertEquals(HttpStatusCode.BadRequest, verify(wrongCodeFor(code)).status) }

        assertEquals(HttpStatusCode.BadRequest, verify(code).status)
        assertNull(pendingRepo.get(email), "code must be burnt once its attempts are used up")
    }

    @Test
    fun `code whose attempts are used up is rejected even when it is the right one`() = testApplication {
        val now = System.currentTimeMillis()
        pendingRepo.seed(PendingEmailVerification(1, email, sha256("123456"), now + 900_000L, 5, now))
        module()

        assertEquals(HttpStatusCode.BadRequest, verify("123456").status)
        assertNull(pendingRepo.get(email), "exhausted code must be removed")
    }

    @Test
    fun `every verify attempt is counted, right or wrong`() = testApplication {
        module()
        start()
        val code = sentCode()

        verify(wrongCodeFor(code))
        verify(wrongCodeFor(code))

        assertEquals(2, pendingRepo.get(email)?.attempts)
    }

    @Test
    fun `fourth wrong attempt still leaves the right code usable`() = testApplication {
        module()
        start()
        val code = sentCode()

        repeat(4) { verify(wrongCodeFor(code)) }

        assertEquals(HttpStatusCode.OK, verify(code).status)
    }

    @Test
    fun `verify with the right code returns a registration token for that email and consumes the code`() = testApplication {
        module()

        val token = verifiedToken()

        assertEquals(email, JWTConfig.verifyRegistrationToken(token))
        assertNull(pendingRepo.get(email))
    }

    // ---- register ----

    @Test
    fun `register without a verification token returns 400 and creates no user`() = testApplication {
        module()

        assertEquals(HttpStatusCode.BadRequest, register(verificationToken = null).status)
        assertNull(userRepo.findUserByEmail(email))
    }

    @Test
    fun `register with a token issued for another email returns 400`() = testApplication {
        module()
        val tokenForSomeoneElse = verifiedToken("someone.else@example.com")

        assertEquals(HttpStatusCode.BadRequest, register(tokenForSomeoneElse).status)
        assertNull(userRepo.findUserByEmail(email))
    }

    @Test
    fun `register with an access token instead of a registration token returns 400`() = testApplication {
        module()
        val accessToken = JWTConfig.generateTokenPair(existingUser(email)).accessToken

        assertEquals(HttpStatusCode.BadRequest, register(accessToken).status)
        assertNull(userRepo.findUserByEmail(email))
    }

    @Test
    fun `register with a valid token creates a verified account and returns session tokens`() = testApplication {
        module()
        val token = verifiedToken()

        val response = register(token)

        val created = assertNotNull(userRepo.findUserByEmail(email))
        response.assertSessionResponse(HttpStatusCode.Created, created.userId)
        assertTrue(created.emailVerified, "account must be created already email-verified")
        assertEquals(0, userRepo.setEmailVerifiedCalls, "verified flag must be set by the same insert, not a second write")
        assertTrue(created.isPro, "registration trial must still be granted")
    }

    @Test
    fun `register with a username that is already taken returns 409`() = testApplication {
        userRepo.seed(existingUser("other@example.com"))
        module()
        val token = verifiedToken()

        val response = postJson("/register", registerBody(verificationToken = token, username = "existing"))

        assertEquals(HttpStatusCode.Conflict, response.status)
        assertNull(userRepo.findUserByEmail(email))
    }

    @Test
    fun `register with a malformed email returns 400 even with a matching token`() = testApplication {
        module()
        val malformedEmail = "not-an-email"
        val token = JWTConfig.generateRegistrationToken(malformedEmail)

        val response = postJson("/register", registerBody(verificationToken = token, forEmail = malformedEmail))

        assertEquals(HttpStatusCode.BadRequest, response.status)
        assertNull(userRepo.findUserByEmail(malformedEmail))
    }

    @Test
    fun `register with a weak password returns 400`() = testApplication {
        module()

        val response = postJson("/register", registerBody(verificationToken = verifiedToken(), password = "weakpass"))

        assertEquals(HttpStatusCode.BadRequest, response.status)
        assertNull(userRepo.findUserByEmail(email))
    }

    @Test
    fun `register with an unknown gender returns 400`() = testApplication {
        module()

        val response = postJson("/register", registerBody(verificationToken = verifiedToken(), gender = "ROBOT"))

        assertEquals(HttpStatusCode.BadRequest, response.status)
        assertNull(userRepo.findUserByEmail(email))
    }

    @Test
    fun `registered user can log in with the chosen password`() = testApplication {
        module()
        register(verifiedToken())

        val response = postJson("/login", """{"login":"$email","password":"$password"}""")

        assertEquals(HttpStatusCode.OK, response.status)
    }

    @Test
    fun `reusing the token after registration returns 409`() = testApplication {
        module()
        val token = verifiedToken()
        register(token)

        assertEquals(HttpStatusCode.Conflict, register(token).status)
    }

    // ---- token scope ----

    @Test
    fun `registration token is rejected by authenticate while an access token passes`() = testApplication {
        application {
            configureSecurity()
            routing {
                authenticate {
                    get("/probe") { call.respondText("ok") }
                }
            }
        }
        val accessToken = JWTConfig.generateTokenPair(existingUser(email)).accessToken
        val registrationToken = JWTConfig.generateRegistrationToken(email)

        val withAccess = client.get("/probe") { bearerAuth(accessToken) }
        val withRegistration = client.get("/probe") { bearerAuth(registrationToken) }

        assertEquals(HttpStatusCode.OK, withAccess.status)
        assertEquals(HttpStatusCode.Unauthorized, withRegistration.status)
    }

    @Test
    fun `registration token is rejected as a refresh token`() {
        val registrationToken = JWTConfig.generateRegistrationToken(email)

        assertNull(JWTConfig.verifyRefreshToken(registrationToken))
    }
}
