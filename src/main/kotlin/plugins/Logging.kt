package plugins

import com.example.auth.EnvironmentConfig
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.plugins.calllogging.*
import io.ktor.server.plugins.doublereceive.*
import io.ktor.server.request.*
import io.ktor.util.*
import org.slf4j.event.Level

private val callStartTimeKey = AttributeKey<Long>("CallStartTime")
private val requestBodyKey = AttributeKey<String>("RequestBody")

fun Application.configureLogging() {
    install(DoubleReceive)

    // Capture request body in suspend context, store in attribute for later use in format lambda
    intercept(ApplicationCallPipeline.Monitoring) {
        val path = call.request.path()
        val isAuthPath = path.contains("/login") || path.contains("/register") ||
                path.contains("/refresh") || path.contains("/logout")
        if (!isAuthPath && call.request.contentType().match(ContentType.Application.Json)) {
            val body = runCatching { call.receiveText().take(500) }.getOrNull()
            if (!body.isNullOrBlank()) {
                call.attributes.put(requestBodyKey, body)
            }
        }
        proceed()
    }

    install(CallLogging) {
        level = when (EnvironmentConfig.logLevel.uppercase()) {
            "TRACE" -> Level.TRACE
            "DEBUG" -> Level.DEBUG
            "INFO" -> Level.INFO
            "WARN" -> Level.WARN
            "ERROR" -> Level.ERROR
            else -> Level.INFO
        }

        format { call ->
            val status = call.response.status()
            val httpMethod = call.request.httpMethod.value
            val path = call.request.path()
            val processingTime = call.processingTimeMillis()
            val body = call.attributes.getOrNull(requestBodyKey)

            buildString {
                append("[$status] $httpMethod $path")
                if (processingTime != null) append(" - ${processingTime}ms")
                if (!body.isNullOrBlank()) append(" | Body: $body")
            }
        }

        filter { call ->
            call.request.path() != "/health"
        }
    }
}

private fun ApplicationCall.processingTimeMillis(): Long? {
    val startTime = attributes.getOrNull(callStartTimeKey)
    return if (startTime != null) {
        System.currentTimeMillis() - startTime
    } else {
        null
    }
}

fun Application.setupRequestTiming() {
    intercept(ApplicationCallPipeline.Setup) {
        call.attributes.put(callStartTimeKey, System.currentTimeMillis())
    }
}
