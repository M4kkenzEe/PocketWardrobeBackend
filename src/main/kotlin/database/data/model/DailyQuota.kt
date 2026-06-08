package com.example.database.data.model

import kotlinx.datetime.LocalDate
import org.jetbrains.exposed.v1.core.ReferenceOption
import org.jetbrains.exposed.v1.core.dao.id.EntityID
import org.jetbrains.exposed.v1.core.dao.id.IntIdTable
import org.jetbrains.exposed.v1.dao.IntEntity
import org.jetbrains.exposed.v1.dao.IntEntityClass
import org.jetbrains.exposed.v1.datetime.date

object DailyQuotaTable : IntIdTable("daily_quotas") {
    val userId = reference("user_id", UserTable, onDelete = ReferenceOption.CASCADE)
    val date = date("date")
    val recommendationsUsed = integer("recommendations_used").default(0)

    init {
        uniqueIndex(userId, date)
    }
}

class DailyQuotaDao(id: EntityID<Int>) : IntEntity(id) {
    companion object : IntEntityClass<DailyQuotaDao>(DailyQuotaTable)

    var userId by DailyQuotaTable.userId
    var date by DailyQuotaTable.date
    var recommendationsUsed by DailyQuotaTable.recommendationsUsed
}
