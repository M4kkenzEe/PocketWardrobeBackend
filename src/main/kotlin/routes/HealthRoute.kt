package com.example.routes

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.Serializable
import org.jetbrains.exposed.v1.jdbc.transactions.transaction

/**
 * Health check endpoint for monitoring service availability
 * Returns 200 OK if service is healthy (including database connectivity)
 * Returns 503 Service Unavailable if there are issues
 */
fun Application.configureHealth() {
    routing {
        get("/health") {
            try {
                // Check database connectivity
                val dbHealthy = checkDatabaseHealth()

                if (dbHealthy) {
                    call.respond(
                        HttpStatusCode.OK,
                        HealthResponse(
                            status = "healthy",
                            database = "connected",
                            timestamp = System.currentTimeMillis()
                        )
                    )
                } else {
                    call.respond(
                        HttpStatusCode.ServiceUnavailable,
                        HealthResponse(
                            status = "unhealthy",
                            database = "disconnected",
                            timestamp = System.currentTimeMillis()
                        )
                    )
                }
            } catch (e: Exception) {
                application.log.error("Health check failed: ${e.message}", e)
                call.respond(
                    HttpStatusCode.ServiceUnavailable,
                    HealthResponse(
                        status = "unhealthy",
                        database = "error",
                        timestamp = System.currentTimeMillis()
                    )
                )
            }
        }
    }

    log.info("✓ Health check endpoint configured at /health")
}

/**
 * Checks database connectivity by executing a simple query
 * Returns true if database is accessible, false otherwise
 */
private fun checkDatabaseHealth(): Boolean {
    return try {
        transaction {
            // Simple query to check if database is accessible
            exec("SELECT 1") { resultSet ->
                resultSet.next()
                resultSet.getInt(1) == 1
            } ?: false
        }
    } catch (e: Exception) {
        false
    }
}

@Serializable
data class HealthResponse(
    val status: String,
    val database: String,
    val timestamp: Long
)
