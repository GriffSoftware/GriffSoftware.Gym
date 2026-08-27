package com.griffgym.infrastructure.network

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import com.griffgym.infrastructure.network.auth.TokenAuthenticator
import com.griffgym.infrastructure.network.interceptor.AuthorizationInterceptor
import com.griffgym.infrastructure.preferences.DeviceIdProvider
import com.griffgym.infrastructure.repository.RetrofitAuthRepository
import com.griffgym.infrastructure.security.AuthTokens
import com.griffgym.infrastructure.security.KeystoreSecureTokenStorage
import com.griffgym.infrastructure.security.SessionExpiredSignal
import com.griffgym.infrastructure.security.TokenCipher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockWebServer
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import java.io.File
import java.security.GeneralSecurityException
import java.time.Instant
import java.util.concurrent.TimeUnit

/**
 * Builds the real HTTP stack — the real interceptor, the real authenticator, the real encrypted
 * storage — against a [MockWebServer].
 *
 * Deliberately not a set of mocks. The behaviour worth testing here *is* the wiring: that the
 * header goes on the right requests, that three simultaneous 401s produce one refresh, that a
 * rejected refresh clears credentials instead of crashing. A test that stubbed the authenticator
 * would assert only that the test's own stub works.
 *
 * The one substitution is [FakeTokenCipher], because the Android Keystore does not exist in a
 * JVM. Everything else this class touches is production code.
 */
internal class NetworkTestHarness(
    private val server: MockWebServer,
    storageDirectory: File,
    readTimeoutMillis: Long = 10_000,
    val cipher: FakeTokenCipher = FakeTokenCipher(),
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    val json: Json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
    }

    private val securePreferences: DataStore<Preferences> =
        PreferenceDataStoreFactory.create(scope = scope) {
            File(storageDirectory, "griff_gym_secure.preferences_pb")
        }

    private val plainPreferences: DataStore<Preferences> =
        PreferenceDataStoreFactory.create(scope = scope) {
            File(storageDirectory, "griff_gym_preferences.preferences_pb")
        }

    val tokenStorage = KeystoreSecureTokenStorage(
        dataStore = securePreferences,
        cipher = cipher,
        json = json,
        ioDispatcher = Dispatchers.IO,
    )

    val sessionExpired = SessionExpiredSignal()

    val deviceIdProvider = DeviceIdProvider(plainPreferences)

    val errorMapper = ApiErrorMapper(json)

    private lateinit var apiInstance: GriffGymApi

    private val authenticator = TokenAuthenticator(
        tokenStorage = tokenStorage,
        api = dagger.Lazy { apiInstance },
        deviceIdProvider = deviceIdProvider,
        sessionExpired = sessionExpired,
    )

    val api: GriffGymApi = Retrofit.Builder()
        .baseUrl(server.url("/"))
        .client(
            OkHttpClient.Builder()
                .readTimeout(readTimeoutMillis, TimeUnit.MILLISECONDS)
                .callTimeout(readTimeoutMillis * 2, TimeUnit.MILLISECONDS)
                .addInterceptor(AuthorizationInterceptor(tokenStorage))
                .authenticator(authenticator)
                .build(),
        )
        .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
        .build()
        .create(GriffGymApi::class.java)
        .also { apiInstance = it }

    val authRepository = RetrofitAuthRepository(
        api = api,
        tokenStorage = tokenStorage,
        deviceIdProvider = deviceIdProvider,
        sessionExpired = sessionExpired,
        errorMapper = errorMapper,
    )

    fun shutdown() {
        scope.cancel()
    }
}

/**
 * A cipher that is reversible without hardware.
 *
 * XOR with a fixed pad, which is exactly as strong as it looks and is the point: this stands in
 * for the Keystore so that [KeystoreSecureTokenStorage]'s own logic — the caching, the
 * clear-on-undecryptable rule, the atomic single-blob write — can be tested off a device.
 *
 * [failDecryption] reproduces what a device does after its Keystore key is destroyed: the
 * ciphertext is still on disk and will never be readable again.
 */
internal class FakeTokenCipher : TokenCipher {

    @Volatile
    var failDecryption: Boolean = false

    override fun encrypt(plaintext: ByteArray): ByteArray =
        ByteArray(plaintext.size) { index -> (plaintext[index].toInt() xor PAD).toByte() }

    override fun decrypt(payload: ByteArray): ByteArray {
        if (failDecryption) {
            throw GeneralSecurityException("Keystore key is gone.")
        }
        return ByteArray(payload.size) { index -> (payload[index].toInt() xor PAD).toByte() }
    }

    private companion object {
        const val PAD = 0x5A
    }
}

internal fun testTokens(
    accessToken: String = "access-1",
    refreshToken: String = "refresh-1",
    userId: String = USER_ID,
    email: String = EMAIL,
): AuthTokens = AuthTokens(
    userId = userId,
    email = email,
    accessToken = accessToken,
    refreshToken = refreshToken,
    accessTokenExpiresAt = Instant.parse("2026-08-27T10:15:00Z"),
)

internal const val USER_ID = "7b1f8c2e-2f2a-4a4e-9f38-3f0f3a1d5c11"
internal const val EMAIL = "lifter@griffgym.test"

/** The shape `POST /auth/login` actually returns, so the DTO is exercised as it ships. */
internal fun authenticationJson(
    accessToken: String = "access-1",
    refreshToken: String = "refresh-1",
    userId: String = USER_ID,
    email: String = EMAIL,
): String = """
    {
      "userId": "$userId",
      "email": "$email",
      "accessToken": "$accessToken",
      "tokenType": "Bearer",
      "accessTokenExpiresAtUtc": "2026-08-27T10:15:00+00:00",
      "expiresInSeconds": 900,
      "refreshToken": "$refreshToken",
      "refreshTokenExpiresAtUtc": "2026-09-26T10:00:00+00:00"
    }
""".trimIndent()

internal fun problemJson(
    status: Int,
    title: String,
    detail: String,
): String = """
    {
      "type": "https://httpstatuses.io/$status",
      "title": "$title",
      "status": $status,
      "detail": "$detail",
      "instance": "/api/v1/auth/login"
    }
""".trimIndent()
