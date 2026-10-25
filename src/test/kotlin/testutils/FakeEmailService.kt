package com.example.testutils

import com.example.services.EmailService

class FakeEmailService : EmailService() {
    private val sent = mutableListOf<Pair<String, String>>()

    override suspend fun sendPasswordResetCode(toEmail: String, code: String) {
        sent.add(toEmail to code)
    }

    fun sentCodes(): List<Pair<String, String>> = sent.toList()
}
