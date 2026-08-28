package com.griffgym.presentation.navigation

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.griffgym.presentation.components.GRIFF_GYM_BRAND
import com.griffgym.presentation.components.GriffGymBottomNavigation
import com.griffgym.presentation.components.GriffGymTopBar
import com.griffgym.presentation.components.HairLine
import com.griffgym.presentation.theme.GriffGymTheme
import kotlinx.coroutines.launch

/**
 * The single Activity-level shell: brand bar on top, four destinations at the bottom, a
 * drawer for the routes that do not deserve a tab.
 */
@Composable
fun GriffGymApp(
    onSignedOut: () -> Unit,
    onAccountDeleted: () -> Unit,
    modifier: Modifier = Modifier,
    navController: NavHostController = rememberNavController(),
    avatarViewModel: AvatarDestinationViewModel = hiltViewModel(),
) {
    val colors = GriffGymTheme.colors
    val avatarDestination by avatarViewModel.destination.collectAsStateWithLifecycle()
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route
    val bottomBarRoute = BottomNavDestinations.firstOrNull { item ->
        backStackEntry?.destination?.hierarchy?.any { it.route == item.route } == true
    }?.route

    ModalNavigationDrawer(
        drawerState = drawerState,
        modifier = modifier,
        drawerContent = {
            ModalDrawerSheet(
                drawerContainerColor = colors.surfaceLowest,
                drawerContentColor = colors.textPrimary,
            ) {
                DrawerContent(
                    currentRoute = currentRoute,
                    onDestinationClick = { route ->
                        scope.launch { drawerState.close() }
                        navController.navigateTopLevel(route)
                    },
                )
            }
        },
    ) {
        Scaffold(
            containerColor = colors.background,
            topBar = {
                GriffGymTopBar(
                    onMenuClick = { scope.launch { drawerState.open() } },
                    // One icon, two destinations, decided by whether there is an account
                    // behind it. Tapping it while already there is a no-op rather than a
                    // second copy of the screen on the stack.
                    onAvatarClick = {
                        if (currentRoute != avatarDestination) {
                            navController.navigate(avatarDestination)
                        }
                    },
                )
            },
            bottomBar = {
                GriffGymBottomNavigation(
                    items = BottomNavDestinations,
                    selectedRoute = bottomBarRoute,
                    onItemClick = { navController.navigateTopLevel(it.route) },
                )
            },
            snackbarHost = { SnackbarHost(snackbarHostState) },
        ) { padding ->
            GriffGymNavHost(
                navController = navController,
                onSignedOut = onSignedOut,
                onAccountDeleted = onAccountDeleted,
                modifier = Modifier.padding(padding),
            )
        }
    }
}

@Composable
private fun DrawerContent(
    currentRoute: String?,
    onDestinationClick: (String) -> Unit,
) {
    val colors = GriffGymTheme.colors
    Column(
        Modifier
            .fillMaxHeight()
            .background(colors.surfaceLowest),
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .windowInsetsPadding(WindowInsets.statusBars)
                .padding(20.dp),
        ) {
            Text(
                text = GRIFF_GYM_BRAND,
                style = GriffGymTheme.typography.brand.copy(fontStyle = FontStyle.Italic),
                color = colors.primary,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = "Powerlifting log",
                style = GriffGymTheme.typography.bodySmall,
                color = colors.textTertiary,
            )
        }
        HairLine(color = colors.surfaceVariant)
        Spacer(Modifier.height(8.dp))

        DrawerDestinations.forEach { destination ->
            val selected = currentRoute == destination.route
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onDestinationClick(destination.route) }
                    .background(if (selected) colors.surface else colors.surfaceLowest)
                    .padding(horizontal = 20.dp, vertical = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (selected) {
                    Box(
                        Modifier
                            .width(3.dp)
                            .height(20.dp)
                            .background(colors.primary),
                    )
                    Spacer(Modifier.width(13.dp))
                } else {
                    Spacer(Modifier.width(16.dp))
                }
                Icon(
                    imageVector = destination.icon,
                    contentDescription = null,
                    tint = if (selected) colors.primary else colors.textSecondary,
                    modifier = Modifier.size(20.dp),
                )
                Spacer(Modifier.width(16.dp))
                Text(
                    text = destination.label,
                    style = GriffGymTheme.typography.title,
                    color = if (selected) colors.primary else colors.textPrimary,
                )
            }
        }
    }
}

/** Standard single-instance top level navigation: no stacking of tabs. */
internal fun NavHostController.navigateTopLevel(route: String) {
    navigate(route) {
        popUpTo(graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}
