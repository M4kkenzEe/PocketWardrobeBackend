package com.example.services

import com.example.auth.EnvironmentConfig
import jakarta.mail.Authenticator
import jakarta.mail.Message
import jakarta.mail.PasswordAuthentication
import jakarta.mail.Session
import jakarta.mail.Transport
import jakarta.mail.internet.InternetAddress
import jakarta.mail.internet.MimeMessage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.slf4j.LoggerFactory
import java.util.Properties

class EmailService {
    private val log = LoggerFactory.getLogger(EmailService::class.java)

    private val session: Session? by lazy {
        if (!EnvironmentConfig.isEmailConfigured) return@lazy null
        val props = Properties().apply {
            put("mail.smtp.auth", "true")
            put("mail.smtp.starttls.enable", "true")
            put("mail.smtp.host", EnvironmentConfig.smtpHost!!)
            put("mail.smtp.port", EnvironmentConfig.smtpPort!!.toString())
            put("mail.smtp.ssl.trust", EnvironmentConfig.smtpHost!!)
        }
        Session.getInstance(props, object : Authenticator() {
            override fun getPasswordAuthentication() =
                PasswordAuthentication(EnvironmentConfig.smtpUser, EnvironmentConfig.smtpPassword)
        })
    }

    suspend fun sendPasswordResetCode(toEmail: String, code: String) = withContext(Dispatchers.IO) {
        val s = session ?: run {
            log.warn("SMTP not configured — skipping email to $toEmail (code: $code)")
            return@withContext
        }
        log.info("Sending password reset code to $toEmail")
        val message = MimeMessage(s).apply {
            setFrom(InternetAddress(EnvironmentConfig.smtpFrom))
            setRecipients(Message.RecipientType.TO, InternetAddress.parse(toEmail))
            subject = "Код восстановления PocketWardrobe"
            setText("Ваш код: $code\nКод действителен 15 минут.")
        }
        Transport.send(message)
        log.info("Password reset code sent to $toEmail")
    }
}
