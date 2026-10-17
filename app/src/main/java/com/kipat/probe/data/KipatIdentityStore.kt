package com.kipat.probe.data

import android.content.Context
import android.util.Log
import com.kipat.probe.model.Identity
import com.kipat.probe.model.IdentityType
import com.kipat.probe.model.ProfileField
import com.kipat.probe.model.ProfileTemplateId
import com.kipat.probe.nfc.profile.NfcProfileFormat
import com.kipat.probe.reveal.RevealTemplateId
import com.kipat.probe.reveal.RevealTheme
import org.json.JSONObject
import java.io.File
import java.security.SecureRandom

/**
 * Persistence for the canonical Kipat identity (KipatRepository.identity): profile fields,
 * profile template, Reveal template, accent and revision in ONE file, replaced atomically
 * (temp + rename). Every share path reads from here or from the in-memory identity it mirrors:
 * phone-to-phone HCE (possibly in a cold process), linked-card resolution and the preview.
 *
 * Stored values use stable ids (`contact_ripple`, `cobalt`), never display labels. An unknown
 * Reveal (removed or renamed) falls back to the product default with
 * `unknown_reveal_template_fallback` in the log.
 */
object KipatIdentityStore {
    const val TAG = "KipatIdentity"
    private const val FILE = "kipat_identity.json"
    private const val FORMAT = 1

    @Volatile private var initialized = false
    @Volatile private var persisted = false
    @Volatile private var cache: PublicKipatProfileSnapshot? = null

    /**
     * Loads the persisted identity into KipatRepository (once per process) and makes every later
     * change persist before it is visible. [persistCurrent] = true (MainActivity) also writes the
     * current identity when nothing was saved yet, so a profile exists for Kipat Tap.
     */
    @Synchronized
    fun init(context: Context, persistCurrent: Boolean = false) {
        val dir = context.applicationContext.filesDir
        if (!initialized) {
            read(File(dir, FILE))?.let { (saved, savedRevision) ->
                var identity = saved
                var revision = savedRevision
                if (needsOwnProfileId(saved)) {
                    // Installs before pilot.2 all shared the sample id: every phone looked like "you".
                    identity = saved.copy(cardId = newProfileId())
                    revision = savedRevision + 1
                    write(File(dir, FILE), identity, revision)
                    runCatching { Log.i(TAG, "profile_id_assigned reason=shared_placeholder") }
                }
                KipatRepository.restoreIdentity(identity, revision)
                persisted = true
            }
            KipatRepository.identityPersistence = { identity, revision ->
                write(File(dir, FILE), identity, revision)
                persisted = true
            }
            initialized = true
        }
        if (persistCurrent && !persisted) {
            val (current, revision) = KipatRepository.published()
            // First run: this install's own public profile id, never the shared sample id.
            val identity = if (needsOwnProfileId(current)) current.copy(cardId = newProfileId()) else current
            write(File(dir, FILE), identity, revision)
            if (identity !== current) KipatRepository.restoreIdentity(identity, revision)
            runCatching { Log.i(TAG, "profile_id_assigned reason=first_run") }
            persisted = true
        }
    }

    /**
     * The public profile id is what Kipat Tap, the share link and Connections use to tell two people
     * apart, so it must be unique per person. The sample identity's id is shared by every install.
     */
    internal fun needsOwnProfileId(identity: Identity): Boolean =
        identity.cardId.isBlank() || identity.cardId == SampleData.DEMO_CARD_ID

    private const val ID_ALPHABET = "0123456789ABCDEFGHJKMNPQRSTVWXYZ" // Crockford base32: no I, L, O, U

    /** "KPT-" + 12 random base32 characters (60 bits). Valid for the NFC profile id rule. */
    internal fun newProfileId(random: SecureRandom = SecureRandom()): String =
        "KPT-" + String(CharArray(12) { ID_ALPHABET[random.nextInt(ID_ALPHABET.length)] })

    /** Whether a profile was ever published on this phone (else Kipat Tap has nothing to share). */
    fun hasPublishedProfile(context: Context): Boolean {
        init(context)
        return persisted
    }

    /**
     * The current public snapshot, rebuilt only when the revision changes. Reads identity and
     * revision together, so a Reveal change is served on the very next tap with no restart.
     */
    fun publicSnapshot(context: Context): PublicKipatProfileSnapshot? {
        if (!hasPublishedProfile(context)) return null
        val (identity, revision) = KipatRepository.published()
        cache?.let { if (it.revision == revision && it.identityId == identity.id) return it }
        return PublicKipatProfileSnapshot.of(identity, revision).also { cache = it }
    }

    // ------------------------------------------------------------------------- serialization

    internal fun encode(identity: Identity, revision: Long): String = JSONObject()
        .put("format", FORMAT)
        .put("revision", revision)
        .put("id", identity.id)
        .put("cardId", identity.cardId)
        .put("type", identity.type.name)
        .put("profileTemplate", identity.profileTemplate.wireId)
        .put("reveal", identity.revealTemplate.stableId)
        .put("accent", NfcProfileFormat.accentWire(identity.revealTheme))
        .put("avatarUri", identity.avatarUri ?: JSONObject.NULL)
        .put("fields", JSONObject().apply { identity.fields.forEach { (k, v) -> put(k.name, v) } })
        .toString()

    internal fun decode(text: String): Pair<Identity, Long>? = runCatching {
        val o = JSONObject(text)
        val revealRaw = o.optString("reveal")
        val reveal = RevealTemplateId.fromStableId(revealRaw) ?: RevealTemplateId.DEFAULT.also {
            runCatching { Log.w(TAG, "unknown_reveal_template_fallback reveal=${revealRaw.take(32)} default=${it.stableId}") }
        }
        val fields = o.optJSONObject("fields")?.let { f ->
            f.keys().asSequence().mapNotNull { k -> ProfileField.entries.firstOrNull { it.name == k }?.let { it to f.optString(k) } }.toMap()
        }.orEmpty()
        Identity(
            id = o.getString("id"),
            cardId = o.getString("cardId"),
            type = IdentityType.entries.firstOrNull { it.name == o.optString("type") } ?: IdentityType.STUDENT,
            profileTemplate = ProfileTemplateId.fromWireId(o.optString("profileTemplate")),
            revealTemplate = reveal,
            revealTheme = NfcProfileFormat.accentOf(o.optString("accent")) ?: RevealTheme.NIGHT,
            fields = fields,
            avatarUri = o.optString("avatarUri").takeIf { it.isNotBlank() && it != "null" },
        ) to o.optLong("revision", 0)
    }.getOrNull()

    private fun read(file: File): Pair<Identity, Long>? = if (file.exists()) decode(file.readText()) else null

    private fun write(file: File, identity: Identity, revision: Long) {
        val tmp = File(file.parentFile, "${file.name}.tmp")
        tmp.writeText(encode(identity, revision))
        if (!tmp.renameTo(file)) tmp.delete()
        cache = null
    }

    /** For tests: forget everything held in memory (as in a fresh process). */
    internal fun resetForTest() {
        initialized = false
        persisted = false
        cache = null
        KipatRepository.identityPersistence = null
    }
}
