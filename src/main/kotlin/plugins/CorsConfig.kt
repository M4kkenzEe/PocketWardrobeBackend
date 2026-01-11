package plugins

import com.example.auth.EnvironmentConfig
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.plugins.cors.routing.*

/**
 * CORS (Cross-Origin Resource Sharing) configuration
 * Controls which origins can access the API from browsers
 */
fun Application.configureCors() {
    install(CORS) {
        // Parse allowed origins from environment config
        val allowedOrigins = EnvironmentConfig.corsAllowedOrigins
            .split(",")
            .map { it.trim() }
            .filter { it.isNotBlank() }

        // Configure allowed origins
        if (allowedOrigins.contains("*")) {
            // Allow all origins (for development only)
            anyHost()
        } else {
            // Allow specific origins (production)
            allowedOrigins.forEach { origin ->
                allowHost(origin, schemes = listOf("http", "https"))
            }
        }

        // Allow common HTTP methods
        allowMethod(HttpMethod.Get)
        allowMethod(HttpMethod.Post)
        allowMethod(HttpMethod.Put)
        allowMethod(HttpMethod.Delete)
        allowMethod(HttpMethod.Patch)
        allowMethod(HttpMethod.Options)
        allowMethod(HttpMethod.Head)

        // Allow common headers
        allowHeader(HttpHeaders.Authorization)
        allowHeader(HttpHeaders.ContentType)
        allowHeader(HttpHeaders.Accept)
        allowHeader(HttpHeaders.AccessControlAllowOrigin)
        allowHeader(HttpHeaders.AccessControlAllowHeaders)

        // Allow credentials (cookies, authorization headers)
        allowCredentials = true

        // Allow non-standard headers
        allowNonSimpleContentTypes = true

        // Max age for preflight cache (in seconds)
        maxAgeInSeconds = 3600
    }
}
