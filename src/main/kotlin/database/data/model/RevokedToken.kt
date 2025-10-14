package com.example.database.data.model

import org.jetbrains.exposed.v1.core.ReferenceOption
import org.jetbrains.exposed.v1.core.dao.id.EntityID
import org.jetbrains.exposed.v1.core.dao.id.IntIdTable
import org.jetbrains.exposed.v1.dao.IntEntity
import org.jetbrains.exposed.v1.dao.IntEntityClass

object RevokedTokenTable : IntIdTable("revoked_tokens") {
    val jti = varchar("jti", 36).uniqueIndex()
    val userId = reference("user_id", UserTable, onDelete = ReferenceOption.CASCADE)
    val expiresAt = long("expires_at")
}

class RevokedTokenDao(id: EntityID<Int>) : IntEntity(id) {
    companion object : IntEntityClass<RevokedTokenDao>(RevokedTokenTable)

    var jti by RevokedTokenTable.jti
    var userId by RevokedTokenTable.userId
    var expiresAt by RevokedTokenTable.expiresAt
}
