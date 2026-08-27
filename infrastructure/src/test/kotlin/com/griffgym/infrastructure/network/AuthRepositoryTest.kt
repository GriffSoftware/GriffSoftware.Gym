package com.griffgym.infrastructure.network

import com.griffgym.domain.model.GriffGymError
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * Registration, sign-in and sign-out against a real HTTP server, real serialisation and the
 * real encrypted token store.
 */
class AuthRepositoryTest {

    @get:Rule
    val temporaryFolder = TemporaryFolder()

    private lateinit var server: MockWebServer
    private lateinit var harness: NetworkTestHarness

    @Before
    fun setUp() {
        server = MockWebServer().apply { start() }
        harness = NetworkTestHarness(server, temporaryFolder.newFolder())
    }

    @After
    fun tearDown() {
        harness.shutdown()
        server.shutdown()
    }

    @Test
    fun `register stores the token pair and reports the session`() = runTest {
        server.enqueue(MockResponse().setResponseCode(201).setBody(authenticationJson()))

        val result = harness.authRepository.register(EMAIL, "correct horse battery staple")

        val session = result.getOrThrow()
        assertEquals(USER_ID, session.userId)
        assertEquals(EMAIL, session.email)

        val stored = harness.tokenStorage.readTokens()
        assertEquals("access-1", stored?.accessToken)
        assertEquals("refresh-1", stored?.refreshToken)
        assertEquals(session, harness.authRepository.restoreSession())
    }

    @Test
    fun `register sends the credentials and a device id, and no bearer token`() = runTest {
        server.enqueue(MockResponse().setResponseCode(201).setBody(authenticationJson()))

        harness.authRepository.register(EMAIL, "correct horse battery staple").getOrThrow()

        val recorded = server.takeRequest()
        assertEquals("/api/v1/auth/register", recorded.path)
        assertNull(recorded.getHeader("Authorization"))

        val body = recorded.body.readUtf8()
        assertTrue(body.contains("\"email\":\"$EMAIL\""))
        assertTrue(body.contains("\"deviceId\""))
    }

    @Test
    fun `a duplicate email is a conflict, not a crash`() = runTest {
        server.enqueue(
            MockResponse()
                .setResponseCode(409)
                .setBody(problemJson(409, "Conflict", "That email is already registered.")),
        )

        val error = harness.authRepository.register(EMAIL, "hunter2").exceptionOrNull()

        assertTrue(error is GriffGymError.Conflict)
        assertEquals("That email is already registered.", error?.message)
        assertNull(harness.tokenStorage.readTokens())
    }

    @Test
    fun `a validation failure keeps every field message`() = runTest {
        server.enqueue(
            MockResponse().setResponseCode(400).setBody(
                """
                {
                  "type": "https://tools.ietf.org/html/rfc9110#section-15.5.1",
                  "title": "Validation failed",
                  "status": 400,
                  "errors": {
                    "email": ["'Email' is not a valid email address."],
                    "password": [
                      "'Password' must be at least 8 characters.",
                      "'Password' must contain a digit."
                    ]
                  }
                }
                """.trimIndent(),
            ),
        )

        val error = harness.authRepository.register("not-an-email", "x").exceptionOrNull()

        val validation = error as GriffGymError.Validation
        assertEquals(
            listOf("'Email' is not a valid email address."),
            validation.fieldErrors["email"],
        )
        assertEquals(2, validation.fieldErrors.getValue("password").size)
        assertEquals("Validation failed", validation.message)
    }

    @Test
    fun `login stores the pair and the session flow reports it`() = runTest {
        server.enqueue(MockResponse().setResponseCode(200).setBody(authenticationJson()))

        val session = harness.authRepository.login(EMAIL, "hunter2").getOrThrow()

        assertEquals(EMAIL, session.email)
        assertEquals(session, harness.authRepository.observeSession().first())
    }

    @Test
    fun `wrong credentials are Unauthorized and leave nothing stored`() = runTest {
        server.enqueue(
            MockResponse()
                .setResponseCode(401)
                .setBody(problemJson(401, "Unauthorized", "Those credentials do not match.")),
        )

        val error = harness.authRepository.login(EMAIL, "wrong").exceptionOrNull()

        assertTrue(error is GriffGymError.Unauthorized)
        assertEquals("Those credentials do not match.", error?.message)
        assertNull(harness.tokenStorage.readTokens())
    }

    /**
     * The header rule, from both sides: it must be absent where a password is being exchanged
     * and present everywhere else. Asserted on the wire rather than on the interceptor, because
     * the thing that can break is the wiring.
     */
    @Test
    fun `the bearer token goes on protected calls and never on login`() = runTest {
        harness.tokenStorage.saveTokens(testTokens(accessToken = "access-1"))
        server.enqueue(MockResponse().setResponseCode(200).setBody("[]"))
        server.enqueue(MockResponse().setResponseCode(200).setBody(authenticationJson()))

        harness.api.exercises()
        harness.api.login(
            com.griffgym.infrastructure.network.dto.LoginRequestDto(EMAIL, "hunter2", "device"),
        )

        val protectedCall = server.takeRequest()
        assertEquals("/api/v1/exercises", protectedCall.path)
        assertEquals("Bearer access-1", protectedCall.getHeader("Authorization"))

        val loginCall = server.takeRequest()
        assertEquals("/api/v1/auth/login", loginCall.path)
        assertNull(loginCall.getHeader("Authorization"))
    }

    @Test
    fun `logout revokes the refresh token server-side`() = runTest {
        harness.tokenStorage.saveTokens(testTokens(refreshToken = "refresh-1"))
        server.enqueue(MockResponse().setResponseCode(204))

        assertTrue(harness.authRepository.logout().isSuccess)

        val recorded = server.takeRequest()
        assertEquals("/api/v1/auth/logout", recorded.path)
        assertTrue(recorded.body.readUtf8().contains("\"refreshToken\":\"refresh-1\""))
        assertNull(harness.tokenStorage.readTokens())
    }

    /**
     * A lifter signing out on a train must not be told "no". The revocation is best effort; the
     * local credentials are not.
     */
    @Test
    fun `logout clears the tokens even when the server refuses`() = runTest {
        harness.tokenStorage.saveTokens(testTokens())
        server.enqueue(MockResponse().setResponseCode(500).setBody("<html>gateway</html>"))

        val result = harness.authRepository.logout()

        assertTrue(result.isSuccess)
        assertNull(harness.tokenStorage.readTokens())
        assertNull(harness.authRepository.restoreSession())
    }

    @Test
    fun `logout clears the tokens even when the connection dies mid-call`() = runTest {
        harness.tokenStorage.saveTokens(testTokens())
        server.shutdown()

        val result = harness.authRepository.logout()

        assertTrue(result.isSuccess)
        assertNull(harness.tokenStorage.readTokens())
        assertNull(harness.authRepository.observeSession().first())
    }

    @Test
    fun `signing in again clears a session-expired prompt`() = runTest {
        harness.sessionExpired.raise()
        assertTrue(harness.authRepository.observeSessionExpired().first())
        server.enqueue(MockResponse().setResponseCode(200).setBody(authenticationJson()))

        harness.authRepository.login(EMAIL, "hunter2").getOrThrow()

        assertFalse(harness.authRepository.observeSessionExpired().first())
        assertNotNull(harness.authRepository.restoreSession())
    }
}
