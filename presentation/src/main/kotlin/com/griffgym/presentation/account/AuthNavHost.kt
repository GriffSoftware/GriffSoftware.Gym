package com.griffgym.presentation.account

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.griffgym.domain.model.AuthSession

/**
 * Everything between "the app just opened" and "we know where this lifter's data lives".
 *
 * A graph of its own, mounted in place of the app rather than nested inside it. Two reasons,
 * both about the back stack: leaving the flow discards the whole graph, so Home is genuinely
 * the root and Back from Home leaves the app rather than returning to a sign-in screen; and
 * nothing in the main app can navigate *into* these screens by accident, because it has no
 * route to any of them.
 *
 * The host is told what happened exactly once, through [AuthFlowResult]. By the time a result
 * arrives the decision has already been written down — the mode persisted, the backup uploaded,
 * the restore committed — so a host that mishandles it loses navigation, never data.
 */
@Composable
fun AuthNavHost(
    onAuthFlowComplete: (AuthFlowResult) -> Unit,
    modifier: Modifier = Modifier,
    navController: NavHostController = rememberNavController(),
) {
    NavHost(
        navController = navController,
        startDestination = AuthRoutes.DATA_PROTECTION,
        modifier = modifier,
    ) {
        composable(AuthRoutes.DATA_PROTECTION) {
            DataProtectionRoute(
                onCreateAccount = { navController.navigate(AuthRoutes.REGISTER) },
                onSignIn = { navController.navigate(AuthRoutes.LOGIN) },
                // Signing in with Google finishes here rather than on a form, so the entry
                // screen reports the same steps the two forms do — including a backup or a
                // restore, which a Google account needs exactly as much as any other.
                onStep = { step -> navController.handle(step, onAuthFlowComplete) },
            )
        }

        composable(AuthRoutes.REGISTER) {
            RegisterRoute(
                onBack = { navController.popBackStack() },
                // Replaces rather than stacks: bouncing between the two forms must not build a
                // pile of screens for Back to walk through afterwards.
                onSignInInstead = {
                    navController.navigate(AuthRoutes.LOGIN) {
                        popUpTo(AuthRoutes.REGISTER) { inclusive = true }
                    }
                },
                onStep = { step -> navController.handle(step, onAuthFlowComplete) },
            )
        }

        composable(AuthRoutes.LOGIN) {
            LoginRoute(
                onBack = { navController.popBackStack() },
                onCreateAccountInstead = {
                    navController.navigate(AuthRoutes.REGISTER) {
                        popUpTo(AuthRoutes.LOGIN) { inclusive = true }
                    }
                },
                onStep = { step -> navController.handle(step, onAuthFlowComplete) },
            )
        }

        composable(
            route = AuthRoutes.BACKUP_LOCAL_DATA,
            arguments = sessionArguments,
        ) {
            BackupProgressRoute(onCompleted = onAuthFlowComplete)
        }

        composable(
            route = AuthRoutes.RESTORE_CLOUD_DATA,
            arguments = sessionArguments,
        ) {
            RestoreProgressRoute(
                onCompleted = onAuthFlowComplete,
                // Back to the entry screen, not `popBackStack()`.
                //
                // Every route into this screen clears the stack behind it, so there is nothing
                // left to pop and the button would silently do nothing — stranding a lifter on
                // a failed restore with no way out. Navigating explicitly is the only thing
                // that works from a single-entry stack.
                //
                // The entry screen is also the right destination: giving up leaves the app
                // *not* marked authenticated, so signing in again offers the restore afresh.
                // Continuing into an empty app would be worse than it sounds — a sync pass
                // only pushes, so nothing would ever pull the history back down on its own.
                onGiveUp = { navController.returnToEntry() },
            )
        }

        composable(
            route = AuthRoutes.DATA_CONFLICT,
            arguments = sessionArguments,
        ) { backStackEntry ->
            val session = backStackEntry.requireSession()

            DataConflictRoute(
                // The conflict screen has already taken its own confirmation before this runs.
                // Replacing the local database is not something to reach by a single tap.
                onUseCloudData = {
                    navController.navigate(AuthRoutes.restoreCloudData(session)) {
                        popUpTo(AuthRoutes.DATA_CONFLICT) { inclusive = true }
                    }
                },
                // Same as the restore screen: the stack was cleared on the way in, so this
                // has to navigate rather than pop. Cancelling touches neither copy of the
                // lifter's history — it simply returns the choice to them.
                onCancel = { navController.returnToEntry() },
            )
        }
    }
}

/**
 * Returns to the data-protection screen and makes it the only thing on the stack.
 *
 * `popUpTo(0)` clears the graph wholesale, which is what leaves Back behaving sensibly
 * afterwards: from the entry screen, Back leaves the app rather than walking through a
 * half-finished restore the lifter has already abandoned.
 */
private fun NavHostController.returnToEntry() {
    navigate(AuthRoutes.DATA_PROTECTION) {
        popUpTo(0) { inclusive = true }
        launchSingleTop = true
    }
}

/**
 * Where the flow goes once there is a session.
 *
 * The three data-moving outcomes each get a screen, because moving somebody's training history
 * is not something to do behind a spinner. [AuthFlowStep.Finish] is already settled by the
 * time it arrives and only needs reporting.
 */
private fun NavHostController.handle(
    step: AuthFlowStep,
    onAuthFlowComplete: (AuthFlowResult) -> Unit,
) {
    when (step) {
        is AuthFlowStep.Finish -> onAuthFlowComplete(step.result)

        is AuthFlowStep.BackUpLocalData ->
            navigate(AuthRoutes.backupLocalData(step.session)) { clearAuthForms() }

        is AuthFlowStep.RestoreCloudData ->
            navigate(AuthRoutes.restoreCloudData(step.session)) { clearAuthForms() }

        is AuthFlowStep.ResolveConflict ->
            navigate(AuthRoutes.dataConflict(step.session)) { clearAuthForms() }
    }
}

/**
 * Drops the sign-in and registration screens once they have done their job.
 *
 * Back from a backup that is halfway through should not land on a login form the lifter has
 * already used — and re-submitting it would start a second migration.
 */
private fun androidx.navigation.NavOptionsBuilder.clearAuthForms() {
    popUpTo(AuthRoutes.DATA_PROTECTION) { inclusive = true }
}

private val sessionArguments = listOf(
    navArgument(AuthRoutes.USER_ID_ARG) { type = NavType.StringType },
    navArgument(AuthRoutes.EMAIL_ARG) { type = NavType.StringType },
)

private fun androidx.navigation.NavBackStackEntry.requireSession(): AuthSession {
    val userId = arguments?.getString(AuthRoutes.USER_ID_ARG)
    val email = arguments?.getString(AuthRoutes.EMAIL_ARG)

    require(!userId.isNullOrBlank() && !email.isNullOrBlank()) {
        "This screen can only be opened for a signed-in account."
    }

    return AuthSession(userId = userId, email = email)
}
