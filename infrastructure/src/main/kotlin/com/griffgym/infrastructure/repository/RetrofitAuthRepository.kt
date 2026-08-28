package com.griffgym.infrastructure.repository

import com.griffgym.domain.model.AuthSession
import com.griffgym.domain.repository.AuthRepository
import com.griffgym.infrastructure.network.ApiErrorMapper
import com.griffgym.infrastructure.network.GriffGymApi
import com.griffgym.infrastructure.network.dto.AuthenticationResponseDto
import com.griffgym.infrastructure.network.dto.GoogleLoginRequestDto
import com.griffgym.infrastructure.network.dto.LoginRequestDto
import com.griffgym.infrastructure.network.dto.LogoutRequestDto
import com.griffgym.infrastructure.network.dto.RegisterRequestDto
import com.griffgym.infrastructure.network.safeApiCall
import com.griffgym.infrastructure.preferences.DeviceIdProvider
import com.griffgym.infrastructure.security.AuthTokens
import com.griffgym.infrastructure.security.SecureTokenStorage
import com.griffgym.infrastructure.security.SessionExpiredSignal
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Registration, sign-in and sign-out against the Griff Gym API.
 *
 * The boundary tokens do not cross. Everything above it deals in [AuthSession], which carries
 * an id and an email and nothing else — a JWT that never leaves this file cannot be logged by
 * an over-eager crash reporter, put into a UI state, or written into a saved-state bundle by a
 * ViewModel that outlives the process.
 *
 * Passwords are parameters and nothing more. They are not cached, not retried from memory, and
 * not held in any state that outlives the call; the logging interceptor is pinned to `BASIC`
 * for the same reason, so a request body containing one is never written to logcat.
 */
@Singleton
internal class RetrofitAuthRepository @Inject constructor(
    private val api: GriffGymApi,
    private val tokenStorage: SecureTokenStorage,
    private val deviceIdProvider: DeviceIdProvider,
    private val sessionExpired: SessionExpiredSignal,
    private val errorMapper: ApiErrorMapper,
) : AuthRepository {

    /**
     * Derived from what is actually stored rather than from a field this class keeps in step.
     * That matters because sign-out can also happen inside OkHttp, when
     * [com.griffgym.infrastructure.network.auth.TokenAuthenticator] gives up on a refresh, and
     * a duplicated copy of the answer would be the one that got missed.
     */
    override fun observeSession(): Flow<AuthSession?> =
        tokenStorage.observeTokens()
            .map { tokens -> tokens?.toSession() }
            .distinctUntilChanged()

    override suspend fun register(email: String, password: String): Result<AuthSession> =
        safeApiCall(errorMapper) {
            api.register(
                RegisterRequestDto(
                    email = email,
                    password = password,
                    deviceId = deviceIdProvider.deviceId(),
                ),
            ).persist()
        }

    override suspend fun login(email: String, password: String): Result<AuthSession> =
        safeApiCall(errorMapper) {
            api.login(
                LoginRequestDto(
                    email = email,
                    password = password,
                    deviceId = deviceIdProvider.deviceId(),
                ),
            ).persist()
        }

    /**
     * Exchanges a Google ID token for a Griff Gym session, registering the account the first
     * time that address is seen.
     *
     * The token is treated exactly as a password is: sent, never stored, never retried from
     * memory. It is also short-lived and single-purpose — the server verifies it against
     * Google's keys and it is worthless afterwards — so the only thing that survives this
     * call is the token pair `persist` writes.
     */
    override suspend fun loginWithGoogle(idToken: String): Result<AuthSession> =
        safeApiCall(errorMapper) {
            api.googleLogin(
                GoogleLoginRequestDto(
                    idToken = idToken,
                    deviceId = deviceIdProvider.deviceId(),
                ),
            ).persist()
        }

    /**
     * Revokes this device's refresh token, then forgets it locally **whether or not the server
     * was reached**.
     *
     * The order is deliberate and the `finally` is the point: a lifter signing out on a train
     * must not be told "no". The worst case of a failed revocation is a refresh token that
     * stays valid on the server until it expires on its own — an inconvenience — against the
     * alternative of leaving one lifter's credentials on a phone being handed to somebody
     * else, which is not.
     *
     * Reported as a success for the same reason. The local outcome is what the caller acts on,
     * and it always happened.
     */
    override suspend fun logout(): Result<Unit> {
        val refreshToken = tokenStorage.readTokens()?.refreshToken

        return try {
            if (refreshToken != null) {
                safeApiCall(errorMapper) { api.logout(LogoutRequestDto(refreshToken)) }
            }
            Result.success(Unit)
        } finally {
            tokenStorage.clearTokens()
            sessionExpired.acknowledge()
        }
    }

    /**
     * Reads the stored session at startup. Never touches the network: a phone in a basement
     * gym opens to its own training data exactly as fast as one with five bars.
     */
    override suspend fun restoreSession(): AuthSession? = tokenStorage.readTokens()?.toSession()

    override fun observeSessionExpired(): Flow<Boolean> = sessionExpired.isExpired

    override suspend fun acknowledgeSessionExpired() = sessionExpired.acknowledge()

    /**
     * Stores the pair before returning, so a caller that acts on the session — starting a
     * backup, say — can never do so with credentials that are not yet on disk.
     */
    private suspend fun AuthenticationResponseDto.persist(): AuthSession {
        val tokens = AuthTokens(
            userId = userId,
            email = email,
            accessToken = accessToken,
            refreshToken = refreshToken,
            accessTokenExpiresAt = accessTokenExpiresAtUtc,
        )
        tokenStorage.saveTokens(tokens)

        // A fresh pair means whatever went wrong before has been put right.
        sessionExpired.acknowledge()

        return tokens.toSession()
    }
}
