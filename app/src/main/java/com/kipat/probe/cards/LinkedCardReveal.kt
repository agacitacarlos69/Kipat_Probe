package com.kipat.probe.cards

import com.kipat.probe.data.NfcProfileReveal.toRevealPayload
import com.kipat.probe.model.AuthenticityState
import com.kipat.probe.reveal.RevealPayload
import com.kipat.probe.reveal.RevealSource

/**
 * [CardResolution] → the existing Reveal. A resolved card goes through exactly the mapping a
 * phone-to-phone Kipat Tap uses ([toRevealPayload] on the same [com.kipat.probe.data.PublicKipatProfileSnapshot]),
 * so the two transports can't drift: same name, lines, Reveal template and accent.
 *
 * Authenticity is UNVERIFIED for every NTAG card (anyone can copy its URI). A card that is turned
 * off, removed or unknown shows no profile at all and can't be connected.
 */
object LinkedCardReveal {

    fun payloadFor(resolution: CardResolution, source: RevealSource = RevealSource.NFC_TAG): RevealPayload = when (resolution) {
        is CardResolution.Resolved -> resolution.snapshot.profile.toRevealPayload(source, testProfile = false)
            .copy(authenticity = AuthenticityState.UNVERIFIED)
        is CardResolution.Disabled -> notice(resolution, source, "Kipat card unavailable", "Its owner turned this card off", AuthenticityState.UNVERIFIED)
        is CardResolution.Revoked -> notice(resolution, source, "Kipat card removed", "This card is no longer linked to a Kipat", AuthenticityState.REVOKED)
        is CardResolution.Unknown -> notice(resolution, source, "Kipat card", "This card can't be opened on this phone yet", AuthenticityState.UNKNOWN)
        is CardResolution.ProfileUnavailable -> notice(resolution, source, "Kipat card", "Its Kipat profile isn't available", AuthenticityState.UNKNOWN)
        is CardResolution.Malformed -> RevealPayload.unresolved(null, source)
    }

    private fun notice(r: CardResolution, source: RevealSource, title: String, line: String, authenticity: AuthenticityState) =
        RevealPayload.unresolved(r.cardId, source).copy(
            displayName = title,
            subtitle = line,
            organization = null,
            authenticity = authenticity,
            connectable = false,
        )
}
