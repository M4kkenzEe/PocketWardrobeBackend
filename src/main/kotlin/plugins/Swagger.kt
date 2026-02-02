package plugins

import io.ktor.server.application.*
import io.ktor.server.plugins.swagger.*
import io.ktor.server.routing.*

fun Application.configureSwagger() {
    routing {
        swaggerUI(path = "swagger", swaggerFile = "openapi/auth-api.yaml") {
            version = "5.17.14"
        }
    }

    log.info("✓ Swagger UI configured at /swagger")
}
