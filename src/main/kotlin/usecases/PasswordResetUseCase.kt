package com.example.usecases

import com.example.database.data.model.User
import com.example.database.domain.repository.PasswordResetRepository
import com.example.database.domain.repository.UserRepository
import com.example.services.EmailService
import org.mindrot.jbcrypt.BCrypt
import validation.AuthValidator

private val RESET_CODE_FORMAT = Regex("^\\d{6}$")

/** [reason] goes to clients as the stable `error` code of the 400 response. */
enum class PasswordResetFailure { INVALID_CODE, CODE_EXPIRED, INVALID_PASSWORD }

class PasswordResetException(val reason: PasswordResetFailure, message: String) : RuntimeException(message)

class PasswordResetUseCase(
    private val userRepository: UserRepository,
    private val passwordResetRepository: PasswordResetRepository,
    private val emailService: EmailService
) {
    /**
     * Sends a new code to the account's address and invalidates the previous one.
     * Does nothing for an unknown email, so the caller can answer the same way for both.
     */
    suspend fun requestCode(email: String) {
        passwordResetRepository.deleteExpiredCodes()
        val user = findAccount(email) ?: return

        val code = (100000..999999).random().toString()
        passwordResetRepository.deleteUserCodes(user.userId)
        passwordResetRepository.createCode(user.userId, code)
        emailService.sendPasswordResetCode(user.email, code)
    }

    /**
     * Sets the new password, invalidates existing sessions and returns the updated user.
     * Throws [PasswordResetException]; an unknown email is reported as [PasswordResetFailure.INVALID_CODE]
     * so the endpoint does not reveal which emails are registered.
     */
    suspend fun resetPassword(email: String, code: String, newPassword: String): User {
        requireValidNewPassword(newPassword)
        val normalizedCode = code.trim()
        if (!RESET_CODE_FORMAT.matches(normalizedCode)) throw invalidCode()

        val user = findAccount(email) ?: throw invalidCode()
        val resetCode = passwordResetRepository.findCode(user.userId, normalizedCode) ?: throw invalidCode()
        if (resetCode.isUsed || resetCode.expiresAt <= System.currentTimeMillis()) {
            throw PasswordResetException(PasswordResetFailure.CODE_EXPIRED, "Code has expired or was already used")
        }

        // Sessions are cut before the password changes: if the second write fails, the user is logged out
        // with the old password still valid, never left with a new password and the thief's session alive.
        userRepository.incrementTokenVersion(user.userId)
        userRepository.updatePassword(user.userId, BCrypt.hashpw(newPassword, BCrypt.gensalt()))
        passwordResetRepository.markUsed(resetCode.id)
        passwordResetRepository.deleteUserCodes(user.userId)

        return userRepository.findUserById(user.userId)
            ?: error("User ${user.userId} disappeared during password reset")
    }

    // Legacy rows may differ only by letter case: the exact address must win, or the younger
    // account's owner would reset the older one.
    private suspend fun findAccount(email: String): User? {
        val trimmedEmail = email.trim()
        return userRepository.findUserByEmail(trimmedEmail) ?: userRepository.findUserByEmailIgnoreCase(trimmedEmail)
    }

    private fun requireValidNewPassword(newPassword: String) {
        try {
            AuthValidator.validateNewPassword(newPassword)
        } catch (e: IllegalArgumentException) {
            throw PasswordResetException(PasswordResetFailure.INVALID_PASSWORD, e.message ?: "Invalid password")
        }
    }

    private fun invalidCode() = PasswordResetException(PasswordResetFailure.INVALID_CODE, "Invalid code or email")
}
