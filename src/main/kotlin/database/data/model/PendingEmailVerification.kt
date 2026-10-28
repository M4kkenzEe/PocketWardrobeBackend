package com.example.database.data.model

import org.jetbrains.exposed.v1.core.dao.id.EntityID
import org.jetbrains.exposed.v1.core.dao.id.IntIdTable
import org.jetbrains.exposed.v1.dao.IntEntity
import org.jetbrains.exposed.v1.dao.IntEntityClass

/** OTP for an email that has no account yet; at most one row per email. */
object PendingEmailVerificationTable : IntIdTable("pending_email_verifications") {
    val email = varchar("email", 100).uniqueIndex()
    val codeHash = varchar("code_hash", 64)
    val expiresAt = long("expires_at")
    val attempts = integer("attempts").default(0)
    val createdAt = long("created_at")
}

class PendingEmailVerificationDao(id: EntityID<Int>) : IntEntity(id) {
    companion object : IntEntityClass<PendingEmailVerificationDao>(PendingEmailVerificationTable)

    var email by PendingEmailVerificationTable.email
    var codeHash by PendingEmailVerificationTable.codeHash
    var expiresAt by PendingEmailVerificationTable.expiresAt
    var attempts by PendingEmailVerificationTable.attempts
    var createdAt by PendingEmailVerificationTable.createdAt
}

data class PendingEmailVerification(
    val id: Int,
    val email: String,
    val codeHash: String,
    val expiresAt: Long,
    val attempts: Int,
    val createdAt: Long
)
