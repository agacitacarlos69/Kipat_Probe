package com.kipat.probe.data

import com.kipat.probe.model.AuthenticityState
import com.kipat.probe.model.Connection
import com.kipat.probe.model.Identity
import com.kipat.probe.model.initialsOf
import com.kipat.probe.nfc.TapChannel
import com.kipat.probe.nfc.TapTarget
import com.kipat.probe.reveal.RevealPayload
import com.kipat.probe.reveal.RevealSource
import com.kipat.probe.reveal.RevealTemplateId
import com.kipat.probe.reveal.RevealTheme

fun Identity.toRevealPayload(source: RevealSource, isDemo: Boolean, authenticity: AuthenticityState): RevealPayload =
    RevealPayload(
        identityId = id,
        cardId = cardId,
        displayName = displayName,
        subtitle = subtitle.ifBlank { null },
        organization = organization.ifBlank { null },
        initials = initialsOf(displayName),
        avatarUri = avatarUri,
        authenticity = authenticity,
        template = revealTemplate,
        theme = revealTheme,
        source = source,
        isDemo = isDemo,
        primaryCta = RevealPayload.defaultPrimaryCta(source),
        secondaryCta = RevealPayload.defaultSecondaryCta(source),
        isOrganization = type == com.kipat.probe.model.IdentityType.COMPANY,
    )

fun Connection.toRevealPayload(source: RevealSource = RevealSource.REPLAY): RevealPayload =
    RevealPayload(
        identityId = id,
        cardId = cardId,
        displayName = displayName,
        subtitle = subtitle.ifBlank { null },
        organization = organization.ifBlank { null },
        initials = initialsOf(displayName),
        avatarUri = null,
        authenticity = authenticity,
        template = revealTemplate,
        theme = revealTheme,
        source = source,
        isDemo = isDemo,
        primaryCta = RevealPayload.defaultPrimaryCta(source),
        secondaryCta = RevealPayload.defaultSecondaryCta(source),
    )

/** Re-opens the Reveal of an earlier tap, still offering Connect (it was a real tap). */
fun com.kipat.probe.model.RecentTap.toRevealPayload(): RevealPayload {
    val parts = subtitle.split(", ", limit = 2)
    return RevealPayload(
        identityId = null,
        cardId = cardId.ifBlank { null },
        displayName = displayName,
        subtitle = parts.getOrNull(0)?.ifBlank { null },
        organization = parts.getOrNull(1)?.ifBlank { null },
        initials = initialsOf(displayName),
        avatarUri = null,
        authenticity = authenticity,
        template = RevealTemplateId.DEFAULT,
        theme = RevealTheme.NIGHT,
        source = RevealSource.NFC_TAG,
        isDemo = isDemo,
        primaryCta = RevealPayload.defaultPrimaryCta(RevealSource.NFC_TAG),
        secondaryCta = RevealPayload.defaultSecondaryCta(RevealSource.NFC_TAG),
    )
}

/**
 * Turns a parsed tap into something renderable immediately, using only local data.
 * Backend verification (future) updates [RevealPayload.authenticity] after the first frame.
 */
object RevealResolver {
    const val DEMO_ALIAS = "demo"

    fun resolve(target: TapTarget, ownIdentity: Identity, ownCardIsDemo: Boolean, connections: List<Connection>): RevealPayload {
        val source = if (target.channel == TapChannel.DEBUG_DEEP_LINK) RevealSource.DEEP_LINK else RevealSource.NFC_TAG
        val id = target.cardId
        val hints = target.hints

        val base: RevealPayload = when {
            id == null && !hints.hasIdentity -> RevealPayload.unresolved(null, source)
            id.equals(DEMO_ALIAS, ignoreCase = true) || id == ownIdentity.cardId ->
                ownIdentity.toRevealPayload(source, isDemo = ownCardIsDemo, authenticity = AuthenticityState.AUTHENTIC)
            target.channel == TapChannel.LEGACY_PROBE_TAG -> RevealPayload.unresolved(id, source).copy(
                displayName = "Kipat probe tag",
                subtitle = "Written by the diagnostic probe",
                organization = "Re-write it from Touch → Prototype Card Tools",
                initials = "KP",
            )
            else -> {
                val known = connections.firstOrNull { it.cardId == id }
                when {
                    known != null -> known.toRevealPayload(source).copy(
                        primaryCta = "Connected",
                        secondaryCta = RevealPayload.defaultSecondaryCta(source),
                    )
                    hints.hasIdentity -> RevealPayload(
                        identityId = null,
                        cardId = id,
                        displayName = hints.name!!,
                        subtitle = hints.subtitle,
                        organization = hints.organization,
                        initials = initialsOf(hints.name),
                        avatarUri = null,
                        // Tag-supplied text is unsigned: never present it as verified.
                        authenticity = AuthenticityState.UNVERIFIED,
                        template = hints.template ?: RevealTemplateId.DEFAULT,
                        theme = hints.theme ?: RevealTheme.NIGHT,
                        source = source,
                        isDemo = false,
                        primaryCta = RevealPayload.defaultPrimaryCta(source),
                        secondaryCta = RevealPayload.defaultSecondaryCta(source),
                    )
                    else -> RevealPayload.unresolved(id, source)
                }
            }
        }

        var result = base
        if (target.channel == TapChannel.DEBUG_DEEP_LINK) {
            // Debug link overrides exist purely for QA; label them as demo so they never look real.
            hints.template?.let { result = result.copy(template = it) }
            hints.theme?.let { result = result.copy(theme = it) }
            hints.debugAuthenticity?.let { result = result.copy(authenticity = it, isDemo = true) }
        }
        return result
    }
}
