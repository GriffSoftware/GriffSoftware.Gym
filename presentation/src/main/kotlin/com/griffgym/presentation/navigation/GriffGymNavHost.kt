package com.griffgym.presentation.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.griffgym.presentation.calculator.CalculatorRoute
import com.griffgym.presentation.history.HistoryRoute
import com.griffgym.presentation.home.HomeRoute
import com.griffgym.presentation.stats.StatsRoute
import com.griffgym.presentation.workout.WorkoutRoute

@Composable
fun GriffGymNavHost(
    navController: NavHostController,
    modifier: Modifier = Modifier,
) {
    NavHost(
        navController = navController,
        startDestination = Routes.HOME,
        modifier = modifier,
    ) {
        composable(Routes.HOME) {
            HomeRoute(onOpenWorkout = { navController.navigateTopLevel(Routes.LOG) })
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
