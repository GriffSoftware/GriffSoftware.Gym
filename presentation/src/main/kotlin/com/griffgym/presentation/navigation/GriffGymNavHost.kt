package com.griffgym.presentation.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.griffgym.presentation.account.AccountRoute
import com.griffgym.presentation.calculator.CalculatorRoute
import com.griffgym.presentation.cycles.CycleDetailRoute
import com.griffgym.presentation.cycles.CycleReviewRoute
import com.griffgym.presentation.cycles.CyclesRoute
import com.griffgym.presentation.history.HistoryRoute
import com.griffgym.presentation.home.HomeRoute
import com.griffgym.presentation.stats.StatsRoute
import com.griffgym.presentation.workout.WorkoutRoute

@Composable
fun GriffGymNavHost(
    navController: NavHostController,
    onSignedOut: () -> Unit,
    modifier: Modifier = Modifier,
) {
    NavHost(
        navController = navController,
        startDestination = Routes.HOME,
        modifier = modifier,
    ) {
        composable(Routes.HOME) {
            HomeRoute(
                onOpenWorkout = { navController.navigateTopLevel(Routes.LOG) },
                onOpenCycles = { navController.navigate(Routes.CYCLES) },
                onReviewCycle = { navController.navigate(Routes.CYCLE_REVIEW) },
                onOpenAccount = { navController.navigate(Routes.ACCOUNT) },
            )
        }

        composable(Routes.ACCOUNT) {
            AccountRoute(
                onCreateAccount = { onSignedOut() },
                onSignIn = { onSignedOut() },
                // Signing out returns the app to the entry screen, which lives above this
                // graph entirely — the whole main NavHost is torn down, so there is no back
                // stack left pointing at one lifter's training for the next person to find.
                onSignedOut = onSignedOut,
            )
        }

        composable(Routes.CYCLES) {
            CyclesRoute(
                onBack = { navController.popBackStack() },
                onOpenCycle = { navController.navigate(Routes.cycleDetail(it)) },
            )
        }

        // Read-only history. Reached from the cycles list, never from a tab.
        composable(
            route = Routes.CYCLE_DETAIL,
            arguments = listOf(navArgument(Routes.CYCLE_ID_ARG) { type = NavType.StringType }),
        ) {
            CycleDetailRoute(onBack = { navController.popBackStack() })
        }

        // The end-of-cycle decision. Once the next cycle exists this screen has nothing left
        // to say, so it pops itself: Back from Home must not lead into a review of a block
        // the lifter has already moved on from.
        composable(Routes.CYCLE_REVIEW) {
            CycleReviewRoute(
                onBack = { navController.popBackStack() },
                onNextCycleStarted = {
                    navController.popBackStack(route = Routes.CYCLE_REVIEW, inclusive = true)
                },
            )
        }

        composable(Routes.LOG) {
            WorkoutRoute(onWorkoutFinished = { navController.navigateTopLevel(Routes.HOME) })
        }

        composable(Routes.STATS) {
            StatsRoute(onOpenSession = { navController.navigate(Routes.session(it)) })
        }

        composable(Routes.CALC) {
            CalculatorRoute()
        }

        composable(Routes.HISTORY) {
            HistoryRoute(onOpenSession = { navController.navigate(Routes.session(it)) })
        }

        // A finished session, opened from history or the consistency calendar. The same
        // screen as the live log, rendered read-only because the session is closed.
        composable(
            route = Routes.SESSION,
            arguments = listOf(navArgument(Routes.SESSION_ID_ARG) { type = NavType.StringType }),
        ) {
            WorkoutRoute(onWorkoutFinished = { navController.popBackStack() })
        }
    }
}
