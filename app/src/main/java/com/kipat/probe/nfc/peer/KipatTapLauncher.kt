package com.kipat.probe.nfc.peer

import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import com.kipat.probe.data.KipatRepository
import com.kipat.probe.data.NfcProfileReveal.toRevealPayload
import com.kipat.probe.nfc.peer.KipatApduProtocol.shortId
import com.kipat.probe.nfc.profile.NfcProfilePayload
import com.kipat.probe.reveal.RevealLauncher
import com.kipat.probe.reveal.RevealPayload
import com.kipat.probe.reveal.RevealSource

/**
 * Turns a validated peer profile into exactly one Reveal on this phone.
 *
 * Responder: called from [KipatHceService] while the NFC stack still has it bound. The stack binds
 * HCE services with BIND_ALLOW_BACKGROUND_ACTIVITY_STARTS (AOSP HostEmulationManager), so the
 * Reveal may start above whatever app is in front. The launch is explicit (TapExperienceActivity,
 * nonce-protected payload) — NFC data can never start anything else.
 * If an OEM blocks it anyway, the person is still recorded under Recent on Home; no overlay
 * permission, accessibility service or full-screen notification is used.
 */
object KipatTapLauncher {
    const val EXTRA_EXCHANGE_ID = "kipat.tap.exchangeId"
    private const val WATCHDOG_MS = 2_000L
    private val main = Handler(Looper.getMainLooper())

    fun payloadFor(peer: NfcProfilePayload): RevealPayload = peer.toRevealPayload(RevealSource.PEER_NFC, testProfile = false)

    fun deliverAsResponder(context: Context, peer: NfcProfilePayload, presenter: ByteArray, reader: ByteArray, uploadedBytes: Int) {
        val log = KipatPeerSessions.log
        val own = KipatTapStore.snapshot(context)?.profile
        if (KipatPeerProfile.isSamePerson(peer, own?.id)) {
            log.event(PeerLog.TAG_LAUNCH, "responder_reveal_skipped", "reason=own_profile")
            return
        }
        val delivery = KipatPeerSessions.deliveries.offer(peer, TapRole.RESPONDER) ?: run {
            log.event(PeerLog.TAG_LAUNCH, "duplicate_exchange_ignored", "role=responder session=${presenter.shortId()}")
            return
        }
        val payload = payloadFor(peer)
        // Discovery only: a recent tap, never a connection.
        KipatRepository.recordTap(payload)
        val intent = RevealLauncher.intent(context, payload)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            .putExtra(EXTRA_EXCHANGE_ID, delivery.exchangeId)
        try {
            context.startActivity(intent)
            log.event(PeerLog.TAG_LAUNCH, "responder_reveal_requested", "exchange=${delivery.exchangeId} session=${presenter.shortId()}/${reader.shortId()} bytes=$uploadedBytes")
        } catch (e: RuntimeException) {
            log.event(PeerLog.TAG_LAUNCH, "background_launch_blocked", "exchange=${delivery.exchangeId} ${e::class.simpleName}")
            return
        }
        // Android reports a blocked background start only in its own log; check that the Reveal appeared.
        main.postDelayed({
            val shown = KipatPeerSessions.deliveries.wasShown(delivery.exchangeId)
            log.event(PeerLog.TAG_LAUNCH, if (shown) "background_launch_allowed" else "background_launch_blocked", "exchange=${delivery.exchangeId}")
        }, WATCHDOG_MS)
    }
}
