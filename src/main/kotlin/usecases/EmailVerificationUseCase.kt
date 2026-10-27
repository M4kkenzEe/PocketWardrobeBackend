package com.example.usecases

import com.example.database.data.model.User
import com.example.database.domain.repository.EmailVerificationRepository
import com.example.database.domain.repository.UserRepository
import com.example.services.EmailService
import org.slf4j.LoggerFactory
import java.security.MessageDigest

data class ResendVerificationCooldownException(val retryAfterSeconds: Int) :
    Exception("Please wait $retryAfterSeconds seconds before requesting a new code")

class EmailVerificationUseCase(
    private val emailVerificationRepository: EmailVerificationRepository,
    private val userRepository: UserRepository,
    private val emailService: EmailService
) {
    private val log = LoggerFactory.getLogger(EmailVerificationUseCase::class.java)

    /**
     * Generates a new verification code for the given user and sends it by email.
     * Email delivery failure is non-fatal — the code is persisted so the user
     * can trigger a resend later via /auth/resend-verification.
     */
    suspend fun sendCode(user: User) {
        val code = (100000..999999).random().toString()
        val codeHash = sha256(code)
        emailVerificationRepository.createCode(user.userId, codeHash)
        try {
            emailService.sendEmailVerificationCode(user.email, code)
        } catch (e: Exception) {
            log.error("Failed to send verification email to ${user.email}", e)
        }
    }

    /**
     * Validates the OTP and marks the user as email-verified.
     *
     * Returns:
     *   true  — code valid, user is now verified
     *   false — user was already verified (idempotent)
     *   null  — code is invalid or expired
     */
    suspend fun verifyCode(userId: Int, code: String): Boolean? {
        val user = userRepository.findUserById(userId) ?: return null
        if (user.emailVerified) return false

        val codeHash = sha256(code)
        val record = emailVerificationRepository.findValidCode(userId, codeHash) ?: return null

        emailVerificationRepository.deleteUserCodes(userId)
        userRepository.setEmailVerified(userId)
        return true
    }

    /**
     * Resends a verification code, enforcing a 60-second cooldown between sends.
     *
     * Returns false if the user is already verified (no-op).
     * Throws [ResendVerificationCooldownException] if the cooldown has not elapsed.
     */
    suspend fun resendCode(userId: Int): Boolean {
        val user = userRepository.findUserById(userId) ?: return false
        if (user.emailVerified) return false

        val latestCode = emailVerificationRepository.getLatestCode(userId)
        val now = System.currentTimeMillis()
        if (latestCode != null && (now - latestCode.createdAt) < 60_000L) {
            val retryAfterSeconds = ((60_000L - (now - latestCode.createdAt)) / 1000).toInt() + 1
            throw ResendVerificationCooldownException(retryAfterSeconds)
        }

        emailVerificationRepository.deleteUserCodes(userId)
        sendCode(user)
        return true
    }

    private fun sha256(input: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        return digest.digest(input.toByteArray()).joinToString("") { "%02x".format(it) }
    }
}
