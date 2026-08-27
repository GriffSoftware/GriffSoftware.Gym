package com.griffgym.infrastructure.network.auth

import com.griffgym.infrastructure.network.AUTHORIZATION_HEADER
import com.griffgym.infrastructure.network.BEARER_PREFIX
import com.griffgym.infrastructure.network.GriffGymApi
import com.griffgym.infrastructure.network.dto.RefreshRequestDto
import com.griffgym.infrastructure.network.isUnauthenticatedEndpoint
import com.griffgym.infrastructure.preferences.DeviceIdProvider
import com.griffgym.infrastructure.security.AuthTokens
import com.griffgym.infrastructure.security.SecureTokenStorage
import com.griffgym.infrastructure.security.SessionExpiredSignal
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import okhttp3.Authenticator
import okhttp3.Request
import okhttp3.Response
import okhttp3.Route
import retrofit2.HttpException
import java.io.IOException
import java.net.HttpURLConnection.HTTP_UNAUTHORIZED
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Renews an expired access token and replays the request that discovered it.
 *
 * An access token lives about fifteen minutes, and a lifter's session lasts a training block.
 * Without this, every screen would need to know how to recover from a 401, and the one that
 * forgot would be the log-a-set screen at the top of a heavy single.
 *
 * ### Single-flight
 * A phone coming back from a dead spot fires several queued requests at once and every one of
 * them gets a 401. Refreshing per request would be wrong twice over: refresh tokens are
 * **single use and rotate**, so the second refresh would present a token the first had already
 * retired — which the server treats as theft, not as a race, and answers by revoking every
 * session on the account. The [Mutex] makes exactly one of them refresh; the rest wait, see
 * that the stored token is no longer the one they failed with, and retry with the new one
 * without asking for another.
 *
 * ### `runBlocking`
 * `Authenticator.authenticate` is a synchronous callback with no suspending equivalent. OkHttp
 * calls it on a thread it owns and expects to block, so [runBlocking] here is the intended
 * shape rather than a shortcut. It is safe because the refresh is issued through the same
 * OkHttp dispatcher, which uses an unbounded thread pool: the waiting threads cannot starve the
 * one doing the work. (This would stop being true past OkHttp's 64 concurrent-call ceiling.
 * Griff Gym issues a handful of requests at a time; a future bulk sync that changes that needs
 * its own dispatcher for the refresh.)
 *
 * ### Giving up
 * One retry, then null. Returning a request from here re-enters the same call, so a token that
 * is refreshed successfully but still rejected would otherwise loop until the rate limiter
 * stopped it. Failure clears the credentials and raises [SessionExpiredSignal] — and touches
 * nothing else. **No Room data is deleted.** A lifter whose session died still has every set
 * they have ever logged, and signing in again picks up where they left off.
 */
@Singleton
internal class TokenAuthenticator @Inject constructor(
    private val tokenStorage: SecureTokenStorage,
    private val api: dagger.Lazy<GriffGymApi>,
    private val deviceIdProvider: DeviceIdProvider,
    private val sessionExpired: SessionExpiredSignal,
) : Authenticator {

    private val refreshMutex = Mutex()

    override fun authenticate(route: Route?, response: Response): Request? {
        // A 401 from login is a wrong password, and a 401 from refresh is a dead session.
        // Neither is fixed by refreshing, and trying would loop.
        if (response.request.isUnauthenticatedEndpoint()) return null

        if (response.retryCount() >= MAX_RETRIES) return null

        val rejectedToken = response.request.bearerToken()
        val freshToken = runBlocking { freshAccessToken(rejectedToken) } ?: return null

        // Application interceptors do not run again on a retry, so the header is set here
        // rather than left to AuthorizationInterceptor.
        return response.request.newBuilder()
            .header(AUTHORIZATION_HEADER, BEARER_PREFIX + freshToken)
            .build()
    }

    /**
     * @param rejectedToken the access token the failed request carried, or null if it carried
     *  none. It is the only reliable way to tell "my token is stale" from "somebody already
     *  fixed this while I was queued at the mutex".
     */
    private suspend fun freshAccessToken(rejectedToken: String?): String? = refreshMutex.withLock {
        val stored = tokenStorage.readTokens() ?: return null

        // Another thread refreshed while this one waited. Retrying with what is now stored is
        // both correct and necessary: refreshing again would burn a rotated token and take the
        // whole account down with it.
        if (rejectedToken != null && stored.accessToken != rejectedToken) {
            return stored.accessToken
        }

        refresh(stored)
    }

    private suspend fun refresh(stored: AuthTokens): String? =
        try {
            val renewed = api.get().refresh(
                RefreshRequestDto(
                    refreshToken = stored.refreshToken,
                    deviceId = deviceIdProvider.deviceId(),
                ),
            )

            // Persisted before the token is handed back, and before anything else is done with
            // the response. The presented token is already retired server-side; a crash between
            // here and the next request would otherwise leave the device holding a secret that
            // is not only useless but, if ever presented, trips the reuse alarm.
            tokenStorage.saveTokens(
                AuthTokens(
                    userId = renewed.userId,
                    email = renewed.email,
                    accessToken = renewed.accessToken,
                    refreshToken = renewed.refreshToken,
                    accessTokenExpiresAt = renewed.accessTokenExpiresAtUtc,
                ),
            )

            renewed.accessToken
        } catch (io: IOException) {
            // No signal is not a dead session. The refresh token is kept: the lifter is offline,
            // and signing them out for it would be the app punishing them for a basement gym.
            null
        } catch (http: HttpException) {
            // 401 from /refresh means exactly one thing, and the server has already revoked the
            // token. Anything else — 429, a 500, a gateway page — might work later.
            if (http.code() == HTTP_UNAUTHORIZED) {
                tokenStorage.clearTokens()
                sessionExpired.raise()
            }
            null
        }

    private fun Request.bearerToken(): String? =
        header(AUTHORIZATION_HEADER)?.removePrefix(BEARER_PREFIX)?.takeIf(String::isNotBlank)

    /** How many times OkHttp has already replayed this call on this authenticator's account. */
    private fun Response.retryCount(): Int {
        var count = 0
        var prior = priorResponse
        while (prior != null) {
            count++
            prior = prior.priorResponse
        }
        return count
    }

    private companion object {
        /**
         * One. A refresh that succeeds and is still rejected means the account itself is gone;
         * replaying it is how a client ends up rate limited for a session that can never work.
         */
        const val MAX_RETRIES = 1
    }
}
