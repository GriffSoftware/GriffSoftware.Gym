package com.griffgym.presentation.onboarding

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.griffgym.presentation.theme.GriffGymTheme

object OnboardingRoutes {
    const val WELCOME = "onboarding/welcome"
    const val LIFT_INDEX_ARG = "index"
    const val LIFT = "onboarding/lift/{$LIFT_INDEX_ARG}"
    const val SUMMARY = "onboarding/summary"

    fun lift(index: Int): String = "onboarding/lift/$index"
}

/**
 * First-run setup as a graph of its own.
 *
 * Home, the log, statistics and the calculator are not registered here at all, so they are
 * unreachable until setup finishes — a stronger guarantee than popping a back stack. When
 * the program has been built the host is told, swaps this graph for the app shell and this
 * whole back stack goes with it, so there is nothing left behind Home to go back to.
 */
@Composable
fun OnboardingNavHost(
    onOnboardingComplete: () -> Unit,
    modifier: Modifier = Modifier,
    navController: NavHostController = rememberNavController(),
    viewModel: OnboardingViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(state.status) {
        if (state.status is OnboardingStatus.Completed) onOnboardingComplete()
    }

    NavHost(
        navController = navController,
        startDestination = OnboardingRoutes.WELCOME,
        modifier = modifier
            .fillMaxSize()
            .background(GriffGymTheme.colors.background)
            .windowInsetsPadding(WindowInsets.safeDrawing),
    ) {
        composable(OnboardingRoutes.WELCOME) {
            OnboardingWelcomeScreen(
                onStart = { navController.navigateForward(OnboardingRoutes.lift(0)) },
            )
        }

        composable(
            route = OnboardingRoutes.LIFT,
            arguments = listOf(
                navArgument(OnboardingRoutes.LIFT_INDEX_ARG) { type = NavType.IntType },
            ),
        ) { entry ->
            val index = entry.arguments?.getInt(OnboardingRoutes.LIFT_INDEX_ARG) ?: 0
            val step = state.step(index)
            if (step != null) {
                OnboardingLiftStepScreen(
                    state = step,
                    onEvent = viewModel::onEvent,
                    onBack = { navController.popBackStack() },
                    onConfirmed = {
                        val next = index + 1
                        navController.navigateForward(
                            if (next < state.steps.size) {
                                OnboardingRoutes.lift(next)
                            } else {
                                OnboardingRoutes.SUMMARY
                            },
                        )
                    },
                )
            }
        }

        composable(OnboardingRoutes.SUMMARY) {
            OnboardingSummaryScreen(
                state = state.summary,
                onEvent = viewModel::onEvent,
                onBack = { navController.popBackStack() },
            )
        }
    }
}

/**
 * Moves one step on without ever stacking the same step twice — a lifter who walks back to
 * change an answer and confirms it again should end up where they were, not one entry
 * deeper. Popping a route that is not on the stack is a no-op, so the first visit behaves
 * like a plain navigate.
 */
private fun NavHostController.navigateForward(route: String) {
    navigate(route) {
        launchSingleTop = true
        popUpTo(route) { inclusive = true }
    }
}
