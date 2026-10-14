package com.kipat.probe.nfc.peer

import android.app.Activity
import android.content.Intent
import android.nfc.tech.IsoDep
import android.os.SystemClock
import com.kipat.probe.nfc.profile.NfcProfilePayload
import com.kipat.probe.nfc.tagExtra
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.IOException
import java.security.SecureRandom

/** What the Reveal host shows before a peer profile is ready. Never protocol words. */
sealed interface TapPending {
    /** First moments after dispatch: nothing drawn, the previous app stays visible. */
    data object Connecting : TapPending
    data object KeepTogether : TapPending
    data class Message(val text: String) : TapPending
}

/**
 * Initiator side of Kipat Tap: Android read the other phone's Type 4 bootstrap and launched
 * TapExperienceActivity with ACTION_NDEF_DISCOVERED. Reuses that same contact (EXTRA_TAG →
 * IsoDep), upgrades to the Kipat AID and runs the mutual exchange. If the link dropped while the
 * Activity started, reader mode — allowed now that Kipat is in the foreground — reacquires the
 * other phone for [REACQUIRE_MS]. No buttons at any point.
 */
class KipatTapInitiator(
    private val activity: Activity,
    private val scope: CoroutineScope,
    private val onPending: (TapPending) -> Unit,
    private val onPeer: (NfcProfilePayload, PeerExchangeResult.Success) -> Unit,
    private val onFinish: () -> Unit,
) {
    private val log = KipatPeerSessions.log
    private val createdAt = SystemClock.elapsedRealtime()
    private var reader: KipatPeerReader? = null
    private var job: Job? = null
    private var resumed = false
    val running: Boolean get() = job?.isActive == true

    fun start(intent: Intent, bootstrap: KipatPeerBootstrap.Result) {
        if (running) return
        log.event(PeerLog.TAG_AMBIENT, "ambient_initiator_created", "since_create_ms=${SystemClock.elapsedRealtime() - createdAt}")
        when (bootstrap) {
            is KipatPeerBootstrap.Result.Unsupported -> return message("This Kipat uses a newer version. Update Kipat to exchange profiles.")
            is KipatPeerBootstrap.Result.Invalid -> {
                log.event(PeerLog.TAG_AMBIENT, "peer_exchange_failed", "reason=bad_bootstrap ${bootstrap.reason}")
                return onFinish()
            }
            is KipatPeerBootstrap.Result.Valid -> Unit
        }
        if (!KipatTapStore.isEnabled(activity)) return message("Kipat Tap is off. Turn it on in Kipat to exchange profiles.")
        val own = KipatTapStore.snapshot(activity)?.profile ?: return message("Publish your profile to use Kipat Tap.")
        val iso = intent.tagExtra()?.let { IsoDep.get(it) }
        onPending(TapPending.Connecting)
        job = scope.launch {
            val result = if (iso == null) null else withContext(Dispatchers.IO) { exchange(iso, own) }
            when {
                result is PeerExchangeResult.Success -> deliver(result)
                result == null || result.retryable() -> reacquire(own)
                else -> message((result as PeerExchangeResult.Failed).reason.message)
            }
        }
    }

    fun onResume() { resumed = true; reader?.onResume() }
    fun onPause() { resumed = false; reader?.onPause() }

    fun cancel() {
        job?.cancel()
        reader?.reset()
    }

    private fun exchange(iso: IsoDep, own: NfcProfilePayload): PeerExchangeResult = try {
        iso.connect()
        iso.timeout = KipatPeerReader.IO_TIMEOUT_MS
        log.event(PeerLog.TAG_AMBIENT, "isodep_connected", "since_create_ms=${SystemClock.elapsedRealtime() - createdAt} maxTransceive=${iso.maxTransceiveLength}")
        val random = SecureRandom()
        KipatPeerExchange(
            transceive = { iso.transceive(it) },
            chunkSize = KipatApduProtocol.chunkSizeFor(iso.maxTransceiveLength),
            ownProfile = own,
            ownProfileId = own.id,
            newSessionId = { ByteArray(KipatApduProtocol.SESSION_ID_BYTES).also(random::nextBytes) },
            clock = { SystemClock.elapsedRealtime() },
            log = log,
        ).run()
    } catch (e: IOException) {
        PeerExchangeResult.Failed(PeerFailure.LINK_LOST, e::class.simpleName ?: "IOException")
    } catch (e: SecurityException) {
        PeerExchangeResult.Failed(PeerFailure.LINK_LOST, "stale tag")
    } catch (e: IllegalStateException) {
        PeerExchangeResult.Failed(PeerFailure.LINK_LOST, "IllegalStateException")
    } finally {
        try { iso.close() } catch (_: IOException) {}
    }

    /** The phones moved, or the other side's encounter wasn't ready: try again on a fresh contact. */
    private fun PeerExchangeResult.retryable() = this is PeerExchangeResult.Failed &&
        (reason == PeerFailure.LINK_LOST || reason == PeerFailure.NOT_READY || reason == PeerFailure.NOT_KIPAT)

    private suspend fun reacquire(own: NfcProfilePayload) {
        log.event(PeerLog.TAG_AMBIENT, "link_lost", "fallback=reader_mode")
        onPending(TapPending.KeepTogether)
        val r = KipatPeerReader(activity, scope).also { reader = it }
        if (resumed) r.onResume()
        r.start(own, sendMyProfileBack = true)
        val timeout = scope.launch {
            delay(REACQUIRE_MS)
            r.reset()
            message("Couldn't exchange profiles. Touch the phones again.")
        }
        r.state.collect { s ->
            when (s) {
                is ReceiveState.Success -> {
                    timeout.cancel()
                    r.reset()
                    deliver(s.result)
                }
                is ReceiveState.Unsupported -> {
                    timeout.cancel()
                    message(s.reason)
                }
                else -> Unit
            }
        }
    }

    private fun deliver(result: PeerExchangeResult.Success) {
        if (result.isSelf) return message("This is your own Kipat profile.")
        log.event(PeerLog.TAG_AMBIENT, "peer_profile_received", "since_create_ms=${SystemClock.elapsedRealtime() - createdAt} down=${result.downloadedBytes} up=${result.uploadedBytes} upload=${result.upload}")
        onPeer(result.profile, result)
    }

    private fun message(text: String) {
        onPending(TapPending.Message(text))
        scope.launch {
            delay(MESSAGE_MS)
            onFinish()
        }
    }

    companion object {
        const val REACQUIRE_MS = 10_000L
        const val MESSAGE_MS = 2_800L
    }
}
