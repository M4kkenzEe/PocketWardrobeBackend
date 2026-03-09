package com.example.database.data.model

import kotlinx.serialization.Serializable
import org.jetbrains.exposed.v1.core.ReferenceOption
import org.jetbrains.exposed.v1.core.dao.id.EntityID
import org.jetbrains.exposed.v1.core.dao.id.IntIdTable
import org.jetbrains.exposed.v1.dao.IntEntity
import org.jetbrains.exposed.v1.dao.IntEntityClass

@Serializable
data class Look(
    val id: Int? = null,
    val name: String,
    val lookItems: List<LookItem>,
    val url: String
)

object LookTable : IntIdTable("looks") {
    val name = varchar("name", 50)
    val url = varchar("url", 255)
    val generatedByAi = bool("generated_by_ai").default(false)
}

class LookDao(id: EntityID<Int>) : IntEntity(id) {
    companion object : IntEntityClass<LookDao>(LookTable)

    var name by LookTable.name
    val lookItems by LookItemDao referrersOn LookItemTable.lookId
    var url by LookTable.url
    var generatedByAi by LookTable.generatedByAi
}

fun daoToModel(dao: LookDao): Look = Look(
    id = dao.id.value,
    name = dao.name,
    lookItems = dao.lookItems.map { daoToModel(it) },
    url = dao.url
)

@Serializable
data class LookDto(
    val name: String,
    val lookItems: List<LookItem>,
)