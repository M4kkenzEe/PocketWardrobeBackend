package com.example.auth

import com.auth0.jwt.JWT
import com.example.auth.service.JWTConfig
import com.example.database.data.model.User
import org.slf4j.LoggerFactory
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Tests that JWT_EXPIRES_IN was successfully removed as a required env variable.
 *
 * All tests in this file require the test .env to be present with:
 * JWT_SECRET, JWT_ISSUER, JWT_AUDIENCE, DB_*, REMOVE_BG_SERVICE_URL, ANALYSIS_SERVICE_URL.
 * JWT_EXPIRES_IN must NOT be in .env — its absence proves the cleanup task succeeded.
 */
class ConfigValidatorTest {

    private val log = LoggerFactory.getLogger(ConfigValidatorTest::class.java)

    private val testUser = User(
        userId = 1,
        username = "testuser",
        email = "test@example.com",
        passwordHash = "hash",
        gender = null
    )

    // ── Config cleanup tests ───────────────────────────────────────────────────

    @Test
    fun `ConfigValidator passes without JWT_EXPIRES_IN in env`() {
        // ConfigValidator must succeed when JWT_EXPIRES_IN is absent from .env.
        // Fails if ConfigValidator still accesses the removed jwtExpiresIn property.
        ConfigValidator.validateConfiguration(log)
    }

    @Test
    fun `EnvironmentConfig does not expose jwtExpiresIn property`() {
        val hasDeadProp = EnvironmentConfig::class.members.any { it.name == "jwtExpiresIn" }
        assertFalse(hasDeadProp, "jwtExpiresIn dead property must be removed from EnvironmentConfig")
    }

    // ── Token TTL tests ────────────────────────────────────────────────────────

    @Test
    fun `jwtAccessExpiresIn has a positive value`() {
        assertTrue(EnvironmentConfig.jwtAccessExpiresIn > 0)
    }

    @Test
    fun `jwtRefreshExpiresIn has a positive value`() {
        assertTrue(EnvironmentConfig.jwtRefreshExpiresIn > 0)
    }

    @Test
    fun `access token TTL is shorter than refresh token TTL`() {
        assertTrue(
            EnvironmentConfig.jwtAccessExpiresIn < EnvironmentConfig.jwtRefreshExpiresIn,
            "Access TTL (${EnvironmentConfig.jwtAccessExpiresIn}ms) must be < " +
                "refresh TTL (${EnvironmentConfig.jwtRefreshExpiresIn}ms)"
        )
    }

    @Test
    fun `generated access token expiry matches jwtAccessExpiresIn`() {
        val before = System.currentTimeMillis()
        val pair = JWTConfig.generateTokenPair(testUser)
        val after = System.currentTimeMillis()

        // JWT stores expiry in Unix seconds (truncated), so compare in seconds.
        val expiresAtSec = JWT.decode(pair.accessToken).expiresAt.time / 1000
        val expectedMinSec = (before + EnvironmentConfig.jwtAccessExpiresIn) / 1000
        val expectedMaxSec = (after + EnvironmentConfig.jwtAccessExpiresIn) / 1000 + 1

        assertTrue(
            expiresAtSec in expectedMinSec..expectedMaxSec,
            "Access token expiry ${expiresAtSec}s must be within [${expectedMinSec}..${expectedMaxSec}]s"
        )
    }

    @Test
    fun `generated refresh token expiry matches jwtRefreshExpiresIn`() {
        val before = System.currentTimeMillis()
        val pair = JWTConfig.generateTokenPair(testUser)
        val after = System.currentTimeMillis()

        // JWT stores expiry in Unix seconds (truncated), so compare in seconds.
        val expiresAtSec = JWT.decode(pair.refreshToken).expiresAt.time / 1000
        val expectedMinSec = (before + EnvironmentConfig.jwtRefreshExpiresIn) / 1000
        val expectedMaxSec = (after + EnvironmentConfig.jwtRefreshExpiresIn) / 1000 + 1

        assertTrue(
            expiresAtSec in expectedMinSec..expectedMaxSec,
            "Refresh token expiry ${expiresAtSec}s must be within [${expectedMinSec}..${expectedMaxSec}]s"
        )
    }
}
