package com.griffgym.presentation.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Calculate
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.ui.graphics.vector.ImageVector
import com.griffgym.presentation.components.BottomNavItem

object Routes {
    const val HOME = "home"
    const val LOG = "log"
    const val STATS = "stats"
    const val CALC = "calc"
    const val HISTORY = "history"
    const val CYCLES = "cycles"

    /** Account and cloud backup. Reached from the drawer, never from a tab. */
    const val ACCOUNT = "account"

    /**
     * The signed-in lifter's own screen. Reached from the avatar and from nowhere else.
     *
     * Deliberately absent from the drawer: [ACCOUNT] is already there, and the avatar routes
     * to whichever of the two this installation has an answer for. Two entries would be two
     * doors to what a lifter experiences as one place.
     */
    const val PROFILE = "profile"

    /** The end-of-cycle decision. Reached from Home, never from a tab. */
    const val CYCLE_REVIEW = "cycles/review"

    const val SESSION_ID_ARG = "sessionId"
    const val SESSION = "session/{$SESSION_ID_ARG}"

    const val CYCLE_ID_ARG = "cycleId"

    /**
     * Kept under `detail/` rather than `cycles/{cycleId}` so it can never collide with
     * [CYCLE_REVIEW] — a route pattern that would happily match the literal "review".
     */
    const val CYCLE_DETAIL = "cycles/detail/{$CYCLE_ID_ARG}"

    fun session(sessionId: Long): String = "session/$sessionId"

    fun cycleDetail(cycleId: Long): String = "cycles/detail/$cycleId"
}

val BottomNavDestinations: List<BottomNavItem> = listOf(
    BottomNavItem("Home", Icons.Filled.Home, Routes.HOME),
    BottomNavItem("Log", Icons.Filled.FitnessCenter, Routes.LOG),
    BottomNavItem("Stats", Icons.Filled.BarChart, Routes.STATS),
    BottomNavItem("Calc", Icons.Filled.Calculate, Routes.CALC),
)

data class DrawerDestination(
    val label: String,
    val icon: ImageVector,
    val route: String,
)

/**
 * The drawer, not a fifth tab: the bottom bar stays at Home / Log / Stats / Calc, which are
 * the four things a lifter reaches for mid-session. Cycles is a between-blocks screen.
 */
val DrawerDestinations: List<DrawerDestination> = listOf(
    DrawerDestination("Home", Icons.Filled.Home, Routes.HOME),
    DrawerDestination("Training log", Icons.Filled.FitnessCenter, Routes.LOG),
    DrawerDestination("Cycles", Icons.Filled.Repeat, Routes.CYCLES),
    DrawerDestination("History", Icons.Filled.History, Routes.HISTORY),
    DrawerDestination("Statistics", Icons.Filled.BarChart, Routes.STATS),
    DrawerDestination("1RM calculator", Icons.Filled.Calculate, Routes.CALC),
    // Last, and deliberately so: an account is a backup, not a feature of training, and the
    // drawer should read as the training app it is.
    DrawerDestination("Account", Icons.Filled.AccountCircle, Routes.ACCOUNT),
)
