package com.example.auth

import com.example.database.data.model.PasswordResetCode
import com.example.database.data.model.User
import com.example.testutils.FakeEmailService
import com.example.testutils.FakePasswordResetRepository
import com.example.testutils.InMemoryUserRepository
import com.example.testutils.testAuthModule
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.server.testing.*
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.mindrot.jbcrypt.BCrypt
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Route-level recovery flow: POST /auth/forgot-password → code from the email → POST /auth/reset-password. */
class PasswordResetFlowTest {

    private val storedEmail = "owner@example.com"
    private val newPassword = "Qwerty1234!"
    private val userRepo = InMemoryUserRepository().apply {
        seed(User(userId = 1, username = "owner", email = storedEmail, passwordHash = BCrypt.hashpw("OldPass12", BCrypt.gensalt(4)), gender = null))
    }
    private val resetRepo = FakePasswordResetRepository()
    private val emailService = FakeEmailService()

    private fun ApplicationTestBuilder.module() = application {
        testAuthModule(passwordResetRepo = resetRepo, userRepo = userRepo, emailService = emailService)
    }

    private suspend fun ApplicationTestBuilder.postJson(path: String, body: String): HttpResponse =
        client.post("/api/v1$path") {
            contentType(ContentType.Application.Json)
            setBody(body)
        }

    private suspend fun ApplicationTestBuilder.forgot(email: String = storedEmail) =
        postJson("/auth/forgot-password", """{"email":"$email"}""")

    private suspend fun ApplicationTestBuilder.reset(code: String, email: String = storedEmail, password: String = newPassword) =
        postJson("/auth/reset-password", """{"email":"$email","code":"$code","newPassword":"$password"}""")

    private fun lastSentCode(): String = emailService.sentCodes().last().second

    private fun errorCode(response: HttpResponse, body: String): String {
        assertEquals(HttpStatusCode.BadRequest, response.status)
        return Json.parseToJsonElement(body).jsonObject.getValue("error").jsonPrimitive.content
    }

    @Test
    fun `forgot then reset with the emailed code returns a session and the new password logs in`() = testApplication {
        module()

        assertEquals(HttpStatusCode.OK, forgot().status)
        val response = reset(lastSentCode())

        assertEquals(HttpStatusCode.OK, response.status)
        assertTrue(response.bodyAsText().contains("accessToken"))
        val login = postJson("/login", """{"login":"$storedEmail","password":"$newPassword"}""")
        assertEquals(HttpStatusCode.OK, login.status)
    }

    @Test
    fun `email with surrounding spaces and other letter case finds the account in both steps`() = testApplication {
        module()

        forgot(email = "  Owner@Example.COM ")
        assertEquals(storedEmail, emailService.sentCodes().single().first, "Code goes to the address stored on the account")

        val response = reset(code = " ${lastSentCode()} ", email = " OWNER@example.com")

        assertEquals(HttpStatusCode.OK, response.status)
    }

    @Test
    fun `resend invalidates the code from the previous email`() = testApplication {
        module()
        forgot()
        val firstCode = lastSentCode()
        forgot()
        val latestCode = lastSentCode()

        val withFirstCode = reset(firstCode)

        assertEquals("INVALID_CODE", errorCode(withFirstCode, withFirstCode.bodyAsText()))
        assertEquals(HttpStatusCode.OK, reset(latestCode).status, "Only the code from the latest email works")
    }

    @Test
    fun `unknown email gets exactly the same answer as a wrong code`() = testApplication {
        module()
        forgot()
        val wrongCode = if (lastSentCode() == "000000") "111111" else "000000"

        val unknownEmail = reset(code = lastSentCode(), email = "nobody@example.com")
        val wrongCodeResponse = reset(code = wrongCode)

        assertEquals(wrongCodeResponse.status, unknownEmail.status)
        assertEquals(wrongCodeResponse.bodyAsText(), unknownEmail.bodyAsText())
        assertEquals("INVALID_CODE", errorCode(unknownEmail, unknownEmail.bodyAsText()))
    }

    @Test
    fun `expired code is reported as CODE_EXPIRED`() = testApplication {
        module()
        val now = System.currentTimeMillis()
        resetRepo.addCode(PasswordResetCode(id = 1, userId = 1, code = "934754", expiresAt = now - 1, isUsed = false, createdAt = now - 900_000))

        val response = reset("934754")

        assertEquals("CODE_EXPIRED", errorCode(response, response.bodyAsText()))
    }

    @Test
    fun `password that breaks the rules is reported as INVALID_PASSWORD and keeps the code usable`() = testApplication {
        module()
        forgot()

        val response = reset(code = lastSentCode(), password = "short")

        assertEquals("INVALID_PASSWORD", errorCode(response, response.bodyAsText()))
        assertEquals(HttpStatusCode.OK, reset(lastSentCode()).status)
    }
}
