package com.kipat.probe.reveal

import com.kipat.probe.model.AuthenticityState
import com.kipat.probe.model.initialsOf

/** Where a Reveal was triggered from. Preview and NFC share the same renderer. */
enum class RevealSource(val wireId: String) {
    NFC_TAG("nfc"),
    DEEP_LINK("deeplink"),
    PREVIEW("preview"),
    REPLAY("replay"),

    /** Another Kipat phone, over phone-to-phone NFC (docs/nfc-phone-to-phone.md). Never verified. */
    PEER_NFC("peer");

    /** Someone else's identity arriving from outside the app (card, link or another phone). */
    val isExternalTap: Boolean get() = this == NFC_TAG || this == DEEP_LINK || this == PEER_NFC

    companion object {
        fun fromWireId(value: String?): RevealSource =
            entries.firstOrNull { it.wireId.equals(value?.trim(), ignoreCase = true) } ?: DEEP_LINK
    }
}

/**
 * Everything the Reveal needs to render, already resolved locally. Nothing in here requires a
 * network round-trip, so the first visual never waits on IO.
 */
data class RevealPayload(
    val identityId: String?,
    val cardId: String?,
    val displayName: String,
    val subtitle: String?,
    val organization: String?,
    val initials: String,
    /** Reserved for real avatars/logos; the renderer falls back to [initials] when null. */
    val avatarUri: String?,
    val authenticity: AuthenticityState,
    val template: RevealTemplateId,
    val theme: RevealTheme,
    val source: RevealSource,
    val isDemo: Boolean,
    val primaryCta: String,
    val secondaryCta: String,
    /** False for cards we couldn't identify (unresolved / legacy probe tags). */
    val connectable: Boolean = true,
    /** A company/organisation identity: its mark is a logo (rounded square), not a person's portrait. */
    val isOrganization: Boolean = false,
    /** Optional third context line (e.g. "Developer" under "BS Computer Science"). */
    val role: String? = null,
    /**
     * Read from a self-contained NFC test profile (docs/nfc-self-contained-profile.md). Shown as
     * "Test profile" next to the trust state; such a profile is never verified.
     */
    val testProfile: Boolean = false,
) {
    /**
     * Connect only makes sense for someone else's identity seen through a real tap, and only
     * once we know who it is (an unresolved card can't become a meaningful connection).
     */
    val canConnect: Boolean
        get() = connectable && source.isExternalTap

    /** Shown as "Unverified Kipat · Nearby Kipat": an HCE exchange proves contact, not identity. */
    val isNearbyPeer: Boolean get() = source == RevealSource.PEER_NFC

    companion object {
        // Match the NFC test-profile limits so names and organisations are never silently cut.
        const val MAX_NAME = 120
        const val MAX_LINE = 200
        const val MAX_ID = 64
        const val FALLBACK_NAME = "Kipat card"

        /** Preview shows exactly what a recipient would see, so it uses the same label. */
        fun defaultPrimaryCta(source: RevealSource) = when (source) {
            RevealSource.REPLAY -> "Connected"
            else -> "Connect"
        }

        fun defaultSecondaryCta(@Suppress("UNUSED_PARAMETER") source: RevealSource) = "Save contact"

        /** Shown when a tag or link could not be resolved to any known identity. */
        fun unresolved(cardId: String?, source: RevealSource): RevealPayload = RevealPayload(
            identityId = null,
            cardId = cardId,
            displayName = FALLBACK_NAME,
            subtitle = cardId?.let { "Card $it" } ?: "Unrecognized card",
            organization = "Details unavailable offline",
            initials = "K",
            avatarUri = null,
            authenticity = AuthenticityState.UNKNOWN,
            template = RevealTemplateId.DEFAULT,
            theme = RevealTheme.NIGHT,
            source = source,
            isDemo = false,
            primaryCta = "Done",
            secondaryCta = defaultSecondaryCta(source),
            connectable = false,
        )
    }
}

/** Turns control characters into spaces, collapses whitespace and clamps length; empty becomes null. */
internal fun sanitizeLine(value: String?, max: Int): String? {
    if (value == null) return null
    val cleaned = buildString {
        for (c in value) append(if (c.isISOControl()) ' ' else c)
    }.trim().replace(Regex("\\s+"), " ")
    if (cleaned.isEmpty()) return null
    return if (cleaned.length <= max) cleaned else cleaned.take(max - 1).trimEnd() + "…"
}

private val ID_PATTERN = Regex("^[A-Za-z0-9_-]{1,${RevealPayload.MAX_ID}}$")

internal fun sanitizeId(value: String?): String? = value?.trim()?.takeIf { ID_PATTERN.matches(it) }

/**
 * Flat key/value encoding of [RevealPayload] used for Intent extras. Kept free of Android types
 * so validation and fallback behaviour are covered by JVM unit tests.
 */
object RevealPayloadCodec {
    const val KEY_IDENTITY_ID = "kipat.reveal.identityId"
    const val KEY_CARD_ID = "kipat.reveal.cardId"
    const val KEY_NAME = "kipat.reveal.name"
    const val KEY_SUBTITLE = "kipat.reveal.subtitle"
    const val KEY_ORG = "kipat.reveal.org"
    const val KEY_AVATAR = "kipat.reveal.avatar"
    const val KEY_AUTH = "kipat.reveal.auth"
    const val KEY_TEMPLATE = "kipat.reveal.template"
    const val KEY_THEME = "kipat.reveal.theme"
    const val KEY_SOURCE = "kipat.reveal.source"
    const val KEY_DEMO = "kipat.reveal.demo"
    const val KEY_PRIMARY = "kipat.reveal.cta1"
    const val KEY_SECONDARY = "kipat.reveal.cta2"
    const val KEY_KIND = "kipat.reveal.kind"
    const val KEY_ROLE = "kipat.reveal.role"
    const val KEY_TEST_PROFILE = "kipat.reveal.testProfile"
    private const val KIND_ORGANIZATION = "org"

    data class Decoded(val payload: RevealPayload, val issues: List<String>) {
        val isClean: Boolean get() = issues.isEmpty()
    }

    fun encode(p: RevealPayload): Map<String, String> = buildMap {
        p.identityId?.let { put(KEY_IDENTITY_ID, it) }
        p.cardId?.let { put(KEY_CARD_ID, it) }
        put(KEY_NAME, p.displayName)
        p.subtitle?.let { put(KEY_SUBTITLE, it) }
        p.organization?.let { put(KEY_ORG, it) }
        p.avatarUri?.let { put(KEY_AVATAR, it) }
        put(KEY_AUTH, p.authenticity.wireId)
        put(KEY_TEMPLATE, p.template.wireId)
        put(KEY_THEME, p.theme.wireId)
        put(KEY_SOURCE, p.source.wireId)
        put(KEY_DEMO, p.isDemo.toString())
        put(KEY_PRIMARY, p.primaryCta)
        put(KEY_SECONDARY, p.secondaryCta)
        if (p.isOrganization) put(KEY_KIND, KIND_ORGANIZATION)
        p.role?.let { put(KEY_ROLE, it) }
        if (p.testProfile) put(KEY_TEST_PROFILE, "true")
    }

    /** True when the extras contain an explicit payload (as opposed to a bare NFC/deep link). */
    fun hasPayload(get: (String) -> String?): Boolean = get(KEY_NAME) != null

    /**
     * Decodes and validates. Never throws: malformed values are replaced with safe defaults and
     * reported in [Decoded.issues] for debug logging.
     */
    fun decode(get: (String) -> String?): Decoded {
        val issues = mutableListOf<String>()
        val source = RevealSource.fromWireId(get(KEY_SOURCE))
        val rawName = get(KEY_NAME)
        val name = sanitizeLine(rawName, RevealPayload.MAX_NAME) ?: run {
            issues += "missing display name"
            RevealPayload.FALLBACK_NAME
        }
        val rawTemplate = get(KEY_TEMPLATE)
        if (rawTemplate != null && !RevealTemplateId.isKnown(rawTemplate)) issues += "unknown template '$rawTemplate'"
        val rawAuth = get(KEY_AUTH)
        val auth = AuthenticityState.fromWireId(rawAuth)
        if (rawAuth != null && auth == AuthenticityState.UNKNOWN && !rawAuth.equals("unknown", true)) issues += "unknown authenticity '$rawAuth'"
        val rawCard = get(KEY_CARD_ID)
        val cardId = sanitizeId(rawCard)
        if (rawCard != null && cardId == null) issues += "invalid card id"
        val avatar = get(KEY_AVATAR)?.takeIf { it.startsWith("content://") || it.startsWith("https://") }
        if (get(KEY_AVATAR) != null && avatar == null) issues += "rejected avatar uri"

        val payload = RevealPayload(
            identityId = sanitizeId(get(KEY_IDENTITY_ID)),
            cardId = cardId,
            displayName = name,
            subtitle = sanitizeLine(get(KEY_SUBTITLE), RevealPayload.MAX_LINE),
            organization = sanitizeLine(get(KEY_ORG), RevealPayload.MAX_LINE),
            initials = initialsOf(name),
            avatarUri = avatar,
            authenticity = auth,
            template = RevealTemplateId.fromWireId(rawTemplate),
            theme = RevealTheme.fromWireId(get(KEY_THEME)),
            source = source,
            isDemo = get(KEY_DEMO)?.toBooleanStrictOrNull() ?: false,
            primaryCta = sanitizeLine(get(KEY_PRIMARY), 24) ?: RevealPayload.defaultPrimaryCta(source),
            secondaryCta = sanitizeLine(get(KEY_SECONDARY), 24) ?: RevealPayload.defaultSecondaryCta(source),
            // Anything other than the organisation marker is a person; never an error.
            isOrganization = get(KEY_KIND) == KIND_ORGANIZATION,
            role = sanitizeLine(get(KEY_ROLE), RevealPayload.MAX_LINE),
            testProfile = get(KEY_TEST_PROFILE) == "true",
        )
        return Decoded(payload, issues)
    }
}
