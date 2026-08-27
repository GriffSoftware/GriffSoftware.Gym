package com.griffgym.infrastructure.network.interceptor

import com.griffgym.infrastructure.network.AUTHORIZATION_HEADER
import com.griffgym.infrastructure.network.BEARER_PREFIX
import com.griffgym.infrastructure.network.isUnauthenticatedEndpoint
import com.griffgym.infrastructure.security.SecureTokenStorage
import okhttp3.Interceptor
import okhttp3.Response
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Puts the access token on every request that needs one.
 *
 * The only place in the app that writes an `Authorization` header on an outgoing request —
 * apart from
 * [com.griffgym.infrastructure.network.auth.TokenAuthenticator], which replaces it after a
 * refresh. A call site that set the header by hand would be the one that kept sending a token
 * the authenticator had already rotated away, and it would fail intermittently, an hour into a
 * session, on somebody else's phone.
 *
 * A request made with no session stored simply goes out unauthenticated and comes back 401.
 * That is correct: the server decides what needs credentials, and inventing a client-side
 * "you are not signed in" error here would make the two disagree.
 */
@Singleton
class AuthorizationInterceptor @Inject internal constructor(
    private val tokenStorage: SecureTokenStorage,
) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        if (request.isUnauthenticatedEndpoint()) return chain.proceed(request)

        val accessToken = tokenStorage.readTokensBlocking()?.accessToken
            ?: return chain.proceed(request)

        return chain.proceed(
            request.newBuilder()
                .header(AUTHORIZATION_HEADER, BEARER_PREFIX + accessToken)
                .build(),
        )
    }
}
