package com.griffgym.presentation.account

import com.griffgym.application.account.InitializeAuthenticatedSessionUseCase
import com.griffgym.application.account.PostSignInAction
import com.griffgym.application.account.ResolvePostSignInActionUseCase
import com.griffgym.domain.model.AuthSession
import javax.inject.Inject

/** Where the auth graph goes next, once there is a session. */
internal sealed interface AuthFlowStep {

    /** Nothing left to move. The flow is over and the host takes the app from here. */
    data class Finish(val result: AuthFlowResult) : AuthFlowStep

    data class BackUpLocalData(val session: AuthSession) : AuthFlowStep

    data class RestoreCloudData(val session: AuthSession) : AuthFlowStep

    data class ResolveConflict(val session: AuthSession) : AuthFlowStep
}

/**
 * Turns "signed in" into "and now what happens to the training data".
 *
 * Sign-in and registration end in exactly the same four situations, so the decision lives
 * here once instead of in both ViewModels — two copies of this would eventually disagree,
 * and the way they would disagree is by overwriting somebody's training history.
 *
 * The two harmless outcomes are settled here, on the spot: when there is nothing to move,
 * the session is marked authenticated and a sync is requested before the flow reports back.
 * The other three end in a screen, because moving a lifter's history is never something to
 * do while they are looking at a spinner.
 *
 * Nothing is marked authenticated on the paths that still have data to move — that only
 * happens once the data has actually arrived. See [InitializeAuthenticatedSessionUseCase].
 *
 * Public only because Hilt has to construct it for the two ViewModels that take it; the
 * decision it makes is not part of anything the app shell can call.
 */
class PostSignInRouter @Inject constructor(
    private val resolvePostSignInAction: ResolvePostSignInActionUseCase,
    private val initializeAuthenticatedSession: InitializeAuthenticatedSessionUseCase,
) {

    internal suspend fun route(session: AuthSession): Result<AuthFlowStep> =
        resolvePostSignInAction(session).mapCatching { action ->
            when (action) {
                PostSignInAction.StartOnboarding -> {
                    initializeAuthenticatedSession(session)
                    AuthFlowStep.Finish(AuthFlowResult.NeedsOnboarding(session))
                }

                PostSignInAction.Continue -> {
                    initializeAuthenticatedSession(session)
                    AuthFlowStep.Finish(AuthFlowResult.Authenticated(session))
                }

                PostSignInAction.BackUpLocalData -> AuthFlowStep.BackUpLocalData(session)

                PostSignInAction.RestoreCloudData -> AuthFlowStep.RestoreCloudData(session)

                PostSignInAction.ResolveConflict -> AuthFlowStep.ResolveConflict(session)
            }
        }
}
