package com.example.auth

import ch.qos.logback.classic.Logger
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.read.ListAppender
import com.example.testutils.FakePasswordResetRepository
import com.example.testutils.postJson
import com.example.testutils.testAuthModule
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.testing.*
import org.slf4j.LoggerFactory
import plugins.configureLogging
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Real request logging on top of the real auth routes: secrets in auth bodies must not reach any appender. */
class AuthRequestLoggingTest {

    private val secret = "Sup3rSecretPass"
    // The appender sits on the global root logger: the positive checks rely on test classes running
    // sequentially (no parallel forks or junit-platform.properties today), otherwise they could see other tests' events.
    private val rootLogger = LoggerFactory.getLogger(org.slf4j.Logger.ROOT_LOGGER_NAME) as Logger
    private val appender = ListAppender<ILoggingEvent>()

    @BeforeTest
    fun attachAppender() {
        appender.start()
        rootLogger.addAppender(appender)
    }

    @AfterTest
    fun detachAppender() {
        rootLogger.detachAppender(appender)
    }

    private fun ApplicationTestBuilder.module() = application { authRoutesWithRequestLogging() }

    private fun Application.authRoutesWithRequestLogging() {
        testAuthModule(passwordResetRepo = FakePasswordResetRepository())
        configureLogging()
        routing { post("/api/v1/looks/probe") { call.respond(HttpStatusCode.OK) } }
    }

    private fun loggedText(): String = appender.list.joinToString("\n") { it.formattedMessage }

    @Test
    fun `login password does not reach the log`() = testApplication {
        module()

        postJson("/login", """{"login":"user@example.com","password":"$secret"}""")

        assertTrue(loggedText().contains("/api/v1/login"), "the request itself is still logged")
        assertFalse(loggedText().contains(secret))
    }

    @Test
    fun `new password from reset does not reach the log`() = testApplication {
        module()

        postJson("/auth/reset-password", """{"email":"user@example.com","code":"123456","newPassword":"$secret"}""")

        assertTrue(loggedText().contains("/api/v1/auth/reset-password"), "the request itself is still logged")
        assertFalse(loggedText().contains(secret))
    }

    @Test
    fun `regular request bodies still reach the log`() = testApplication {
        module()

        postJson("/looks/probe", """{"marker":"$secret"}""")

        assertTrue(loggedText().contains(secret), "proves the capture works, so the negative checks above are not vacuous")
    }
}
