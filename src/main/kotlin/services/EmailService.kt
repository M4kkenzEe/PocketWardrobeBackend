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
            subject = "Код восстановления Clothis"
            setContent(buildPasswordResetHtml(code), "text/html; charset=UTF-8")
        }
        Transport.send(message)
        log.info("Password reset code sent to $toEmail")
    }

    private fun buildPasswordResetHtml(code: String): String = """
        <!DOCTYPE html>
        <html lang="ru">
        <head>
          <meta charset="UTF-8">
          <meta name="viewport" content="width=device-width, initial-scale=1.0">
          <title>Код восстановления</title>
        </head>
        <body style="margin:0;padding:0;background:#f5f5f5;font-family:-apple-system,BlinkMacSystemFont,'Segoe UI',Roboto,sans-serif;">
          <table width="100%" cellpadding="0" cellspacing="0" style="background:#f5f5f5;padding:40px 0;">
            <tr><td align="center">
              <table width="480" cellpadding="0" cellspacing="0" style="background:#ffffff;border-radius:12px;overflow:hidden;max-width:480px;width:100%;">
                <!-- Header -->
                <tr>
                  <td style="background:#0A0A0A;padding:28px 40px;text-align:center;">
                    <span style="color:#ffffff;font-size:22px;font-weight:700;letter-spacing:2px;">CLOTHIS</span>
                  </td>
                </tr>
                <!-- Body -->
                <tr>
                  <td style="padding:40px 40px 32px;">
                    <p style="margin:0 0 8px;font-size:24px;font-weight:600;color:#0A0A0A;">Восстановление пароля</p>
                    <p style="margin:0 0 32px;font-size:15px;color:#666666;line-height:1.5;">
                      Введите этот код в приложении, чтобы создать новый пароль. Код действителен 15&nbsp;минут.
                    </p>
                    <!-- Code block -->
                    <div style="background:#f5f5f5;border-radius:8px;padding:24px;text-align:center;margin-bottom:32px;">
                      <span style="font-size:40px;font-weight:700;letter-spacing:12px;color:#0A0A0A;font-family:'Courier New',monospace;">$code</span>
                    </div>
                    <p style="margin:0;font-size:13px;color:#999999;line-height:1.5;">
                      Если вы не запрашивали сброс пароля — просто проигнорируйте это письмо. Ваш пароль останется без изменений.
                    </p>
                  </td>
                </tr>
                <!-- Footer -->
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

}
