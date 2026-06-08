package com.example.routes

import com.example.auth.model.UserPrincipal
import com.example.database.domain.repository.DailyQuotaRepository
import com.example.database.domain.repository.UserClotheRepository
import com.example.database.domain.repository.UserLookRepository
import com.example.database.domain.repository.UserRepository
import com.example.profile.toProfileResponse
import io.ktor.server.application.Application
import io.ktor.server.auth.authenticate
import io.ktor.server.auth.principal
import io.ktor.server.plugins.ratelimit.*
import io.ktor.server.response.respond
import io.ktor.server.routing.get
import io.ktor.server.routing.route
import io.ktor.server.routing.routing
import kotlinx.datetime.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import org.koin.ktor.ext.inject
import io.ktor.server.plugins.ratelimit.RateLimitName

fun Application.profile() {
    val userRepository: UserRepository by inject()
    val userClotheRepository: UserClotheRepository by inject()
    val userLookRepository: UserLookRepository by inject()
    val dailyQuotaRepository: DailyQuotaRepository by inject()
    routing {
        authenticate {
            route("/api/v1") {
                rateLimit(RateLimitName("default")) {
                    route("/profile") {
                        get {
                            val userId = call.principal<UserPrincipal>()?.userId
                                ?: throw IllegalStateException("User not authenticated")
                            val user = userRepository.findUserById(userId)
                                ?: throw IllegalStateException("User not found")
                            val clothesCount = userClotheRepository.countByUserId(userId)
                            val looksCount = userLookRepository.countByUserId(userId)
                            val today = Clock.System.now().toLocalDateTime(TimeZone.of("Europe/Moscow")).date
                            val quotaToday = dailyQuotaRepository.getCount(userId, today)
                            call.respond(user.toProfileResponse(clothesCount, looksCount, quotaToday))
                        }
                    }
                }
            }
        }
    }
}