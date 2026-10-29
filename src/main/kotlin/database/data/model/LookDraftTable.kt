package com.example.database.data.model

import org.jetbrains.exposed.v1.core.ReferenceOption
import org.jetbrains.exposed.v1.core.dao.id.EntityID
import org.jetbrains.exposed.v1.core.dao.id.IntIdTable
import org.jetbrains.exposed.v1.dao.IntEntity
import org.jetbrains.exposed.v1.dao.IntEntityClass

/**
 * A look rendered around an anchor clothe that the user has not added to "my looks" yet.
 * Kept apart from user_looks so every user_looks reader (list, profile stats, sharing, dedup in
 * RecommendationService) ignores drafts without changes.
 */
object LookDraftTable : IntIdTable("look_drafts") {
    val userId = reference("user_id", UserTable, onDelete = ReferenceOption.CASCADE).index()
    val lookId = reference("look_id", LookTable, onDelete = ReferenceOption.CASCADE).uniqueIndex()
    val anchorClotheId = integer("anchor_clothe_id")
    val createdAt = long("created_at")
    val savedAt = long("saved_at").nullable()
}

class LookDraftDao(id: EntityID<Int>) : IntEntity(id) {
    companion object : IntEntityClass<LookDraftDao>(LookDraftTable)

    var userId by LookDraftTable.userId
    var lookId by LookDraftTable.lookId
    var anchorClotheId by LookDraftTable.anchorClotheId
    var createdAt by LookDraftTable.createdAt
    var savedAt by LookDraftTable.savedAt
}
