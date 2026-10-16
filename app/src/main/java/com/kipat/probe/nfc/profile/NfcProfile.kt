package com.kipat.probe.nfc.profile

import com.kipat.probe.reveal.RevealTemplateId
import com.kipat.probe.reveal.RevealTheme
import org.json.JSONException
import org.json.JSONObject
import org.json.JSONTokener
import java.net.URI
import java.nio.ByteBuffer
import java.nio.charset.CharacterCodingException
import java.nio.charset.CodingErrorAction

/**
 * Self-contained Kipat test profile carried on an NFC tag (docs/nfc-self-contained-profile.md).
 *
 * DEVELOPMENT / TEST ONLY. Anything written to an ordinary NFC tag can be read and copied by any
 * phone, so this format holds public, non-sensitive profile fields only, and a profile read from
 * a tag is never shown as verified. Production cards will carry an opaque identifier resolved by
 * a backend, with secure card authenticity (e.g. NTAG 424 DNA).
 *
 * This is a transport payload, not a database entity.
 */
enum class NfcProfileType(val wire: String) {
    PERSON("person"),
    ORGANIZATION("organization");

    companion object {
        fun fromWire(value: String?): NfcProfileType? = entries.firstOrNull { it.wire.equals(value?.trim(), ignoreCase = true) }
    }
}

data class NfcProfilePayload(
    val version: Int = NfcProfileFormat.VERSION,
    val id: String? = null,
    val type: NfcProfileType = NfcProfileType.PERSON,
    val displayName: String,
    val headline: String? = null,
    val organization: String? = null,
    val role: String? = null,
    val bio: String? = null,
    val avatarUrl: String? = null,
    val website: String? = null,
    val github: String? = null,
    val linkedin: String? = null,
    val publicEmail: String? = null,
    /** Kipat accent ([NfcProfileFormat.accentOf]); null = default. */
    val accent: RevealTheme? = null,
    /** Reveal template ([NfcProfileFormat.revealOf]); null = default. */
    val reveal: RevealTemplateId? = null,
) {
    /** Compact keeps what the Reveal needs; Full adds bio, links, email and avatar URL. */
    fun compact(): NfcProfilePayload = copy(bio = null, avatarUrl = null, website = null, github = null, linkedin = null, publicEmail = null)

    companion object {
        /** The developer sample. No URLs or email: nothing personal is invented. */
        val SAMPLE = NfcProfilePayload(
            id = "test-vash-puno",
            type = NfcProfileType.PERSON,
            displayName = "Vash Puno",
            headline = "BS Computer Science",
            organization = "Polytechnic University of the Philippines",
            role = "Developer",
            bio = "Computer Science student and developer.",
            accent = RevealTheme.NIGHT,
            reveal = RevealTemplateId.PRECISION_ORBIT,
        )
    }
}

enum class NfcProfileMode { COMPACT, FULL }

/** Field limits (characters). NFC input is untrusted: nothing larger is ever kept. */
object NfcProfileLimits {
    const val ID = 64
    const val NAME = 120
    const val HEADLINE = 160
    const val ORGANIZATION = 200
    const val ROLE = 120
    const val BIO = 500
    const val URL = 500
    const val EMAIL = 254
    /** Larger payloads are rejected before parsing (largest common tags hold < 1 KB). */
    const val MAX_PAYLOAD_BYTES = 4096
}

/** A non-fatal problem: the field was ignored, the rest of the profile is used. */
data class NfcProfileWarning(val field: String, val message: String)

sealed interface NfcProfileParse {
    data class Valid(val profile: NfcProfilePayload, val warnings: List<NfcProfileWarning>, val payloadBytes: Int) : NfcProfileParse
    /** A Kipat profile record whose version this build doesn't understand. Never reinterpreted. */
    data class UnsupportedVersion(val version: Int, val payloadBytes: Int) : NfcProfileParse
    /** "Kipat profile detected but invalid": [reason] is for developer diagnostics. */
    data class Invalid(val reason: String, val payloadBytes: Int) : NfcProfileParse
}

object NfcProfileFormat {
    const val MIME_TYPE = "application/vnd.kipat.profile+json"
    const val VERSION = 1

    private val ACCENTS = mapOf(
        "cobalt" to RevealTheme.NIGHT,
        "teal" to RevealTheme.AURORA,
        "amber" to RevealTheme.EMBER,
        "graphite" to RevealTheme.MONO,
    )

    fun accentWire(theme: RevealTheme): String = ACCENTS.entries.first { it.value == theme }.key

    /** "cobalt" / "teal" / "amber" / "graphite"; the older wire IDs ("night"…) are accepted too. */
    fun accentOf(value: String?): RevealTheme? {
        val v = value?.trim()?.lowercase() ?: return null
        return ACCENTS[v] ?: RevealTheme.entries.firstOrNull { it.wireId == v }
    }

    fun revealWire(template: RevealTemplateId): String = template.stableId

    /** "precision_orbit" etc., or the short wire IDs ("orbit"). Unknown → null (the caller warns). */
    fun revealOf(value: String?): RevealTemplateId? =
        value?.takeIf { RevealTemplateId.isKnown(it) }?.let { RevealTemplateId.fromWireId(it) }

    // ------------------------------------------------------------------ serialization

    /**
     * Compact JSON (no whitespace), fixed key order, null fields omitted, non-ASCII written as
     * UTF-8 (not \u escapes) so sizes stay small and deterministic.
     */
    fun toJson(profile: NfcProfilePayload, mode: NfcProfileMode = NfcProfileMode.FULL): String {
        val p = if (mode == NfcProfileMode.COMPACT) profile.compact() else profile
        val parts = mutableListOf<String>()
        fun put(key: String, value: String?) { if (value != null) parts += "${quote(key)}:${quote(value)}" }
        parts += "\"v\":${p.version}"
        put("id", p.id)
        put("type", p.type.wire)
        put("name", p.displayName)
        put("headline", p.headline)
        put("organization", p.organization)
        put("role", p.role)
        put("bio", p.bio)
        put("avatarUrl", p.avatarUrl)
        val links = listOfNotNull(
            p.website?.let { "\"website\":${quote(it)}" },
            p.github?.let { "\"github\":${quote(it)}" },
            p.linkedin?.let { "\"linkedin\":${quote(it)}" },
        )
        if (links.isNotEmpty()) parts += "\"links\":{${links.joinToString(",")}}"
        put("publicEmail", p.publicEmail)
        put("accent", p.accent?.let(::accentWire))
        put("reveal", p.reveal?.let(::revealWire))
        return parts.joinToString(",", "{", "}")
    }

    fun toBytes(profile: NfcProfilePayload, mode: NfcProfileMode = NfcProfileMode.FULL): ByteArray =
        toJson(profile, mode).toByteArray(Charsets.UTF_8)

    private fun quote(s: String): String = buildString(s.length + 2) {
        append('"')
        for (c in s) {
            when {
                c == '"' -> append("\\\"")
                c == '\\' -> append("\\\\")
                c == '\n' -> append("\\n")
                c == '\r' -> append("\\r")
                c == '\t' -> append("\\t")
                c < ' ' -> append("\\u%04x".format(c.code))
                else -> append(c)
            }
        }
        append('"')
    }

    // ------------------------------------------------------------------ parsing

    /** Never throws: anything unexpected becomes [NfcProfileParse.Invalid]. */
    fun parse(payload: ByteArray): NfcProfileParse {
        val size = payload.size
        if (size > NfcProfileLimits.MAX_PAYLOAD_BYTES) return NfcProfileParse.Invalid("payload is $size bytes; the limit is ${NfcProfileLimits.MAX_PAYLOAD_BYTES}", size)
        val text = try {
            Charsets.UTF_8.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT)
                .decode(ByteBuffer.wrap(payload)).toString()
        } catch (e: CharacterCodingException) {
            return NfcProfileParse.Invalid("payload is not valid UTF-8", size)
        }
        val json = try {
            val value = JSONTokener(text).nextValue()
            value as? JSONObject ?: return NfcProfileParse.Invalid("payload is not a JSON object", size)
        } catch (e: JSONException) {
            // Android's message ends with " of <entire input>": keep the position, never echo the payload.
            return NfcProfileParse.Invalid("malformed JSON (${e.message?.substringBefore(" of ")?.take(80)})", size)
        } catch (e: RuntimeException) {
            return NfcProfileParse.Invalid("malformed JSON", size)
        }

        if (!json.has("v")) return NfcProfileParse.Invalid("missing version \"v\"", size)
        val version = (json.opt("v") as? Number)?.takeIf { it.toDouble() % 1.0 == 0.0 }?.toInt()
            ?: return NfcProfileParse.Invalid("version \"v\" is not an integer", size)
        if (version != VERSION) return NfcProfileParse.UnsupportedVersion(version, size)

        val warnings = mutableListOf<NfcProfileWarning>()
        fun str(key: String, obj: JSONObject = json): String? {
            if (!obj.has(key) || obj.isNull(key)) return null
            val v = obj.opt(key)
            if (v !is String) { warnings += NfcProfileWarning(key, "not text, ignored"); return null }
            return v.takeIf { it.isNotBlank() }
        }

        val name = str("name")?.let { singleLine(it) }
        if (name.isNullOrEmpty()) return NfcProfileParse.Invalid("missing name", size)
        if (name.length > NfcProfileLimits.NAME) return NfcProfileParse.Invalid("name longer than ${NfcProfileLimits.NAME} characters", size)

        val links = (json.opt("links") as? JSONObject)
        if (json.has("links") && !json.isNull("links") && links == null) warnings += NfcProfileWarning("links", "not an object, ignored")

        val typeRaw = str("type")
        val type = when {
            typeRaw == null -> NfcProfileType.PERSON
            else -> NfcProfileType.fromWire(typeRaw) ?: NfcProfileType.PERSON.also { warnings += NfcProfileWarning("type", "unknown type \"${typeRaw.take(40)}\", shown as a person") }
        }
        val accentRaw = str("accent")
        val accent = accentRaw?.let { accentOf(it) ?: null.also { warnings += NfcProfileWarning("accent", "unknown accent \"${accentRaw.take(40)}\", default used") } }
        val revealRaw = str("reveal")
        val reveal = revealRaw?.let { revealOf(it) ?: null.also { warnings += NfcProfileWarning("reveal", "unknown Reveal \"${revealRaw.take(40)}\", default used") } }

        val candidate = NfcProfilePayload(
            version = version,
            id = str("id")?.trim(),
            type = type,
            displayName = name,
            headline = str("headline")?.let(::singleLine),
            organization = str("organization")?.let(::singleLine),
            role = str("role")?.let(::singleLine),
            bio = str("bio")?.trim(),
            avatarUrl = str("avatarUrl")?.trim(),
            website = links?.let { str("website", it) }?.trim(),
            github = links?.let { str("github", it) }?.trim(),
            linkedin = links?.let { str("linkedin", it) }?.trim(),
            publicEmail = str("publicEmail")?.trim(),
            accent = accent,
            reveal = reveal,
        )
        val (clean, issues) = NfcProfileValidator.dropInvalidOptional(candidate)
        return NfcProfileParse.Valid(clean, warnings + issues, size)
    }

    /** Control characters become spaces and whitespace collapses: text stays text, on one line. */
    internal fun singleLine(s: String): String = buildString { for (c in s) append(if (c.isISOControl()) ' ' else c) }
        .trim().replace(Regex("\\s+"), " ")
}

/**
 * Field rules shared by the parser (drops bad optional fields with a warning) and the editor
 * (shows them as errors before anything is written).
 */
object NfcProfileValidator {
    private val ID = Regex("^[A-Za-z0-9_-]{1,${NfcProfileLimits.ID}}$")
    private val EMAIL = Regex("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$")

    /** Problems with each field; empty = writable. A bad name is the only fatal one. */
    fun issues(p: NfcProfilePayload): List<NfcProfileWarning> = buildList {
        if (p.displayName.isBlank()) add(NfcProfileWarning("name", "required"))
        if (p.displayName.length > NfcProfileLimits.NAME) add(NfcProfileWarning("name", "longer than ${NfcProfileLimits.NAME} characters"))
        p.id?.let { if (!ID.matches(it)) add(NfcProfileWarning("id", "use 1–${NfcProfileLimits.ID} letters, digits, - or _")) }
        length("headline", p.headline, NfcProfileLimits.HEADLINE)
        length("organization", p.organization, NfcProfileLimits.ORGANIZATION)
        length("role", p.role, NfcProfileLimits.ROLE)
        length("bio", p.bio, NfcProfileLimits.BIO)
        url("avatarUrl", p.avatarUrl)
        url("website", p.website)
        url("github", p.github)
        url("linkedin", p.linkedin)
        p.publicEmail?.let {
            if (it.length > NfcProfileLimits.EMAIL) add(NfcProfileWarning("publicEmail", "longer than ${NfcProfileLimits.EMAIL} characters"))
            else if (!EMAIL.matches(it)) add(NfcProfileWarning("publicEmail", "not an email address"))
        }
    }

    private fun MutableList<NfcProfileWarning>.length(field: String, value: String?, max: Int) {
        if (value != null && value.length > max) add(NfcProfileWarning(field, "longer than $max characters"))
    }

    /** Only plain http(s) links with a host. intent:, javascript:, file: and the like are refused. */
    private fun MutableList<NfcProfileWarning>.url(field: String, value: String?) {
        value ?: return
        if (value.length > NfcProfileLimits.URL) { add(NfcProfileWarning(field, "longer than ${NfcProfileLimits.URL} characters")); return }
        if (!isWebUrl(value)) add(NfcProfileWarning(field, "not an http(s) link"))
    }

    fun isWebUrl(value: String): Boolean = try {
        val u = URI(value)
        (u.scheme.equals("https", true) || u.scheme.equals("http", true)) && !u.host.isNullOrBlank()
    } catch (e: Exception) {
        false
    }

    /** Parser path: optional fields that break a rule are removed, never trusted. */
    fun dropInvalidOptional(p: NfcProfilePayload): Pair<NfcProfilePayload, List<NfcProfileWarning>> {
        val bad = issues(p).filter { it.field != "name" }
        if (bad.isEmpty()) return p to emptyList()
        val f = bad.map { it.field }.toSet()
        val clean = p.copy(
            id = p.id.takeUnless { "id" in f },
            headline = p.headline.takeUnless { "headline" in f },
            organization = p.organization.takeUnless { "organization" in f },
            role = p.role.takeUnless { "role" in f },
            bio = p.bio.takeUnless { "bio" in f },
            avatarUrl = p.avatarUrl.takeUnless { "avatarUrl" in f },
            website = p.website.takeUnless { "website" in f },
            github = p.github.takeUnless { "github" in f },
            linkedin = p.linkedin.takeUnless { "linkedin" in f },
            publicEmail = p.publicEmail.takeUnless { "publicEmail" in f },
        )
        return clean to bad.map { it.copy(message = "${it.message}, ignored") }
    }
}
