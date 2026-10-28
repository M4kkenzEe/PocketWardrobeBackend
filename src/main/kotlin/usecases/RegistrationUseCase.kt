package com.example.usecases

import com.example.auth.service.JWTConfig
import com.example.database.data.model.User
import com.example.database.domain.repository.PendingEmailVerificationRepository
import com.example.database.domain.repository.UserRepository
import com.example.services.EmailService
import java.security.MessageDigest

private const val MAX_ATTEMPTS = 5
private const val RESEND_COOLDOWN_MS = 60_000L

/**
 * Registration where the account is created last: the email is proven with an OTP first
 * (no user row exists until then), and /register only accepts the resulting token.
 */
class RegistrationUseCase(
    private val pendingEmailVerificationRepository: PendingEmailVerificationRepository,
    private val userRepository: UserRepository,
    private val emailService: EmailService
) {
    /**
     * Throws [IllegalStateException] if the email already belongs to a user and
     * [ResendVerificationCooldownException] if a code was sent less than a minute ago.
     */
    suspend fun startVerification(email: String) {
        pendingEmailVerificationRepository.deleteExpired()
        check(userRepository.findUserByEmail(email) == null) { "User with this email already exists" }

        val now = System.currentTimeMillis()
        val previous = pendingEmailVerificationRepository.find(email)
        if (previous != null && now - previous.createdAt < RESEND_COOLDOWN_MS) {
            val retryAfterSeconds = ((RESEND_COOLDOWN_MS - (now - previous.createdAt)) / 1000).toInt() + 1
            throw ResendVerificationCooldownException(retryAfterSeconds)
        }

        val code = (100000..999999).random().toString()
        pendingEmailVerificationRepository.replaceCode(email, sha256(code))
        try {
            emailService.sendEmailVerificationCode(email, code)
        } catch (e: Exception) {
            // Without the mail the code is useless and would only block a retry through the cooldown.
            pendingEmailVerificationRepository.delete(email)
            throw e
        }
    }

    /**
     * Checks the OTP and returns a short-lived registration token for [email].
     * Throws [IllegalArgumentException] for a wrong or expired code; a code stops working after [MAX_ATTEMPTS] attempts.
     */
    suspend fun verifyCode(email: String, code: String): String {
        // The attempt is claimed by one atomic UPDATE before the hash is compared, so parallel
        // guesses cannot get more than MAX_ATTEMPTS comparisons out of a single code.
        if (!pendingEmailVerificationRepository.claimAttempt(email, MAX_ATTEMPTS)) {
            pendingEmailVerificationRepository.delete(email)
            throw IllegalArgumentException(INVALID_CODE_MESSAGE)
        }

        val pending = pendingEmailVerificationRepository.find(email)
        require(pending != null) { INVALID_CODE_MESSAGE }
        require(MessageDigest.isEqual(sha256(code).toByteArray(), pending.codeHash.toByteArray())) {
            INVALID_CODE_MESSAGE
        }

        pendingEmailVerificationRepository.delete(email)
        return JWTConfig.generateRegistrationToken(email)
    }

    /**
     * Creates the account as already email-verified.
     * Returns null when the username or email is taken; throws [IllegalArgumentException]
     * when the token is missing, invalid, expired or was issued for another email.
     */
    suspend fun register(user: User, verificationToken: String?): User? {
        val verifiedEmail = verificationToken?.let { JWTConfig.verifyRegistrationToken(it) }
        require(verifiedEmail != null) { "Invalid or expired verification token" }
        require(verifiedEmail == user.email) { "Verification token was issued for a different email" }

        return userRepository.register(user.copy(emailVerified = true))
    }

    private fun sha256(input: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        return digest.digest(input.toByteArray()).joinToString("") { "%02x".format(it) }
    }

    private companion object {
        const val INVALID_CODE_MESSAGE = "Invalid or expired code"
    }
}
