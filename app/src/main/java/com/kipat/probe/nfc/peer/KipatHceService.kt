package com.kipat.probe.nfc.peer

import android.content.Context
import android.content.pm.PackageManager
import android.nfc.NfcAdapter
import android.nfc.cardemulation.HostApduService
import android.os.Bundle
import android.os.SystemClock
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Presenter role ("Present my Kipat"): Android routes APDUs for the Kipat AID here while the phone
 * is unlocked (res/xml/kipat_apdu_service.xml: category "other", requireDeviceUnlock).
 *
 * Thin by design: all protocol logic lives in [KipatApduProcessor]. It serves profile data only
 * while the user has armed a Present session; otherwise every Kipat command returns 6985.
 * Nothing here touches the network, disk, images or a database.
 */
class KipatHceService : HostApduService() {
    private val processor by lazy { KipatApduProcessor(KipatPeerSessions.presenter, KipatPeerSessions.log) }

    override fun processCommandApdu(commandApdu: ByteArray?, extras: Bundle?): ByteArray =
        processor.process(commandApdu)

    override fun onDeactivated(reason: Int) {
        processor.onDeactivated(linkLost = reason == DEACTIVATION_LINK_LOSS)
    }
}

/** Process-wide phone-to-phone state, shared by the Share UI and [KipatHceService]. */
object KipatPeerSessions {
    val log = PeerLog { tag, name, detail -> Log.i(tag, if (detail.isEmpty()) name else "$name $detail") }
    val presenter = PeerSessionStore(clock = { SystemClock.elapsedRealtime() }, log = log)

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
