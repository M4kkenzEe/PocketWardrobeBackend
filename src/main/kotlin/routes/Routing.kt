package com.example.routes

import com.example.auth.model.RegisterRequest
import com.example.auth.service.JWTConfig
import com.example.database.data.model.User
import com.example.database.domain.repository.UserRepository
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.auth.jwt.*
import io.ktor.server.plugins.ratelimit.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.Serializable
import org.koin.ktor.ext.inject
import io.ktor.server.plugins.ratelimit.RateLimitName

fun Application.configureRouting() {
    val userService: UserRepository by inject()

    routing {
        // Root endpoint (no versioning)
        get("/") {
            call.respondText("PocketWardrobe API - Use /api/v1 endpoints")
        }

        // API v1 routes
        route("/api/v1") {
            // Authentication endpoints with rate limiting
            rateLimit(RateLimitName("auth")) {
                post("/register") {
                    val request = call.receive<RegisterRequest>()
                    val user = userService.register(
                        User(
                            userId = 0,
                            username = request.username,
                            email = request.email,
                            passwordHash = request.password, // Will be hashed in service
                            gender = request.gender
                        )
                    )
                    if (user != null) {
                        call.respond(HttpStatusCode.Created, user)
                    } else {
                        call.respond(HttpStatusCode.Conflict, "User with this username or email already exists")
                    }
                }

                post("/login") {
                    val request = call.receive<LoginRequest>()
                    val user = userService.authenticate(request.username, request.password)
                    if (user != null) {
                        val token = JWTConfig.generateToken(user)
                        call.respond(LoginResponse(token))
                    } else {
                        call.respond(HttpStatusCode.Unauthorized, "Invalid credentials")
                    }
                }
            }

            // Protected routes with default rate limiting
            rateLimit(RateLimitName("default")) {
                authenticate {
                    get("/protected") {
                        val principal = call.principal<JWTPrincipal>()
                        val userId = principal?.payload?.getClaim("userId")?.asString()
                        call.respond(mapOf("message" to "Hello, $userId!"))
                    }
                }
            }
        }
    }

    log.info("✓ Routing configured with /api/v1 prefix and rate limiting")
}

data class UserPrincipal(val userId: Int, val username: String)

@Serializable
data class LoginRequest(val username: String, val password: String)

@Serializable
data class LoginResponse(val token: String)