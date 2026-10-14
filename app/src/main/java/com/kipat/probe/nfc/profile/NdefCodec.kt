package com.kipat.probe.nfc.profile

/**
 * Platform-free NDEF records, so message layout and exact byte sizes are unit tested on the JVM.
 * Converted to/from android.nfc.NdefRecord only at the tag boundary (NfcProfileTagIo).
 */
class NdefRecordData(val tnf: Short, val type: ByteArray, val id: ByteArray = ByteArray(0), val payload: ByteArray) {
    val typeString: String get() = String(type, Charsets.US_ASCII)

    /** Encoded size as one record of a message (NFC Forum NDEF 1.0; short record when payload < 256). */
    val encodedSize: Int
        get() = 1 + 1 + (if (payload.size < 256) 1 else 4) + (if (id.isNotEmpty()) 1 + id.size else 0) + type.size + payload.size

    val isKipatProfile: Boolean
        get() = tnf == Ndef.TNF_MIME_MEDIA && typeString.equals(NfcProfileFormat.MIME_TYPE, ignoreCase = true)

    /** A short human summary for the "replace existing data?" prompt and diagnostics. */
    fun summary(): String = when (tnf) {
        Ndef.TNF_EMPTY -> "Empty record"
        Ndef.TNF_WELL_KNOWN -> when (typeString) {
            "U" -> "Link: ${uriOrNull() ?: "unreadable"}"
            "T" -> "Text record (${payload.size} bytes)"
            else -> "Well-known \"$typeString\" record (${payload.size} bytes)"
        }
        Ndef.TNF_MIME_MEDIA -> if (isKipatProfile) "Kipat test profile (${payload.size} bytes)" else "$typeString (${payload.size} bytes)"
        Ndef.TNF_ABSOLUTE_URI -> "Link: ${typeString.take(80)}"
        Ndef.TNF_EXTERNAL_TYPE -> if (typeString == Ndef.AAR_TYPE) "Opens app ${String(payload, Charsets.UTF_8).take(80)}" else "External \"${typeString.take(40)}\" record"
        else -> "Unknown record (${payload.size} bytes)"
    }

    /** Decodes a well-known URI record (prefix byte + rest). Null for anything else. */
    fun uriOrNull(): String? {
        if (tnf == Ndef.TNF_ABSOLUTE_URI) return typeString
        if (tnf != Ndef.TNF_WELL_KNOWN || typeString != "U" || payload.isEmpty()) return null
        val prefix = Ndef.URI_PREFIXES.getOrNull(payload[0].toInt() and 0xFF) ?: return null
        return prefix + String(payload, 1, payload.size - 1, Charsets.UTF_8)
    }
}

object Ndef {
    const val TNF_EMPTY: Short = 0
    const val TNF_WELL_KNOWN: Short = 1
    const val TNF_MIME_MEDIA: Short = 2
    const val TNF_ABSOLUTE_URI: Short = 3
    const val TNF_EXTERNAL_TYPE: Short = 4
    const val AAR_TYPE = "android.com:pkg"

    /** NFC Forum URI RTD abbreviation table (index = prefix byte). */
    val URI_PREFIXES = listOf(
        "", "http://www.", "https://www.", "http://", "https://", "tel:", "mailto:",
        "ftp://anonymous:anonymous@", "ftp://ftp.", "ftps://", "sftp://", "smb://", "nfs://", "ftp://",
        "dav://", "news:", "telnet://", "imap:", "rtsp://", "urn:", "pop:", "sip:", "sips:", "tftp:",
        "btspp://", "btl2cap://", "btgoep://", "tcpobex://", "irdaobex://", "file://", "urn:epc:id:",
        "urn:epc:tag:", "urn:epc:pat:", "urn:epc:raw:", "urn:epc:", "urn:nfc:",
    )

    /** MIME media record: the type is stored in lower case (as android.nfc.NdefRecord.createMime does). */
    fun mime(mimeType: String, payload: ByteArray) =
        NdefRecordData(TNF_MIME_MEDIA, mimeType.lowercase().toByteArray(Charsets.US_ASCII), payload = payload)

    /** Android Application Record: makes Android open [packageName] (or its Play listing). */
    fun aar(packageName: String) =
        NdefRecordData(TNF_EXTERNAL_TYPE, AAR_TYPE.toByteArray(Charsets.US_ASCII), payload = packageName.toByteArray(Charsets.UTF_8))

    /** Well-known URI record with the shortest matching prefix abbreviation. */
    fun uri(uri: String): NdefRecordData {
        val (code, rest) = URI_PREFIXES.withIndex().drop(1)
            .filter { uri.startsWith(it.value) }
            .maxByOrNull { it.value.length }
            ?.let { it.index to uri.removePrefix(it.value) } ?: (0 to uri)
        return NdefRecordData(TNF_WELL_KNOWN, "U".toByteArray(Charsets.US_ASCII), payload = byteArrayOf(code.toByte()) + rest.toByteArray(Charsets.UTF_8))
    }

    /** Exact size of the NDEF message these records form (what Ndef.getMaxSize is compared with). */
    fun messageSize(records: List<NdefRecordData>): Int = records.sumOf { it.encodedSize }
}

/** What goes on a test profile card, and how big it is. */
object KipatProfileMessage {
    /**
     * Record order matters: Android dispatches NDEF_DISCOVERED on the FIRST record, so the profile
     * MIME record comes first (matched by TapExperienceActivity's mimeType filter). The AAR comes
     * last, as Android documents, and guarantees Kipat (or its store page) opens even on a phone
     * where the MIME filter isn't matched. No URI record by default: it would only cost bytes.
     */
    fun records(profile: NfcProfilePayload, mode: NfcProfileMode, packageName: String, includeAar: Boolean = true): List<NdefRecordData> =
        buildList {
            add(Ndef.mime(NfcProfileFormat.MIME_TYPE, NfcProfileFormat.toBytes(profile, mode)))
            if (includeAar) add(Ndef.aar(packageName))
        }

    fun size(profile: NfcProfilePayload, mode: NfcProfileMode, packageName: String, includeAar: Boolean = true): Int =
        Ndef.messageSize(records(profile, mode, packageName, includeAar))

    /** Bytes each optional field adds to the JSON, largest first: what to remove when a card is too small. */
    fun fieldCosts(profile: NfcProfilePayload, mode: NfcProfileMode): List<Pair<String, Int>> {
        val full = NfcProfileFormat.toBytes(profile, mode).size
        fun without(p: NfcProfilePayload) = full - NfcProfileFormat.toBytes(p, mode).size
        return listOf(
            "Bio" to without(profile.copy(bio = null)),
            "Links" to without(profile.copy(website = null, github = null, linkedin = null)),
            "Public email" to without(profile.copy(publicEmail = null)),
            "Avatar URL" to without(profile.copy(avatarUrl = null)),
            "Organization" to without(profile.copy(organization = null)),
            "Headline" to without(profile.copy(headline = null)),
            "Role" to without(profile.copy(role = null)),
            "ID" to without(profile.copy(id = null)),
        ).filter { it.second > 0 }.sortedByDescending { it.second }
    }
}
