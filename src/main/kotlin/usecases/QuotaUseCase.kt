package com.example.usecases

import com.example.database.data.model.DailyQuotaDao
import com.example.database.data.model.DailyQuotaTable
import com.example.database.data.model.UserTable
import com.example.database.data.model.isProActive
import com.example.database.domain.repository.UserRepository
import com.example.database.suspendTransaction
import kotlinx.datetime.Clock
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.plus
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.dao.id.EntityID

data class QuotaExceededException(val used: Int, val limit: Int, val resetAt: String) : Exception()

class QuotaUseCase(
    private val userRepository: UserRepository
) {
    suspend fun checkAndConsume(userId: Int) {
        val user = userRepository.findUserById(userId) ?: throw IllegalStateException("User not found")
        if (user.isProActive()) return

        val moscowTz = TimeZone.of("Europe/Moscow")
        val today = Clock.System.now().toLocalDateTime(moscowTz).date
        val tomorrow = today.plus(1, DateTimeUnit.DAY)
        val tomorrowMidnight = LocalDateTime(tomorrow, LocalTime(0, 0, 0))
        val resetAt = tomorrowMidnight.toInstant(moscowTz).toString()

        suspendTransaction {
            val row = DailyQuotaDao.find {
                (DailyQuotaTable.userId eq userId) and (DailyQuotaTable.date eq today)
            }.firstOrNull() ?: DailyQuotaDao.new {
                this.userId = EntityID(userId, UserTable)
                this.date = today
                this.recommendationsUsed = 0
            }

            if (row.recommendationsUsed >= 1) {
                throw QuotaExceededException(used = row.recommendationsUsed, limit = 1, resetAt = resetAt)
            }
            row.recommendationsUsed += 1
        }
    }
}
