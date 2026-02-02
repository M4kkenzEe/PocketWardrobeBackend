package com.example.routes

import com.example.auth.EnvironmentConfig
import com.example.auth.model.AuthErrorResponse
import com.example.auth.model.LoginResponse
import com.example.auth.model.RefreshResponse
import com.example.auth.model.RegisterRequest
import com.example.auth.model.RegisterResponse
import com.example.auth.service.TokenService
import com.example.database.data.model.User
import com.example.database.domain.repository.RefreshTokenRepository
import com.example.database.domain.repository.UserRepository
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.plugins.ratelimit.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.util.date.*
import kotlinx.serialization.Serializable
import org.koin.ktor.ext.inject

// Login request for auth routes (uses email)
@Serializable
private data class AuthLoginRequest(
    val email: String,
    val password: String
)

fun Application.authRoutes() {
    val userRepository: UserRepository by inject()
    val refreshTokenRepository: RefreshTokenRepository by inject()
    val tokenService: TokenService by inject()

    routing {
        route("/api/auth") {
            rateLimit(RateLimitName("auth")) {

                // POST /api/auth/register
                post("/register") {
                    val request = call.receive<RegisterRequest>()

                    // Validate input
                    if (request.email.isBlank() || request.password.isBlank() || request.username.isBlank()) {
                        call.respond(
                            HttpStatusCode.BadRequest,
                            AuthErrorResponse(
                                error = "validation_error",
                                message = "Email, username, and password are required"
                            )
                        )
                        return@post
                    }

                    // Create user (password is hashed in UserRepositoryImpl)
                    val user = userRepository.register(
                        User(
                            userId = 0,
                            username = request.username,
                            email = request.email,
                            passwordHash = request.password,
                            gender = request.gender
                        )
                    )

                    if (user != null) {
                        call.respond(
                            HttpStatusCode.Created,
                            RegisterResponse(
                                message = "User registered successfully",
                                userId = user.userId
                            )
                        )
                    } else {
                        call.respond(
                            HttpStatusCode.Conflict,
                            AuthErrorResponse(
                                error = "user_exists",
                                message = "User with this username or email already exists"
                            )
                        )
                    }
                }

                // POST /api/auth/login
                post("/login") {
                    val request = call.receive<AuthLoginRequest>()

                    // Authenticate user by email
                    val user = userRepository.authenticate(request.email, request.password)

                    if (user == null) {
                        call.respond(
                            HttpStatusCode.Unauthorized,
                            AuthErrorResponse(
                                error = "invalid_credentials",
                                message = "Invalid email or password"
                            )
                        )
                        return@post
                    }

                    // Generate tokens
                    val (accessToken, expiresInSeconds) = tokenService.generateAccessToken(user)
                    val (refreshToken, refreshExpiresAt) = tokenService.generateRefreshToken()
                    val refreshTokenHash = tokenService.hashToken(refreshToken)

                    // Get client info for security logging
                    val userAgent = call.request.headers["User-Agent"]
                    val ipAddress = call.request.local.remoteHost

                    // Store refresh token hash in DB
                    refreshTokenRepository.create(
                        userId = user.userId,
                        tokenHash = refreshTokenHash,
                        expiresAt = refreshExpiresAt,
                        userAgent = userAgent,
                        ipAddress = ipAddress
                    )

                    // Set refresh token in httpOnly cookie
                    setRefreshTokenCookie(call, refreshToken, refreshExpiresAt)

                    // Return tokens in response body
                    call.respond(
                        HttpStatusCode.OK,
                        LoginResponse(
                            accessToken = accessToken,
                            refreshToken = refreshToken,
                            expiresIn = expiresInSeconds,
                            tokenType = "Bearer"
                        )
                    )
                }

                // POST /api/auth/refresh
                post("/refresh") {
                    // Extract refresh token from cookie
                    val refreshToken = call.request.cookies["refresh_token"]

                    if (refreshToken == null) {
                        call.respond(
                            HttpStatusCode.Unauthorized,
                            AuthErrorResponse(
                                error = "missing_token",
                                message = "Refresh token is missing"
                            )
                        )
                        return@post
                    }

                    val tokenHash = tokenService.hashToken(refreshToken)

                    // Find token in DB
                    val storedToken = refreshTokenRepository.findByTokenHash(tokenHash)

                    if (storedToken == null) {
                        // Token not found or expired - clear cookie
                        clearRefreshTokenCookie(call)
                        call.respond(
                            HttpStatusCode.Unauthorized,
                            AuthErrorResponse(
                                error = "invalid_token",
                                message = "Refresh token is invalid or expired"
                            )
                        )
                        return@post
                    }

                    // Get user
                    val user = userRepository.findUserById(storedToken.userId)
                    if (user == null) {
                        // User deleted - revoke all tokens
                        refreshTokenRepository.deleteAllForUser(storedToken.userId)
                        clearRefreshTokenCookie(call)
                        call.respond(
                            HttpStatusCode.Unauthorized,
                            AuthErrorResponse(
                                error = "user_not_found",
                                message = "User no longer exists"
                            )
                        )
                        return@post
                    }

                    // Token Rotation: Delete old token
                    refreshTokenRepository.deleteByTokenHash(tokenHash)

                    // Generate new tokens
                    val (newAccessToken, expiresInSeconds) = tokenService.generateAccessToken(user)
                    val (newRefreshToken, newRefreshExpiresAt) = tokenService.generateRefreshToken()
                    val newRefreshTokenHash = tokenService.hashToken(newRefreshToken)

                    // Get client info
                    val userAgent = call.request.headers["User-Agent"]
                    val ipAddress = call.request.local.remoteHost

                    // Store new refresh token hash
                    refreshTokenRepository.create(
                        userId = user.userId,
                        tokenHash = newRefreshTokenHash,
                        expiresAt = newRefreshExpiresAt,
                        userAgent = userAgent,
                        ipAddress = ipAddress
                    )

                    // Set new refresh token cookie
                    setRefreshTokenCookie(call, newRefreshToken, newRefreshExpiresAt)

                    // Return new tokens
                    call.respond(
                        HttpStatusCode.OK,
                        RefreshResponse(
                            accessToken = newAccessToken,
                            refreshToken = newRefreshToken,
                            expiresIn = expiresInSeconds,
                            tokenType = "Bearer"
                        )
                    )
                }

                // POST /api/auth/logout
                post("/logout") {
                    val refreshToken = call.request.cookies["refresh_token"]

                    if (refreshToken != null) {
                        val tokenHash = tokenService.hashToken(refreshToken)
                        refreshTokenRepository.deleteByTokenHash(tokenHash)
                    }

                    // Clear the cookie
                    clearRefreshTokenCookie(call)

                    call.respond(HttpStatusCode.NoContent)
                }
            }

            // Protected route: logout from all devices
            rateLimit(RateLimitName("default")) {
                authenticate {
                    post("/logout-all") {
                        val principal = call.principal<UserPrincipal>()
                        if (principal == null) {
                            call.respond(HttpStatusCode.Unauthorized)
                            return@post
                        }

                        val deletedCount = refreshTokenRepository.deleteAllForUser(principal.userId)
                        clearRefreshTokenCookie(call)

                        call.respond(
                            HttpStatusCode.OK,
                            mapOf(
                                "message" to "Logged out from all devices",
                                "sessions_revoked" to deletedCount
                            )
                        )
                    }
                }
            }
        }
    }

    log.info("✓ Auth routes configured at /api/auth")
}

private fun setRefreshTokenCookie(call: ApplicationCall, token: String, expiresAt: Long) {
    val maxAgeSeconds = ((expiresAt - System.currentTimeMillis()) / 1000).toInt()

    call.response.cookies.append(
        Cookie(
            name = "refresh_token",
            value = token,
            maxAge = maxAgeSeconds,
            expires = GMTDate(expiresAt),
            path = "/api/auth",  // Only sent to auth endpoints
            httpOnly = true,
            secure = EnvironmentConfig.cookieSecure,
            extensions = mapOf("SameSite" to EnvironmentConfig.cookieSameSite)
        )
    )
}

private fun clearRefreshTokenCookie(call: ApplicationCall) {
    call.response.cookies.append(
        Cookie(
            name = "refresh_token",
            value = "",
            maxAge = 0,
            expires = GMTDate.START,
            path = "/api/auth",
            httpOnly = true,
            secure = EnvironmentConfig.cookieSecure,
            extensions = mapOf("SameSite" to EnvironmentConfig.cookieSameSite)
        )
    )
}
