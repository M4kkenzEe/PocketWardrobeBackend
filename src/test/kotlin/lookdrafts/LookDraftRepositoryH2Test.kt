package com.example.lookdrafts

import com.example.database.data.model.ClotheDao
import com.example.database.data.model.ClotheTable
import com.example.database.data.model.LookDao
import com.example.database.data.model.LookDraftTable
import com.example.database.data.model.LookItemDao
import com.example.database.data.model.LookItemTable
import com.example.database.data.model.LookTable
import com.example.database.data.model.UserClotheTable
import com.example.database.data.model.UserDao
import com.example.database.data.model.UserLookTable
import com.example.database.data.model.UserTable
import com.example.database.data.repository.LookDraftRepositoryImpl
import com.example.database.data.repository.UserLookRepositoryImpl
import com.example.database.domain.repository.LookDraftRepository
import com.example.database.domain.repository.LookRepository
import com.example.database.domain.repository.UserLookRepository
import database.data.repository.LookRepositoryImpl
import kotlinx.coroutines.runBlocking
import org.jetbrains.exposed.v1.core.dao.id.EntityID
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.SchemaUtils
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import java.util.UUID
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The production Exposed queries against in-memory H2. The route tests run on fakes, so this is the only
 * place where the real draft access rules and clothe-set lookups are executed.
 */
class LookDraftRepositoryH2Test {

    private val drafts: LookDraftRepository = LookDraftRepositoryImpl()
    private val looks: LookRepository = LookRepositoryImpl()
    private val userLooks: UserLookRepository = UserLookRepositoryImpl()

    private var ownerId = 0
    private var strangerId = 0
    private val clotheIds = mutableListOf<Int>()

    @BeforeTest
    fun setUp() {
        Database.connect("jdbc:h2:mem:${UUID.randomUUID()};DB_CLOSE_DELAY=-1", driver = "org.h2.Driver")
        transaction {
            SchemaUtils.create(
                UserTable, ClotheTable, UserClotheTable, LookTable, UserLookTable, LookItemTable, LookDraftTable
            )
            ownerId = createUser("owner")
            strangerId = createUser("stranger")
            repeat(4) { clotheIds += createClothe() }
        }
    }

    private fun createUser(name: String): Int = UserDao.new {
        username = name
        email = "$name@test.com"
        passwordHash = "hash"
    }.id.value

    private fun createClothe(): Int = ClotheDao.new {
        name = "clothe"
        imageUrl = ""
        storeUrl = ""
    }.id.value

    private fun createLookWith(vararg ids: Int): Int = transaction {
        val look = LookDao.new {
            name = "look"
            url = ""
        }
        ids.forEach { clotheId ->
            LookItemDao.new {
                lookId = look.id
                this.clotheId = EntityID(clotheId, ClotheTable)
                size = 1
                x = 0f
                y = 0f
                z = 0f
                rotation = 0f
            }
        }
        look.id.value
    }

    @Test
    fun `unsaved draft is found by a clothe it contains with its full clothe set`() = runBlocking {
        val lookId = createLookWith(clotheIds[0], clotheIds[1], clotheIds[2])
        drafts.createDraft(ownerId, lookId, clotheIds[0])

        val found = drafts.findUnsavedContainingClothe(ownerId, clotheIds[1]).single()

        assertEquals(lookId, found.lookId)
        assertEquals(setOf(clotheIds[0], clotheIds[1], clotheIds[2]), found.clotheIds)
        assertFalse(found.isSaved)
    }

    @Test
    fun `draft of another user or without the clothe is not found`() = runBlocking {
        val lookId = createLookWith(clotheIds[0], clotheIds[1])
        drafts.createDraft(ownerId, lookId, clotheIds[0])

        assertTrue(drafts.findUnsavedContainingClothe(strangerId, clotheIds[0]).isEmpty())
        assertTrue(drafts.findUnsavedContainingClothe(ownerId, clotheIds[3]).isEmpty())
        assertNull(drafts.findDraft(strangerId, lookId))
    }

    @Test
    fun `saved draft is no longer offered for reuse`() = runBlocking {
        val lookId = createLookWith(clotheIds[0], clotheIds[1])
        drafts.createDraft(ownerId, lookId, clotheIds[0])

        drafts.markSaved(lookId)

        assertTrue(drafts.findUnsavedContainingClothe(ownerId, clotheIds[0]).isEmpty())
        assertTrue(drafts.findDraft(ownerId, lookId)!!.isSaved)
    }

    @Test
    fun `getLookById gives the owner an unsaved draft marked isDraft`() = runBlocking {
        val lookId = createLookWith(clotheIds[0], clotheIds[1])
        drafts.createDraft(ownerId, lookId, clotheIds[0])

        val look = looks.getLookById(lookId, ownerId)

        assertTrue(look.isDraft)
        assertEquals(2, look.lookItems.size)
    }

    @Test
    fun `getLookById refuses another user's draft`() = runBlocking {
        val lookId = createLookWith(clotheIds[0], clotheIds[1])
        drafts.createDraft(ownerId, lookId, clotheIds[0])

        assertFailsWith<IllegalArgumentException> { looks.getLookById(lookId, strangerId) }
        Unit
    }

    @Test
    fun `getLookById marks a look in user looks as not a draft even when its draft row exists`() = runBlocking {
        val lookId = createLookWith(clotheIds[0], clotheIds[1])
        drafts.createDraft(ownerId, lookId, clotheIds[0])
        userLooks.addLookToUser(ownerId, lookId)
        drafts.markSaved(lookId)

        assertFalse(looks.getLookById(lookId, ownerId).isDraft)
    }

    @Test
    fun `getLookById refuses a saved draft after the user removed it from my looks`() = runBlocking {
        val lookId = createLookWith(clotheIds[0], clotheIds[1])
        drafts.createDraft(ownerId, lookId, clotheIds[0])
        userLooks.addLookToUser(ownerId, lookId)
        drafts.markSaved(lookId)
        userLooks.removeLookFromUser(ownerId, lookId)

        assertFailsWith<IllegalArgumentException> { looks.getLookById(lookId, ownerId) }
        Unit
    }

    @Test
    fun `draft is absent from the look list and from saved clothe sets until added to user looks`() = runBlocking {
        val lookId = createLookWith(clotheIds[0], clotheIds[1])
        drafts.createDraft(ownerId, lookId, clotheIds[0])

        assertTrue(looks.getLookList(ownerId).isEmpty())
        assertTrue(looks.getSavedClotheIdsByLook(ownerId).isEmpty())

        userLooks.addLookToUser(ownerId, lookId)

        assertEquals(listOf(lookId), looks.getLookList(ownerId).map { it.id })
        assertEquals(mapOf(lookId to setOf(clotheIds[0], clotheIds[1])), looks.getSavedClotheIdsByLook(ownerId))
    }
}
