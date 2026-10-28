package com.example.testutils

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import com.example.auth.model.UserPrincipal
import com.example.database.domain.repository.ClotheRepository
import com.example.database.domain.repository.EmailVerificationRepository
import com.example.database.domain.repository.PasswordResetRepository
import com.example.database.domain.repository.PendingEmailVerificationRepository
import com.example.database.domain.repository.RevokedTokenRepository
import com.example.database.domain.repository.UserClotheRepository
import com.example.database.domain.repository.UserRepository
import com.example.routes.admin
import com.example.routes.clothes
import com.example.routes.configureRouting
import com.example.routes.looks
import com.example.services.ClotheAnalysisService
import com.example.services.EmailService
import com.example.services.RemoveBgService
import com.example.usecases.AdminUserUseCase
import com.example.usecases.ClotheUseCase
import com.example.usecases.EmailVerificationUseCase
import com.example.usecases.PasswordResetUseCase
import com.example.usecases.RegistrationUseCase
import io.ktor.client.engine.cio.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.auth.jwt.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.plugins.ratelimit.*
import io.ktor.server.plugins.statuspages.*
import io.ktor.server.response.*
import kotlinx.serialization.json.Json
import org.koin.dsl.module
import org.koin.ktor.plugin.Koin
import plugins.ErrorResponse
import plugins.configureErrorHandling
import plugins.configureForwardedHeaders
import plugins.installDoubleReceiveOnce
import plugins.registerAuthRateLimit
import plugins.registerRefreshRateLimit
import plugins.registerRegistrationRateLimit
import kotlin.time.Duration.Companion.minutes
import io.ktor.client.HttpClient as KtorHttpClient

const val TEST_JWT_SECRET   = "test-secret-key-for-pocket-wardrobe-tests-32ch"
const val TEST_JWT_ISSUER   = "pocket-wardrobe-test"
const val TEST_JWT_AUDIENCE = "pocket-wardrobe-test-audience"

fun Application.installTestJwtAuth() {
    val algorithm = Algorithm.HMAC256(TEST_JWT_SECRET)
    authentication {
        jwt {
            realm = "test"
            verifier(
                JWT.require(algorithm)
                    .withAudience(TEST_JWT_AUDIENCE)
                    .withIssuer(TEST_JWT_ISSUER)
                    .withClaim("type", "access")
                    .build()
            )
            validate { credential ->
                val userId   = credential.payload.getClaim("userId")?.asInt()
                val username = credential.payload.getClaim("username")?.asString()
                if (userId != null && username != null) UserPrincipal(userId, username) else null
            }
        }
    }
}

fun Application.testClothesModule(
    clotheRepo: ClotheRepository,
    userRepo: UserRepository = FakeUserRepository(),
    userClotheRepo: UserClotheRepository = FakeUserClotheRepository()
) {
    install(ContentNegotiation) {
        json(Json { ignoreUnknownKeys = true })
    }

    install(StatusPages) {
        exception<Throwable> { call, _ ->
            call.respond(HttpStatusCode.InternalServerError,
                ErrorResponse("Internal server error", "An unexpected error occurred."))
        }
        status(HttpStatusCode.Unauthorized) { call, status ->
            call.respond(status, ErrorResponse("Unauthorized", "Authentication required"))
        }
        status(HttpStatusCode.NotFound) { call, status ->
            call.respond(status, ErrorResponse("Not found", "The requested endpoint does not exist"))
        }
    }

    install(RateLimit) {
        register(RateLimitName("default")) {
            rateLimiter(limit = 10_000, refillPeriod = 1.minutes)
        }
        register(RateLimitName("upload")) {
            rateLimiter(limit = 10_000, refillPeriod = 1.minutes)
        }
    }

    installTestJwtAuth()

    install(Koin) {
        modules(module {
            single<ClotheRepository> { clotheRepo }
            single<UserClotheRepository> { userClotheRepo }
            single<UserRepository> { userRepo }
            single<RemoveBgService> {
                RemoveBgService(KtorHttpClient(CIO), "http://stub/", "uploads")
            }
            single<ClotheAnalysisService> {
                ClotheAnalysisService(KtorHttpClient(CIO), "http://stub/")
            }
            single<ClotheUseCase> {
                ClotheUseCase(FakeLookRepository())
            }
        })
    }

    clothes()
}

/**
 * Test module for auth routes. Calls the production [configureRouting] so tests
 * cover the real handler, not a copy of it. EmailService is faked so tests never
 * open an SMTP connection, even when .env carries real SMTP credentials.
 * Forwarded headers and the auth, refresh and registration buckets are registered by the production
 * functions; only the limits differ (huge by default, pass the real values to test the real boundaries).
 *
 * Requires: .env with JWT_SECRET, DB_URL, DB_USER, DB_PASSWORD, DB_NAME,
 * REMOVE_BG_SERVICE_URL, ANALYSIS_SERVICE_URL (same requirement as ConfigValidatorTest).
 */
fun Application.testAuthModule(
    passwordResetRepo: PasswordResetRepository,
    userRepo: UserRepository = FakeUserRepository(),
    revokedTokenRepo: RevokedTokenRepository = FakeRevokedTokenRepository(),
    emailService: EmailService = FakeEmailService(),
    emailVerificationRepo: EmailVerificationRepository = FakeEmailVerificationRepository(),
    pendingEmailVerificationRepo: PendingEmailVerificationRepository = FakePendingEmailVerificationRepository(),
    authRateLimit: Int = 10_000,
    refreshRateLimit: Int = 10_000,
    registrationRateLimit: Int = 10_000
) {
    install(ContentNegotiation) {
        json(Json { ignoreUnknownKeys = true })
    }

    configureErrorHandling()
    configureForwardedHeaders()
    installDoubleReceiveOnce()

    install(RateLimit) {
        registerAuthRateLimit(authRateLimit)
        registerRefreshRateLimit(refreshRateLimit)
        registerRegistrationRateLimit(registrationRateLimit)
        register(RateLimitName("default")) {
            rateLimiter(limit = 10_000, refillPeriod = 1.minutes)
        }
    }

    installTestJwtAuth()

    install(Koin) {
        modules(module {
            single<UserRepository> { userRepo }
            single<RevokedTokenRepository> { revokedTokenRepo }
            single<PasswordResetRepository> { passwordResetRepo }
            single<EmailService> { emailService }
            single<EmailVerificationRepository> { emailVerificationRepo }
            single<PendingEmailVerificationRepository> { pendingEmailVerificationRepo }
            single { AdminUserUseCase(userRepository = userRepo) }
            single {
                RegistrationUseCase(
                    pendingEmailVerificationRepository = pendingEmailVerificationRepo,
                    userRepository = userRepo,
                    emailService = emailService
                )
            }
            single {
                PasswordResetUseCase(
                    userRepository = userRepo,
                    passwordResetRepository = passwordResetRepo,
                    emailService = emailService
                )
            }
            single {
                EmailVerificationUseCase(
                    emailVerificationRepository = emailVerificationRepo,
                    userRepository = userRepo,
                    emailService = emailService
                )
            }
        })
    }

    configureRouting()
}

/** Auth routes (so tests can log in as a created user) plus the admin routes under test. */
fun Application.testAdminModule(
    userRepo: UserRepository,
    adminSecret: String?,
    emailService: EmailService = FakeEmailService()
) {
    testAuthModule(
        passwordResetRepo = FakePasswordResetRepository(),
        userRepo = userRepo,
        emailService = emailService
    )
    admin(adminSecret)
}

fun Application.testLooksEmailVerificationModule(
    userRepo: UserRepository
) {
    install(ContentNegotiation) {
        json(Json { ignoreUnknownKeys = true })
    }
    configureErrorHandling()
    install(RateLimit) {
        register(RateLimitName("default")) { rateLimiter(limit = 10_000, refillPeriod = 1.minutes) }
        register(RateLimitName("upload")) { rateLimiter(limit = 10_000, refillPeriod = 1.minutes) }
        register(RateLimitName("generate")) { rateLimiter(limit = 10_000, refillPeriod = 1.minutes) }
    }
    installTestJwtAuth()
    install(Koin) {
        modules(module {
            single<UserRepository> { userRepo }
            single<com.example.database.domain.repository.LookRepository> { FakeLookRepository() }
            single<com.example.database.domain.repository.SharedLookRepository> { FakeSharedLookRepository() }
            single<com.example.database.domain.repository.UserLookRepository> { FakeUserLookRepository() }
            single {
                com.example.usecases.GenerateLookUseCase(
                    generateLookService = com.example.services.GenerateLookService(KtorHttpClient(CIO), "http://stub-never-called"),
                    lookRepository = FakeLookRepository()
                )
            }
            single { com.example.usecases.QuotaUseCase(userRepository = userRepo) }
        })
    }
    looks()
}
