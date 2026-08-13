package plugins

import com.example.usecases.FreemiumLimitException
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.plugins.statuspages.*
import io.ktor.server.response.*
import kotlinx.serialization.Serializable

/**
 * Centralized error handling plugin using StatusPages
 * Prevents database error leakage by returning generic error messages to clients
 * while logging full errors server-side
 */
fun Application.configureErrorHandling() {
    install(StatusPages) {
        // Handle generic exceptions
        exception<Throwable> { call, cause ->
            // Log full error details server-side
            call.application.log.error("Unhandled exception: ${cause.message}", cause)

            // Return generic error to client
            call.respond(
                HttpStatusCode.InternalServerError,
                ErrorResponse(
                    error = "Internal server error",
                    message = "An unexpected error occurred. Please try again later."
                )
            )
        }

        // Free plan wardrobe cap. Mirrors the body shape the clothes routes already return.
        exception<FreemiumLimitException> { call, cause ->
            call.respond(
                HttpStatusCode.PaymentRequired,
                FreemiumLimitErrorResponse(error = "FREEMIUM_LIMIT", limit = cause.limit, current = cause.current)
            )
        }

        // Handle IllegalArgumentException (validation errors)
        exception<IllegalArgumentException> { call, cause ->
            call.application.log.warn("Validation error: ${cause.message}")

            call.respond(
                HttpStatusCode.BadRequest,
                ErrorResponse(
                    error = "Validation error",
                    message = cause.message ?: "Invalid request parameters"
                )
            )
        }

        // Handle IllegalStateException (state errors)
        exception<IllegalStateException> { call, cause ->
            call.application.log.warn("State error: ${cause.message}")

            call.respond(
                HttpStatusCode.Conflict,
                ErrorResponse(
                    error = "State error",
                    message = cause.message ?: "Invalid operation state"
                )
            )
        }

        // Handle NoSuchElementException (not found)
        exception<NoSuchElementException> { call, cause ->
            call.application.log.warn("Resource not found: ${cause.message}")

            call.respond(
                HttpStatusCode.NotFound,
                ErrorResponse(
                    error = "Not found",
                    message = "The requested resource was not found"
                )
            )
        }

        // Handle 404 Not Found
        status(HttpStatusCode.NotFound) { call, status ->
            call.respond(
                status,
                ErrorResponse(
                    error = "Not found",
                    message = "The requested endpoint does not exist"
                )
            )
        }

        // Handle 401 Unauthorized
        status(HttpStatusCode.Unauthorized) { call, status ->
            call.respond(
                status,
                ErrorResponse(
                    error = "Unauthorized",
                    message = "Authentication required"
                )
            )
        }

        // Handle 403 Forbidden
        status(HttpStatusCode.Forbidden) { call, status ->
            call.respond(
                status,
                ErrorResponse(
                    error = "Forbidden",
                    message = "You don't have permission to access this resource"
                )
            )
        }

        // Handle 429 Too Many Requests (Rate Limiting)
        status(HttpStatusCode.TooManyRequests) { call, status ->
            call.respond(
                status,
                ErrorResponse(
                    error = "Too many requests",
                    message = "Rate limit exceeded. Please try again later."
                )
            )
        }
    }

    log.info("✓ Error handling configured with StatusPages plugin")
}

@Serializable
data class ErrorResponse(
    val error: String,
    val message: String
)

@Serializable
data class FreemiumLimitErrorResponse(
    val error: String,
    val limit: Int,
    val current: Int
)
