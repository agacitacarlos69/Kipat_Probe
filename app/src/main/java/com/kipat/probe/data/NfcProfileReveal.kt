package com.kipat.probe.data

import com.kipat.probe.model.AuthenticityState
import com.kipat.probe.model.initialsOf
import com.kipat.probe.nfc.profile.NfcProfileParse
import com.kipat.probe.nfc.profile.NfcProfilePayload
import com.kipat.probe.nfc.profile.NfcProfileType
import com.kipat.probe.reveal.RevealPayload
import com.kipat.probe.reveal.RevealSource
import com.kipat.probe.reveal.RevealTemplateId
import com.kipat.probe.reveal.RevealTheme
import com.kipat.probe.reveal.sanitizeId

/**
 * Self-contained NFC test profile → the existing Reveal. No second UI: the profile becomes a
 * [RevealPayload] like any other tap.
 *
 * Authenticity is always UNVERIFIED and the payload is marked as a test profile: parsing data that
 * any phone can write is not proof of who it belongs to (docs/nfc-self-contained-profile.md).
 */
object NfcProfileReveal {

    fun payloadFor(result: NfcProfileParse, source: RevealSource): RevealPayload = when (result) {
        is NfcProfileParse.Valid -> result.profile.toRevealPayload(source)
        is NfcProfileParse.UnsupportedVersion -> notice(source, "Unsupported Kipat NFC profile version", "This card uses profile version ${result.version}")
        is NfcProfileParse.Invalid -> notice(source, "Kipat profile detected but invalid", "The profile on this card couldn't be read")
    }

    fun NfcProfilePayload.toRevealPayload(source: RevealSource): RevealPayload {
        // A headline leads; the role becomes a third line under it, or the only line if there's no headline.
        val subtitle = headline ?: role
        val extraRole = role.takeIf { headline != null }
        return RevealPayload(
            identityId = null,
            cardId = cardIdFor(this),
            displayName = displayName,
            subtitle = subtitle,
            organization = organization,
            initials = initialsOf(displayName),
            // Only https avatars are kept; the prototype has no network permission, so the
            // monogram shows and the Reveal never waits on an image.
            avatarUri = avatarUrl?.takeIf { it.startsWith("https://") },
            authenticity = AuthenticityState.UNVERIFIED,
            template = reveal ?: RevealTemplateId.DEFAULT,
            theme = accent ?: RevealTheme.NIGHT,
            source = source,
            isDemo = false,
            primaryCta = RevealPayload.defaultPrimaryCta(source),
            secondaryCta = RevealPayload.defaultSecondaryCta(source),
            isOrganization = type == NfcProfileType.ORGANIZATION,
            role = extraRole,
            testProfile = true,
        )
    }

    /** The profile's id when usable; otherwise a stable id derived from the name (for duplicate-tap and Connect). */
    fun cardIdFor(profile: NfcProfilePayload): String =
        sanitizeId(profile.id) ?: "tp-" + Integer.toHexString(profile.displayName.hashCode())

    private fun notice(source: RevealSource, title: String, line: String) =
        RevealPayload.unresolved(null, source).copy(
            displayName = title,
            subtitle = line,
            organization = null,
            initials = "K",
            authenticity = AuthenticityState.UNVERIFIED,
            testProfile = true,
        )
}
