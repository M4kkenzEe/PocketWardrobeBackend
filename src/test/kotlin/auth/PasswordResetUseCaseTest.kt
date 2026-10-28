package com.example.auth

import com.example.database.data.model.PasswordResetCode
import com.example.database.data.model.User
import com.example.database.domain.repository.UserRepository
import com.example.testutils.FakeEmailService
import com.example.testutils.FakePasswordResetRepository
import com.example.testutils.InMemoryUserRepository
import com.example.usecases.PasswordResetException
import com.example.usecases.PasswordResetFailure
import com.example.usecases.PasswordResetUseCase
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PasswordResetUseCaseTest {

    private val userRepo = InMemoryUserRepository().apply {
        seed(User(userId = 1, username = "owner", email = "owner@example.com", passwordHash = "old-hash", gender = null))
    }
    private val resetRepo = FakePasswordResetRepository()
    private val emailService = FakeEmailService()
    private val useCase = PasswordResetUseCase(userRepo, resetRepo, emailService)

    private fun seedCode(code: String, expiresAt: Long = System.currentTimeMillis() + 60_000, isUsed: Boolean = false) {
        resetRepo.addCode(PasswordResetCode(id = 1, userId = 1, code = code, expiresAt = expiresAt, isUsed = isUsed, createdAt = 0))
    }

    private suspend fun failureOf(email: String = "owner@example.com", code: String = "123456", password: String = "NewPass12"): PasswordResetFailure =
        assertFailsWith<PasswordResetException> { useCase.resetPassword(email, code, password) }.reason

    @Test
    fun `unknown email gets no code and no email`() = runTest {
        useCase.requestCode("nobody@example.com")

        assertTrue(emailService.sentCodes().isEmpty())
        assertTrue(resetRepo.getCodes().isEmpty())
    }

    @Test
    fun `code is sent to the stored address when the typed email differs in case and spaces`() = runTest {
        useCase.requestCode(" OWNER@Example.com  ")

        assertEquals("owner@example.com", emailService.sentCodes().single().first)
    }

    private suspend fun assertAccountUntouched() {
        val user = userRepo.findUserById(1)!!
        assertEquals("old-hash", user.passwordHash)
        assertEquals(0L, user.tokenVersion, "A rejected reset must not end the user's sessions")
    }

    @Test
    fun `wrong code is INVALID_CODE and leaves the account untouched`() = runTest {
        seedCode("123456")

        assertEquals(PasswordResetFailure.INVALID_CODE, failureOf(code = "654321"))
        assertAccountUntouched()
    }

    @Test
    fun `code that is not six digits is INVALID_CODE`() = runTest {
        assertEquals(PasswordResetFailure.INVALID_CODE, failureOf(code = "12 456"))
    }

    @Test
    fun `unknown email is INVALID_CODE, same as a wrong code`() = runTest {
        seedCode("123456")

        assertEquals(PasswordResetFailure.INVALID_CODE, failureOf(email = "nobody@example.com"))
    }

    @Test
    fun `expired code is CODE_EXPIRED and leaves the account untouched`() = runTest {
        seedCode("123456", expiresAt = System.currentTimeMillis() - 1)

        assertEquals(PasswordResetFailure.CODE_EXPIRED, failureOf())
        assertAccountUntouched()
    }

    @Test
    fun `used code is CODE_EXPIRED and leaves the account untouched`() = runTest {
        seedCode("123456", isUsed = true)

        assertEquals(PasswordResetFailure.CODE_EXPIRED, failureOf())
        assertAccountUntouched()
    }

    @Test
    fun `password without a digit is INVALID_PASSWORD`() = runTest {
        seedCode("123456")

        assertEquals(PasswordResetFailure.INVALID_PASSWORD, failureOf(password = "NoDigitsHere"))
    }

    @Test
    fun `successful reset invalidates old sessions and burns the code`() = runTest {
        seedCode("123456")

        val user = useCase.resetPassword("owner@example.com", "123456", "NewPass12")

        assertEquals(1L, user.tokenVersion, "Refresh tokens issued before the reset must stop working (EPIC-20)")
        assertTrue(resetRepo.getCodes().isEmpty())
    }

    @Test
    fun `exact address wins over an older account that differs only by case`() = runTest {
        userRepo.seed(User(userId = 2, username = "younger", email = "Owner@Example.com", passwordHash = "younger-hash", gender = null))

        useCase.requestCode("Owner@Example.com")
        val code = emailService.sentCodes().single().second
        val user = useCase.resetPassword("Owner@Example.com", code, "NewPass12")

        assertEquals(2, user.userId)
        assertEquals("old-hash", userRepo.findUserById(1)!!.passwordHash, "The older look-alike account must keep its password")
    }

    @Test
    fun `failed password write leaves sessions already cut, old password and the code usable`() = runTest {
        seedCode("123456")
        val failingPasswordWrite = object : UserRepository by userRepo {
            override suspend fun updatePassword(userId: Int, newPasswordHash: String) = error("DB down")
        }
        val resetWithFailingWrite = PasswordResetUseCase(failingPasswordWrite, resetRepo, emailService)

        assertFailsWith<IllegalStateException> { resetWithFailingWrite.resetPassword("owner@example.com", "123456", "NewPass12") }

        val user = userRepo.findUserById(1)!!
        assertEquals(1L, user.tokenVersion, "Sessions must be cut before the password is written")
        assertEquals("old-hash", user.passwordHash)
        assertFalse(resetRepo.getCodes().single().isUsed, "The code must survive so the user can retry")
    }
}
