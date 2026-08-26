package com.griffgym.presentation.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Calculate
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.ui.graphics.vector.ImageVector
import com.griffgym.presentation.components.BottomNavItem

object Routes {
    const val HOME = "home"
    const val LOG = "log"
    const val STATS = "stats"
    const val CALC = "calc"
    const val HISTORY = "history"

    const val SESSION_ID_ARG = "sessionId"
    const val SESSION = "session/{$SESSION_ID_ARG}"

    fun session(sessionId: Long): String = "session/$sessionId"
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

val DrawerDestinations: List<DrawerDestination> = listOf(
    DrawerDestination("Home", Icons.Filled.Home, Routes.HOME),
    DrawerDestination("Training log", Icons.Filled.FitnessCenter, Routes.LOG),
    DrawerDestination("History", Icons.Filled.History, Routes.HISTORY),
    DrawerDestination("Statistics", Icons.Filled.BarChart, Routes.STATS),
    DrawerDestination("1RM calculator", Icons.Filled.Calculate, Routes.CALC),
)
