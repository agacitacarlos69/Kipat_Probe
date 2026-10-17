package com.kipat.probe.model

import com.kipat.probe.reveal.RevealTemplateId
import com.kipat.probe.reveal.RevealTheme

/** How a connection was made. A tap alone never creates a connection — see [RecentTap]. */
enum class ConnectionSource(val label: String) {
    NFC_CARD("Kipat Card tap"),
    PHONE_TAP("Phone tap"),
    QR("QR code"),
    LINK("Shared link"),
    EVENT("Event"),
}

data class Connection(
    val id: String,
    val cardId: String,
    val displayName: String,
    val subtitle: String,
    val organization: String,
    val authenticity: AuthenticityState,
    val source: ConnectionSource,
    val connectedAtMillis: Long,
    val pinned: Boolean = false,
    val revealTemplate: RevealTemplateId = RevealTemplateId.DEFAULT,
    val revealTheme: RevealTheme = RevealTheme.NIGHT,
    val isDemo: Boolean = true,
    /** Third context line from their public profile (e.g. "Developer"), when they publish one. */
    val role: String? = null,
    val avatarUrl: String? = null,
    /** Their public profile revision when this snapshot was taken; null when the transport didn't carry one. */
    val profileRevision: Long? = null,
    /** Last time a Kipat encounter (tap, card, link) showed this person again; null = only at connect. */
    val lastSeenAtMillis: Long? = null,
)

enum class ConnectionSort(val label: String) { RECENT("Recent"), ALPHABETICAL("A–Z") }

data class ConnectionSections(val pinned: List<Connection>, val others: List<Connection>) {
    val isEmpty: Boolean get() = pinned.isEmpty() && others.isEmpty()
}

/**
 * Search + sort used by the Connections screen. Pure so it can be unit tested.
 * Search matches name, subtitle and organization, case-insensitively.
 */
fun organizeConnections(all: List<Connection>, query: String, sort: ConnectionSort): ConnectionSections {
    val q = query.trim()
    val matches = if (q.isEmpty()) all else all.filter {
        it.displayName.contains(q, ignoreCase = true) ||
            it.subtitle.contains(q, ignoreCase = true) ||
            it.organization.contains(q, ignoreCase = true)
    }
    val sorted = when (sort) {
        ConnectionSort.RECENT -> matches.sortedByDescending { it.connectedAtMillis }
        ConnectionSort.ALPHABETICAL -> matches.sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.displayName })
    }
    return ConnectionSections(pinned = sorted.filter { it.pinned }, others = sorted.filterNot { it.pinned })
}
