package com.griffgym.infrastructure.security

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Carries "the refresh token was rejected for good" out of OkHttp and up to the UI.
 *
 * The discovery happens on an OkHttp thread, inside an `Authenticator`, in the middle of some
 * unrelated request. There is no return path from there to a ViewModel, so this singleton is
 * the seam: the authenticator raises it, `RetrofitAuthRepository` exposes it, and a lifter
 * mid-workout gets one "please sign in again" prompt instead of every screen inventing its own
 * story about why the sync stopped.
 *
 * In memory only, and that is the right lifetime. It exists to explain a sign-out that happened
 * *while the app was open*; after a restart the tokens are simply gone and the entry screen is
 * shown for that reason alone, so persisting the flag would only risk showing the prompt twice.
 *
 * Raising it never touches Room. An expired session costs a lifter a backup, never a workout.
 */
@Singleton
internal class SessionExpiredSignal @Inject constructor() {

    private val expired = MutableStateFlow(false)

    val isExpired: StateFlow<Boolean> = expired.asStateFlow()

    /** Idempotent: three simultaneous 401s must not produce three prompts. */
    fun raise() {
        expired.value = true
    }

    fun acknowledge() {
        expired.value = false
    }
}
