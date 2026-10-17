package com.kipat.probe.nfc.peer

import com.kipat.probe.data.PublicKipatProfileSnapshot
import com.kipat.probe.nfc.peer.KipatApduProtocol.shortId
import com.kipat.probe.nfc.peer.KipatApduProtocol.toHex
import com.kipat.probe.nfc.profile.NfcProfilePayload
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.security.SecureRandom

/**
 * One responder session: either a developer "Present my Kipat" window (manual) or one ambient
 * Kipat Tap encounter. The profile is serialized before the session exists, never in the APDU path.
 */
class PeerPresentSession(
    val sessionId: ByteArray,
    val expiresAtMs: Long,
    val profile: NfcProfilePayload,
    val profileBytes: ByteArray,
    val sha256: ByteArray,
    /** Opened by an NFC encounter for Kipat Tap rather than armed by a person in manual mode. */
    val ambient: Boolean = false,
) {
    val profileType: Byte get() = if (profile.type.wire == "organization") 2 else 1
}

/** Receives a validated peer profile from an ambient encounter (the responder side of Kipat Tap). */
fun interface PeerDeliveryListener {
    fun onPeerDelivered(peer: NfcProfilePayload, presenterSession: ByteArray, readerSession: ByteArray, uploadedBytes: Int)
}

/** Presenter UI states. Typed: no protocol state is encoded in strings. */
sealed interface PresentState {
    data object Idle : PresentState
    data object Preparing : PresentState
    data class Ready(val expiresAtMs: Long, val profile: NfcProfilePayload) : PresentState
    data class PeerDetected(val expiresAtMs: Long) : PresentState
    data object ReceivingPeerProfile : PresentState

    /**
     * Exchange finished. [peer] is null for one-way sharing (the receiver kept its profile).
     * [exchangeId] lets the UI open exactly one Reveal per exchange.
     */
    data class Success(val exchangeId: Long, val peer: NfcProfilePayload?, val isSelf: Boolean) : PresentState
    data object Expired : PresentState
    data class Unsupported(val reason: String) : PresentState
    data class Error(val message: String) : PresentState
}

/** What Developer tools shows about the last exchange. Measured values only. */
data class PeerDiagnostics(
    val role: String,
    val protocolVersion: Int,
    val peerName: String?,
    val downloadedBytes: Int,
    val uploadedBytes: Int,
    val integrity: String,
    val durationMs: Long?,
    val session: String,
    val result: String,
)

/** Same pair of session ids finishing twice in quick succession is one discovery. */
class PeerExchangeDeduper(private val windowMs: Long = 10_000) {
    private val seen = LinkedHashMap<String, Long>()

    @Synchronized
    fun firstTime(key: String, nowMs: Long): Boolean {
        seen.entries.removeAll { nowMs - it.value > windowMs }
        if (seen.containsKey(key)) return false
        seen[key] = nowMs
        return true
    }
}

/**
 * Responder-side state shared by [KipatHceService] and the UI. One instance per process
 * ([KipatPeerSessions]).
 *
 * Two kinds of session:
 * - **Ambient (Kipat Tap, the product flow):** opened on demand when a reader selects the Type 4
 *   NDEF or Kipat AID, only while [ambientProfile] says Kipat Tap is eligible (enabled, public
 *   profile published, device unlocked). One session per NFC encounter, closed by COMPLETE or
 *   after [ENCOUNTER_WINDOW_MS]. No UI arms it and nothing expires while the user is elsewhere.
 * - **Manual (Developer tools only):** a 60 s window armed by a person, for OEM diagnosis.
 *   It takes precedence while armed.
 */
class PeerSessionStore(
    private val clock: () -> Long,
    private val random: SecureRandom = SecureRandom(),
    private val log: PeerLog = PeerLog.NONE,
    private val ambientProfile: () -> PublicKipatProfileSnapshot? = { null },
    /** Why [ambientProfile] is null right now (kipat_tap_disabled, no_public_profile, device_locked). */
    private val ambientRefusal: () -> String = { "not_eligible" },
    /**
     * Runs a delivery after the current APDU response has been returned. The HCE service posts to
     * the main thread; tests run it inline. An Activity launch never delays an APDU.
     */
    private val post: (() -> Unit) -> Unit = { it() },
) {
    /** A peer profile committed on this phone, waiting for the FINALIZE barrier (ambient only). */
    private class Committed(val session: PeerPresentSession, val reader: ByteArray, val peer: NfcProfilePayload, val bytes: Int)

    /** Set by the HCE service: shows the Reveal for a peer received during an ambient encounter. */
    @Volatile var ambientListener: PeerDeliveryListener? = null

    private val _state = MutableStateFlow<PresentState>(PresentState.Idle)
    val state: StateFlow<PresentState> = _state.asStateFlow()

    private val _diagnostics = MutableStateFlow<PeerDiagnostics?>(null)
    val diagnostics: StateFlow<PeerDiagnostics?> = _diagnostics.asStateFlow()

    @Volatile private var session: PeerPresentSession? = null
    @Volatile private var encounter: PeerPresentSession? = null
    private var committed: Committed? = null

    /** The reason the last ambient encounter was refused, for diagnostics. */
    @Volatile var lastRefusal: String = "none"
        private set
    private val encounterStarts = ArrayDeque<Long>()
    private var exchangeCounter = 0L
    private var revealShownFor = -1L
    private var helloAt = 0L
    private val deduper = PeerExchangeDeduper()

    sealed interface ArmResult {
        data class Armed(val session: PeerPresentSession) : ArmResult
        data class TooLarge(val bytes: Int) : ArmResult
    }

    /** Pre-serializes the public profile and opens a short sharing window. */
    @Synchronized
    fun arm(profile: NfcProfilePayload, durationMs: Long = PRESENT_WINDOW_MS): ArmResult {
        _state.value = PresentState.Preparing
        val bytes = KipatPeerProfile.toBytes(profile)
        if (bytes.size > KipatApduProtocol.MAX_PROFILE_BYTES) {
            session = null
            _state.value = PresentState.Error("Profile too large for nearby NFC sharing.")
            return ArmResult.TooLarge(bytes.size)
        }
        val id = ByteArray(KipatApduProtocol.SESSION_ID_BYTES).also(random::nextBytes)
        val s = PeerPresentSession(id, clock() + durationMs, profile, bytes, KipatApduProtocol.sha256(bytes))
        session = s
        _state.value = PresentState.Ready(s.expiresAtMs, profile)
        log.event(PeerLog.TAG_SESSION, "peer_present_started", "session=${id.shortId()} bytes=${bytes.size} expiresInMs=$durationMs")
        return ArmResult.Armed(s)
    }

    @Synchronized
    fun cancel() {
        session = null
        _state.value = PresentState.Idle
    }

    fun markUnsupported(reason: String) {
        session = null
        _state.value = PresentState.Unsupported(reason)
    }

    /** The live session (manual first, then the ambient encounter), or null. Fast: called per APDU. */
    fun activeSession(): PeerPresentSession? {
        session?.let { s ->
            if (clock() < s.expiresAtMs) return s
            expire(s)
        }
        val e = encounter ?: return null
        if (clock() < e.expiresAtMs) return e
        encounter = null
        return null
    }

    /**
     * Called when a reader selects the Type 4 NDEF or Kipat AID. Reuses the open encounter (so the
     * bootstrap nonce and the HELLO session id match) or opens one if Kipat Tap is eligible.
     * Null = nothing may be shared now. Bounded: at most [MAX_ENCOUNTERS_PER_MINUTE].
     */
    @Synchronized
    fun beginEncounter(): PeerPresentSession? {
        activeSession()?.let { return it }
        val tap = ambientProfile() ?: run {
            lastRefusal = ambientRefusal()
            return null
        }
        val now = clock()
        while (encounterStarts.isNotEmpty() && now - encounterStarts.first() > 60_000) encounterStarts.removeFirst()
        if (encounterStarts.size >= MAX_ENCOUNTERS_PER_MINUTE) {
            lastRefusal = "rate_limited"
            log.event(PeerLog.TAG_AMBIENT, "ambient_rate_limited", "encounters=${encounterStarts.size}")
            return null
        }
        encounterStarts.addLast(now)
        val id = ByteArray(KipatApduProtocol.SESSION_ID_BYTES).also(random::nextBytes)
        val e = PeerPresentSession(id, now + ENCOUNTER_WINDOW_MS, tap.profile, tap.bytes, tap.sha256, ambient = true)
        encounter = e
        log.event(PeerLog.TAG_AMBIENT, "ambient_encounter_opened", "session=${id.shortId()} bytes=${tap.bytes.size}")
        return e
    }

    /** Called by the UI ticker and by [activeSession]. */
    @Synchronized
    fun expireIfDue() {
        session?.let { if (clock() >= it.expiresAtMs) expire(it) }
    }

    @Synchronized
    private fun expire(s: PeerPresentSession) {
        if (session !== s) return
        session = null
        val st = _state.value
        if (st !is PresentState.Success) _state.value = PresentState.Expired
        log.event(PeerLog.TAG_SESSION, "peer_present_expired", "session=${s.sessionId.shortId()}")
    }

    fun onPeerHello(s: PeerPresentSession) {
        helloAt = clock()
        if (s.ambient) return
        if (session !== s) return
        _state.value = PresentState.PeerDetected(s.expiresAtMs)
    }

    fun onUploadBegun(s: PeerPresentSession) {
        if (!s.ambient && session === s) _state.value = PresentState.ReceivingPeerProfile
    }

    /**
     * A complete, hash-checked, valid peer profile arrived (COMMIT). Ambient: held until the FINALIZE
     * barrier ([onExchangeComplete]) so both phones reveal from the same point; it survives the RF
     * link ending ([onLinkEnded]). Delivered at most once per session pair.
     */
    @Synchronized
    fun onPeerProfileReceived(s: PeerPresentSession, readerSession: ByteArray, peer: NfcProfilePayload, uploadedBytes: Int) {
        val key = "${s.sessionId.toHex()}:${readerSession.toHex()}:${peer.id ?: peer.displayName}"
        if (!deduper.firstTime(key, clock())) {
            log.event(PeerLog.TAG_AMBIENT, "duplicate_exchange_ignored", "session=${s.sessionId.shortId()}")
            return
        }
        val isSelf = KipatPeerProfile.isSamePerson(peer, s.profile.id)
        diagnostics(s, readerSession, peer.displayName, uploadedBytes, if (isSelf) "Own profile" else "Received")
        if (s.ambient) {
            log.event(PeerLog.TAG_AMBIENT, "peer_profile_received", "session=${s.sessionId.shortId()} bytes=$uploadedBytes self=$isSelf")
            committed = Committed(s, readerSession, peer, uploadedBytes)
            return
        }
        _state.value = PresentState.Success(++exchangeCounter, peer, isSelf)
    }

    /**
     * The link ended after COMMIT without FINALIZE (the reader moved away or closed early). The
     * reader already had our profile before it uploaded, and its COMMIT was answered, so this is a
     * completed mutual exchange: deliver now rather than lose it.
     */
    @Synchronized
    fun onLinkEnded(s: PeerPresentSession?) {
        val c = committed ?: return
        if (s != null && c.session !== s) return
        log.event(PeerLog.TAG_AMBIENT, "finalize_missing", "session=${c.session.sessionId.shortId()}")
        deliver(c, "commit_then_link_end")
    }

    private fun deliver(c: Committed, reason: String) {
        committed = null
        log.event(PeerLog.TAG_AMBIENT, "mutual_complete", "reason=$reason")
        // After the APDU response: the Reveal launch never delays FINALIZE's 9000.
        post { ambientListener?.onPeerDelivered(c.peer, c.session.sessionId, c.reader, c.bytes) }
    }

    /**
     * FINALIZE (the protocol's COMPLETE). With a committed peer this is the mutual-complete barrier:
     * the reader reveals when it gets our 9000, we reveal right after sending it.
     * Ends the encounter: the next touch opens a new one.
     */
    @Synchronized
    fun onExchangeComplete(s: PeerPresentSession, readerSession: ByteArray, peerUploaded: Boolean) {
        if (s.ambient) {
            if (encounter === s) encounter = null
            log.event(PeerLog.TAG_AMBIENT, "finalize_received", "session=${s.sessionId.shortId()} uploaded=$peerUploaded")
            committed?.takeIf { it.session === s }?.let { deliver(it, "finalize") }
            return
        }
        if (session !== s) return
        session = null
        if (!peerUploaded && _state.value !is PresentState.Success) {
            _state.value = PresentState.Success(++exchangeCounter, peer = null, isSelf = false)
            diagnostics(s, readerSession, null, 0, "Shared one way")
        }
        log.event(PeerLog.TAG_SESSION, "peer_exchange_success", "session=${s.sessionId.shortId()} uploaded=$peerUploaded")
    }

    /** NFC link dropped mid-exchange: back to Ready while the window is still open, so the user can retry. */
    @Synchronized
    fun onLinkLost(s: PeerPresentSession?) {
        val st = _state.value
        if (s != null && session === s && (st is PresentState.PeerDetected || st is PresentState.ReceivingPeerProfile)) {
            _state.value = PresentState.Ready(s.expiresAtMs, s.profile)
        }
    }

    /** True exactly once per exchange: the UI opens one Reveal, even across recomposition or recreation. */
    @Synchronized
    fun claimReveal(exchangeId: Long): Boolean {
        if (exchangeId <= revealShownFor) return false
        revealShownFor = exchangeId
        return true
    }

    private fun diagnostics(s: PeerPresentSession, reader: ByteArray, peerName: String?, uploaded: Int, result: String) {
        _diagnostics.value = PeerDiagnostics(
            role = if (s.ambient) "Responder (Kipat Tap)" else "Presenter (manual)",
            protocolVersion = KipatApduProtocol.PROTOCOL_VERSION.toInt(),
            peerName = peerName,
            downloadedBytes = s.profileBytes.size,
            uploadedBytes = uploaded,
            integrity = "Passed",
            durationMs = (clock() - helloAt).takeIf { helloAt > 0 && it >= 0 },
            session = "${s.sessionId.shortId()}/${reader.shortId()}",
            result = result,
        )
    }

    companion object {
        /** How long the developer's manual "Present my Kipat" stays readable. Not used by Kipat Tap. */
        const val PRESENT_WINDOW_MS = 60_000L
        /** One NFC encounter: bootstrap read → Kipat exchange. Generous for an initiator cold start. */
        const val ENCOUNTER_WINDOW_MS = 20_000L
        /** Bounds how often any reader can open encounters (drain/abuse protection). */
        const val MAX_ENCOUNTERS_PER_MINUTE = 20
    }
}
