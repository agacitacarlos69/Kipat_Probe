package com.kipat.probe

import com.kipat.probe.nfc.peer.KipatApduProcessor
import com.kipat.probe.nfc.peer.KipatPeerExchange
import com.kipat.probe.nfc.peer.PeerExchangeResult
import com.kipat.probe.nfc.peer.PeerLog
import com.kipat.probe.nfc.peer.PeerSessionStore
import com.kipat.probe.nfc.peer.PresentState
import com.kipat.probe.nfc.peer.UploadOutcome
import com.kipat.probe.nfc.profile.NfcProfilePayload
import com.kipat.probe.pilot.PilotReport
import com.kipat.probe.pilot.PilotTracker
import com.kipat.probe.pilot.StageResult
import com.kipat.probe.pilot.TapRoleSeen
import com.kipat.probe.reveal.RevealTemplateId
import com.kipat.probe.reveal.RevealTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Experiments A and B of the NFC feasibility decision gate, in simulation: the Developer controlled
 * test (one phone explicitly presents over HCE, the other is an explicit reader-mode reader), both
 * directions. Same code paths as the screen: PeerSessionStore.arm + KipatApduProcessor on the
 * presenter, KipatPeerExchange with send-back on the reader. Not evidence of radio behaviour.
 */
@RunWith(RobolectricTestRunner::class)
class KipatControlledExchangeTest {

    private var now = 900_000L
    private val kyle = NfcProfilePayload(id = "KPT-KYLE0000000A", displayName = "Kyle Jimenez", reveal = RevealTemplateId.PRECISION_ORBIT, accent = RevealTheme.NIGHT)
    private val randy = NfcProfilePayload(id = "KPT-RANDY000000B", displayName = "Randy Orton", reveal = RevealTemplateId.CONTACT_RIPPLE, accent = RevealTheme.EMBER)

    private inner class Phone(val profile: NfcProfilePayload) {
        val pilot = PilotTracker(monoClock = { now }, wallClock = { now })
        val log = PeerLog { tag, name, detail -> pilot.onEvent(name, detail, tag) }
        val store = PeerSessionStore(clock = { now }, log = log)
        val processor = KipatApduProcessor(store, log)

        fun actAsPresenter() {
            log.event(PeerLog.TAG_SESSION, "controlled_test_started", "role=presenter")
            store.arm(profile)
        }

        fun actAsReader(other: Phone): PeerExchangeResult {
            log.event(PeerLog.TAG_SESSION, "controlled_test_started", "role=reader")
            var seq = profile.displayName.length
            return KipatPeerExchange(
                transceive = { other.processor.process(it) }, chunkSize = 64, ownProfile = profile, ownProfileId = profile.id,
                newSessionId = { ByteArray(16) { (it + ++seq).toByte() } }, clock = { now }, log = log,
            ).run()
        }
    }

    private fun experiment(presenter: Phone, reader: Phone) {
        presenter.actAsPresenter()
        val r = reader.actAsReader(presenter) as PeerExchangeResult.Success
        // The reader has the presenter's profile; the presenter's COMMIT and FINALIZE were answered.
        assertEquals(presenter.profile, r.profile)
        assertEquals(UploadOutcome.SENT, r.upload)
        assertTrue(r.finalized)
        // The presenter has the reader's profile (what its screen opens a Reveal for).
        assertEquals(reader.profile, (presenter.store.state.value as PresentState.Success).peer)

        val a = reader.pilot.lastControlled(TapRoleSeen.INITIATOR)!!
        val b = presenter.pilot.lastControlled(TapRoleSeen.RESPONDER)!!
        PilotReport.CONTROLLED_STAGES.filter { it.name != "REVEAL_REQUESTED" }.forEach { s ->
            assertEquals("reader $s", StageResult.PASS, a.result(s))
            assertEquals("presenter $s", StageResult.PASS, b.result(s))
        }
        assertTrue(a.mutual && b.mutual)
        assertEquals("both exports name the same exchange", a.correlation, b.correlation)
        assertTrue(a.controlled && b.controlled)
        // Controlled runs never count as ambient evidence.
        listOf(reader, presenter).forEach { p ->
            val e = p.pilot.ambientEvidence()
            assertFalse(e.hceServiceCreated || e.ndefSelectReceived || e.customAidSelected || e.kipatDispatchedHere)
            assertEquals(null, p.pilot.lastAmbient())
        }
    }

    @Test
    fun experimentA_kylePresents_randyReads() = experiment(presenter = Phone(kyle), reader = Phone(randy))

    @Test
    fun experimentB_rolesSwapped() = experiment(presenter = Phone(randy), reader = Phone(kyle))
}
