package com.example.database.data.model

import kotlinx.serialization.Serializable
import org.jetbrains.exposed.v1.core.ReferenceOption
import org.jetbrains.exposed.v1.core.dao.id.EntityID
import org.jetbrains.exposed.v1.core.dao.id.IntIdTable
import org.jetbrains.exposed.v1.dao.IntEntity
import org.jetbrains.exposed.v1.dao.IntEntityClass

@Serializable
data class SharedLook(
    val id: Int? = null,
    val lookId: Int,
    val shareToken: String,
    val createdAt: Long
)

object SharedLookTable : IntIdTable("shared_looks") {
    val lookId = reference("look_id", LookTable, onDelete = ReferenceOption.CASCADE)
    val shareToken = varchar("share_token", 64).uniqueIndex()
    val createdAt = long("created_at")
}

class SharedLookDao(id: EntityID<Int>) : IntEntity(id) {
    companion object : IntEntityClass<SharedLookDao>(SharedLookTable)

    var lookId by SharedLookTable.lookId
    var shareToken by SharedLookTable.shareToken
    var createdAt by SharedLookTable.createdAt
    val look by LookDao referencedOn SharedLookTable.lookId
}

fun daoToModel(dao: SharedLookDao) = SharedLook(
    id = dao.id.value,
    lookId = dao.lookId.value,
    shareToken = dao.shareToken,
    createdAt = dao.createdAt
)

@Serializable
data class SharedLookDetails(
    val look: Look,
    val shareToken: String
)

@Serializable
data class ShareLinkResponse(
    val shareToken: String,
    val shareUrl: String
)

@Serializable
data class ImportRequest(
    val importType: ImportType,
    val clotheIds: List<Int>? = null
)

@Serializable
enum class ImportType {
    FULL_LOOK,
    SELECTED_ITEMS
}
