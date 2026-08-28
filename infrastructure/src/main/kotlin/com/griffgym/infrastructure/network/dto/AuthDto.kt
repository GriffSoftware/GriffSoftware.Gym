package com.griffgym.infrastructure.network.dto

import kotlinx.serialization.Serializable
import java.time.Instant

/*
 * The `api/v1/auth` endpoints. Mirrors GriffGym.Api.Contracts.V1.AuthContracts, field for field.
 */

/**
 * [deviceId] is an opaque label for this installation, sent so a lifter can hold several live
 * sessions at once — phone, old phone, tablet — and so revoking one does not sign the others
 * out. The server never treats it as a credential, so it must not be derived from anything
 * identifying: see `DeviceIdProvider`, which stores a random value generated on first use.
 */
@Serializable
internal data class RegisterRequestDto(
    val email: String,
    val password: String,
    val deviceId: String? = null,
)

@Serializable
internal data class LoginRequestDto(
    val email: String,
    val password: String,
    val deviceId: String? = null,
)

/**
 * A Google sign-in, which is also a registration the first time an address is seen.
 *
 * [idToken] is the signed JWT Credential Manager hands back — never an access token. The
 * server verifies the signature, the audience and the expiry against Google's published
 * keys, so nothing about this request is trusted because the client sent it.
 */
@Serializable
internal data class GoogleLoginRequestDto(
    val idToken: String,
    val deviceId: String? = null,
)

@Serializable
internal data class RefreshRequestDto(
    val refreshToken: String,
    val deviceId: String? = null,
)

@Serializable
internal data class LogoutRequestDto(val refreshToken: String)

/**
 * A minted token pair.
 *
 * The refresh token appears in exactly one response: this one. The server keeps only its hash
 * and rotates it on every use, so a client that fails to persist [refreshToken] before doing
 * anything else with the response has permanently lost the session — and presenting the
 * previous one afterwards is read as theft and revokes every device.
 */
@Serializable
internal data class AuthenticationResponseDto(
    val userId: String,
    val email: String,
    val accessToken: String,
    val tokenType: String,
    @Serializable(with = InstantSerializer::class)
    val accessTokenExpiresAtUtc: Instant,
    val expiresInSeconds: Int,
    val refreshToken: String,
    @Serializable(with = InstantSerializer::class)
    val refreshTokenExpiresAtUtc: Instant,
)

@Serializable
internal data class UserResponseDto(
    val id: String,
    val email: String,
    @Serializable(with = InstantSerializer::class)
    val createdAtUtc: Instant,
    @Serializable(with = InstantSerializer::class)
    val updatedAtUtc: Instant,
)
