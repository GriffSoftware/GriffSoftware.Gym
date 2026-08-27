package com.griffgym.infrastructure.security

import com.griffgym.domain.model.AuthSession
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import java.time.Instant

/**
 * The credentials for one signed-in lifter.
 *
 * Internal, and deliberately never converted into anything the layers above can hold. Nothing
 * outside infrastructure has a use for a JWT, and a value that never leaves this layer cannot
 * be logged, put into a UI state, or written into a saved-state bundle by accident.
 *
 * [accessTokenExpiresAt] is kept so a future sync engine can refresh a moment *before* the
 * token dies rather than discovering it through a 401 mid-workout.
 */
@Serializable
internal data class AuthTokens(
    val userId: String,
    val email: String,
    val accessToken: String,
    val refreshToken: String,
    @Serializable(with = InstantEpochSerializer::class)
    val accessTokenExpiresAt: Instant,
) {
    fun toSession(): AuthSession = AuthSession(userId = userId, email = email)
}

/**
 * Where the token pair lives between launches.
 *
 * The refresh token is the whole account: it mints access tokens for as long as it is valid,
 * so it exists on disk only encrypted, under a key held by the Android Keystore and never by
 * this process. The access token is comparatively cheap — fifteen minutes, and useless once
 * expired — but it is stored the same way because splitting the two would mean two formats,
 * two failure modes and no benefit.
 *
 * Every read is allowed to return null. A device that lost its Keystore key, or that restored
 * an app backup taken on different hardware, has credentials it can never decrypt again; the
 * only honest reading of that is "signed out", and the only safe response is to clear them and
 * ask the lifter to sign in. Crashing at startup is not an option, and neither is looping.
 */
internal interface SecureTokenStorage {

    /**
     * Emits on every change, including a clear performed by the token authenticator on a
     * different thread. A single source of truth for "is there a session?" is what keeps the
     * sign-out that happens deep inside OkHttp from having to be plumbed back by hand.
     */
    fun observeTokens(): Flow<AuthTokens?>

    suspend fun readTokens(): AuthTokens?

    /**
     * The same read, for OkHttp's interceptor chain, which is not a coroutine and cannot
     * suspend. Backed by an in-memory copy so that the common case costs nothing; only a cold
     * process pays for a disk read here.
     */
    fun readTokensBlocking(): AuthTokens?

    suspend fun saveTokens(tokens: AuthTokens)

    suspend fun clearTokens()
}

/**
 * Stores an [Instant] as epoch milliseconds inside the encrypted blob.
 *
 * A number rather than a formatted string: this payload is never read by anything but this
 * class, and epoch millis cannot be misparsed by a future locale, time zone or formatter
 * change — which for a value that decides whether a lifter stays signed in is worth more than
 * being readable in a hex dump nobody will ever take.
 */
internal object InstantEpochSerializer : KSerializer<Instant> {

    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor("com.griffgym.AuthTokens.Instant", PrimitiveKind.LONG)

    override fun serialize(encoder: Encoder, value: Instant) {
        encoder.encodeLong(value.toEpochMilli())
    }

    override fun deserialize(decoder: Decoder): Instant =
        Instant.ofEpochMilli(decoder.decodeLong())
}
