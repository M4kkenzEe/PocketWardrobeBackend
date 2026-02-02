package com.example.database.data.model

import kotlinx.serialization.Serializable
import org.jetbrains.exposed.v1.core.ReferenceOption
import org.jetbrains.exposed.v1.core.dao.id.EntityID
import org.jetbrains.exposed.v1.core.dao.id.IntIdTable
import org.jetbrains.exposed.v1.dao.IntEntity
import org.jetbrains.exposed.v1.dao.IntEntityClass

@Serializable
data class RefreshToken(
    val id: Int,
    val userId: Int,
    val tokenHash: String,
    val expiresAt: Long,
    val createdAt: Long,
    val userAgent: String? = null,
    val ipAddress: String? = null
)

object RefreshTokenTable : IntIdTable("refresh_tokens") {
    val userId = reference("user_id", UserTable, onDelete = ReferenceOption.CASCADE)
    val tokenHash = varchar("token_hash", 256).uniqueIndex()  // SHA-256 hash
    val expiresAt = long("expires_at").index()
    val createdAt = long("created_at")
    val userAgent = varchar("user_agent", 512).nullable()
    val ipAddress = varchar("ip_address", 45).nullable()  // IPv6 max length
}

class RefreshTokenDao(id: EntityID<Int>) : IntEntity(id) {
    companion object : IntEntityClass<RefreshTokenDao>(RefreshTokenTable)

    var userId by RefreshTokenTable.userId
    var tokenHash by RefreshTokenTable.tokenHash
    var expiresAt by RefreshTokenTable.expiresAt
    var createdAt by RefreshTokenTable.createdAt
    var userAgent by RefreshTokenTable.userAgent
    var ipAddress by RefreshTokenTable.ipAddress
}

fun daoToModel(dao: RefreshTokenDao) = RefreshToken(
    id = dao.id.value,
    userId = dao.userId.value,
    tokenHash = dao.tokenHash,
    expiresAt = dao.expiresAt,
    createdAt = dao.createdAt,
    userAgent = dao.userAgent,
    ipAddress = dao.ipAddress
)
