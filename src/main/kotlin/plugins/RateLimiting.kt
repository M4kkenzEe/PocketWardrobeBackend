package plugins

import com.example.auth.EnvironmentConfig
import com.example.auth.model.RefreshRequest
import com.example.auth.model.UserPrincipal
import com.example.auth.service.JWTConfig
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.plugins.doublereceive.*
import io.ktor.server.plugins.origin
import io.ktor.server.plugins.ratelimit.*
import io.ktor.server.request.*
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes

/**
 * Rate limiting configuration to prevent abuse and brute-force attacks
 *
 * 1. Auth endpoints (login, forgot/reset password) - IP-based, strictest limits
 * 2. Refresh/logout - per user from the verified refresh token
 * 3. Registration (start/verify/register) - IP-based, roomier than auth
 * 4. File upload, generation, affiliate, default - user-based
 */
fun Application.configureRateLimiting() {
    installDoubleReceiveOnce()
    install(RateLimit) {
        // 1. Rate limit for brute-force targets (login, forgot/reset password)
        // Most strict - IP-based to prevent brute force attacks
        registerAuthRateLimit(EnvironmentConfig.rateLimitAuth)
        registerRefreshRateLimit(EnvironmentConfig.rateLimitRefresh)
        registerRegistrationRateLimit(EnvironmentConfig.rateLimitRegistration)

        // 2. Rate limit for file upload endpoints
        // Moderate - prevents abuse of file storage
        register(RateLimitName("upload")) {
            rateLimiter(limit = EnvironmentConfig.rateLimitUpload, refillPeriod = 1.minutes)
            requestKey { call ->
                // Use userId from JWT if available, otherwise IP
                val principal = call.principal<UserPrincipal>()
                principal?.userId?.toString() ?: call.request.origin.remoteHost
            }
        }

        // 3. Rate limit for AI look generation — expensive operation
        register(RateLimitName("generate")) {
            rateLimiter(limit = EnvironmentConfig.rateLimitGenerate, refillPeriod = 1.hours)
            requestKey { call ->
                val principal = call.principal<UserPrincipal>()
                principal?.userId?.toString() ?: call.request.origin.remoteHost
            }
        }

        // 3a. Rate limit for look drafts around an item — generous, drafts are reused between taps
        register(RateLimitName("drafts")) {
            rateLimiter(limit = EnvironmentConfig.rateLimitDrafts, refillPeriod = 1.hours)
            requestKey { call ->
                val principal = call.principal<UserPrincipal>()
                principal?.userId?.toString() ?: call.request.origin.remoteHost
            }
        }

        // 4. Rate limit for affiliate link generation
        register(RateLimitName("affiliate")) {
            rateLimiter(limit = EnvironmentConfig.rateLimitAffiliate, refillPeriod = 1.minutes)
            requestKey { call ->
                val principal = call.principal<UserPrincipal>()
                principal?.userId?.toString() ?: call.request.origin.remoteHost
            }
        }

        // 5. Rate limit for regular authenticated endpoints
        // Most generous - normal API usage
        register(RateLimitName("default")) {
            rateLimiter(limit = EnvironmentConfig.rateLimitDefault, refillPeriod = 1.minutes)
            requestKey { call ->
                // Use userId from JWT if available, otherwise IP
                val principal = call.principal<UserPrincipal>()
                principal?.userId?.toString() ?: call.request.origin.remoteHost
            }
        }
    }

    log.info("✓ Rate limiting configured:")
    log.info("  - Auth endpoints: ${EnvironmentConfig.rateLimitAuth} req/min per IP")
    log.info("  - Refresh/logout: ${EnvironmentConfig.rateLimitRefresh} req/min per user")
    log.info("  - Registration endpoints: ${EnvironmentConfig.rateLimitRegistration} req/min per IP")
    log.info("  - Upload endpoints: ${EnvironmentConfig.rateLimitUpload} req/min per user")
    log.info("  - Generate endpoint: ${EnvironmentConfig.rateLimitGenerate} req/hour per user")
    log.info("  - Drafts endpoint: ${EnvironmentConfig.rateLimitDrafts} req/hour per user")
    log.info("  - Affiliate endpoint: ${EnvironmentConfig.rateLimitAffiliate} req/min per user")
    log.info("  - Default endpoints: ${EnvironmentConfig.rateLimitDefault} req/min per user")
}

// No need for helper objects - RateLimitName is already available from Ktor

/** IP-based bucket for auth endpoints. Separate from [configureRateLimiting] so tests can hit it with a small limit. */
fun RateLimitConfig.registerAuthRateLimit(limit: Int) {
    register(RateLimitName("auth")) {
        rateLimiter(limit = limit, refillPeriod = 1.minutes)
        // The client IP as resolved by configureForwardedHeaders (last X-Forwarded-For value behind nginx).
        requestKey { call -> call.request.origin.remoteHost }
    }
}

/**
 * Per-user bucket for /refresh and /logout. Behind mobile CGNAT hundreds of users share one IP,
 * so an IP key would log them out en masse. The key comes only from a token that passes
 * signature and type checks: keying on the raw token string would hand an attacker a fresh bucket
 * per garbage token. Unverifiable bodies share one bucket per IP.
 * Reads the body before the handler, so the application needs DoubleReceive (see [installDoubleReceiveOnce]).
 */
fun RateLimitConfig.registerRefreshRateLimit(limit: Int) {
    register(RateLimitName("refresh")) {
        rateLimiter(limit = limit, refillPeriod = 1.minutes)
        requestKey { call -> refreshRateLimitKey(call) }
    }
}

/** IP-based bucket for the registration funnel, kept apart from login so signups behind CGNAT don't eat the brute-force budget. */
fun RateLimitConfig.registerRegistrationRateLimit(limit: Int) {
    register(RateLimitName("registration")) {
        rateLimiter(limit = limit, refillPeriod = 1.minutes)
        requestKey { call -> call.request.origin.remoteHost }
    }
}

/** Both request logging and the refresh bucket read the body before the handler; installing twice would throw. */
fun Application.installDoubleReceiveOnce() {
    if (pluginOrNull(DoubleReceive) == null) install(DoubleReceive)
}

/**
 * The token version is part of the key: after a password reset a thief's old tokens still pass the
 * signature check, and without the version they could keep the victim's new session at 429.
 * Revocation is not checked here (it would cost a DB query per refresh), so a stolen token of the
 * current version can still drain the bucket; that needs a live leak and is accepted for now.
 */
private suspend fun refreshRateLimitKey(call: ApplicationCall): String {
    val refreshToken = runCatching { call.receive<RefreshRequest>().refreshToken }.getOrNull()
    val decoded = refreshToken?.let { JWTConfig.verifyRefreshToken(it) }
    val userId = decoded?.getClaim("userId")?.asInt() ?: return "ip:${call.request.origin.remoteHost}"
    // Missing claim = token issued before tokenVersion existed; the /refresh handler treats it as 0 too.
    val tokenVersion = decoded.getClaim("tokenVersion").asLong() ?: 0L
    return "user:$userId:v$tokenVersion"
}
