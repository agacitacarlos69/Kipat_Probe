package com.kipat.probe.navigation

import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.kipat.probe.nfc.NfcWriteController
import com.kipat.probe.ui.screens.connections.ConnectionsScreen
import com.kipat.probe.ui.screens.connections.PersonScreen
import com.kipat.probe.ui.screens.developer.DeveloperToolsScreen
import com.kipat.probe.ui.screens.developer.NfcProfileEditorScreen
import com.kipat.probe.ui.screens.developer.NfcProfileTestScreen
import com.kipat.probe.ui.screens.home.HomeScreen
import com.kipat.probe.ui.screens.profile.AddKipatCardScreen
import com.kipat.probe.ui.screens.profile.KipatCardDetailScreen
import com.kipat.probe.ui.screens.profile.KipatCardsScreen
import com.kipat.probe.ui.screens.profile.ProfileEditScreen
import com.kipat.probe.ui.screens.profile.ProfileScreen
import com.kipat.probe.ui.screens.profile.RevealStyleScreen
import com.kipat.probe.ui.screens.developer.ManualPeerTestScreen
import com.kipat.probe.ui.screens.developer.PhysicalPilotScreen
import com.kipat.probe.ui.screens.share.ShareScreen
import com.kipat.probe.ui.theme.Kipat
import com.kipat.probe.ui.theme.KipatMotion
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first

/** Root of the Kipat shell: three tabs with saved per-tab state, plus pushed routes. */
@Composable
fun KipatApp(
    writer: NfcWriteController,
    pendingDestination: MutableStateFlow<String?>,
) {
    val navController = rememberNavController()
    val snackbar = remember { SnackbarHostState() }
    val pending by pendingDestination.collectAsStateWithLifecycle()
    val backStack by navController.currentBackStackEntryAsState()
    val route = backStack?.destination?.route
    // The bar shows on the three tab roots only; pushed screens get the whole height.
    val showBar = KipatDestination.fromRoute(route) != null || route == null

    LaunchedEffect(pending) {
        val target = pending ?: return@LaunchedEffect
        // On a cold start the NavHost (inside Scaffold's subcomposition) may not have set its graph
        // yet; wait for the start destination before navigating (e.g. "Open profile" from a Reveal
        // shown in a fresh process).
        navController.currentBackStackEntryFlow.first()
        when {
            KipatDestination.fromRoute(target) != null -> navController.navigateToTab(KipatDestination.fromRoute(target)!!)
            target.startsWith("person/") -> {
                navController.navigateToTab(KipatDestination.Connections)
                navController.navigate(target)
            }
            target == KipatRoutes.SHARE -> navController.navigate(KipatRoutes.SHARE)
            // Two-phone device kit opens the manual peer test directly.
            target == KipatRoutes.NFC_MANUAL_PEER -> {
                navController.navigateToTab(KipatDestination.Profile)
                navController.navigate(KipatRoutes.DEVELOPER)
                navController.navigate(KipatRoutes.NFC_MANUAL_PEER)
            }
            // Device kit (phase nfcprofile) opens the test screen directly.
            target == KipatRoutes.NFC_PROFILE -> {
                navController.navigateToTab(KipatDestination.Profile)
                navController.navigate(KipatRoutes.DEVELOPER)
                navController.navigate(KipatRoutes.NFC_PROFILE)
            }
        }
        pendingDestination.value = null
    }

    Scaffold(
        containerColor = Kipat.colors.background,
        contentWindowInsets = WindowInsets(0),
        snackbarHost = { SnackbarHost(snackbar) },
        bottomBar = { if (showBar) KipatNavigationBar(navController, route) },
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = KipatDestination.Home.route,
            modifier = Modifier
                .padding(padding)
                .consumeWindowInsets(padding),
            enterTransition = { fadeIn(tween(KipatMotion.TabFade)) },
            exitTransition = { fadeOut(tween(KipatMotion.TabFade)) },
        ) {
            composable(KipatDestination.Home.route) {
                HomeScreen(
                    onShare = { navController.navigate(KipatRoutes.SHARE) },
                    onOpenConnections = { navController.navigateToTab(KipatDestination.Connections) },
                    onOpenPerson = { navController.navigate(KipatRoutes.person(it)) },
                    onCards = { navController.navigate(KipatRoutes.CARDS) },
                )
            }
            composable(KipatDestination.Connections.route) {
                ConnectionsScreen(snackbar = snackbar, onOpenPerson = { navController.navigate(KipatRoutes.person(it)) })
            }
            composable(KipatDestination.Profile.route) {
                ProfileScreen(
                    onEdit = { navController.navigate(KipatRoutes.PROFILE_EDIT) },
                    onRevealStyle = { navController.navigate(KipatRoutes.REVEAL_STYLE) },
                    onCards = { navController.navigate(KipatRoutes.CARDS) },
                    onShare = { navController.navigate(KipatRoutes.SHARE) },
                    onDeveloperTools = { navController.navigate(KipatRoutes.DEVELOPER) },
                )
            }
            pushed(KipatRoutes.SHARE) { ShareScreen(onBack = { navController.popBackStack() }) }
            pushed(KipatRoutes.NFC_MANUAL_PEER) { ManualPeerTestScreen(onBack = { navController.popBackStack() }) }
            pushed(
                KipatRoutes.PERSON,
                arguments = listOf(navArgument(KipatRoutes.PERSON_ID_ARG) { type = NavType.StringType }),
            ) { id -> PersonScreen(connectionId = id.orEmpty(), snackbar = snackbar, onBack = { navController.popBackStack() }) }
            pushed(KipatRoutes.PROFILE_EDIT) { ProfileEditScreen(snackbar = snackbar, onDone = { navController.popBackStack() }) }
            pushed(KipatRoutes.REVEAL_STYLE) { RevealStyleScreen(onBack = { navController.popBackStack() }) }
            pushed(KipatRoutes.CARDS) {
                KipatCardsScreen(
                    onBack = { navController.popBackStack() },
                    onAdd = { navController.navigate(KipatRoutes.CARD_ADD) },
                    onOpen = { navController.navigate(KipatRoutes.card(it)) },
                )
            }
            pushed(KipatRoutes.CARD_ADD) {
                AddKipatCardScreen(
                    onBack = { navController.popBackStack() },
                    onOpenCard = { id -> navController.navigate(KipatRoutes.card(id)) { popUpTo(KipatRoutes.CARDS) } },
                )
            }
            pushed(
                KipatRoutes.CARD,
                arguments = listOf(navArgument(KipatRoutes.CARD_ID_ARG) { type = NavType.StringType }),
            ) { id ->
                KipatCardDetailScreen(
                    cardId = id.orEmpty(),
                    onBack = { navController.popBackStack() },
                    onRevealStyle = { navController.navigate(KipatRoutes.REVEAL_STYLE) },
                )
            }
            pushed(KipatRoutes.DEVELOPER) {
                DeveloperToolsScreen(
                    writer = writer,
                    onBack = { navController.popBackStack() },
                    onNfcProfileTest = { navController.navigate(KipatRoutes.NFC_PROFILE) },
                    onManualPeerTest = { navController.navigate(KipatRoutes.NFC_MANUAL_PEER) },
                    onPhysicalPilot = { navController.navigate(KipatRoutes.PILOT) },
                )
            }
            pushed(KipatRoutes.PILOT) {
                PhysicalPilotScreen(
                    onBack = { navController.popBackStack() },
                    onCardTester = { navController.navigate(KipatRoutes.NFC_PROFILE) },
                    onKipatCards = { navController.navigate(KipatRoutes.CARDS) },
                )
            }
            pushed(KipatRoutes.NFC_PROFILE) {
                NfcProfileTestScreen(
                    onBack = { navController.popBackStack() },
                    onEdit = { navController.navigate(KipatRoutes.NFC_PROFILE_EDIT) },
                    onCreate = { navController.navigate(KipatRoutes.NFC_PROFILE_NEW) },
                    onConvertToLinked = { navController.navigate(KipatRoutes.CARD_ADD) },
                )
            }
            pushed(KipatRoutes.NFC_PROFILE_EDIT) { NfcProfileEditorScreen(startBlank = false, onDone = { navController.popBackStack() }) }
            pushed(KipatRoutes.NFC_PROFILE_NEW) { NfcProfileEditorScreen(startBlank = true, onDone = { navController.popBackStack() }) }
        }
    }
}

/** A pushed screen: short horizontal slide + fade (predictive-back friendly; no custom back). */
private fun NavGraphBuilder.pushed(
    route: String,
    arguments: List<androidx.navigation.NamedNavArgument> = emptyList(),
    content: @Composable (String?) -> Unit,
) {
    composable(
        route = route,
        arguments = arguments,
        enterTransition = { slideInHorizontally(tween(KipatMotion.Push, easing = KipatMotion.Decelerate)) { it / 8 } + fadeIn(tween(KipatMotion.Push)) },
        exitTransition = { fadeOut(tween(KipatMotion.Quick)) },
        popEnterTransition = { fadeIn(tween(KipatMotion.Quick)) },
        popExitTransition = { slideOutHorizontally(tween(KipatMotion.Standard, easing = KipatMotion.Accelerate)) { it / 8 } + fadeOut(tween(KipatMotion.Standard)) },
    ) { entry -> content(arguments.firstOrNull()?.let { entry.arguments?.getString(it.name) }) }
}

@Composable
private fun KipatNavigationBar(navController: NavHostController, route: String?) {
    val selectedTab = KipatRoutes.owningTab(route)
    Column {
        HorizontalDivider(thickness = 1.dp, color = Kipat.colors.divider)
        NavigationBar(containerColor = Kipat.colors.background, tonalElevation = 0.dp) {
            KipatDestination.entries.forEach { dest ->
                val selected = selectedTab == dest
                NavigationBarItem(
                    selected = selected,
                    onClick = {
                        // Re-tapping the active tab returns to its root instead of re-creating it.
                        if (selected) navController.popBackStack(dest.route, inclusive = false) else navController.navigateToTab(dest)
                    },
                    icon = { Icon(if (selected) dest.selectedIcon else dest.icon, contentDescription = null) },
                    label = { Text(dest.label, style = MaterialTheme.typography.labelMedium) },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = Kipat.colors.accent,
                        selectedTextColor = Kipat.colors.textPrimary,
                        indicatorColor = Kipat.colors.accentContainer,
                        unselectedIconColor = Kipat.colors.textSecondary,
                        unselectedTextColor = Kipat.colors.textSecondary,
                    ),
                )
            }
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
