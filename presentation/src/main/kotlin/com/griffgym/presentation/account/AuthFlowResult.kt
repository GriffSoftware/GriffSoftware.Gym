package com.griffgym.presentation.account

import com.griffgym.domain.model.AuthSession

/**
 * The entire contract between the auth graph and the rest of the app.
 *
 * [AuthNavHost] is deliberately a black box: it owns every screen between "the app just
 * opened" and "we know how this lifter's data is stored", and it reports exactly one of
 * these outcomes when it is finished. Nothing in here is a request — by the time a result
 * is delivered the decision has already been *written down*, so a host that drops it on the
 * floor loses navigation, never data.
 *
 * What has already happened when each arrives:
 *
 *  - [ContinuedLocally] — the local-only choice is persisted. The entry screen will not be
 *    shown again on the next launch.
 *  - [Authenticated] — the account is live, the app is marked authenticated and a sync has
 *    been requested. The lifter's training data is already where it belongs.
 *  - [NeedsOnboarding] — same as [Authenticated], except there is no training data anywhere
 *    yet. The host is expected to mount first-run setup rather than Home.
 *  - [Restored] — the account's backup has been written into this device's database inside
 *    one transaction, and the app is marked authenticated.
 *
 * Three of the four carry the [AuthSession] because a host that wants to greet the lifter
 * by email should not have to go and read it back out of a repository.
 */
sealed interface AuthFlowResult {

    /** No account. Everything stays on this phone, and the lifter was told what that costs. */
    data object ContinuedLocally : AuthFlowResult

    /** Signed in with training data already in the right place. Open the app normally. */
    data class Authenticated(val session: AuthSession) : AuthFlowResult

    /** Signed in, but there is no block on the phone or in the account yet. Run setup. */
    data class NeedsOnboarding(val session: AuthSession) : AuthFlowResult

    /**
     * Signed in on a new device and the account's history has just been pulled down.
     *
     * Kept apart from [Authenticated] so a host can say "your training is back" — the one
     * moment where the whole feature visibly pays for itself.
     */
    data class Restored(val session: AuthSession) : AuthFlowResult
}
