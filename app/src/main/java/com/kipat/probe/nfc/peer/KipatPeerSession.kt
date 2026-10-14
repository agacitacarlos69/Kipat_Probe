package com.kipat.probe.nfc.peer

import com.kipat.probe.nfc.peer.KipatApduProtocol.shortId
import com.kipat.probe.nfc.peer.KipatApduProtocol.toHex
import com.kipat.probe.nfc.profile.NfcProfilePayload
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.security.SecureRandom

/** One armed "Present my Kipat" session. The profile is serialized before arming, never in the APDU path. */
class PeerPresentSession(
    val sessionId: ByteArray,
    val expiresAtMs: Long,
    val profile: NfcProfilePayload,
    val profileBytes: ByteArray,
    val sha256: ByteArray,
) {
    val profileType: Byte get() = if (profile.type.wire == "organization") 2 else 1
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
 * Presenter-side state shared by the Present UI and [KipatHceService]. One instance per process
 * ([KipatPeerSessions]); survives Activity recreation. Nothing is persisted: after process death
 * no session is armed and the service refuses every profile request.
 */
class PeerSessionStore(
    private val clock: () -> Long,
    private val random: SecureRandom = SecureRandom(),
    private val log: PeerLog = PeerLog.NONE,
) {
    private val _state = MutableStateFlow<PresentState>(PresentState.Idle)
    val state: StateFlow<PresentState> = _state.asStateFlow()

    private val _diagnostics = MutableStateFlow<PeerDiagnostics?>(null)
    val diagnostics: StateFlow<PeerDiagnostics?> = _diagnostics.asStateFlow()

    @Volatile private var session: PeerPresentSession? = null
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

    /** The armed session, or null (never armed, cancelled, finished or expired). Fast: called per APDU. */
    fun activeSession(): PeerPresentSession? {
        val s = session ?: return null
        if (clock() >= s.expiresAtMs) {
            expire(s)
            return null
        }
        return s
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
        if (session !== s) return
        helloAt = clock()
        _state.value = PresentState.PeerDetected(s.expiresAtMs)
    }

    fun onUploadBegun(s: PeerPresentSession) {
        if (session === s) _state.value = PresentState.ReceivingPeerProfile
    }

    /** A complete, hash-checked, valid peer profile arrived (COMMIT). Delivered at most once per session pair. */
    @Synchronized
    fun onPeerProfileReceived(s: PeerPresentSession, readerSession: ByteArray, peer: NfcProfilePayload, uploadedBytes: Int) {
        val key = "${s.sessionId.toHex()}:${readerSession.toHex()}:${peer.id ?: peer.displayName}"
        if (!deduper.firstTime(key, clock())) return
        val isSelf = KipatPeerProfile.isSamePerson(peer, s.profile.id)
        _state.value = PresentState.Success(++exchangeCounter, peer, isSelf)
        diagnostics(s, readerSession, peer.displayName, uploadedBytes, if (isSelf) "Own profile" else "Received")
    }

    /** The receiver finished (COMPLETE). Ends the sharing window: one exchange per Present. */
    @Synchronized
    fun onExchangeComplete(s: PeerPresentSession, readerSession: ByteArray, peerUploaded: Boolean) {
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
            role = "Presenter",
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
        /** How long "Present my Kipat" stays readable. */
        const val PRESENT_WINDOW_MS = 60_000L
    }
}
