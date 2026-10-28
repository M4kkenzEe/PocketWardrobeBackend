package plugins

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SensitivePathLoggingTest {

    @Test
    fun `password recovery bodies are kept out of the request log`() {
        assertTrue(isSensitivePath("/api/v1/auth/reset-password"), "Body holds the new password and the code")
        assertTrue(isSensitivePath("/api/v1/auth/forgot-password"))
        assertTrue(isSensitivePath("/api/v1/auth/registration/verify"))
    }

    @Test
    fun `session, registration and admin bodies are kept out of the request log`() {
        listOf(
            "/api/v1/login",
            "/api/v1/register",
            "/api/v1/refresh",
            "/api/v1/logout",
            "/api/v1/auth/registration/start",
            "/api/v1/auth/verify-email",
            "/admin/users"
        ).forEach { path -> assertTrue(isSensitivePath(path), path) }
    }

    @Test
    fun `regular api bodies are still logged`() {
        assertFalse(isSensitivePath("/api/v1/looks/generate"))
    }
}
