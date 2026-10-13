package com.kipat.probe.navigation

import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.kipat.probe.nfc.NfcWriteController
import com.kipat.probe.ui.screens.connections.ConnectionProfileScreen
import com.kipat.probe.ui.screens.connections.ConnectionsScreen
import com.kipat.probe.ui.screens.home.HomeScreen
import com.kipat.probe.ui.screens.profile.ProfileScreen
import com.kipat.probe.ui.screens.touch.TouchScreen
import com.kipat.probe.ui.theme.Kipat
import com.kipat.probe.ui.theme.KipatMotion
import kotlinx.coroutines.flow.MutableStateFlow

/** Root of the Kipat app: four-tab shell with state-preserving navigation. */
@Composable
fun KipatApp(
    writer: NfcWriteController,
    pendingDestination: MutableStateFlow<String?>,
) {
    val navController = rememberNavController()
    val snackbar = remember { SnackbarHostState() }
    val pending by pendingDestination.collectAsStateWithLifecycle()

    LaunchedEffect(pending) {
        val route = pending ?: return@LaunchedEffect
        KipatDestination.fromRoute(route)?.let { navController.navigateToTab(it) }
        pendingDestination.value = null
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets(0),
        snackbarHost = { SnackbarHost(snackbar) },
        bottomBar = { KipatBottomBar(navController) },
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = KipatDestination.Home.route,
            modifier = Modifier.padding(padding),
            enterTransition = { fadeIn(tween(KipatMotion.Standard)) },
            exitTransition = { fadeOut(tween(KipatMotion.Quick)) },
        ) {
            composable(KipatDestination.Home.route) {
                HomeScreen(
                    onOpenConnections = { navController.navigateToTab(KipatDestination.Connections) },
                    onOpenProfile = { navController.navigateToTab(KipatDestination.Profile) },
                    onOpenTouch = { navController.navigateToTab(KipatDestination.Touch) },
                    onOpenConnection = { navController.navigate(KipatRoutes.connectionProfile(it)) },
                )
            }
            composable(KipatDestination.Connections.route) {
                ConnectionsScreen(
                    snackbar = snackbar,
                    onOpenProfile = { navController.navigate(KipatRoutes.connectionProfile(it)) },
                )
            }
            composable(KipatDestination.Touch.route) {
                TouchScreen(writer = writer)
            }
            composable(KipatDestination.Profile.route) {
                ProfileScreen(snackbar = snackbar)
            }
            composable(
                route = KipatRoutes.CONNECTION_PROFILE,
                arguments = listOf(navArgument(KipatRoutes.CONNECTION_ID_ARG) { type = NavType.StringType }),
                enterTransition = { slideInHorizontally(tween(KipatMotion.Emphasized, easing = KipatMotion.EaseOut)) { it / 6 } + fadeIn(tween(KipatMotion.Standard)) },
                popExitTransition = { slideOutHorizontally(tween(KipatMotion.Standard, easing = KipatMotion.EaseIn)) { it / 6 } + fadeOut(tween(KipatMotion.Quick)) },
            ) { entry ->
                ConnectionProfileScreen(
                    connectionId = entry.arguments?.getString(KipatRoutes.CONNECTION_ID_ARG).orEmpty(),
                    onBack = { navController.popBackStack() },
                )
            }
        }
    }
}

@Composable
private fun KipatBottomBar(navController: NavHostController) {
    val backStack by navController.currentBackStackEntryAsState()
    val current = backStack?.destination
    NavigationBar(
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        tonalElevation = androidx.compose.ui.unit.Dp.Hairline,
    ) {
        KipatDestination.entries.forEach { dest ->
            val selected = current?.hierarchy?.any { it.route == dest.route } == true ||
                (dest == KipatDestination.Connections && current?.route == KipatRoutes.CONNECTION_PROFILE)
            NavigationBarItem(
                selected = selected,
                onClick = {
                    if (selected) {
                        // Re-tapping the active tab returns to its root instead of re-creating it.
                        navController.popBackStack(dest.route, inclusive = false)
                    } else {
                        navController.navigateToTab(dest)
                    }
                },
                icon = { Icon(if (selected) dest.selectedIcon else dest.icon, contentDescription = null) },
                label = { Text(dest.label, style = MaterialTheme.typography.labelMedium) },
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = Kipat.colors.accent,
                    selectedTextColor = Kipat.colors.textPrimary,
                    indicatorColor = Kipat.colors.accent.copy(alpha = 0.12f),
                    unselectedIconColor = Kipat.colors.textTertiary,
                    unselectedTextColor = Kipat.colors.textTertiary,
                ),
            )
        }
    }
}

/** Standard multi-back-stack tab navigation: saves and restores each tab's state. */
fun NavHostController.navigateToTab(dest: KipatDestination) {
    navigate(dest.route) {
        popUpTo(graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}
