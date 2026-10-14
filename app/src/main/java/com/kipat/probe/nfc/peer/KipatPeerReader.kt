package com.kipat.probe.nfc.peer

import android.app.Activity
import android.nfc.NfcAdapter
import android.nfc.Tag
import android.nfc.tech.IsoDep
import android.os.Bundle
import android.os.SystemClock
import com.kipat.probe.nfc.profile.NfcProfilePayload
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.IOException
import java.security.SecureRandom
import java.util.concurrent.atomic.AtomicBoolean

/** Receiver UI states. */
sealed interface ReceiveState {
    data object Idle : ReceiveState
    data class Scanning(val note: String? = null) : ReceiveState
    data object KipatDetected : ReceiveState
    data object ReadingProfile : ReceiveState
    data object SendingOwnProfile : ReceiveState
    data class Success(val exchangeId: Long, val result: PeerExchangeResult.Success) : ReceiveState
    data class TagLost(val message: String) : ReceiveState
    data class Unsupported(val reason: String) : ReceiveState
    data class Error(val message: String) : ReceiveState
}

/**
 * Receiver role ("Receive a Kipat"): NFC reader mode + IsoDep, active ONLY between [start] and
 * [cancel]/success while the screen is resumed — so it never steals physical card scans elsewhere.
 *
 * Reader mode gives Kipat the NFC interaction directly: Android's generic tag viewer ("New tag
 * collected") is not involved. NDEF checks are skipped because a Kipat phone speaks APDUs.
 * All IsoDep I/O runs on Dispatchers.IO.
 */
class KipatPeerReader(private val activity: Activity, private val scope: CoroutineScope) : NfcAdapter.ReaderCallback {
    private val adapter: NfcAdapter? = NfcAdapter.getDefaultAdapter(activity)
    private val _state = MutableStateFlow<ReceiveState>(ReceiveState.Idle)
    val state: StateFlow<ReceiveState> = _state.asStateFlow()

    private val busy = AtomicBoolean(false)
    @Volatile private var active = false
    private var resumed = false
    private var readerEnabled = false
    private var ownProfile: NfcProfilePayload? = null
    private var ownProfileId: String? = null
    private var sendBack = true
    private var exchangeCounter = 0L
    private val random = SecureRandom()
    private val log = KipatPeerSessions.log

    fun start(own: NfcProfilePayload, sendMyProfileBack: Boolean) {
        val caps = KipatPeerSessions.capabilities(activity)
        when {
            !caps.canReceive -> { _state.value = ReceiveState.Unsupported("This phone doesn't have NFC, so it can't receive a Kipat by touch."); return }
            !caps.nfcEnabled -> { _state.value = ReceiveState.Unsupported("Turn on NFC to receive a Kipat."); return }
        }
        ownProfile = own
        ownProfileId = own.id
        sendBack = sendMyProfileBack
        active = true
        _state.value = ReceiveState.Scanning()
        log.event(PeerLog.TAG_READER, "reader_mode_started", "send_back=$sendMyProfileBack")
        updateReader()
    }

    fun cancel() {
        active = false
        if (_state.value !is ReceiveState.Success) _state.value = ReceiveState.Idle
        updateReader()
    }

    fun reset() {
        active = false
        _state.value = ReceiveState.Idle
        updateReader()
    }

    private var revealShownFor = -1L

    /** True exactly once per successful exchange: one Reveal, even if the screen recomposes. */
    @Synchronized
    fun claimReveal(exchangeId: Long): Boolean {
        if (exchangeId <= revealShownFor) return false
        revealShownFor = exchangeId
        return true
    }

    fun onResume() { resumed = true; updateReader() }
    fun onPause() { resumed = false; updateReader() }

    override fun onTagDiscovered(tag: Tag) {
        if (!active || !busy.compareAndSet(false, true)) return
        scope.launch(Dispatchers.IO) {
            try {
                exchange(tag)
            } finally {
                busy.set(false)
            }
        }
    }

    private fun exchange(tag: Tag) {
        val iso = IsoDep.get(tag)
        if (iso == null) {
            // A card or a phone that isn't presenting over ISO-DEP: keep looking.
            _state.value = ReceiveState.Scanning("That isn't a Kipat phone. Keep looking.")
            log.event(PeerLog.TAG_READER, "peer_exchange_failed", "reason=no_isodep techs=${tag.techList.joinToString { it.substringAfterLast('.') }}")
            return
        }
        val result = try {
            iso.connect()
            iso.timeout = IO_TIMEOUT_MS
            val chunk = KipatApduProtocol.chunkSizeFor(iso.maxTransceiveLength)
            log.event(PeerLog.TAG_READER, "kipat_peer_detected", "maxTransceive=${iso.maxTransceiveLength} chunk=$chunk")
            KipatPeerExchange(
                transceive = { iso.transceive(it) },
                chunkSize = chunk,
                ownProfile = ownProfile.takeIf { sendBack },
                ownProfileId = ownProfileId,
                newSessionId = { ByteArray(KipatApduProtocol.SESSION_ID_BYTES).also(random::nextBytes) },
                clock = { SystemClock.elapsedRealtime() },
                log = log,
                onProgress = { p ->
                    _state.value = when (p) {
                        ReceiveProgress.KIPAT_DETECTED -> ReceiveState.KipatDetected
                        ReceiveProgress.READING_PROFILE -> ReceiveState.ReadingProfile
                        ReceiveProgress.SENDING_OWN_PROFILE -> ReceiveState.SendingOwnProfile
                    }
                },
            ).run()
        } catch (e: IOException) {
            PeerExchangeResult.Failed(PeerFailure.LINK_LOST, e::class.simpleName ?: "IOException")
        } catch (e: SecurityException) {
            // Stale tag (left the field and was rediscovered).
            PeerExchangeResult.Failed(PeerFailure.LINK_LOST, "SecurityException")
        } catch (e: IllegalStateException) {
            PeerExchangeResult.Failed(PeerFailure.PROTOCOL, "IllegalStateException")
        } finally {
            try { iso.close() } catch (_: IOException) {}
        }
        finish(result)
    }

    private fun finish(result: PeerExchangeResult) {
        when (result) {
            is PeerExchangeResult.Success -> {
                if (!KipatPeerSessions.receiverDedupe.firstTime(result.dedupeKey, SystemClock.elapsedRealtime())) {
                    log.event(PeerLog.TAG_READER, "peer_exchange_duplicate", "presenter=${result.presenterSession.take(8)}")
                    return
                }
                active = false
                _state.value = ReceiveState.Success(++exchangeCounter, result)
                KipatPeerSessions.recordReceiverDiagnostics(result)
                scope.launch { updateReader() }
            }
            is PeerExchangeResult.Failed -> {
                // Stay armed: touching the phones again retries.
                _state.value = if (result.reason == PeerFailure.LINK_LOST) ReceiveState.TagLost(result.reason.message)
                else ReceiveState.Error(result.reason.message)
            }
        }
    }

    private fun updateReader() {
        val nfc = adapter ?: return
        val want = resumed && active
        if (want == readerEnabled) return
        try {
            if (want) {
                // ISO-DEP phones only, no NDEF probing, no platform beep (Kipat gives its own haptic).
                val flags = NfcAdapter.FLAG_READER_NFC_A or NfcAdapter.FLAG_READER_NFC_B or
                    NfcAdapter.FLAG_READER_SKIP_NDEF_CHECK or NfcAdapter.FLAG_READER_NO_PLATFORM_SOUNDS
                nfc.enableReaderMode(activity, this, flags, Bundle())
            } else {
                nfc.disableReaderMode(activity)
            }
            readerEnabled = want
        } catch (e: IllegalStateException) {
            readerEnabled = false
        }
    }

    companion object {
        /** Per-APDU timeout. Generous for slow HCE stacks; a dropped link still fails fast with TagLostException. */
        const val IO_TIMEOUT_MS = 3_000
    }
}
