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

open class EmailService {
    private val log = LoggerFactory.getLogger(EmailService::class.java)

    private val session: Session? by lazy {
        if (!EnvironmentConfig.isEmailConfigured) return@lazy null
        val props = Properties().apply {
            put("mail.smtp.auth", "true")
            put("mail.smtp.starttls.enable", "true")
            put("mail.smtp.host", EnvironmentConfig.smtpHost!!)
            put("mail.smtp.port", EnvironmentConfig.smtpPort!!.toString())
            put("mail.smtp.ssl.trust", EnvironmentConfig.smtpHost!!)
            // Prevent the SMTP call from hanging the request thread indefinitely
            put("mail.smtp.connectiontimeout", "5000")
            put("mail.smtp.timeout", "10000")
        }
        Session.getInstance(props, object : Authenticator() {
            override fun getPasswordAuthentication() =
                PasswordAuthentication(EnvironmentConfig.smtpUser, EnvironmentConfig.smtpPassword)
        })
    }

    open suspend fun sendPasswordResetCode(toEmail: String, code: String): Unit = withContext(Dispatchers.IO) {
        log.info("Sending password reset code")
        sendHtmlEmail(
            toEmail = toEmail,
            subject = "Код восстановления Clothis",
            html = buildPasswordResetHtml(code)
        )
        log.info("Password reset code sent")
    }

    open suspend fun sendEmailVerificationCode(toEmail: String, code: String): Unit = withContext(Dispatchers.IO) {
        log.info("Sending email verification code")
        sendHtmlEmail(
            toEmail = toEmail,
            subject = "Подтверждение email — Clothis",
            html = buildEmailVerificationHtml(code)
        )
        log.info("Email verification code sent")
    }

    private suspend fun sendHtmlEmail(toEmail: String, subject: String, html: String) = withContext(Dispatchers.IO) {
        val s = session ?: run {
            log.warn("SMTP not configured — skipping email")
            return@withContext
        }
        val message = MimeMessage(s).apply {
            setFrom(InternetAddress(EnvironmentConfig.smtpFrom))
            setRecipients(Message.RecipientType.TO, InternetAddress.parse(toEmail))
            this.subject = subject
            setContent(html, "text/html; charset=UTF-8")
        }
        Transport.send(message)
    }

    private fun buildEmailHtml(title: String, heading: String, body: String, code: String, footer: String): String = """
        <!DOCTYPE html>
        <html lang="ru">
        <head>
          <meta charset="UTF-8">
          <meta name="viewport" content="width=device-width, initial-scale=1.0">
          <title>$title</title>
        </head>
        <body style="margin:0;padding:0;background:#f5f5f5;font-family:-apple-system,BlinkMacSystemFont,'Segoe UI',Roboto,sans-serif;">
          <table width="100%" cellpadding="0" cellspacing="0" style="background:#f5f5f5;padding:40px 0;">
            <tr><td align="center">
              <table width="480" cellpadding="0" cellspacing="0" style="background:#ffffff;border-radius:12px;overflow:hidden;max-width:480px;width:100%;">
                <tr>
                  <td style="background:#0A0A0A;padding:28px 40px;text-align:center;">
                    <span style="color:#ffffff;font-size:22px;font-weight:700;letter-spacing:2px;">CLOTHIS</span>
                  </td>
                </tr>
                <tr>
                  <td style="padding:40px 40px 32px;">
                    <p style="margin:0 0 8px;font-size:24px;font-weight:600;color:#0A0A0A;">$heading</p>
                    <p style="margin:0 0 32px;font-size:15px;color:#666666;line-height:1.5;">$body</p>
                    <div style="background:#f5f5f5;border-radius:8px;padding:24px;text-align:center;margin-bottom:32px;">
                      <span style="font-size:40px;font-weight:700;letter-spacing:12px;color:#0A0A0A;font-family:'Courier New',monospace;">$code</span>
                    </div>
                    <p style="margin:0;font-size:13px;color:#999999;line-height:1.5;">$footer</p>
                  </td>
                </tr>
                <tr>
                  <td style="background:#f9f9f9;padding:20px 40px;border-top:1px solid #eeeeee;text-align:center;">
                    <p style="margin:0;font-size:12px;color:#aaaaaa;">© 2026 Clothis. Все права защищены.</p>
                  </td>
                </tr>
              </table>
            </td></tr>
          </table>
        </body>
        </html>
    """.trimIndent()

    private fun buildPasswordResetHtml(code: String): String = buildEmailHtml(
        title = "Код восстановления",
        heading = "Восстановление пароля",
        body = "Введите этот код в приложении, чтобы создать новый пароль. Код действителен 15&nbsp;минут.",
        code = code,
        footer = "Если вы не запрашивали сброс пароля — просто проигнорируйте это письмо. Ваш пароль останется без изменений."
    )

    private fun buildEmailVerificationHtml(code: String): String = buildEmailHtml(
        title = "Подтверждение email",
        heading = "Подтвердите email",
        body = "Введите этот код в приложении для подтверждения вашего email. Код действителен 15&nbsp;минут.",
        code = code,
        footer = "Если вы не регистрировались в Clothis — просто проигнорируйте это письмо."
    )
}
