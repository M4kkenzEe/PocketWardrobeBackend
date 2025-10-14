package plugins

import com.example.auth.EnvironmentConfig
import io.ktor.server.application.*
import io.ktor.util.*
import io.ktor.server.plugins.calllogging.*
import io.ktor.server.request.*
import org.slf4j.event.Level

/**
 * Request logging configuration using CallLogging plugin
 * Logs all incoming HTTP requests with method, path, status, and processing time
 */
fun Application.configureLogging() {
    install(CallLogging) {
        // Set logging level from environment config
        level = when (EnvironmentConfig.logLevel.uppercase()) {
            "TRACE" -> Level.TRACE
            "DEBUG" -> Level.DEBUG
            "INFO" -> Level.INFO
            "WARN" -> Level.WARN
            "ERROR" -> Level.ERROR
            else -> Level.INFO
        }

        // Custom log format
        format { call ->
            val status = call.response.status()
            val httpMethod = call.request.httpMethod.value
            val path = call.request.path()
            val userAgent = call.request.headers["User-Agent"]

            // Include processing time if available
            val processingTime = call.processingTimeMillis()

            buildString {
                append("$httpMethod $path")
                append(" - Status: $status")
                if (processingTime != null) {
                    append(" - Time: ${processingTime}ms")
                }
                if (userAgent != null) {
                    append(" - UA: ${userAgent.take(50)}")
                }
            }
        }

        // Filter sensitive endpoints from detailed logging (optional)
        filter { call ->
            val path = call.request.path()
            // Log all requests, but you can filter sensitive endpoints here
            // For example: !path.startsWith("/admin/secrets")
            true
        }
    }

    log.info("✓ Call logging configured at level: ${EnvironmentConfig.logLevel.uppercase()}")
}

/**
 * Extension function to get processing time
 */
private fun ApplicationCall.processingTimeMillis(): Long? {
    val startTime = attributes.getOrNull(callStartTimeKey)
    return if (startTime != null) {
        System.currentTimeMillis() - startTime
    } else {
        null
    }
}

private val callStartTimeKey = AttributeKey<Long>("CallStartTime")

/**
 * Interceptor to track request start time
 */
fun Application.setupRequestTiming() {
    intercept(ApplicationCallPipeline.Setup) {
        call.attributes.put(callStartTimeKey, System.currentTimeMillis())
    }
}
