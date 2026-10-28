package com.example.auth

import com.example.auth.service.JWTConfig
import com.example.database.data.model.User
import com.example.testutils.FakePasswordResetRepository
import com.example.testutils.InMemoryUserRepository
import com.example.testutils.TestJwtFactory
import com.example.testutils.assertSessionResponse
import com.example.testutils.postLogout
import com.example.testutils.postRefresh
import com.example.testutils.testAuthModule
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.server.testing.*
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class RefreshLogoutRouteTest {

    private val user = User(userId = 3, username = "returning", email = "returning@example.com", passwordHash = "hash", gender = null)
    private val userRepo = InMemoryUserRepository().apply { seed(user) }

    private fun ApplicationTestBuilder.module() = application {
        testAuthModule(passwordResetRepo = FakePasswordResetRepository(), userRepo = userRepo)
    }

    @Test
    fun `refresh returns 200 with a new session for the same user`() = testApplication {
        module()
        val oldRefreshToken = JWTConfig.generateTokenPair(user).refreshToken

        val body = postRefresh(oldRefreshToken).assertSessionResponse(HttpStatusCode.OK, user.userId)

        assertNotEquals(oldRefreshToken, body.getValue("refreshToken").jsonPrimitive.content)
    }

    @Test
    fun `expired refresh token returns 401`() = testApplication {
        module()

        assertEquals(HttpStatusCode.Unauthorized, postRefresh(TestJwtFactory.expiredRefreshToken(user.userId)).status)
    }

    @Test
    fun `access token sent as a refresh token returns 401`() = testApplication {
        module()

        assertEquals(HttpStatusCode.Unauthorized, postRefresh(JWTConfig.generateTokenPair(user).accessToken).status)
    }

    @Test
    fun `logout returns 204 with an empty body`() = testApplication {
        module()

        val response = postLogout(JWTConfig.generateTokenPair(user).refreshToken)

        assertEquals(HttpStatusCode.NoContent, response.status)
        assertTrue(response.bodyAsText().isEmpty())
    }

    @Test
    fun `refresh token cannot be used after logout`() = testApplication {
        module()
        val refreshToken = JWTConfig.generateTokenPair(user).refreshToken
        postLogout(refreshToken)

        assertEquals(HttpStatusCode.Unauthorized, postRefresh(refreshToken).status)
    }

    @Test
    fun `logout with an invalid token returns 401`() = testApplication {
        module()

        assertEquals(HttpStatusCode.Unauthorized, postLogout("not-a-jwt").status)
    }
}
