package com.kipat.probe.nfc.peer

import com.kipat.probe.nfc.peer.KipatApduProtocol.shortId
import com.kipat.probe.nfc.peer.KipatApduProtocol.toHex
import com.kipat.probe.nfc.profile.NfcProfileParse
import com.kipat.probe.nfc.profile.NfcProfilePayload
import java.io.IOException

/** Why a receive failed, with the one line a person needs (no protocol words). */
enum class PeerFailure(val message: String) {
    NOT_KIPAT("That doesn't look like a Kipat phone. Ask them to open Kipat and tap Present my Kipat."),
    NOT_READY("Their Kipat isn't sharing right now. Ask them to tap Present my Kipat again."),
    UNSUPPORTED_VERSION("Their Kipat uses a different version. Update both apps and try again."),
    TOO_LARGE("Their profile is too large for nearby NFC sharing."),
    INTEGRITY("The profile didn't arrive intact. Keep the phones together and try again."),
    INVALID_PROFILE("Their profile couldn't be read."),
    LINK_LOST("Keep the phones together and try again."),
    PROTOCOL("Something went wrong talking to their phone. Try again."),
}

enum class UploadOutcome { NOT_REQUESTED, SENT, NOT_SUPPORTED_BY_PEER, SKIPPED_OWN_PROFILE, TOO_LARGE, FAILED }

sealed interface PeerExchangeResult {
    data class Success(
        val profile: NfcProfilePayload,
        val presenterSession: String,
        val readerSession: String,
        val downloadedBytes: Int,
        val uploadedBytes: Int,
        val upload: UploadOutcome,
        val durationMs: Long,
        /** The presenter is showing this phone's own profile (same card id). Not an error, not a connection. */
        val isSelf: Boolean,
    ) : PeerExchangeResult {
        /** Key for "same exchange twice in a row is one discovery". */
        val dedupeKey: String get() = "$presenterSession:${profile.id ?: profile.displayName}"
    }

    data class Failed(val reason: PeerFailure, val detail: String) : PeerExchangeResult
}

/** Receiver progress, mapped by the UI to "Kipat found", "Receiving profile", "Exchanging profiles". */
enum class ReceiveProgress { KIPAT_DETECTED, READING_PROFILE, SENDING_OWN_PROFILE }

/**
 * Reader side of the protocol: SELECT → HELLO → GET META → READ chunks → verify SHA-256 → parse →
 * (optional) BEGIN/WRITE/COMMIT own profile → COMPLETE.
 *
 * Pure: [transceive] is IsoDep.transceive on a phone, or [KipatApduProcessor.process] in the
 * protocol simulation. Blocking — the Android wrapper runs it off the main thread.
 */
class KipatPeerExchange(
    private val transceive: (ByteArray) -> ByteArray,
    private val chunkSize: Int,
    /** This phone's public profile to send back, or null for one-way receive. */
    private val ownProfile: NfcProfilePayload?,
    private val ownProfileId: String?,
    private val newSessionId: () -> ByteArray,
    private val clock: () -> Long,
    private val log: PeerLog = PeerLog.NONE,
    private val onProgress: (ReceiveProgress) -> Unit = {},
) {
    private class Abort(val reason: PeerFailure, val detail: String) : Exception(detail)

    private var down = 0
    private var up = 0

    fun run(): PeerExchangeResult {
        val started = clock()
        val chunk = chunkSize.coerceIn(16, KipatApduProtocol.MAX_CHUNK)
        val readerSession = newSessionId()
        return try {
            // SELECT: only a phone presenting the Kipat AID answers 9000.
            val sel = send(KipatApduProtocol.select(), "select")
            if (!sel.isOk) fail(PeerFailure.NOT_KIPAT, "select sw=${sw(sel)}")
            log.event(PeerLog.TAG_READER, "hce_aid_selected", "ok")
            onProgress(ReceiveProgress.KIPAT_DETECTED)

            val hello = send(KipatApduProtocol.hello(readerSession), "hello")
            when (Sw.of(hello.sw)) {
                Sw.OK -> Unit
                Sw.CONDITIONS_NOT_SATISFIED -> fail(PeerFailure.NOT_READY, "hello sw=${sw(hello)}")
                Sw.INVALID_DATA -> fail(PeerFailure.UNSUPPORTED_VERSION, "hello sw=${sw(hello)}")
                else -> fail(PeerFailure.PROTOCOL, "hello sw=${sw(hello)}")
            }
            if (hello.data.size != KipatApduProtocol.HELLO_RESPONSE_BYTES) fail(PeerFailure.PROTOCOL, "hello length ${hello.data.size}")
            if (hello.data[0] != KipatApduProtocol.PROTOCOL_VERSION) fail(PeerFailure.UNSUPPORTED_VERSION, "presenter v=${hello.data[0]}")
            val presenterSession = hello.data.copyOfRange(1, 1 + KipatApduProtocol.SESSION_ID_BYTES)
            val caps = hello.data.last().toInt() and 0xFF
            log.event(PeerLog.TAG_PROTOCOL, "peer_hello", "v=1 presenter=${presenterSession.shortId()} reader=${readerSession.shortId()} caps=$caps")

            val meta = expectOk(send(KipatApduProtocol.getMeta(), "meta"), "meta")
            if (meta.data.size != KipatApduProtocol.META_RESPONSE_BYTES) fail(PeerFailure.PROTOCOL, "meta length ${meta.data.size}")
            if (meta.data[0] != KipatApduProtocol.PROFILE_SCHEMA) fail(PeerFailure.UNSUPPORTED_VERSION, "schema=${meta.data[0]}")
            val length = ((meta.data[1].toInt() and 0xFF) shl 8) or (meta.data[2].toInt() and 0xFF)
            if (length == 0) fail(PeerFailure.PROTOCOL, "empty profile")
            if (length > KipatApduProtocol.MAX_PROFILE_BYTES) fail(PeerFailure.TOO_LARGE, "length=$length")
            val expectedHash = meta.data.copyOfRange(3, 3 + KipatApduProtocol.HASH_BYTES)
            log.event(PeerLog.TAG_PROTOCOL, "peer_profile_meta", "bytes=$length")

            onProgress(ReceiveProgress.READING_PROFILE)
            log.event(PeerLog.TAG_READER, "profile_download_started", "bytes=$length chunk=$chunk")
            val bytes = download(length, chunk)
            if (!KipatApduProtocol.sha256(bytes).contentEquals(expectedHash)) fail(PeerFailure.INTEGRITY, "sha256 mismatch")
            val profile = when (val p = KipatPeerProfile.parse(bytes)) {
                is NfcProfileParse.Valid -> p.profile
                is NfcProfileParse.UnsupportedVersion -> fail(PeerFailure.UNSUPPORTED_VERSION, "profile v=${p.version}")
                is NfcProfileParse.Invalid -> fail(PeerFailure.INVALID_PROFILE, "invalid profile")
            }
            log.event(PeerLog.TAG_READER, "profile_download_complete", "bytes=$length")

            val isSelf = KipatPeerProfile.isSamePerson(profile, ownProfileId)
            val upload = when {
                isSelf -> UploadOutcome.SKIPPED_OWN_PROFILE
                ownProfile == null -> UploadOutcome.NOT_REQUESTED
                caps and KipatApduProtocol.CAP_PEER_PROFILE_UPLOAD == 0 -> UploadOutcome.NOT_SUPPORTED_BY_PEER
                else -> uploadOwn(ownProfile, chunk)
            }
            // Best effort: the profile is already safely received.
            runCatching { send(KipatApduProtocol.complete(), "complete") }

            val ms = clock() - started
            log.event(PeerLog.TAG_SESSION, "peer_exchange_success", "down=$down up=$up upload=$upload ms=$ms")
            PeerExchangeResult.Success(profile, presenterSession.toHex(), readerSession.toHex(), down, up, upload, ms, isSelf)
        } catch (a: Abort) {
            log.event(PeerLog.TAG_SESSION, "peer_exchange_failed", "reason=${a.reason} ${a.detail}")
            PeerExchangeResult.Failed(a.reason, a.detail)
        } catch (e: IOException) {
            // TagLostException, IsoDep timeouts and phones moving apart all land here.
            log.event(PeerLog.TAG_SESSION, "peer_link_lost", "${e::class.simpleName}")
            PeerExchangeResult.Failed(PeerFailure.LINK_LOST, e::class.simpleName ?: "IOException")
        } catch (e: RuntimeException) {
            log.event(PeerLog.TAG_SESSION, "peer_exchange_failed", "reason=PROTOCOL ${e::class.simpleName}")
            PeerExchangeResult.Failed(PeerFailure.PROTOCOL, e::class.simpleName ?: "error")
        }
    }

    private fun download(length: Int, chunk: Int): ByteArray {
        val out = ByteArray(length)
        var offset = 0
        while (offset < length) {
            val want = minOf(chunk, length - offset)
            val r = expectOk(send(KipatApduProtocol.readChunk(offset, want), "read"), "read@$offset")
            if (r.data.isEmpty() || r.data.size > want) fail(PeerFailure.PROTOCOL, "chunk size ${r.data.size} for $want")
            r.data.copyInto(out, offset)
            offset += r.data.size
            down += r.data.size
        }
        return out
    }

    private fun uploadOwn(profile: NfcProfilePayload, chunk: Int): UploadOutcome {
        val bytes = KipatPeerProfile.toBytes(profile)
        if (bytes.size > KipatApduProtocol.MAX_PROFILE_BYTES) return UploadOutcome.TOO_LARGE
        onProgress(ReceiveProgress.SENDING_OWN_PROFILE)
        log.event(PeerLog.TAG_READER, "peer_upload_started", "bytes=${bytes.size}")
        return try {
            if (!send(KipatApduProtocol.beginUpload(bytes.size, KipatApduProtocol.sha256(bytes)), "begin").isOk) return UploadOutcome.FAILED
            var offset = 0
            while (offset < bytes.size) {
                val part = bytes.copyOfRange(offset, minOf(bytes.size, offset + chunk))
                if (!send(KipatApduProtocol.writeChunk(offset, part), "write").isOk) return UploadOutcome.FAILED
                offset += part.size
            }
            if (!send(KipatApduProtocol.commitUpload(), "commit").isOk) return UploadOutcome.FAILED
            up = bytes.size
            log.event(PeerLog.TAG_READER, "peer_upload_completed", "bytes=${bytes.size}")
            UploadOutcome.SENT
        } catch (e: IOException) {
            // We already have their profile; only the return trip failed.
            log.event(PeerLog.TAG_READER, "peer_link_lost", "during_upload")
            UploadOutcome.FAILED
        }
    }

    private fun send(command: ByteArray, what: String): KipatApduProtocol.Response =
        KipatApduProtocol.parseResponse(transceive(command)) ?: fail(PeerFailure.PROTOCOL, "$what: no status word")

    private fun expectOk(r: KipatApduProtocol.Response, what: String): KipatApduProtocol.Response = when {
        r.isOk -> r
        r.sw == Sw.CONDITIONS_NOT_SATISFIED.code -> fail(PeerFailure.NOT_READY, "$what sw=${sw(r)}")
        else -> fail(PeerFailure.PROTOCOL, "$what sw=${sw(r)}")
    }

    private fun sw(r: KipatApduProtocol.Response) = "%04X".format(r.sw)

    private fun fail(reason: PeerFailure, detail: String): Nothing = throw Abort(reason, detail)
}
