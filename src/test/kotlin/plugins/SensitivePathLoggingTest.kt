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
    fun `regular api bodies are still logged`() {
        assertFalse(isSensitivePath("/api/v1/looks/generate"))
    }
}
