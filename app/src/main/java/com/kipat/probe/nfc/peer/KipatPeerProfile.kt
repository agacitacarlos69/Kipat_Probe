package com.kipat.probe.nfc.peer

import com.kipat.probe.model.Identity
import com.kipat.probe.model.IdentityType
import com.kipat.probe.model.ProfileField
import com.kipat.probe.model.ProfileTemplateId
import com.kipat.probe.nfc.profile.NfcProfileFormat
import com.kipat.probe.nfc.profile.NfcProfileMode
import com.kipat.probe.nfc.profile.NfcProfileParse
import com.kipat.probe.nfc.profile.NfcProfilePayload
import com.kipat.probe.nfc.profile.NfcProfileType
import com.kipat.probe.nfc.profile.NfcProfileValidator

/** Structured, content-free events (shape and sizes only). Android wires this to Logcat. */
fun interface PeerLog {
    fun event(tag: String, name: String, detail: String)

    companion object {
        const val TAG_SESSION = "KipatPeerNfc"
        const val TAG_HCE = "KipatHce"
        const val TAG_READER = "KipatPeerReader"
        const val TAG_PROTOCOL = "KipatPeerProtocol"
        val NONE = PeerLog { _, _, _ -> }
    }
}

/**
 * The public profile a phone shares over NFC. Built ONLY from [Identity.publishedFields] — the
 * same boundary the recipient's Reveal uses — and serialized with the self-contained NFC profile
 * format, so both NFC transports share one schema, one validator and one Reveal mapping.
 *
 * Never included: tokens, passwords, device identifiers, location, contacts, unpublished fields.
 */
object KipatPeerProfile {

    fun fromIdentity(identity: Identity): NfcProfilePayload {
        val published = identity.publishedFields()
        fun pub(f: ProfileField) = published[f]?.trim()?.takeIf { it.isNotEmpty() }
        // The same primary line the Reveal shows (program for students, role otherwise), without the bio fallback.
        val headline = if (identity.profileTemplate == ProfileTemplateId.STUDENT_MINIMAL) pub(ProfileField.PROGRAM) else pub(ProfileField.ROLE)
        val role = pub(ProfileField.ROLE)?.takeIf { it != headline }
        val raw = NfcProfilePayload(
            id = identity.cardId,
            type = if (identity.type == IdentityType.COMPANY) NfcProfileType.ORGANIZATION else NfcProfileType.PERSON,
            displayName = identity.displayName,
            headline = headline,
            // Only if the active template publishes it (a Creator profile has no organisation line).
            organization = if (identity.profileTemplate == ProfileTemplateId.STUDENT_MINIMAL) pub(ProfileField.UNIVERSITY) else pub(ProfileField.COMPANY),
            role = role,
            bio = pub(ProfileField.BIO),
            avatarUrl = identity.avatarUri?.takeIf { it.startsWith("https://") },
            website = pub(ProfileField.WEBSITE)?.let(::webUrl),
            github = pub(ProfileField.GITHUB)?.let { webUrl(it, "https://github.com/") },
            linkedin = pub(ProfileField.LINKEDIN)?.let(::webUrl),
            publicEmail = pub(ProfileField.PUBLIC_EMAIL),
            accent = identity.revealTheme,
            reveal = identity.revealTemplate,
        )
        // Same rules as the reader: anything that would be dropped on arrival is not sent.
        return NfcProfileValidator.dropInvalidOptional(raw).first
    }

    fun toBytes(profile: NfcProfilePayload): ByteArray = NfcProfileFormat.toBytes(profile, NfcProfileMode.FULL)

    /** Parses received bytes with the same hostile-input rules as a physical profile card. */
    fun parse(bytes: ByteArray): NfcProfileParse = NfcProfileFormat.parse(bytes)

    /** Same person: equal public profile ids (the card id). Never a connection to yourself. */
    fun isSamePerson(a: NfcProfilePayload?, ownId: String?): Boolean = a?.id != null && a.id == ownId

    /** "github.com/x" → "https://github.com/x"; a bare GitHub username gets the GitHub prefix. */
    internal fun webUrl(value: String, bareNamePrefix: String? = null): String? {
        val v = value.trim()
        if (v.startsWith("https://") || v.startsWith("http://")) return v
        if (v.contains('.') && !v.contains(' ')) return "https://$v"
        if (bareNamePrefix != null && Regex("^[A-Za-z0-9-]{1,39}$").matches(v)) return bareNamePrefix + v
        return null
    }
}
