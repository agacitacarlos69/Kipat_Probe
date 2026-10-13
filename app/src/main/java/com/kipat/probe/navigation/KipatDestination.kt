package com.kipat.probe.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Person
import androidx.compose.ui.graphics.vector.ImageVector
import com.kipat.probe.ui.components.KipatIcons

/** The four primary tabs, in bottom-bar order. */
enum class KipatDestination(
    val route: String,
    val label: String,
    val icon: ImageVector,
    val selectedIcon: ImageVector,
) {
    Home("home", "Home", Icons.Outlined.Home, Icons.Filled.Home),
    Connections("connections", "Connections", KipatIcons.Connections, KipatIcons.Connections),
    Touch("touch", "Touch", KipatIcons.Touch, KipatIcons.Touch),
    Profile("profile", "Profile", Icons.Outlined.Person, Icons.Filled.Person);

    companion object {
        fun fromRoute(route: String?): KipatDestination? = entries.firstOrNull { it.route == route }
    }
}

/** Secondary routes pushed on top of a tab. */
object KipatRoutes {
    const val CONNECTION_ID_ARG = "connectionId"
    const val CONNECTION_PROFILE = "connection/{$CONNECTION_ID_ARG}"
    fun connectionProfile(id: String) = "connection/$id"
}
