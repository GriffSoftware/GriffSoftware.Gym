package com.griffgym.infrastructure.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStoreFile
import com.griffgym.domain.repository.AuthRepository
import com.griffgym.domain.repository.NetworkMonitor
import com.griffgym.domain.repository.UserModeRepository
import com.griffgym.infrastructure.BuildConfig
import com.griffgym.infrastructure.network.AUTHORIZATION_HEADER
import com.griffgym.infrastructure.network.GriffGymApi
import com.griffgym.infrastructure.network.NetworkMonitorImpl
import com.griffgym.infrastructure.network.auth.TokenAuthenticator
import com.griffgym.infrastructure.network.interceptor.AuthorizationInterceptor
import com.griffgym.infrastructure.preferences.DataStoreUserModeRepository
import com.griffgym.infrastructure.repository.RetrofitAuthRepository
import com.griffgym.infrastructure.security.AndroidKeystoreTokenCipher
import com.griffgym.infrastructure.security.IO_DISPATCHER
import com.griffgym.infrastructure.security.KeystoreSecureTokenStorage
import com.griffgym.infrastructure.security.SECURE_PREFERENCES
import com.griffgym.infrastructure.security.SecureTokenStorage
import com.griffgym.infrastructure.security.TokenCipher
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import java.util.concurrent.TimeUnit
import javax.inject.Named
import javax.inject.Singleton

/**
 * The HTTP stack, in one place.
 *
 * Kept apart from `DatabaseModule` so that the cloud feature can be reasoned about — and
 * reviewed — without reading the persistence wiring, and so that a change to a timeout cannot
 * accidentally land in the file that builds the database.
 */
@Module
@InstallIn(SingletonComponent::class)
internal object NetworkModule {

    /**
     * `ignoreUnknownKeys` because the server is allowed to add fields without this app being
     * rebuilt; the alternative is a deployed phone that starts throwing on every response the
     * day a column is added.
     *
     * `explicitNulls = false` so an absent optional is simply absent from a request body rather
     * than an explicit `null`. It matches what the server writes — its serialiser drops nulls
     * too — which keeps request and response shapes symmetrical, and keeps bodies smaller on a
     * connection that may be a lifter's mobile data.
     */
    @Provides
    @Singleton
    fun provideJson(): Json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
    }

    /**
     * Timeouts chosen for a phone in a gym, not for a server room.
     *
     * A connect that has not happened in fifteen seconds is not going to; a read that has not
     * started in thirty is a dead connection rather than a slow one. The call timeout is the
     * one that actually protects the lifter: it bounds the *whole* exchange, redirects and
     * retries included, so a request cannot hang forever behind a captive portal that accepts
     * a connection and then says nothing.
     *
     * `retryOnConnectionFailure` stays on: OkHttp retrying a different route for the same
     * request is what makes a Wi-Fi-to-mobile handover invisible.
     */
    @Provides
    @Singleton
    fun provideOkHttpClient(
        authorizationInterceptor: AuthorizationInterceptor,
        tokenAuthenticator: TokenAuthenticator,
    ): OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(CONNECT_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .readTimeout(READ_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .writeTimeout(WRITE_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .callTimeout(CALL_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .addInterceptor(authorizationInterceptor)
        .authenticator(tokenAuthenticator)
        .apply {
            if (BuildConfig.HTTP_LOGGING_ENABLED) {
                addInterceptor(loggingInterceptor())
            }
        }
        .build()

    @Provides
    @Singleton
    fun provideRetrofit(client: OkHttpClient, json: Json): Retrofit = Retrofit.Builder()
        .baseUrl(BuildConfig.API_BASE_URL)
        .client(client)
        .addConverterFactory(json.asConverterFactory(JSON_MEDIA_TYPE.toMediaType()))
        .build()

    @Provides
    @Singleton
    fun provideGriffGymApi(retrofit: Retrofit): GriffGymApi = retrofit.create(GriffGymApi::class.java)

    /**
     * The encrypted store, in its own file and provided separately from the app's ordinary
     * preferences.
     *
     * Two stores rather than two keys in one, so that a future `clear()` on settings can never
     * take the session with it, and so the two can carry different backup rules — an app backup
     * restored onto new hardware contains ciphertext this device's Keystore cannot read, and
     * that should be a sign-in prompt, not a puzzle.
     */
    @Provides
    @Singleton
    @Named(SECURE_PREFERENCES)
    fun provideSecureDataStore(
        @ApplicationContext context: Context,
    ): DataStore<Preferences> = PreferenceDataStoreFactory.create {
        context.preferencesDataStoreFile(KeystoreSecureTokenStorage.PREFERENCES_NAME)
    }

    /** Injected rather than referenced statically so tests can make disk work deterministic. */
    @Provides
    @Singleton
    @Named(IO_DISPATCHER)
    fun provideIoDispatcher(): CoroutineDispatcher = Dispatchers.IO

    /**
     * `BASIC`, and only in a build that asked for logging.
     *
     * Never `BODY`. A register or login request body is an email and a **plaintext password**,
     * and `BODY` would write it to logcat, where any app with read access on an older device —
     * and every bug report a lifter ever attaches — would carry it. `redactHeader` covers the
     * other half: the access token is a bearer credential, and one in a log is one that can be
     * replayed.
     */
    private fun loggingInterceptor(): HttpLoggingInterceptor =
        HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BASIC
            redactHeader(AUTHORIZATION_HEADER)
        }

    private const val JSON_MEDIA_TYPE = "application/json"
    private const val CONNECT_TIMEOUT_SECONDS = 15L
    private const val READ_TIMEOUT_SECONDS = 30L
    private const val WRITE_TIMEOUT_SECONDS = 30L
    private const val CALL_TIMEOUT_SECONDS = 60L
}

@Module
@InstallIn(SingletonComponent::class)
internal abstract class NetworkBindingsModule {

    @Binds
    @Singleton
    abstract fun bindSecureTokenStorage(impl: KeystoreSecureTokenStorage): SecureTokenStorage

    @Binds
    @Singleton
    abstract fun bindTokenCipher(impl: AndroidKeystoreTokenCipher): TokenCipher

    @Binds
    @Singleton
    abstract fun bindAuthRepository(impl: RetrofitAuthRepository): AuthRepository

    @Binds
    @Singleton
    abstract fun bindUserModeRepository(impl: DataStoreUserModeRepository): UserModeRepository

    @Binds
    @Singleton
    abstract fun bindNetworkMonitor(impl: NetworkMonitorImpl): NetworkMonitor
}
