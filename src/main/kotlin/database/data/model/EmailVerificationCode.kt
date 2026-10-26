package com.example.database.data.model

import org.jetbrains.exposed.v1.core.ReferenceOption
import org.jetbrains.exposed.v1.core.dao.id.EntityID
import org.jetbrains.exposed.v1.core.dao.id.IntIdTable
import org.jetbrains.exposed.v1.dao.IntEntity
import org.jetbrains.exposed.v1.dao.IntEntityClass

object EmailVerificationCodeTable : IntIdTable("email_verification_codes") {
    val userId = reference("user_id", UserTable, onDelete = ReferenceOption.CASCADE)
    val codeHash = varchar("code_hash", 64)
    val expiresAt = long("expires_at")
    val isUsed = bool("is_used").default(false)
    val createdAt = long("created_at")
}

class EmailVerificationCodeDao(id: EntityID<Int>) : IntEntity(id) {
    companion object : IntEntityClass<EmailVerificationCodeDao>(EmailVerificationCodeTable)

    var userId by EmailVerificationCodeTable.userId
    var codeHash by EmailVerificationCodeTable.codeHash
    var expiresAt by EmailVerificationCodeTable.expiresAt
    var isUsed by EmailVerificationCodeTable.isUsed
    var createdAt by EmailVerificationCodeTable.createdAt
}

data class EmailVerificationCode(
    val id: Int,
    val userId: Int,
    val codeHash: String,
    val expiresAt: Long,
    val isUsed: Boolean,
    val createdAt: Long
)
