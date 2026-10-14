package com.kipat.probe.nfc.profile

/**
 * What a tag looked like when it was read. Platform-free so the write decisions, read-back
 * verification and diagnostic report are unit tested without NFC hardware.
 *
 * @param maxSize Ndef.getMaxSize, the card's own NDEF capacity. Null when the tag only offers
 *   NdefFormatable (capacity unknown until it's formatted).
 * @param records the NDEF message's records; empty for a blank card; null when it couldn't be read.
 */
data class TagSnapshot(
    val uidHex: String?,
    val techs: List<String>,
    val ndefType: String?,
    val maxSize: Int?,
    val writable: Boolean?,
    val formatable: Boolean,
    val records: List<NdefRecordData>?,
    val readError: String? = null,
) {
    val supportsNdef: Boolean get() = ndefType != null
    val messageSize: Int? get() = records?.let { Ndef.messageSize(it) }
    val profileRecord: NdefRecordData? get() = records?.firstOrNull { it.isKipatProfile }

    /** True when writing would replace something (a lone empty record is a blank card; unreadable data counts). */
    val hasData: Boolean get() = records?.any { it.tnf != Ndef.TNF_EMPTY } ?: true
}

sealed interface WriteDecision {
    data object Proceed : WriteDecision
    data class ConfirmReplace(val existing: List<String>) : WriteDecision
    data class TooLarge(val required: Int, val available: Int, val fieldCosts: List<Pair<String, Int>>) : WriteDecision
    data object ReadOnly : WriteDecision
    data object NotNdef : WriteDecision
}

/** The checks of the write flow that happen before any byte is written, in order. */
object ProfileWritePlan {
    fun decide(
        tag: TagSnapshot,
        required: Int,
        confirmedReplace: Boolean,
        fieldCosts: () -> List<Pair<String, Int>>,
    ): WriteDecision {
        if (!tag.supportsNdef) return if (tag.formatable) WriteDecision.Proceed else WriteDecision.NotNdef
        if (tag.writable == false) return WriteDecision.ReadOnly
        val available = tag.maxSize ?: 0
        // Never truncate: a profile that doesn't fit is not written at all.
        if (required > available) return WriteDecision.TooLarge(required, available, fieldCosts())
        if (tag.hasData && !confirmedReplace) return WriteDecision.ConfirmReplace(tag.records?.map { it.summary() } ?: listOf("Unreadable NDEF data"))
        return WriteDecision.Proceed
    }
}

sealed interface VerifyResult {
    data class Verified(val profile: NfcProfilePayload, val messageBytes: Int) : VerifyResult
    data class Mismatch(val reason: String) : VerifyResult
}

/** "PROFILE WRITE VERIFIED" only when the card, read back after the write, holds exactly what was written. */
object ProfileReadBack {
    fun verify(written: List<NdefRecordData>, expected: NfcProfilePayload, mode: NfcProfileMode, readBack: List<NdefRecordData>?): VerifyResult {
        if (readBack == null) return VerifyResult.Mismatch("the card could not be read back")
        if (readBack.size != written.size) return VerifyResult.Mismatch("expected ${written.size} records, read ${readBack.size}")
        written.zip(readBack).forEachIndexed { i, (w, r) ->
            if (w.tnf != r.tnf || !w.type.contentEquals(r.type) || !w.payload.contentEquals(r.payload)) {
                return VerifyResult.Mismatch("record ${i + 1} differs from what was written")
            }
        }
        val record = readBack.firstOrNull { it.isKipatProfile } ?: return VerifyResult.Mismatch("no Kipat profile record on the card")
        val parsed = NfcProfileFormat.parse(record.payload) as? NfcProfileParse.Valid
            ?: return VerifyResult.Mismatch("the profile read back does not parse")
        val wanted = (NfcProfileFormat.parse(NfcProfileFormat.toBytes(expected, mode)) as? NfcProfileParse.Valid)?.profile
        if (parsed.profile != wanted) return VerifyResult.Mismatch("the profile read back differs from the one written")
        return VerifyResult.Verified(parsed.profile, Ndef.messageSize(readBack))
    }
}

/** Plain-text report for "Copy diagnostic report". Holds tag facts and the parse result, never secrets. */
object NfcProfileDiagnostics {
    fun report(tag: TagSnapshot, appVersion: String, sdkInt: Int, device: String): String = buildString {
        appendLine("Kipat NFC full profile test — diagnostic report")
        appendLine("App: $appVersion · Android API $sdkInt · $device")
        appendLine("Tag UID: ${tag.uidHex ?: "unknown"}")
        appendLine("Technologies: ${tag.techs.joinToString().ifEmpty { "none" }}")
        appendLine("NDEF type: ${tag.ndefType ?: "not NDEF"}${if (tag.formatable) " (NDEF-formatable)" else ""}")
        appendLine("Capacity (maxSize): ${tag.maxSize?.let { "$it bytes" } ?: "unknown"}")
        appendLine("Writable: ${tag.writable?.toString() ?: "unknown"}")
        tag.readError?.let { appendLine("Read error: $it") }
        val records = tag.records
        if (records == null) {
            appendLine("NDEF message: unreadable")
        } else {
            appendLine("NDEF message: ${records.size} record(s), ${tag.messageSize} bytes")
            records.forEachIndexed { i, r -> appendLine("  ${i + 1}. TNF ${r.tnf} · ${r.typeString.take(60)} · ${r.payload.size} B — ${r.summary()}") }
        }
        val profile = tag.profileRecord
        if (profile == null) {
            appendLine("Kipat profile: none")
        } else {
            when (val p = NfcProfileFormat.parse(profile.payload)) {
                is NfcProfileParse.Valid -> {
                    appendLine("Kipat profile: valid, v${p.profile.version}, ${p.payloadBytes} bytes")
                    appendLine("  name: ${p.profile.displayName}")
                    appendLine("  fields: ${presentFields(p.profile).joinToString()}")
                    p.warnings.forEach { appendLine("  warning: ${it.field}: ${it.message}") }
                }
                is NfcProfileParse.UnsupportedVersion -> appendLine("Kipat profile: unsupported version ${p.version} (${p.payloadBytes} bytes)")
                is NfcProfileParse.Invalid -> appendLine("Kipat profile: invalid — ${p.reason} (${p.payloadBytes} bytes)")
            }
        }
        append("Authenticity: unverified test data (not a Kipat-issued card)")
    }

    fun presentFields(p: NfcProfilePayload): List<String> = buildList {
        add("name")
        if (p.id != null) add("id")
        add("type")
        if (p.headline != null) add("headline")
        if (p.organization != null) add("organization")
        if (p.role != null) add("role")
        if (p.bio != null) add("bio")
        if (p.avatarUrl != null) add("avatarUrl")
        if (p.website != null) add("website")
        if (p.github != null) add("github")
        if (p.linkedin != null) add("linkedin")
        if (p.publicEmail != null) add("publicEmail")
        if (p.accent != null) add("accent")
        if (p.reveal != null) add("reveal")
    }
}
