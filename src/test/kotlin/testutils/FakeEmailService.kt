package com.example.testutils

import com.example.services.EmailService

class FakeEmailService : EmailService() {
    private val sentPasswordResets = mutableListOf<Pair<String, String>>()
    private val sentVerificationCodes = mutableListOf<Pair<String, String>>()

    override suspend fun sendPasswordResetCode(toEmail: String, code: String) {
        sentPasswordResets.add(toEmail to code)
    }

    override suspend fun sendEmailVerificationCode(toEmail: String, code: String) {
        sentVerificationCodes.add(toEmail to code)
    }

    fun sentCodes(): List<Pair<String, String>> = sentPasswordResets.toList()
    fun sentVerifications(): List<Pair<String, String>> = sentVerificationCodes.toList()
}
