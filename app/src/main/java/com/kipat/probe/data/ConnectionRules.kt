package com.kipat.probe.data

import com.kipat.probe.model.Connection
import com.kipat.probe.model.ConnectionSource
import com.kipat.probe.reveal.RevealPayload
import com.kipat.probe.reveal.RevealSource
import com.kipat.probe.reveal.sanitizeId

/**
 * The Connection lifecycle rules, pure so they are unit tested without Android:
 *
 * - One person = one Connection. The key is their stable public Kipat id ([identityKeyOf]): the
 *   same for a phone tap, their linked card and their link, so all three land on one row.
 * - Only an explicit Connect creates a Connection. An encounter refreshes an existing one; it never
 *   creates or re-creates one (a removed person stays removed until Connect is pressed again).
 * - A refresh keeps the relationship (local id, connectedAt, pinned, source) and only replaces the
 *   public snapshot, and only from a live profile that is not older than the stored one.
 */
object ConnectionRules {

    /** The stable public id of the person on the Reveal, or null when there isn't a usable one. */
    fun identityKeyOf(payload: RevealPayload): String? = sanitizeId(payload.cardId)

    fun sourceOf(source: RevealSource): ConnectionSource = when (source) {
        RevealSource.PEER_NFC -> ConnectionSource.PHONE_TAP
        RevealSource.NFC_TAG -> ConnectionSource.NFC_CARD
        else -> ConnectionSource.LINK
    }

    fun newConnection(id: String, payload: RevealPayload, at: Long, revision: Long? = null, source: ConnectionSource = sourceOf(payload.source)) = Connection(
        id = id,
        cardId = identityKeyOf(payload) ?: payload.cardId.orEmpty(),
        displayName = payload.displayName,
        subtitle = payload.subtitle.orEmpty(),
        organization = payload.organization.orEmpty(),
        authenticity = payload.authenticity,
        source = source,
        connectedAtMillis = at,
        revealTemplate = payload.template,
        revealTheme = payload.theme,
        isDemo = payload.isDemo,
        role = payload.role,
        avatarUrl = payload.avatarUri?.takeIf { it.startsWith("https://") },
        profileRevision = revision,
        lastSeenAtMillis = at,
    )

    /**
     * Another encounter with someone already connected. Always moves [Connection.lastSeenAtMillis].
     * Replaces the public snapshot only when [live] (read from their phone or resolved from their
     * linked card, not a static self-contained card or unsigned tag hints) and [revision] is not older
     * than the stored one. When either revision is unknown, the live encounter is the newest view.
     */
    fun refreshed(existing: Connection, payload: RevealPayload, at: Long, live: Boolean, revision: Long? = null): Connection {
        val seen = existing.copy(lastSeenAtMillis = maxOf(at, existing.lastSeenAtMillis ?: 0L))
        if (!live) return seen
        val stored = existing.profileRevision
        if (revision != null && stored != null && revision < stored) return seen
        return seen.copy(
            displayName = payload.displayName,
            subtitle = payload.subtitle.orEmpty(),
            organization = payload.organization.orEmpty(),
            role = payload.role,
            avatarUrl = payload.avatarUri?.takeIf { it.startsWith("https://") } ?: existing.avatarUrl,
            authenticity = payload.authenticity,
            revealTemplate = payload.template,
            revealTheme = payload.theme,
            profileRevision = revision ?: stored,
        )
    }
}
