package com.example.database.data.model

import org.jetbrains.exposed.v1.core.ReferenceOption
import org.jetbrains.exposed.v1.core.dao.id.EntityID
import org.jetbrains.exposed.v1.core.dao.id.IntIdTable
import org.jetbrains.exposed.v1.dao.IntEntity
import org.jetbrains.exposed.v1.dao.IntEntityClass

object PasswordResetCodeTable : IntIdTable("password_reset_codes") {
    val userId = reference("user_id", UserTable, onDelete = ReferenceOption.CASCADE)
    val code = varchar("code", 6)
    val expiresAt = long("expires_at")
    val isUsed = bool("is_used").default(false)
    val createdAt = long("created_at")
}

class PasswordResetCodeDao(id: EntityID<Int>) : IntEntity(id) {
    companion object : IntEntityClass<PasswordResetCodeDao>(PasswordResetCodeTable)

    var userId by PasswordResetCodeTable.userId
    var code by PasswordResetCodeTable.code
    var expiresAt by PasswordResetCodeTable.expiresAt
    var isUsed by PasswordResetCodeTable.isUsed
    var createdAt by PasswordResetCodeTable.createdAt
}

data class PasswordResetCode(
    val id: Int,
    val userId: Int,
    val code: String,
    val expiresAt: Long,
    val isUsed: Boolean,
    val createdAt: Long
)
