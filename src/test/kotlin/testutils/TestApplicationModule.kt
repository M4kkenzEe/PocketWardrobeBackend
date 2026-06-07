package com.example.testutils

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import com.example.auth.model.UserPrincipal
import com.example.database.domain.repository.ClotheRepository
import com.example.database.domain.repository.UserClotheRepository
import com.example.database.domain.repository.UserRepository
import com.example.routes.clothes
import com.example.services.ClotheAnalysisService
import com.example.services.RemoveBgService
import com.example.usecases.ClotheUseCase
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
import kotlin.time.Duration.Companion.minutes
import io.ktor.client.HttpClient as KtorHttpClient

const val TEST_JWT_SECRET   = "test-secret-key-for-pocket-wardrobe-tests-32ch"
const val TEST_JWT_ISSUER   = "pocket-wardrobe-test"
const val TEST_JWT_AUDIENCE = "pocket-wardrobe-test-audience"

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
