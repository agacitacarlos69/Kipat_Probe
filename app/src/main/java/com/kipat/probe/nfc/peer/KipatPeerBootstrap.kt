package com.kipat.probe.nfc.peer

import com.kipat.probe.nfc.peer.KipatApduProtocol.toHex
import com.kipat.probe.nfc.profile.Ndef
import com.kipat.probe.nfc.profile.NdefRecordData
import org.json.JSONException
import org.json.JSONObject
import org.json.JSONTokener

/**
 * The tiny NDEF message a Kipat phone exposes as an NFC Forum Type 4 tag
 * (docs/nfc/ambient-peer-feasibility.md). It carries NO profile data — it only tells the other
 * phone's Android "this is a Kipat endpoint, open Kipat", so the real exchange can continue
 * over the Kipat AID on the same contact.
 *
 * Records, in order: MIME `application/vnd.kipat.peer` (FIRST: Android dispatches on the first
 * record's type), then an Android Application Record (anywhere but first, per the NFC guide).
 */
object KipatPeerBootstrap {
    const val MIME_TYPE = "application/vnd.kipat.peer"
    const val VERSION = 1
    const val TRANSPORT = "iso-dep"
    /** Bytes of the encounter session id exposed as the nonce (not identity; links bootstrap → HELLO). */
    const val NONCE_BYTES = 8
    /** Anything larger is not a Kipat bootstrap. */
    const val MAX_PAYLOAD = 128

    data class Parsed(val version: Int, val protocol: Int, val nonceHex: String?)

    sealed interface Result {
        data class Valid(val bootstrap: Parsed) : Result
        data class Unsupported(val version: Int) : Result
        data class Invalid(val reason: String) : Result
    }

    /** `{"v":1,"t":"iso-dep","p":1,"s":"<16 hex>"}` — compact, fixed order. */
    fun payload(sessionId: ByteArray): ByteArray =
        """{"v":$VERSION,"t":"$TRANSPORT","p":${KipatApduProtocol.PROTOCOL_VERSION},"s":"${sessionId.copyOf(NONCE_BYTES).toHex()}"}"""
            .toByteArray(Charsets.US_ASCII)

    fun records(sessionId: ByteArray, packageName: String): List<NdefRecordData> =
        listOf(Ndef.mime(MIME_TYPE, payload(sessionId)), Ndef.aar(packageName))

    /** The serialized NDEF message (what a Type 4 NDEF file holds after NLEN). */
    fun message(sessionId: ByteArray, packageName: String): ByteArray = encode(records(sessionId, packageName))

    fun isBootstrap(record: NdefRecordData): Boolean =
        record.tnf == Ndef.TNF_MIME_MEDIA && record.typeString.equals(MIME_TYPE, ignoreCase = true)

    /** Strict: only this exact shape is accepted; the bootstrap never carries anything to render. */
    fun parse(payload: ByteArray): Result {
        if (payload.isEmpty() || payload.size > MAX_PAYLOAD) return Result.Invalid("size ${payload.size}")
        val json = try {
            JSONTokener(String(payload, Charsets.UTF_8)).nextValue() as? JSONObject ?: return Result.Invalid("not an object")
        } catch (e: JSONException) {
            return Result.Invalid("malformed")
        } catch (e: RuntimeException) {
            return Result.Invalid("malformed")
        }
        val v = (json.opt("v") as? Int) ?: return Result.Invalid("no version")
        if (v != VERSION) return Result.Unsupported(v)
        if (json.opt("t") != TRANSPORT) return Result.Invalid("transport")
        val p = (json.opt("p") as? Int) ?: return Result.Invalid("no protocol")
        if (p != KipatApduProtocol.PROTOCOL_VERSION.toInt()) return Result.Unsupported(p)
        val s = json.opt("s")
        val nonce = when {
            s == null || s == JSONObject.NULL -> null
            s is String && Regex("^[0-9A-Fa-f]{${NONCE_BYTES * 2}}$").matches(s) -> s.uppercase()
            else -> return Result.Invalid("nonce")
        }
        return Result.Valid(Parsed(v, p, nonce))
    }

    /** NDEF message encoding (MB/ME flags, short records), matching android.nfc.NdefMessage. */
    fun encode(records: List<NdefRecordData>): ByteArray {
        val out = java.io.ByteArrayOutputStream()
        records.forEachIndexed { i, r ->
            val short = r.payload.size < 256
            var header = r.tnf.toInt() and 0x07
            if (i == 0) header = header or 0x80
            if (i == records.lastIndex) header = header or 0x40
            if (short) header = header or 0x10
            if (r.id.isNotEmpty()) header = header or 0x08
            out.write(header)
            out.write(r.type.size)
            if (short) out.write(r.payload.size) else {
                out.write(r.payload.size ushr 24); out.write(r.payload.size ushr 16); out.write(r.payload.size ushr 8); out.write(r.payload.size)
            }
            if (r.id.isNotEmpty()) out.write(r.id.size)
            out.write(r.type)
            if (r.id.isNotEmpty()) out.write(r.id)
            out.write(r.payload)
        }
        return out.toByteArray()
    }
}
