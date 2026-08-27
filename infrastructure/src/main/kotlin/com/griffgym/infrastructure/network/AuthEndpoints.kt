package com.griffgym.infrastructure.network

import okhttp3.Request

/**
 * The three endpoints that must never carry a bearer token, and must never trigger a refresh.
 *
 * `register` and `login` mint a session from a password; `refresh` mints one from a refresh
 * token. Attaching a stale access token to any of them is at best noise, and letting a 401
 * from `refresh` start another refresh is an infinite loop that ends with a rate-limited
 * account.
 *
 * `logout` is deliberately absent: it is anonymous server-side, but sending the header when
 * there is one costs nothing and keeps the "everything except these three" rule simple.
 *
 * Matched on the path suffix rather than the full URL so that a deployment served from a
 * subdirectory, or the loopback address an emulator uses, still matches.
 */
private val UNAUTHENTICATED_PATHS = setOf(
    "/api/v1/auth/register",
    "/api/v1/auth/login",
    "/api/v1/auth/refresh",
)

internal fun Request.isUnauthenticatedEndpoint(): Boolean =
    UNAUTHENTICATED_PATHS.any { url.encodedPath.endsWith(it) }

internal const val AUTHORIZATION_HEADER: String = "Authorization"

internal const val BEARER_PREFIX: String = "Bearer "
