package com.kipat.probe.nfc

import com.kipat.probe.model.AuthenticityState
import com.kipat.probe.nfc.peer.KipatPeerBootstrap
import com.kipat.probe.nfc.profile.NdefRecordData
import com.kipat.probe.nfc.profile.NfcProfileFormat
import com.kipat.probe.nfc.profile.NfcProfileParse
import com.kipat.probe.reveal.RevealTemplateId
import com.kipat.probe.reveal.RevealTheme
import com.kipat.probe.reveal.sanitizeId
import com.kipat.probe.reveal.sanitizeLine
import java.net.URI
import java.net.URLDecoder
import java.net.URLEncoder

/** How a tap reached the app. */
enum class TapChannel {
    /** NDEF_DISCOVERED from a Kipat card (https://kipat.app/c/…). */
    NFC_TAG,
    /** Tag written by the original diagnostic probe (https://probe.kipat.com). */
    LEGACY_PROBE_TAG,
    /** kipatprobe://tap/… debug route, used for ADB smoke tests. */
    DEBUG_DEEP_LINK,
}

/**
 * Optional display hints carried on the tag/link itself. They are unsigned, so a Reveal built
 * from them must be shown as [AuthenticityState.UNVERIFIED].
 */
data class TapHints(
    val name: String? = null,
    val subtitle: String? = null,
    val organization: String? = null,
    val template: RevealTemplateId? = null,
    val theme: RevealTheme? = null,
    /** Only honoured for [TapChannel.DEBUG_DEEP_LINK] so QA can exercise every badge state. */
    val debugAuthenticity: AuthenticityState? = null,
    /** `k=org`, debug link only: show the identity as an organisation (device validation of the company case). */
    val debugOrganization: Boolean = false,
) {
    val hasIdentity: Boolean get() = name != null
}

data class TapTarget(
    val channel: TapChannel,
    /** Null when the URI had no usable identifier; the Reveal then shows an unresolved card. */
    val cardId: String?,
    val hints: TapHints,
    val rawUri: String,
)

/**
 * What a tap delivered, in order of precedence (docs/nfc-self-contained-profile.md):
 * an embedded Kipat test profile beats a Kipat card URI on the same tag (in TEST mode the URI is
 * only routing); anything else is unknown.
 */
sealed interface ParsedNfcIntent {
    /** A `application/vnd.kipat.profile+json` record: valid, invalid or an unsupported version. */
    data class Profile(val result: NfcProfileParse, val recordCount: Int) : ParsedNfcIntent
    /**
     * Another Kipat phone's Type 4 bootstrap (`application/vnd.kipat.peer`): start Kipat Tap on the
     * same contact. Carries no profile; [result] is only checked for a supported protocol.
     */
    data class PeerBootstrap(val result: KipatPeerBootstrap.Result) : ParsedNfcIntent
    data class Card(val target: TapTarget) : ParsedNfcIntent
    data class Unknown(val recordCount: Int) : ParsedNfcIntent
}

/**
 * Parses Kipat card URIs. Pure JVM code (java.net.URI) so it is unit tested without a device.
 *
 * Accepted forms:
 *  - `https://kipat.app/c/{cardId}?n=&s=&o=&t=&th=`  (current card format)
 *  - `https://kipat.app/tap/{cardId}` and `/p/{cardId}` (aliases)
 *  - `https://probe.kipat.com[/…]` (tags written by the original probe)
 *  - `kipatprobe://tap/{cardId}?…&a=&k=org` (debug deep link)
 */
object NfcIntentParser {
    /**
     * Classifies a tap from its NDEF records and/or launch URI. Records are untrusted: only the
     * Kipat profile MIME type and Kipat URIs are interpreted; every other record is ignored.
     */
    fun parseTap(records: List<NdefRecordData>, launchUri: String?): ParsedNfcIntent {
        records.firstOrNull { it.isKipatProfile }?.let { return ParsedNfcIntent.Profile(NfcProfileFormat.parse(it.payload), records.size) }
        records.firstOrNull { KipatPeerBootstrap.isBootstrap(it) }?.let { return ParsedNfcIntent.PeerBootstrap(KipatPeerBootstrap.parse(it.payload)) }
        val uri = launchUri ?: records.firstNotNullOfOrNull { it.uriOrNull() }
        return parse(uri)?.let { ParsedNfcIntent.Card(it) } ?: ParsedNfcIntent.Unknown(records.size)
    }

    const val CARD_HOST = "kipat.app"
    const val LEGACY_PROBE_HOST = "probe.kipat.com"
    const val DEBUG_SCHEME = "kipatprobe"
    const val DEBUG_HOST = "tap"
    const val LEGACY_PROBE_CARD_ID = "legacy-probe"
    private val CARD_PATH_PREFIXES = setOf("c", "tap", "p")

    fun parse(raw: String?): TapTarget? {
        val text = raw?.trim().orEmpty()
        if (text.isEmpty() || text.length > 512) return null
        val uri = try {
            URI(text)
        } catch (e: Exception) {
            return null
        }
        val scheme = uri.scheme?.lowercase() ?: return null
        val host = uri.host?.lowercase()
        val segments = uri.rawPath.orEmpty().split('/').filter { it.isNotEmpty() }.map(::decode)
        val query = parseQuery(uri.rawQuery)

        return when {
            scheme == DEBUG_SCHEME && host == DEBUG_HOST -> TapTarget(
                channel = TapChannel.DEBUG_DEEP_LINK,
                cardId = sanitizeId(segments.firstOrNull()),
                hints = hintsFrom(query, allowAuthenticity = true),
                rawUri = text,
            )
            (scheme == "https" || scheme == "http") && host == LEGACY_PROBE_HOST -> TapTarget(
                channel = TapChannel.LEGACY_PROBE_TAG,
                cardId = LEGACY_PROBE_CARD_ID,
                hints = TapHints(),
                rawUri = text,
            )
            scheme == "https" && (host == CARD_HOST || host == "www.$CARD_HOST") -> {
                val id = if (segments.size >= 2 && segments[0].lowercase() in CARD_PATH_PREFIXES) segments[1] else null
                TapTarget(TapChannel.NFC_TAG, sanitizeId(id), hintsFrom(query, allowAuthenticity = false), text)
            }
            else -> null
        }
    }

    private fun hintsFrom(q: Map<String, String>, allowAuthenticity: Boolean) = TapHints(
        name = sanitizeLine(q["n"], 48),
        subtitle = sanitizeLine(q["s"], 72),
        organization = sanitizeLine(q["o"], 72),
        template = q["t"]?.takeIf { RevealTemplateId.isKnown(it) }?.let { RevealTemplateId.fromWireId(it) },
        theme = q["th"]?.let { RevealTheme.fromWireId(it) },
        debugAuthenticity = if (allowAuthenticity) q["a"]?.let { AuthenticityState.fromWireId(it) } else null,
        debugOrganization = allowAuthenticity && q["k"] == "org",
    )

    private fun parseQuery(raw: String?): Map<String, String> {
        if (raw.isNullOrEmpty()) return emptyMap()
        return raw.split('&').mapNotNull { part ->
            val idx = part.indexOf('=')
            if (idx <= 0) null else decode(part.substring(0, idx)) to decode(part.substring(idx + 1))
        }.toMap()
    }

    private fun decode(s: String): String = try {
        URLDecoder.decode(s, "UTF-8")
    } catch (e: Exception) {
        s
    }
}

/** Builds the URI written to prototype Kipat cards. */
object KipatTagUri {
    /** NTAG213 offers 144 bytes of NDEF; keep hints short so the AAR record still fits. */
    const val MAX_NAME_HINT = 24

    fun build(cardId: String, nameHint: String? = null, template: RevealTemplateId? = null): String {
        val id = requireNotNull(sanitizeId(cardId)) { "Invalid card id" }
        val params = buildList {
            sanitizeLine(nameHint, MAX_NAME_HINT)?.let { add("n=" + URLEncoder.encode(it, "UTF-8")) }
            template?.let { add("t=" + it.wireId) }
        }
        val base = "https://${NfcIntentParser.CARD_HOST}/c/$id"
        return if (params.isEmpty()) base else base + "?" + params.joinToString("&")
    }
}
