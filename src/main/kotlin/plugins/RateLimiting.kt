package plugins

import com.example.auth.EnvironmentConfig
import com.example.auth.model.UserPrincipal
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.plugins.ratelimit.*
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes

/**
 * Rate limiting configuration to prevent abuse and brute-force attacks
 *
 * Three rate limit configurations:
 * 1. Auth endpoints (login/register) - IP-based, strictest limits
 * 2. File upload endpoints - User-based, moderate limits
 * 3. Regular authenticated endpoints - User-based, generous limits
 */
fun Application.configureRateLimiting() {
    install(RateLimit) {
        // 1. Rate limit for authentication endpoints (login, register)
        // Most strict - IP-based to prevent brute force attacks
        register(RateLimitName("auth")) {
            rateLimiter(limit = EnvironmentConfig.rateLimitAuth, refillPeriod = 1.minutes)
            requestKey { call ->
                // Use client IP as the key
                call.request.local.remoteHost
            }
        }

        // 2. Rate limit for file upload endpoints
        // Moderate - prevents abuse of file storage
        register(RateLimitName("upload")) {
            rateLimiter(limit = EnvironmentConfig.rateLimitUpload, refillPeriod = 1.minutes)
            requestKey { call ->
                // Use userId from JWT if available, otherwise IP
                val principal = call.principal<UserPrincipal>()
                principal?.userId?.toString() ?: call.request.local.remoteHost
            }
        }

        // 3. Rate limit for AI look generation — expensive operation
        register(RateLimitName("generate")) {
            rateLimiter(limit = EnvironmentConfig.rateLimitGenerate, refillPeriod = 1.hours)
            requestKey { call ->
                val principal = call.principal<UserPrincipal>()
                principal?.userId?.toString() ?: call.request.local.remoteHost
            }
        }

        // 4. Rate limit for regular authenticated endpoints
        // Most generous - normal API usage
        register(RateLimitName("default")) {
            rateLimiter(limit = EnvironmentConfig.rateLimitDefault, refillPeriod = 1.minutes)
            requestKey { call ->
                // Use userId from JWT if available, otherwise IP
                val principal = call.principal<UserPrincipal>()
                principal?.userId?.toString() ?: call.request.local.remoteHost
            }
        }
    }

    log.info("✓ Rate limiting configured:")
    log.info("  - Auth endpoints: ${EnvironmentConfig.rateLimitAuth} req/min per IP")
    log.info("  - Upload endpoints: ${EnvironmentConfig.rateLimitUpload} req/min per user")
    log.info("  - Generate endpoint: ${EnvironmentConfig.rateLimitGenerate} req/hour per user")
    log.info("  - Default endpoints: ${EnvironmentConfig.rateLimitDefault} req/min per user")
}

// No need for helper objects - RateLimitName is already available from Ktor
