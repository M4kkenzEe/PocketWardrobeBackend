package com.example.routes

import com.example.auth.model.LoginRequest
import com.example.auth.model.LoginResponse
import com.example.auth.model.RefreshRequest
import com.example.auth.model.RegisterRequest
import com.example.auth.service.JWTConfig
import com.example.database.data.model.User
import com.example.database.domain.repository.RevokedTokenRepository
import com.example.database.domain.repository.UserRepository
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.auth.jwt.*
import io.ktor.server.plugins.ratelimit.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import org.koin.ktor.ext.inject
import io.ktor.server.plugins.ratelimit.RateLimitName
import plugins.ErrorResponse
import validation.AuthValidator
import java.util.UUID

fun Application.configureRouting() {
    val userService: UserRepository by inject()
    val revokedTokenRepository: RevokedTokenRepository by inject()

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

                    AuthValidator.validateRegisterRequest(
                        email = request.email,
                        password = request.password,
                        username = request.username,
                        gender = request.gender
                    )

                    val username = request.username ?: generateUsernameFromEmail(request.email)

                    val user = userService.register(
                        User(
                            userId = 0,
                            username = username,
                            email = request.email,
                            passwordHash = request.password,
                            gender = request.gender?.uppercase()
                        )
                    )
                    if (user != null) {
                        val tokens = JWTConfig.generateTokenPair(user)
                        call.respond(HttpStatusCode.Created, LoginResponse(
                            accessToken = tokens.accessToken,
                            refreshToken = tokens.refreshToken,
                            expiresAt = tokens.expiresAt,
                            userId = user.userId
                        ))
                    } else {
                        call.respond(HttpStatusCode.Conflict, ErrorResponse(
                            error = "Conflict",
                            message = "User with this username or email already exists"
                        ))
                    }
                }

                post("/login") {
                    val request = call.receive<LoginRequest>()

                    AuthValidator.validateLoginRequest(
                        login = request.login,
                        password = request.password
                    )

                    val user = userService.authenticate(request.login, request.password)
                    if (user != null) {
                        val tokens = JWTConfig.generateTokenPair(user)
                        call.respond(LoginResponse(
                            accessToken = tokens.accessToken,
                            refreshToken = tokens.refreshToken,
                            expiresAt = tokens.expiresAt,
                            userId = user.userId
                        ))
                    } else {
                        call.respond(HttpStatusCode.Unauthorized, ErrorResponse(
                            error = "Unauthorized",
                            message = "Invalid credentials"
                        ))
                    }
                }

                post("/refresh") {
                    val request = call.receive<RefreshRequest>()
                    val decoded = JWTConfig.verifyRefreshToken(request.refreshToken)

                    if (decoded != null) {
                        val jti = decoded.id
                        if (jti != null && revokedTokenRepository.isTokenRevoked(jti)) {
                            return@post call.respond(HttpStatusCode.Unauthorized, ErrorResponse(
                                error = "Unauthorized",
                                message = "Token has been revoked"
                            ))
                        }

                        val userId = decoded.getClaim("userId").asInt()
                        val user = userService.findUserById(userId)

                        if (user != null) {
                            val tokens = JWTConfig.generateTokenPair(user)
                            call.respond(LoginResponse(
                                accessToken = tokens.accessToken,
                                refreshToken = tokens.refreshToken,
                                expiresAt = tokens.expiresAt,
                                userId = user.userId
                            ))
                        } else {
                            call.respond(HttpStatusCode.Unauthorized, ErrorResponse(
                                error = "Unauthorized",
                                message = "User not found"
                            ))
                        }
                    } else {
                        call.respond(HttpStatusCode.Unauthorized, ErrorResponse(
                            error = "Unauthorized",
                            message = "Invalid or expired refresh token"
                        ))
                    }
                }

                post("/logout") {
                    val request = call.receive<RefreshRequest>()
                    val decoded = JWTConfig.verifyRefreshToken(request.refreshToken)

                    if (decoded != null) {
                        val jti = decoded.id
                        val userId = decoded.getClaim("userId").asInt()
                        val expiresAt = decoded.expiresAt?.time ?: 0L
                        if (jti != null) {
                            revokedTokenRepository.revokeToken(jti, userId, expiresAt)
                        }
                        call.respond(HttpStatusCode.NoContent)
                    } else {
                        call.respond(HttpStatusCode.Unauthorized, ErrorResponse(
                            error = "Unauthorized",
                            message = "Invalid or expired refresh token"
                        ))
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

private fun generateUsernameFromEmail(email: String): String {
    val base = email.substringBefore("@")
        .replace(Regex("[^a-zA-Z0-9_]"), "_")
        .take(20)
    val suffix = UUID.randomUUID().toString().take(4)
    return "${base}_${suffix}"
}
