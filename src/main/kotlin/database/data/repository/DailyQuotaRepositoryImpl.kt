package com.example.database.data.repository

import com.example.database.data.model.DailyQuotaDao
import com.example.database.data.model.DailyQuotaTable
import com.example.database.data.model.UserTable
import com.example.database.domain.repository.DailyQuotaRepository
import com.example.database.suspendTransaction
import kotlinx.datetime.LocalDate
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.dao.id.EntityID

class DailyQuotaRepositoryImpl : DailyQuotaRepository {

    override suspend fun getOrCreate(userId: Int, date: LocalDate): Int = suspendTransaction {
        val existing = DailyQuotaDao.find {
            (DailyQuotaTable.userId eq userId) and (DailyQuotaTable.date eq date)
        }.firstOrNull()

        existing?.recommendationsUsed ?: run {
            DailyQuotaDao.new {
                this.userId = EntityID(userId, UserTable)
                this.date = date
                this.recommendationsUsed = 0
            }.recommendationsUsed
        }
    }

    override suspend fun increment(userId: Int, date: LocalDate): Unit = suspendTransaction {
        val row = DailyQuotaDao.find {
            (DailyQuotaTable.userId eq userId) and (DailyQuotaTable.date eq date)
        }.firstOrNull() ?: DailyQuotaDao.new {
            this.userId = EntityID(userId, UserTable)
            this.date = date
            this.recommendationsUsed = 0
        }
        row.recommendationsUsed += 1
    }

    override suspend fun getCount(userId: Int, date: LocalDate): Int = suspendTransaction {
        DailyQuotaDao.find {
            (DailyQuotaTable.userId eq userId) and (DailyQuotaTable.date eq date)
        }.firstOrNull()?.recommendationsUsed ?: 0
    }
}
