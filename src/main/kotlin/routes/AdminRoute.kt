package com.example.routes

import com.example.admin.AdminCreateUserRequest
import com.example.admin.toAdminResponse
import com.example.auth.EnvironmentConfig
import com.example.usecases.AdminUserUseCase
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.plugins.ratelimit.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import org.koin.ktor.ext.inject
import plugins.ErrorResponse
import java.security.MessageDigest

private const val ADMIN_SECRET_HEADER = "X-Admin-Secret"

// adminSecret is a parameter so tests can cover the "not configured" case without touching process env.
fun Application.admin(adminSecret: String? = EnvironmentConfig.adminSecret) {
    val adminUserUseCase: AdminUserUseCase by inject()

    routing {
        rateLimit(RateLimitName("auth")) {
            route("/admin/users") {
                get {
                    if (!call.isAdminAuthorized(adminSecret)) return@get
                    call.respond(adminUserUseCase.listUsers().map { it.toAdminResponse() })
                }

                post {
                    if (!call.isAdminAuthorized(adminSecret)) return@post
                    val request = call.receive<AdminCreateUserRequest>()
                    val createdUser = adminUserUseCase.createUser(
                        username = generateUsernameFromEmail(request.email),
                        email = request.email,
                        password = request.password
                    )
                    call.respond(HttpStatusCode.Created, createdUser.toAdminResponse())
                }
            }
        }
    }
}

/** Responds 503 / 403 itself and returns false when access is denied. */
private suspend fun ApplicationCall.isAdminAuthorized(adminSecret: String?): Boolean {
    if (adminSecret == null) {
        respond(
            HttpStatusCode.ServiceUnavailable,
            ErrorResponse(error = "ServiceUnavailable", message = "Admin API is not configured")
        )
        return false
    }

    val providedSecret = request.headers[ADMIN_SECRET_HEADER]
    val isSecretValid = providedSecret != null &&
        MessageDigest.isEqual(providedSecret.toByteArray(), adminSecret.toByteArray())
    if (!isSecretValid) {
        respond(HttpStatusCode.Forbidden, ErrorResponse(error = "Forbidden", message = "Invalid admin secret"))
        return false
    }
    return true
}
