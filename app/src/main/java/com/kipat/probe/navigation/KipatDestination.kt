package com.kipat.probe.navigation

import androidx.compose.ui.graphics.vector.ImageVector
import com.kipat.probe.ui.components.KipatSymbols

/**
 * The three top-level places (design/research/navigation-decision.md). Sharing is an action on
 * Home, not a tab; developer tools live under Profile.
 */
enum class KipatDestination(
    val route: String,
    val label: String,
    val icon: ImageVector,
    val selectedIcon: ImageVector,
) {
    Home("home", "Home", KipatSymbols.Home, KipatSymbols.HomeFilled),
    Connections("connections", "Connections", KipatSymbols.People, KipatSymbols.PeopleFilled),
    Profile("profile", "Profile", KipatSymbols.Person, KipatSymbols.PersonFilled);

    companion object {
        fun fromRoute(route: String?): KipatDestination? = entries.firstOrNull { it.route == route }
    }
}

/** Routes pushed on top of a tab. */
object KipatRoutes {
    const val SHARE = "share"
    const val PERSON_ID_ARG = "connectionId"
    const val PERSON = "person/{$PERSON_ID_ARG}"
    const val PROFILE_EDIT = "profile/edit"
    const val REVEAL_STYLE = "profile/reveal-style"
    const val DEVELOPER = "profile/developer"
    const val NFC_PROFILE = "profile/developer/nfc-profile"
    const val NFC_PROFILE_EDIT = "profile/developer/nfc-profile/edit"
    const val NFC_PROFILE_NEW = "profile/developer/nfc-profile/new"
    const val NFC_MANUAL_PEER = "profile/developer/nfc-manual-peer"
    const val CARDS = "profile/cards"
    const val CARD_ADD = "profile/cards/add"
    const val CARD_ID_ARG = "cardId"
    const val CARD = "profile/card/{$CARD_ID_ARG}"

    fun person(id: String) = "person/$id"
    fun card(id: String) = "profile/card/$id"

    /** Which tab a pushed route belongs to, for the selected state of the navigation bar. */
    fun owningTab(route: String?): KipatDestination? = when (route) {
        PERSON -> KipatDestination.Connections
        PROFILE_EDIT, REVEAL_STYLE, CARDS, CARD_ADD, CARD, DEVELOPER, NFC_PROFILE, NFC_PROFILE_EDIT, NFC_PROFILE_NEW, NFC_MANUAL_PEER -> KipatDestination.Profile
        SHARE -> null
        else -> KipatDestination.fromRoute(route)
    }
}
