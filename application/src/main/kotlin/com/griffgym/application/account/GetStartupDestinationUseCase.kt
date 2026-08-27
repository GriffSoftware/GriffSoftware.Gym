package com.griffgym.application.account

import com.griffgym.application.onboarding.AppInitializationState
import com.griffgym.application.onboarding.GetAppInitializationStateUseCase
import com.griffgym.domain.model.UserMode
import javax.inject.Inject

/** What the very first frame of the app should be. */
sealed interface StartupDestination {

    /**
     * The lifter has not said where their data should live. Shown once, and shown to an
     * installation that already holds years of training just as much as to a fresh one —
     * that lifter has the most to lose and has never been asked.
     */
    data object ChooseDataMode : StartupDestination

    /** First-run setup: the three maxes, then cycle 1. */
    data object Onboarding : StartupDestination

    data object Ready : StartupDestination
}

/**
 * Resolves, once per process, where the app opens.
 *
 * The order of the two questions is the whole design. "Has a data mode been chosen?" comes
 * first, because it is about the lifter's data and applies whether or not they have any yet.
 * "Is setup done?" comes second, and is answered by the existing check — so an installation
 * that predates accounts is never dragged back through onboarding it already completed.
 *
 * Nothing here touches the network. A signed-in lifter's session is read from local storage,
 * and whether the server is reachable has no bearing on the app opening: Home is drawn from
 * Room, and the backup catches up on its own.
 */
class GetStartupDestinationUseCase @Inject constructor(
    private val getUserMode: GetUserModeUseCase,
    private val restoreSession: RestoreSessionUseCase,
    private val getAppInitializationState: GetAppInitializationStateUseCase,
) {

    suspend operator fun invoke(): StartupDestination {
        val mode = getUserMode.current()

        if (mode is UserMode.Undecided) return StartupDestination.ChooseDataMode

        // Warms the stored session so the rest of the app has it. A failure here is not a
        // reason to stop: an account holder with an unreadable token still owns everything in
        // their local database, and they get a "sign in again" prompt, not a locked app.
        if (mode is UserMode.Authenticated) {
            runCatching { restoreSession() }
        }

        return when (getAppInitializationState()) {
            AppInitializationState.NeedsOnboarding -> StartupDestination.Onboarding
            AppInitializationState.Ready -> StartupDestination.Ready
        }
    }
}
