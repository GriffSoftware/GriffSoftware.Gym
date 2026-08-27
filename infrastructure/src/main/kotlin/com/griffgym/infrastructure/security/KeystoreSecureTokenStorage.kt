package com.griffgym.infrastructure.security

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import java.io.IOException
import java.security.GeneralSecurityException
import java.util.Base64
import javax.inject.Inject
import javax.inject.Named
import javax.inject.Singleton

/**
 * The token pair, encrypted with an Android Keystore key and kept in its own DataStore file.
 *
 * Three decisions this class exists to enforce:
 *
 *  1. **One blob, not one field per token.** The pair is serialised together, encrypted once
 *     and written once. Storing the access and refresh tokens as separate encrypted values
 *     would allow a half-written state in which they belong to different sessions, and a
 *     refresh token that does not match its access token is a locked-out account.
 *  2. **Its own file.** [PREFERENCES_NAME] is separate from the app's ordinary preferences so
 *     that clearing settings, or a future `clear()` on that store, can never take the session
 *     with it — and so the two have unrelated backup rules.
 *  3. **Unreadable means signed out.** A Keystore key is destroyed by a factory reset, by the
 *     lifter re-enrolling their device credential on some OEM builds, and by restoring an app
 *     backup onto different hardware. In every one of those cases the ciphertext on disk is
 *     permanently undecryptable. Clearing it and reporting "no session" is the only outcome
 *     that lets the lifter sign in again; throwing would brick the app at startup, and
 *     retrying would loop.
 *
 * Room is untouched by any of this. Losing a session never costs a lifter a single set: the
 * training history is local and stays local.
 */
@Singleton
internal class KeystoreSecureTokenStorage @Inject constructor(
    @Named(SECURE_PREFERENCES) private val dataStore: DataStore<Preferences>,
    private val cipher: TokenCipher,
    private val json: Json,
    @Named(IO_DISPATCHER) private val ioDispatcher: CoroutineDispatcher,
) : SecureTokenStorage {

    /**
     * The last decrypted value, so OkHttp's interceptor chain can read a token without a disk
     * round trip and without decrypting on every single request. Volatile because it is
     * written from coroutine threads and read from OkHttp's.
     */
    @Volatile
    private var cached: AuthTokens? = null

    @Volatile
    private var cacheIsWarm: Boolean = false

    override fun observeTokens(): Flow<AuthTokens?> =
        dataStore.data
            .map { preferences -> decodeOrNull(preferences[ENCRYPTED_TOKENS]) }
            .distinctUntilChanged()

    override suspend fun readTokens(): AuthTokens? {
        cached?.let { return it }

        val stored = withContext(ioDispatcher) { dataStore.data.first()[ENCRYPTED_TOKENS] }
        if (stored == null) {
            warmCacheWith(null)
            return null
        }

        val decoded = decodeOrNull(stored)
        if (decoded == null) {
            // Ciphertext that is present but will not decrypt is never going to start working.
            // It is dropped here rather than left to fail identically on every later read.
            clearTokens()
            return null
        }

        warmCacheWith(decoded)
        return decoded
    }

    override fun readTokensBlocking(): AuthTokens? {
        cached?.let { return it }
        if (cacheIsWarm) return null

        // Only a cold process reaches this. runBlocking is safe here because OkHttp calls
        // interceptors on its own dispatcher threads, never on a coroutine dispatcher, so
        // there is no scheduler to starve.
        return runBlocking { readTokens() }
    }

    override suspend fun saveTokens(tokens: AuthTokens) {
        val payload = encrypt(tokens)

        // The in-memory copy is updated only once the write has landed. The other order would
        // let a failed write leave the process believing in a session that is not on disk,
        // which after a restart becomes an unexplained sign-out.
        withContext(ioDispatcher) {
            dataStore.edit { preferences -> preferences[ENCRYPTED_TOKENS] = payload }
        }
        warmCacheWith(tokens)
    }

    override suspend fun clearTokens() {
        warmCacheWith(null)
        withContext(ioDispatcher) {
            dataStore.edit { preferences -> preferences.remove(ENCRYPTED_TOKENS) }
        }
    }

    /**
     * `java.util.Base64` rather than `android.util.Base64`: the app's minimum is API 26, the
     * JDK class is available from it, and using it keeps this class — the one with the
     * interesting failure handling in it — testable off a device.
     */
    private fun encrypt(tokens: AuthTokens): String {
        val plaintext = json.encodeToString(AuthTokens.serializer(), tokens).toByteArray()
        return Base64.getEncoder().encodeToString(cipher.encrypt(plaintext))
    }

    /**
     * Every way this can fail — a missing key, a rotated key, a truncated file, a payload
     * written by an older format — has the same answer, so they are caught together rather
     * than enumerated into branches that all do the same thing.
     *
     * `KeyPermanentlyInvalidatedException` is a `GeneralSecurityException`; `AEADBadTagException`
     * and `IllegalBlockSizeException`, which is what a Keystore whose key has gone throws on
     * some OEM builds, are too.
     */
    private fun decodeOrNull(encoded: String?): AuthTokens? {
        if (encoded.isNullOrBlank()) return null

        return try {
            val plaintext = cipher.decrypt(Base64.getDecoder().decode(encoded))
            json.decodeFromString(AuthTokens.serializer(), plaintext.decodeToString())
        } catch (security: GeneralSecurityException) {
            null
        } catch (illegalArgument: IllegalArgumentException) {
            // Base64 that is not Base64, or JSON that is not JSON.
            null
        } catch (io: IOException) {
            null
        }
    }

    private fun warmCacheWith(tokens: AuthTokens?) {
        cached = tokens
        cacheIsWarm = true
    }

    companion object {
        /**
         * Its own file, and named so it is obvious in a bug report which one holds secrets.
         * Deliberately not the store the onboarding flag lives in.
         */
        const val PREFERENCES_NAME: String = "griff_gym_secure"

        internal val ENCRYPTED_TOKENS = stringPreferencesKey("encrypted_tokens")
    }
}

/** Qualifier for the encrypted preference store, to keep it apart from the ordinary one. */
internal const val SECURE_PREFERENCES = "griffgym.secure-preferences"

/** Qualifier for the dispatcher disk work runs on, so tests can make it deterministic. */
internal const val IO_DISPATCHER = "griffgym.io-dispatcher"
