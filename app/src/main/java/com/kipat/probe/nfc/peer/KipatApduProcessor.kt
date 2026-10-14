package com.kipat.probe.nfc.peer

import com.kipat.probe.nfc.peer.KipatApduProtocol.shortId
import com.kipat.probe.nfc.profile.NfcProfileParse

/**
 * Presenter side of the Kipat APDU protocol: every command the HCE service receives goes through
 * [process]. Pure Kotlin (no Android types) so the whole exchange is unit tested and simulated.
 *
 * Fast by construction: the profile bytes and hash are prepared when Present mode is armed; this
 * only slices arrays, copies upload chunks into a bounded buffer and, once, hashes and parses the
 * peer profile on COMMIT (≤ 4 KB).
 *
 * Profile data is served only after the Kipat AID was selected on this link, a HELLO succeeded,
 * and while the user's Present session is armed and unexpired.
 */
class KipatApduProcessor(
    private val store: PeerSessionStore,
    private val log: PeerLog = PeerLog.NONE,
) {
    private var selected = false
    private var linkSession: PeerPresentSession? = null
    private var readerSession: ByteArray? = null
    private var upload: Upload? = null
    private var committed = false

    private class Upload(val length: Int, val sha256: ByteArray) {
        val buffer = ByteArray(length)
        val written = BooleanArray(length)
        val complete: Boolean get() = written.all { it }
    }

    @Synchronized
    fun process(apdu: ByteArray?): ByteArray {
        if (apdu == null || apdu.size < 4) return Sw.WRONG_LENGTH.bytes
        val cla = apdu[0]
        val ins = apdu[1]

        if (cla == KipatApduProtocol.CLA_ISO && ins == KipatApduProtocol.INS_SELECT) return select(apdu)
        if (!selected) return Sw.NOT_FOUND.bytes
        if (cla != KipatApduProtocol.CLA_KIPAT) return Sw.CLA_NOT_SUPPORTED.bytes

        // Every Kipat command needs the user's armed, unexpired Present session — the same one this link said HELLO to.
        val session = store.activeSession() ?: return Sw.CONDITIONS_NOT_SATISFIED.bytes.also {
            log.event(PeerLog.TAG_HCE, "peer_exchange_failed", "reason=not_armed ins=${hexByte(ins)}")
        }
        if (ins == KipatApduProtocol.INS_HELLO) return hello(apdu, session)
        if (linkSession !== session) return Sw.CONDITIONS_NOT_SATISFIED.bytes

        return when (ins) {
            KipatApduProtocol.INS_GET_META -> meta(apdu, session)
            KipatApduProtocol.INS_READ_CHUNK -> read(apdu, session)
            KipatApduProtocol.INS_BEGIN_UPLOAD -> beginUpload(apdu, session)
            KipatApduProtocol.INS_WRITE_CHUNK -> writeChunk(apdu)
            KipatApduProtocol.INS_COMMIT_UPLOAD -> commit(apdu, session)
            KipatApduProtocol.INS_COMPLETE -> complete(apdu, session)
            else -> Sw.INS_NOT_SUPPORTED.bytes
        }
    }

    /**
     * The reader moved away or selected something else. A committed peer profile stays delivered;
     * a partial upload is discarded so it can never become an identity.
     */
    @Synchronized
    fun onDeactivated(linkLost: Boolean) {
        val partial = upload != null && !committed
        if (partial) log.event(PeerLog.TAG_HCE, "peer_link_lost", "partial_upload_cleared=true")
        else if (linkLost) log.event(PeerLog.TAG_HCE, "peer_link_lost", "committed=$committed")
        if (!committed) store.onLinkLost(linkSession)
        resetLink()
        selected = false
    }

    // ------------------------------------------------------------------------------- commands

    private fun select(apdu: ByteArray): ByteArray {
        val aid = body(apdu, allowLe = true)
        if (aid == null || apdu[2] != 0x04.toByte()) return Sw.WRONG_LENGTH.bytes
        if (!aid.contentEquals(KipatApduProtocol.AID)) {
            selected = false
            return Sw.NOT_FOUND.bytes
        }
        selected = true
        resetLink()
        log.event(PeerLog.TAG_HCE, "hce_aid_selected", "aid=${KipatApduProtocol.AID_HEX}")
        return KipatApduProtocol.ok(byteArrayOf(KipatApduProtocol.PROTOCOL_VERSION))
    }

    private fun hello(apdu: ByteArray, session: PeerPresentSession): ByteArray {
        val data = body(apdu, allowLe = true) ?: return Sw.WRONG_LENGTH.bytes
        if (data.size != KipatApduProtocol.HELLO_REQUEST_BYTES) return Sw.WRONG_LENGTH.bytes
        if (data[0] != KipatApduProtocol.PROTOCOL_VERSION) {
            log.event(PeerLog.TAG_PROTOCOL, "peer_exchange_failed", "reason=unsupported_version v=${data[0]}")
            return Sw.INVALID_DATA.bytes
        }
        resetLink()
        linkSession = session
        readerSession = data.copyOfRange(1, data.size)
        store.onPeerHello(session)
        log.event(PeerLog.TAG_PROTOCOL, "peer_hello", "v=1 presenter=${session.sessionId.shortId()} reader=${readerSession!!.shortId()}")
        val caps = KipatApduProtocol.CAP_PROFILE_DOWNLOAD or KipatApduProtocol.CAP_PEER_PROFILE_UPLOAD
        return KipatApduProtocol.ok(byteArrayOf(KipatApduProtocol.PROTOCOL_VERSION) + session.sessionId + byteArrayOf(caps.toByte()))
    }

    private fun meta(apdu: ByteArray, s: PeerPresentSession): ByteArray {
        if (apdu.size !in 4..5) return Sw.WRONG_LENGTH.bytes
        val n = s.profileBytes.size
        log.event(PeerLog.TAG_PROTOCOL, "peer_profile_meta", "bytes=$n")
        return KipatApduProtocol.ok(byteArrayOf(KipatApduProtocol.PROFILE_SCHEMA, (n shr 8).toByte(), n.toByte()) + s.sha256 + byteArrayOf(s.profileType))
    }

    private fun read(apdu: ByteArray, s: PeerPresentSession): ByteArray {
        if (apdu.size != 5) return Sw.WRONG_LENGTH.bytes
        val offset = u16(apdu[2], apdu[3])
        val want = apdu[4].toInt() and 0xFF
        if (want == 0 || want > KipatApduProtocol.MAX_CHUNK) return Sw.WRONG_LENGTH.bytes
        val n = s.profileBytes.size
        if (offset >= n) return Sw.WRONG_P1P2.bytes
        val end = minOf(n, offset + want)
        log.event(PeerLog.TAG_PROTOCOL, "peer_profile_chunk_read", "offset=$offset bytes=${end - offset} of=$n")
        return KipatApduProtocol.ok(s.profileBytes.copyOfRange(offset, end))
    }

    private fun beginUpload(apdu: ByteArray, s: PeerPresentSession): ByteArray {
        val data = body(apdu, allowLe = false) ?: return Sw.WRONG_LENGTH.bytes
        if (data.size != KipatApduProtocol.BEGIN_UPLOAD_BYTES) return Sw.WRONG_LENGTH.bytes
        if (data[0] != KipatApduProtocol.PROFILE_SCHEMA) return Sw.INVALID_DATA.bytes
        val length = u16(data[1], data[2])
        // Bounded before anything is allocated.
        if (length <= 0 || length > KipatApduProtocol.MAX_PROFILE_BYTES) return Sw.WRONG_LENGTH.bytes
        upload = Upload(length, data.copyOfRange(3, 3 + KipatApduProtocol.HASH_BYTES))
        committed = false
        store.onUploadBegun(s)
        log.event(PeerLog.TAG_PROTOCOL, "peer_upload_started", "bytes=$length")
        return Sw.OK.bytes
    }

    /** Writes by offset (not append): a retried or reordered chunk lands in the same place. */
    private fun writeChunk(apdu: ByteArray): ByteArray {
        val u = upload ?: return Sw.CONDITIONS_NOT_SATISFIED.bytes
        if (committed) return Sw.CONDITIONS_NOT_SATISFIED.bytes
        val data = body(apdu, allowLe = false) ?: return Sw.WRONG_LENGTH.bytes
        if (data.isEmpty() || data.size > KipatApduProtocol.MAX_CHUNK) return Sw.WRONG_LENGTH.bytes
        val offset = u16(apdu[2], apdu[3])
        if (offset + data.size > u.length) return Sw.WRONG_P1P2.bytes
        data.copyInto(u.buffer, offset)
        for (i in offset until offset + data.size) u.written[i] = true
        return Sw.OK.bytes
    }

    private fun commit(apdu: ByteArray, s: PeerPresentSession): ByteArray {
        if (apdu.size !in 4..5) return Sw.WRONG_LENGTH.bytes
        val u = upload ?: return Sw.CONDITIONS_NOT_SATISFIED.bytes
        if (!u.complete) return Sw.CONDITIONS_NOT_SATISFIED.bytes.also {
            log.event(PeerLog.TAG_PROTOCOL, "peer_exchange_failed", "reason=upload_incomplete")
        }
        if (!KipatApduProtocol.sha256(u.buffer).contentEquals(u.sha256)) {
            upload = null
            log.event(PeerLog.TAG_PROTOCOL, "peer_exchange_failed", "reason=hash_mismatch")
            return Sw.INVALID_DATA.bytes
        }
        val parsed = KipatPeerProfile.parse(u.buffer)
        upload = null
        if (parsed !is NfcProfileParse.Valid) {
            log.event(PeerLog.TAG_PROTOCOL, "peer_exchange_failed", "reason=invalid_profile kind=${parsed::class.simpleName}")
            return Sw.INVALID_DATA.bytes
        }
        committed = true
        store.onPeerProfileReceived(s, readerSession ?: ByteArray(0), parsed.profile, u.length)
        log.event(PeerLog.TAG_PROTOCOL, "peer_upload_completed", "bytes=${u.length}")
        return Sw.OK.bytes
    }

    private fun complete(apdu: ByteArray, s: PeerPresentSession): ByteArray {
        if (apdu.size !in 4..5) return Sw.WRONG_LENGTH.bytes
        store.onExchangeComplete(s, readerSession ?: ByteArray(0), committed)
        return Sw.OK.bytes
    }

    // ------------------------------------------------------------------------------- parsing

    private fun resetLink() {
        linkSession = null
        readerSession = null
        upload = null
        committed = false
    }

    private fun u16(hi: Byte, lo: Byte) = ((hi.toInt() and 0xFF) shl 8) or (lo.toInt() and 0xFF)

    private fun hexByte(b: Byte) = "%02X".format(b)

    /**
     * Command data of a short APDU: header(4) Lc data [Le]. Null when Lc disagrees with the
     * actual length, so a truncated or padded command is rejected instead of misread.
     */
    private fun body(apdu: ByteArray, allowLe: Boolean): ByteArray? {
        if (apdu.size < 5) return null
        val lc = apdu[4].toInt() and 0xFF
        if (lc == 0) return null
        val exact = 5 + lc
        return when {
            apdu.size == exact -> apdu.copyOfRange(5, exact)
            allowLe && apdu.size == exact + 1 -> apdu.copyOfRange(5, exact)
            else -> null
        }
    }
}
