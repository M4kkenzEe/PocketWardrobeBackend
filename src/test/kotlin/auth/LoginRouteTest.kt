package com.example.auth

import com.example.database.data.model.User
import com.example.testutils.FakePasswordResetRepository
import com.example.testutils.InMemoryUserRepository
import com.example.testutils.assertSessionResponse
import com.example.testutils.postJson
import com.example.testutils.testAuthModule
import io.ktor.http.*
import io.ktor.server.testing.*
import kotlin.test.Test
import kotlin.test.assertEquals

class LoginRouteTest {

    private val username = "closet_owner"
    private val email = "owner@example.com"
    private val password = "SecurePass1"
    private val userRepo = InMemoryUserRepository()

    private suspend fun ApplicationTestBuilder.moduleWithUser(): User {
        application { testAuthModule(passwordResetRepo = FakePasswordResetRepository(), userRepo = userRepo) }
        return requireNotNull(
            userRepo.register(User(userId = 0, username = username, email = email, passwordHash = password, gender = null))
        )
    }

    private suspend fun ApplicationTestBuilder.login(login: String, password: String) =
        postJson("/login", """{"login":"$login","password":"$password"}""")

    @Test
    fun `login by username returns 200 with the session contract`() = testApplication {
        val user = moduleWithUser()

        login(username, password).assertSessionResponse(HttpStatusCode.OK, user.userId)
    }

    @Test
    fun `login by email returns 200 with the session contract`() = testApplication {
        val user = moduleWithUser()

        login(email, password).assertSessionResponse(HttpStatusCode.OK, user.userId)
    }

    @Test
    fun `wrong password returns 401`() = testApplication {
        moduleWithUser()

        assertEquals(HttpStatusCode.Unauthorized, login(username, "WrongPass1").status)
    }

    @Test
    fun `unknown user returns 401`() = testApplication {
        moduleWithUser()

        assertEquals(HttpStatusCode.Unauthorized, login("nobody@example.com", password).status)
    }

    @Test
    fun `blank login returns 400`() = testApplication {
        moduleWithUser()

        assertEquals(HttpStatusCode.BadRequest, login("", password).status)
    }

    @Test
    fun `blank password returns 400`() = testApplication {
        moduleWithUser()

        assertEquals(HttpStatusCode.BadRequest, login(username, "").status)
    }
}
