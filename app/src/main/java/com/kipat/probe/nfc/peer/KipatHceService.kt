package com.kipat.probe.nfc.peer

import android.content.Context
import android.content.pm.PackageManager
import android.nfc.NfcAdapter
import android.nfc.cardemulation.HostApduService
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.Process
import android.os.SystemClock
import android.util.Log
import com.kipat.probe.pilot.PilotStore
import com.kipat.probe.data.KipatIdentityStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Responder side of Kipat Tap. Android binds this service when another phone selects one of our
 * AIDs (res/xml/kipat_apdu_service.xml: category "other", requireDeviceUnlock) — Kipat does not
 * need to be open, and the process is started if it isn't running.
 *
 * - `D2760000850101`: read-only NFC Forum Type 4 tag holding the tiny bootstrap NDEF, so the other
 *   phone's Android launches Kipat ([KipatType4Tag]).
 * - `F04B4950415401`: the Kipat protocol ([KipatApduProcessor]).
 *
 * Thin by design. Profile data is served only while Kipat Tap is eligible (enabled, public profile
 * published, device unlocked) or a developer armed manual mode. The snapshot is loaded in
 * [onCreate], so the APDU path never touches the network, images or a database.
 */
class KipatHceService : HostApduService() {
    private var createdAt = 0L
    private var firstApdu = true
    private val router by lazy {
        KipatHceRouter(
            KipatType4Tag(KipatPeerSessions.presenter, packageName, KipatPeerSessions.log),
            KipatApduProcessor(KipatPeerSessions.presenter, KipatPeerSessions.log),
        )
    }

    override fun onCreate() {
        super.onCreate()
        val start = SystemClock.elapsedRealtime()
        KipatIdentityStore.init(this)
        KipatPeerSessions.attach(this)
        KipatTapStore.snapshot(this) // warm the public snapshot before the first APDU
        createdAt = SystemClock.elapsedRealtime()
        // Android's NDEF check waits about 1 s for each answer (libnfc-nci RW_T4T_TOUT_RESP), and the
        // first SELECT waits for this process to start: record how close a cold start comes.
        val sinceProcess = createdAt - Process.getStartElapsedRealtime()
        KipatPeerSessions.log.event(
            PeerLog.TAG_HCE, "hce_service_created",
            "cold=${sinceProcess < COLD_START_MS} since_process_ms=$sinceProcess create_ms=${createdAt - start}",
        )
    }

    override fun processCommandApdu(commandApdu: ByteArray?, extras: Bundle?): ByteArray {
        if (firstApdu) {
            firstApdu = false
            KipatPeerSessions.log.event(PeerLog.TAG_HCE, "hce_first_apdu", "ms=${SystemClock.elapsedRealtime() - createdAt}")
        }
        return router.process(commandApdu)
    }

    override fun onDeactivated(reason: Int) {
        router.onDeactivated(linkLost = reason == DEACTIVATION_LINK_LOSS)
    }

    private companion object {
        /** A service created this soon after its process started was a cold start. */
        const val COLD_START_MS = 3_000L
    }
}

/** Process-wide phone-to-phone state, shared by the UI, [KipatHceService] and the Reveal. */
object KipatPeerSessions {
    val log = PeerLog { tag, name, detail ->
        Log.i(tag, if (detail.isEmpty()) name else "$name $detail")
        // Physical pilot: the same content-free events, kept in a bounded on-device buffer.
        PilotStore.record(name, detail, tag)
    }

    @Volatile private var appContext: Context? = null

    /** Gives the ambient source a Context; called by the service, MainActivity and the Reveal. */
    fun attach(context: Context) {
        appContext = context.applicationContext
        PilotStore.attach(context)
        presenter.ambientListener = PeerDeliveryListener { peer, presenter, reader, bytes ->
            appContext?.let { KipatTapLauncher.deliverAsResponder(it, peer, presenter, reader, bytes) }
        }
    }

    private val main = Handler(Looper.getMainLooper())

    val presenter = PeerSessionStore(
        clock = { SystemClock.elapsedRealtime() },
        log = log,
        ambientProfile = { appContext?.let { KipatTapStore.eligibleProfile(it) } },
        ambientRefusal = { appContext?.let { KipatTapStore.refusal(it) } ?: "no_context" },
        post = { main.post(it) },
    )

    /** Exactly-once hand-off from either role to the Reveal. */
    val deliveries = IncomingKipatExchangeStore(clock = { SystemClock.elapsedRealtime() })

    /** Receiver side: the same exchange completing twice in quick succession is one discovery. */
    val receiverDedupe = PeerExchangeDeduper()

    private val _receiverDiagnostics = MutableStateFlow<PeerDiagnostics?>(null)
    val receiverDiagnostics: StateFlow<PeerDiagnostics?> = _receiverDiagnostics.asStateFlow()

    fun recordReceiverDiagnostics(r: PeerExchangeResult.Success) {
        _receiverDiagnostics.value = PeerDiagnostics(
            role = "Receiver",
            protocolVersion = KipatApduProtocol.PROTOCOL_VERSION.toInt(),
            peerName = r.profile.displayName,
            downloadedBytes = r.downloadedBytes,
            uploadedBytes = r.uploadedBytes,
            integrity = "Passed",
            durationMs = r.durationMs,
            session = "${r.presenterSession.take(8)}/${r.readerSession.take(8)}",
            result = when (r.upload) {
                UploadOutcome.SENT -> "Exchanged"
                UploadOutcome.NOT_REQUESTED -> "Received (one way)"
                UploadOutcome.SKIPPED_OWN_PROFILE -> "Own profile"
                else -> "Received; return trip ${r.upload.name.lowercase().replace('_', ' ')}"
            },
        )
    }

    fun capabilities(context: Context): KipatPeerCapabilities {
        val pm = context.packageManager
        val adapter = NfcAdapter.getDefaultAdapter(context)
        val hasNfc = pm.hasSystemFeature(PackageManager.FEATURE_NFC) && adapter != null
        return KipatPeerCapabilities(
            hasNfc = hasNfc,
            nfcEnabled = adapter?.isEnabled == true,
            hasHce = hasNfc && pm.hasSystemFeature(PackageManager.FEATURE_NFC_HOST_CARD_EMULATION),
        )
    }
}
