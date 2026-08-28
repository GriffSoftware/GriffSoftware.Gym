package com.griffgym.infrastructure.network

import com.griffgym.domain.model.GriffGymError
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.SocketPolicy
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * `deleteAccount` against a real HTTP server, the real interceptor and authenticator, and the
 * real encrypted token store.
 *
 * Every test here is a variation on one question: **were the credentials cleared, and should
 * they have been?** The asymmetry with sign-out is the entire point of the method. Signing out
 * clears the tokens whatever the server says, because it is a local wish. Deleting an account
 * is a remote fact, and clearing the tokens on anything other than a confirmed deletion would
 * strand the lifter outside an account that still exists — with the backup still in it and no
 * session left to try again with.
 *
 * `NetworkTestHarness` substitutes only the Keystore cipher, which does not exist on a JVM.
 * Everything else below is production code.
 */
class AccountDeletionRepositoryTest {

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
    fun `a confirmed deletion sends a bearer DELETE and then forgets the credentials`() = runTest {
        harness.tokenStorage.saveTokens(testTokens(accessToken = "access-1"))
        server.enqueue(MockResponse().setResponseCode(204))

        val result = harness.authRepository.deleteAccount()

        assertTrue(result.isSuccess)

        val recorded = server.takeRequest()
        assertEquals("DELETE", recorded.method)
        assertEquals("/api/v1/users/me", recorded.path)
        // Whose account is deleted is carried by the token and by nothing else — no id in the
        // path, and no body to put one in.
        assertEquals("Bearer access-1", recorded.getHeader("Authorization"))
        assertEquals(0L, recorded.bodySize)

        assertNull(harness.tokenStorage.readTokens())
        assertNull(harness.authRepository.restoreSession())
        assertNull(harness.authRepository.observeSession().first())
    }

    /**
     * The one that would hurt. A 500 means the account is still there; clearing the tokens
     * would leave a lifter locked out of data that still exists.
     */
    @Test
    fun `a server error leaves the credentials exactly where they were`() = runTest {
        harness.tokenStorage.saveTokens(testTokens(accessToken = "access-1"))
        server.enqueue(MockResponse().setResponseCode(500).setBody("<html>gateway</html>"))

        val result = harness.authRepository.deleteAccount()

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is GriffGymError.Server)

        val stored = harness.tokenStorage.readTokens()
        assertNotNull(stored)
        assertEquals("access-1", stored?.accessToken)
        assertEquals("refresh-1", stored?.refreshToken)
        assertNotNull(harness.authRepository.restoreSession())
    }

    @Test
    fun `a service that is down leaves the credentials exactly where they were`() = runTest {
        harness.tokenStorage.saveTokens(testTokens())
        server.enqueue(MockResponse().setResponseCode(503))

        val result = harness.authRepository.deleteAccount()

        assertTrue(result.isFailure)
        assertNotNull(harness.tokenStorage.readTokens())
    }

    /**
     * Offline. There is deliberately no queued retry: an irreversible deletion that happens by
     * itself, hours later, on a phone in a pocket, is not something anybody agreed to.
     */
    @Test
    fun `no connection deletes nothing and signs nobody out`() = runTest {
        harness.tokenStorage.saveTokens(testTokens())
        server.shutdown()

        val result = harness.authRepository.deleteAccount()

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is GriffGymError.Network)
        assertNotNull(harness.tokenStorage.readTokens())
        assertNotNull(harness.authRepository.observeSession().first())
    }

    /**
     * The request left the phone and the connection died before any answer came back — the
     * genuinely ambiguous case, because the server may or may not have carried the deletion
     * out. The credentials stay: if the account survived, the lifter can retry, and if it did
     * not, the next authenticated call is refused and the app recovers through the ordinary
     * expired-session path. Guessing the optimistic way round and wiping locally would destroy
     * training data on nothing more than a dropped packet.
     */
    @Test
    fun `a connection that dies before any answer leaves the credentials in place`() = runTest {
        harness.tokenStorage.saveTokens(testTokens())
        server.enqueue(MockResponse().setSocketPolicy(SocketPolicy.DISCONNECT_AT_START))

        val result = harness.authRepository.deleteAccount()

        assertTrue(result.isFailure)
        assertNotNull(harness.tokenStorage.readTokens())
    }

    /**
     * A 401 goes through `TokenAuthenticator` first, which refreshes and replays. From the
     * caller's side that is invisible: the deletion simply succeeds, on the new token.
     */
    @Test
    fun `an expired access token is refreshed and the deletion goes through on the new one`() =
        runTest {
            harness.tokenStorage.saveTokens(testTokens(accessToken = "stale-access"))

            server.enqueue(MockResponse().setResponseCode(401))
            server.enqueue(
                MockResponse().setResponseCode(200)
                    .setBody(authenticationJson(accessToken = "fresh-access", refreshToken = "refresh-2")),
            )
            server.enqueue(MockResponse().setResponseCode(204))

            val result = harness.authRepository.deleteAccount()

            assertTrue(result.isSuccess)

            assertEquals("/api/v1/users/me", server.takeRequest().path)
            assertEquals("/api/v1/auth/refresh", server.takeRequest().path)

            val replayed = server.takeRequest()
            assertEquals("/api/v1/users/me", replayed.path)
            assertEquals("Bearer fresh-access", replayed.getHeader("Authorization"))

            assertNull(harness.tokenStorage.readTokens())
        }

    /**
     * The refresh failed too, so the app can no longer prove it owns the account. The account
     * is untouched and the lifter has to sign in before anything can be deleted — which is why
     * this surfaces as `Unauthorized` and not as a generic failure: the screen tells them to
     * sign in rather than offering a TRY AGAIN that cannot work.
     */
    @Test
    fun `a session that cannot be refreshed reports Unauthorized and deletes nothing`() = runTest {
        harness.tokenStorage.saveTokens(testTokens(accessToken = "stale-access"))

        server.enqueue(MockResponse().setResponseCode(401))
        server.enqueue(MockResponse().setResponseCode(401))

        val result = harness.authRepository.deleteAccount()

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is GriffGymError.Unauthorized)

        assertEquals("/api/v1/users/me", server.takeRequest().path)
        assertEquals("/api/v1/auth/refresh", server.takeRequest().path)
        assertEquals(2, server.requestCount)
    }
}
