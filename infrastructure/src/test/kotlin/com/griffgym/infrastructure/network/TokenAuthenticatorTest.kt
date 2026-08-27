package com.griffgym.infrastructure.network

import com.griffgym.domain.model.GriffGymError
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.util.concurrent.atomic.AtomicInteger

/**
 * The behaviour that keeps a lifter signed in for a whole training block without ever seeing a
 * 401, and that keeps a dead session from turning into a crash or a loop.
 */
class TokenAuthenticatorTest {

    @get:Rule
    val temporaryFolder = TemporaryFolder()

    private lateinit var server: MockWebServer
    private lateinit var harness: NetworkTestHarness

    private val refreshCount = AtomicInteger(0)

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

    /**
     * The one that matters most.
     *
     * A phone coming out of a dead spot flushes several queued requests at once and every one
     * of them gets a 401. Refresh tokens rotate and are single use, so a second refresh would
     * present a secret the first had already retired — which the server reads as theft and
     * answers by revoking every session on the account. One refresh, three retries, no
     * sign-out.
     */
    @Test
    fun `three simultaneous 401s produce exactly one refresh and three successes`() = runBlocking {
        harness.tokenStorage.saveTokens(testTokens(accessToken = STALE, refreshToken = "refresh-1"))
        server.dispatcher = refreshingDispatcher(refreshDelayMillis = 150)

        val responses = withContext(Dispatchers.IO) {
            List(CONCURRENT_CALLS) { async { harness.api.exercises() } }.awaitAll()
        }

        assertEquals(CONCURRENT_CALLS, responses.size)
        responses.forEach { exercises -> assertEquals(1, exercises.size) }
        assertEquals(1, refreshCount.get())

        assertEquals(FRESH, harness.tokenStorage.readTokens()?.accessToken)
        assertEquals("refresh-2", harness.tokenStorage.readTokens()?.refreshToken)
        assertFalse(harness.sessionExpired.isExpired.value)
    }

    @Test
    fun `the rotated refresh token is persisted before the retry goes out`() = runBlocking {
        harness.tokenStorage.saveTokens(testTokens(accessToken = STALE, refreshToken = "refresh-1"))
        server.dispatcher = refreshingDispatcher()

        harness.api.exercises()

        assertEquals("refresh-2", harness.tokenStorage.readTokens()?.refreshToken)
    }

    /**
     * A refresh the server rejects means one thing only: the session is over. It must end in a
     * prompt, never in a crash — and never in lost training data.
     */
    @Test
    fun `a rejected refresh clears the credentials and raises the expiry signal`() = runBlocking {
        harness.tokenStorage.saveTokens(testTokens(accessToken = STALE))
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse = when {
                request.path == REFRESH_PATH -> {
                    refreshCount.incrementAndGet()
                    MockResponse()
                        .setResponseCode(401)
                        .setBody(problemJson(401, "Unauthorized", "That token has been revoked."))
                }

                else -> MockResponse()
                    .setResponseCode(401)
                    .setBody(problemJson(401, "Unauthorized", "Authentication is required."))
            }
        }

        val error = safeApiCall(harness.errorMapper) { harness.api.exercises() }.exceptionOrNull()

        assertTrue(error is GriffGymError.Unauthorized)
        assertEquals(1, refreshCount.get())
        assertNull(harness.tokenStorage.readTokens())
        assertTrue(harness.sessionExpired.isExpired.value)
    }

    /**
     * A refresh that succeeds into a token the server still rejects would otherwise replay
     * forever and end with a rate-limited account.
     */
    @Test
    fun `a token that is refreshed and still rejected is retried once and then given up on`() =
        runBlocking {
            harness.tokenStorage.saveTokens(testTokens(accessToken = STALE))
            server.dispatcher = object : Dispatcher() {
                override fun dispatch(request: RecordedRequest): MockResponse =
                    if (request.path == REFRESH_PATH) {
                        refreshCount.incrementAndGet()
                        MockResponse().setResponseCode(200).setBody(
                            authenticationJson(accessToken = FRESH, refreshToken = "refresh-2"),
                        )
                    } else {
                        MockResponse().setResponseCode(401).setBody(
                            problemJson(401, "Unauthorized", "Still no."),
                        )
                    }
            }

            val error =
                safeApiCall(harness.errorMapper) { harness.api.exercises() }.exceptionOrNull()

            assertTrue(error is GriffGymError.Unauthorized)
            assertEquals(1, refreshCount.get())

            // The credentials survive: the refresh itself was fine, so this is not the
            // "your session is over" case and must not be treated as one.
            assertEquals(FRESH, harness.tokenStorage.readTokens()?.accessToken)
        }

    /**
     * Signing in with the wrong password is a 401 too. Trying to refresh out of it would burn a
     * refresh token to answer a question about a password.
     */
    @Test
    fun `a 401 from login never triggers a refresh`() = runBlocking {
        harness.tokenStorage.saveTokens(testTokens(accessToken = STALE))
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                if (request.path == REFRESH_PATH) refreshCount.incrementAndGet()
                return MockResponse()
                    .setResponseCode(401)
                    .setBody(problemJson(401, "Unauthorized", "Those credentials do not match."))
            }
        }

        val error = harness.authRepository.login(EMAIL, "wrong").exceptionOrNull()

        assertTrue(error is GriffGymError.Unauthorized)
        assertEquals(0, refreshCount.get())
        assertFalse(harness.sessionExpired.isExpired.value)
    }

    /**
     * Losing the connection during a refresh is not a dead session. Clearing the refresh token
     * here would sign a lifter out for training in a basement.
     */
    @Test
    fun `a refresh that cannot reach the server keeps the credentials`() = runBlocking {
        harness.tokenStorage.saveTokens(testTokens(accessToken = STALE))
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse =
                if (request.path == REFRESH_PATH) {
                    refreshCount.incrementAndGet()
                    MockResponse().setSocketPolicy(
                        okhttp3.mockwebserver.SocketPolicy.DISCONNECT_AT_START,
                    )
                } else {
                    MockResponse().setResponseCode(401).setBody(
                        problemJson(401, "Unauthorized", "Authentication is required."),
                    )
                }
        }

        val error = safeApiCall(harness.errorMapper) { harness.api.exercises() }.exceptionOrNull()

        // Whether this surfaces as the original 401 or as the dropped connection depends on
        // whether the poisoned socket was still pooled, and both readings are honest. What
        // matters is that it is *retryable*: the background worker reschedules a Network
        // failure and gives up on an Unauthorized one, so classifying a basement gym as a dead
        // session would quietly stop backing the lifter up.
        assertTrue("unexpected error: $error", (error as GriffGymError).isRetryable)

        // And the credentials survive. Losing signal is not losing an account.
        assertEquals("refresh-1", harness.tokenStorage.readTokens()?.refreshToken)
        assertFalse(harness.sessionExpired.isExpired.value)
    }

    /**
     * @param refreshDelayMillis holds the refreshing thread inside the mutex long enough for the
     *  others to queue behind it, which is the state the single-flight rule exists for.
     */
    private fun refreshingDispatcher(refreshDelayMillis: Long = 0): Dispatcher =
        object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse = when {
                request.path == REFRESH_PATH -> {
                    refreshCount.incrementAndGet()
                    MockResponse()
                        .setResponseCode(200)
                        .setBodyDelay(refreshDelayMillis, java.util.concurrent.TimeUnit.MILLISECONDS)
                        .setBody(authenticationJson(accessToken = FRESH, refreshToken = "refresh-2"))
                }

                request.getHeader("Authorization") == "Bearer $FRESH" ->
                    MockResponse().setResponseCode(200).setBody(EXERCISES_BODY)

                else -> MockResponse()
                    .setResponseCode(401)
                    .setBody(problemJson(401, "Unauthorized", "Authentication is required."))
            }
        }

    private companion object {
        const val CONCURRENT_CALLS = 3
        const val STALE = "access-stale"
        const val FRESH = "access-fresh"
        const val REFRESH_PATH = "/api/v1/auth/refresh"

        val EXERCISES_BODY = """
            [
              {
                "id": "3c1e1d7a-9a0b-4c2d-8f11-6a5b4c3d2e1f",
                "name": "Przysiad",
                "category": "Squat",
                "createdAtUtc": "2026-03-02T18:00:00+00:00",
                "updatedAtUtc": "2026-03-02T18:00:00+00:00",
                "version": 1,
                "syncVersion": 12
              }
            ]
        """.trimIndent()
    }
}
