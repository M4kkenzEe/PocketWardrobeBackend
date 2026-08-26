package com.example.routes

import com.example.auth.model.ForgotPasswordRequest
import com.example.auth.model.LoginRequest
import com.example.auth.model.LoginResponse
import com.example.auth.model.RefreshRequest
import com.example.auth.model.RegisterRequest
import com.example.auth.model.ResetPasswordRequest
import org.mindrot.jbcrypt.BCrypt
import com.example.auth.service.JWTConfig
import com.example.database.data.model.User
import com.example.database.domain.repository.PasswordResetRepository
import com.example.database.domain.repository.RevokedTokenRepository
import com.example.database.domain.repository.UserRepository
import com.example.services.EmailService
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
    val passwordResetRepository: PasswordResetRepository by inject()
    val emailService: EmailService by inject()

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
                        if (jti == null) {
                            return@post call.respond(HttpStatusCode.Unauthorized, ErrorResponse(
                                error = "Unauthorized",
                                message = "Invalid refresh token"
                            ))
                        }
                        if (revokedTokenRepository.isTokenRevoked(jti)) {
                            return@post call.respond(HttpStatusCode.Unauthorized, ErrorResponse(
                                error = "Unauthorized",
                                message = "Token has been revoked"
                            ))
                        }

                        val userId = decoded.getClaim("userId").asInt()
                        val user = userService.findUserById(userId)

                        if (user != null) {
                            val tokens = JWTConfig.generateTokenPair(user)
                            val oldExpiresAt = decoded.expiresAt?.time ?: 0L
                            revokedTokenRepository.revokeToken(jti, userId, oldExpiresAt)
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

                post("/auth/reset-password") {
                    val request = call.receive<ResetPasswordRequest>()

                    AuthValidator.validatePasswordResetRequest(
                        email = request.email,
                        code = request.code,
                        newPassword = request.newPassword
                    )

                    val user = userService.findUserByEmail(request.email)
                    if (user == null) {
                        call.respond(HttpStatusCode.BadRequest, ErrorResponse(
                            error = "BadRequest",
                            message = "Invalid code or email"
                        ))
                        return@post
                    }

                    val resetCode = passwordResetRepository.findValidCode(user.userId, request.code)
                    if (resetCode == null) {
                        call.respond(HttpStatusCode.BadRequest, ErrorResponse(
                            error = "BadRequest",
                            message = "Invalid or expired code"
                        ))
                        return@post
                    }

                    val newHash = BCrypt.hashpw(request.newPassword, BCrypt.gensalt())
                    userService.updatePassword(user.userId, newHash)
                    passwordResetRepository.markUsed(resetCode.id)
                    passwordResetRepository.deleteUserCodes(user.userId)

                    val updatedUser = user.copy(passwordHash = newHash)
                    val tokens = JWTConfig.generateTokenPair(updatedUser)
                    call.respond(HttpStatusCode.OK, LoginResponse(
                        accessToken = tokens.accessToken,
                        refreshToken = tokens.refreshToken,
                        expiresAt = tokens.expiresAt,
                        userId = updatedUser.userId
                    ))
                }

                post("/auth/forgot-password") {
                    val request = call.receive<ForgotPasswordRequest>()
                    val genericMessage = mapOf("message" to "Если email зарегистрирован, письмо отправлено")

                    passwordResetRepository.deleteExpiredCodes()

                    val user = userService.findUserByEmail(request.email)
                    if (user == null) {
                        call.respond(HttpStatusCode.OK, genericMessage)
                        return@post
                    }

                    val code = (100000..999999).random().toString()
                    passwordResetRepository.deleteUserCodes(user.userId)
                    passwordResetRepository.createCode(user.userId, code)

                    try {
                        emailService.sendPasswordResetCode(request.email, code)
                        call.respond(HttpStatusCode.OK, genericMessage)
                    } catch (e: Exception) {
                        log.error("Failed to send password reset email to ${request.email}", e)
                        call.respond(HttpStatusCode.InternalServerError, ErrorResponse(
                            error = "EmailError",
                            message = "Не удалось отправить письмо"
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
