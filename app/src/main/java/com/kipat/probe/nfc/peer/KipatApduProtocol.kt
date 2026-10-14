package com.kipat.probe.nfc.peer

import java.security.MessageDigest

/**
 * Kipat phone-to-phone NFC protocol, version 1 (docs/nfc-phone-to-phone.md).
 *
 * Transport: ISO-DEP (ISO/IEC 14443-4) with ISO/IEC 7816-4 short APDUs. The presenter phone runs a
 * HostApduService (card role); the receiver phone uses reader mode + IsoDep (reader role).
 * This is the single source of truth for the AID, instructions, limits and status words.
 *
 * Not authentication: the AID is public and any app can emulate it. The SHA-256 only checks that
 * a transfer arrived intact.
 */
object KipatApduProtocol {
    /**
     * Proprietary, unregistered AID (first nibble F = ISO/IEC 7816-5 category "proprietary").
     * F0 · "KIPAT" in ASCII (4B 49 50 41 54) · 01 = protocol v1. 7 bytes: within Android's 5–16
     * byte AID range, even-length hex. Registered in res/xml/kipat_apdu_service.xml (keep in sync).
     */
    const val AID_HEX = "F04B4950415401"
    val AID: ByteArray = hex(AID_HEX)

    const val PROTOCOL_VERSION: Byte = 1
    /** Profile schema carried inside the transfer: the NFC profile JSON format (NfcProfileFormat.VERSION). */
    const val PROFILE_SCHEMA: Byte = 1

    /** Serialized public profile limit, both directions. Larger profiles are never shared or accepted. */
    const val MAX_PROFILE_BYTES = 4096
    /** Largest chunk either side moves in one APDU. Conservative for cross-OEM ISO-DEP frames. */
    const val MAX_CHUNK = 200
    const val SESSION_ID_BYTES = 16
    const val HASH_BYTES = 32

    // Classes and instructions
    const val CLA_ISO: Byte = 0x00
    const val CLA_KIPAT: Byte = 0x80.toByte()
    const val INS_SELECT: Byte = 0xA4.toByte()
    const val INS_HELLO: Byte = 0x10
    const val INS_GET_META: Byte = 0x20
    const val INS_READ_CHUNK: Byte = 0x22
    const val INS_BEGIN_UPLOAD: Byte = 0x30
    const val INS_WRITE_CHUNK: Byte = 0x32
    const val INS_COMMIT_UPLOAD: Byte = 0x34
    const val INS_COMPLETE: Byte = 0x3F

    /** Capability flags returned by HELLO. */
    const val CAP_PROFILE_DOWNLOAD = 0x01
    const val CAP_PEER_PROFILE_UPLOAD = 0x02

    /** HELLO request: version + 16-byte reader session id. */
    const val HELLO_REQUEST_BYTES = 1 + SESSION_ID_BYTES
    /** HELLO response: version + 16-byte presenter session id + capability flags. */
    const val HELLO_RESPONSE_BYTES = 1 + SESSION_ID_BYTES + 1
    /** Metadata / BEGIN_UPLOAD body: schema + length (2, big-endian) + SHA-256 (+ type on metadata). */
    const val META_RESPONSE_BYTES = 1 + 2 + HASH_BYTES + 1
    const val BEGIN_UPLOAD_BYTES = 1 + 2 + HASH_BYTES

    // ---------------------------------------------------------------- command builders (reader)

    /** ISO 7816-4 SELECT by DF name: 00 A4 04 00 Lc AID Le(00). */
    fun select(aid: ByteArray = AID): ByteArray = byteArrayOf(CLA_ISO, INS_SELECT, 0x04, 0x00, aid.size.toByte()) + aid + byteArrayOf(0x00)

    fun hello(readerSession: ByteArray, version: Byte = PROTOCOL_VERSION): ByteArray =
        case4(INS_HELLO, 0, 0, byteArrayOf(version) + readerSession)

    fun getMeta(): ByteArray = byteArrayOf(CLA_KIPAT, INS_GET_META, 0, 0, 0x00)

    /** Case 2: offset in P1-P2 (big-endian), Le = bytes wanted (1..[MAX_CHUNK]). */
    fun readChunk(offset: Int, length: Int): ByteArray =
        byteArrayOf(CLA_KIPAT, INS_READ_CHUNK, (offset shr 8).toByte(), offset.toByte(), length.toByte())

    fun beginUpload(length: Int, sha256: ByteArray, schema: Byte = PROFILE_SCHEMA): ByteArray =
        case3(INS_BEGIN_UPLOAD, 0, 0, byteArrayOf(schema, (length shr 8).toByte(), length.toByte()) + sha256)

    fun writeChunk(offset: Int, data: ByteArray): ByteArray =
        case3(INS_WRITE_CHUNK, offset shr 8, offset, data)

    fun commitUpload(): ByteArray = byteArrayOf(CLA_KIPAT, INS_COMMIT_UPLOAD, 0, 0)

    fun complete(): ByteArray = byteArrayOf(CLA_KIPAT, INS_COMPLETE, 0, 0)

    private fun case3(ins: Byte, p1: Int, p2: Int, data: ByteArray) =
        byteArrayOf(CLA_KIPAT, ins, p1.toByte(), p2.toByte(), data.size.toByte()) + data

    private fun case4(ins: Byte, p1: Int, p2: Int, data: ByteArray) = case3(ins, p1, p2, data) + byteArrayOf(0x00)

    // ---------------------------------------------------------------- responses

    fun ok(data: ByteArray = ByteArray(0)): ByteArray = data + Sw.OK.bytes

    fun parseResponse(raw: ByteArray?): Response? {
        if (raw == null || raw.size < 2) return null
        val sw = ((raw[raw.size - 2].toInt() and 0xFF) shl 8) or (raw[raw.size - 1].toInt() and 0xFF)
        return Response(raw.copyOfRange(0, raw.size - 2), sw)
    }

    class Response(val data: ByteArray, val sw: Int) {
        val isOk: Boolean get() = sw == Sw.OK.code
    }

    // ---------------------------------------------------------------- helpers

    fun sha256(bytes: ByteArray): ByteArray = MessageDigest.getInstance("SHA-256").digest(bytes)

    fun hex(s: String): ByteArray = ByteArray(s.length / 2) { s.substring(it * 2, it * 2 + 2).toInt(16).toByte() }

    fun ByteArray.toHex(): String = joinToString("") { "%02X".format(it) }

    /** First 8 hex characters: enough to correlate logs, never the whole id. */
    fun ByteArray.shortId(): String = toHex().take(8)

    /** Largest chunk that fits this reader's ISO-DEP frame (command = 5 header bytes + data). */
    fun chunkSizeFor(maxTransceiveLength: Int): Int = (maxTransceiveLength - 6).coerceIn(16, MAX_CHUNK)
}

/** ISO/IEC 7816-4 status words used by the protocol. */
enum class Sw(val code: Int) {
    OK(0x9000),
    WRONG_LENGTH(0x6700),
    /** Present mode not armed, expired, or a step out of order (no HELLO, no upload begun). */
    CONDITIONS_NOT_SATISFIED(0x6985),
    INVALID_DATA(0x6A80),
    /** Kipat application/session unavailable (command before a successful SELECT). */
    NOT_FOUND(0x6A82),
    /** Offset outside the profile or upload buffer. */
    WRONG_P1P2(0x6B00),
    INS_NOT_SUPPORTED(0x6D00),
    CLA_NOT_SUPPORTED(0x6E00);

    val bytes: ByteArray get() = byteArrayOf((code shr 8).toByte(), code.toByte())

    companion object {
        fun of(code: Int): Sw? = entries.firstOrNull { it.code == code }
    }
}
