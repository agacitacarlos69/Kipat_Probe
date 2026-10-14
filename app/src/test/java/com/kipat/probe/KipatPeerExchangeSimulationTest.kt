package com.kipat.probe

import com.kipat.probe.data.KipatRepository
import com.kipat.probe.data.NfcProfileReveal.toRevealPayload
import com.kipat.probe.model.AuthenticityState
import com.kipat.probe.nfc.peer.KipatApduProcessor
import com.kipat.probe.nfc.peer.KipatApduProtocol
import com.kipat.probe.nfc.peer.KipatPeerExchange
import com.kipat.probe.nfc.peer.KipatPeerProfile
import com.kipat.probe.nfc.peer.PeerExchangeDeduper
import com.kipat.probe.nfc.peer.PeerExchangeResult
import com.kipat.probe.nfc.peer.PeerFailure
import com.kipat.probe.nfc.peer.PeerSessionStore
import com.kipat.probe.nfc.peer.PresentState
import com.kipat.probe.nfc.peer.ReceiveProgress
import com.kipat.probe.nfc.peer.Sw
import com.kipat.probe.nfc.peer.UploadOutcome
import com.kipat.probe.nfc.profile.NfcProfilePayload
import com.kipat.probe.reveal.RevealSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.IOException

/**
 * PROTOCOL SIMULATION — not a device test. Phone B's reader logic ([KipatPeerExchange]) talks
 * directly to phone A's presenter logic ([KipatApduProcessor]) through an in-memory transceive.
 * It proves the protocol and both state machines; it does not prove radio behaviour on hardware.
 */
@RunWith(RobolectricTestRunner::class)
class KipatPeerExchangeSimulationTest {

    private var now = 10_000L
    private val phoneA = NfcProfilePayload.SAMPLE // Vash Puno
    private val phoneB = NfcProfilePayload(
        id = "KPT-DEMO-0103", displayName = "José Dela Cruz", headline = "BS Architecture",
        organization = "University of Santo Tomas", bio = ("Design student. " + "Builds models. ".repeat(20)).trim(),
    )
    private val storeA = PeerSessionStore(clock = { now })
    private val processorA = KipatApduProcessor(storeA)
    private var seq = 0

    private fun exchange(
        own: NfcProfilePayload? = phoneB,
        ownId: String? = phoneB.id,
        chunk: Int = 180,
        transceive: (ByteArray) -> ByteArray = { processorA.process(it) },
        progress: MutableList<ReceiveProgress> = mutableListOf(),
    ) = KipatPeerExchange(
        transceive = transceive,
        chunkSize = chunk,
        ownProfile = own,
        ownProfileId = ownId,
        newSessionId = { ByteArray(16) { (it + ++seq).toByte() } },
        clock = { now },
        onProgress = { progress += it },
    ).run()

    @Test
    fun mutualExchangeDeliversBothProfilesIntact() {
        storeA.arm(phoneA)
        val progress = mutableListOf<ReceiveProgress>()
        val result = exchange(progress = progress) as PeerExchangeResult.Success

        // B received A, byte-for-byte equal after parse.
        assertEquals(phoneA, result.profile)
        assertEquals(KipatPeerProfile.toBytes(phoneA).size, result.downloadedBytes)
        // A received B.
        assertEquals(UploadOutcome.SENT, result.upload)
        assertEquals(KipatPeerProfile.toBytes(phoneB).size, result.uploadedBytes)
        assertTrue("B's profile needs several chunks", result.uploadedBytes > 180)
        val a = storeA.state.value as PresentState.Success
        assertEquals(phoneB, a.peer)
        assertFalse(a.isSelf)
        assertEquals(listOf(ReceiveProgress.KIPAT_DETECTED, ReceiveProgress.READING_PROFILE, ReceiveProgress.SENDING_OWN_PROFILE), progress)
        // COMPLETE closed A's sharing window.
        assertEquals(null, storeA.activeSession())
        assertEquals("Presenter (manual)", storeA.diagnostics.value?.role)
    }

    @Test
    fun everyChunkSizeWorks() {
        listOf(16, 50, 64, 180, 200, 255).forEach { chunk ->
            storeA.arm(phoneA)
            val r = exchange(chunk = chunk) as PeerExchangeResult.Success
            assertEquals("chunk $chunk", phoneA, r.profile)
            assertEquals(phoneB, (storeA.state.value as PresentState.Success).peer)
        }
        assertEquals(200, KipatApduProtocol.chunkSizeFor(261))
        assertEquals(26, KipatApduProtocol.chunkSizeFor(32))
    }

    @Test
    fun oneWayReceiveLeavesPresenterWithoutPeer() {
        storeA.arm(phoneA)
        val r = exchange(own = null) as PeerExchangeResult.Success
        assertEquals(phoneA, r.profile)
        assertEquals(UploadOutcome.NOT_REQUESTED, r.upload)
        assertEquals(0, r.uploadedBytes)
        val a = storeA.state.value as PresentState.Success
        assertEquals(null, a.peer)
    }

    @Test
    fun sameProfileOnBothPhonesIsRecognised() {
        storeA.arm(phoneA)
        val r = exchange(own = phoneA, ownId = phoneA.id) as PeerExchangeResult.Success
        assertTrue(r.isSelf)
        assertEquals(UploadOutcome.SKIPPED_OWN_PROFILE, r.upload)
    }

    @Test
    fun presenterNotArmedOrExpired() {
        assertEquals(PeerFailure.NOT_READY, (exchange() as PeerExchangeResult.Failed).reason)
        storeA.arm(phoneA)
        now += PeerSessionStore.PRESENT_WINDOW_MS + 1
        assertEquals(PeerFailure.NOT_READY, (exchange() as PeerExchangeResult.Failed).reason)
        assertEquals(PresentState.Expired, storeA.state.value)
    }

    @Test
    fun sessionExpiringMidExchangeFailsCleanly() {
        storeA.arm(phoneA)
        var calls = 0
        val r = exchange(transceive = { cmd ->
            if (++calls == 4) now += PeerSessionStore.PRESENT_WINDOW_MS // expires during the download
            processorA.process(cmd)
        })
        assertEquals(PeerFailure.NOT_READY, (r as PeerExchangeResult.Failed).reason)
    }

    @Test
    fun nonKipatPhoneIsRejectedAtSelect() {
        val r = exchange(transceive = { Sw.NOT_FOUND.bytes })
        assertEquals(PeerFailure.NOT_KIPAT, (r as PeerExchangeResult.Failed).reason)
    }

    @Test
    fun phonesMovedApartMapToLinkLost() {
        storeA.arm(phoneA)
        var calls = 0
        val r = exchange(transceive = { cmd -> if (++calls == 4) throw IOException("Tag was lost.") else processorA.process(cmd) })
        val f = r as PeerExchangeResult.Failed
        assertEquals(PeerFailure.LINK_LOST, f.reason)
        assertEquals("Keep the phones together and try again.", f.reason.message)
    }

    @Test
    fun linkLostDuringUploadStillKeepsReceivedProfile() {
        storeA.arm(phoneA)
        val r = exchange(transceive = { cmd ->
            if (cmd[1] == KipatApduProtocol.INS_WRITE_CHUNK) throw IOException("Tag was lost.") else processorA.process(cmd)
        }) as PeerExchangeResult.Success
        assertEquals(phoneA, r.profile)
        assertEquals(UploadOutcome.FAILED, r.upload)
        assertTrue(storeA.state.value !is PresentState.Success || (storeA.state.value as PresentState.Success).peer == null)
    }

    @Test
    fun corruptedChunkFailsIntegrityAndNeverRenders() {
        storeA.arm(phoneA)
        val r = exchange(transceive = { cmd ->
            val resp = processorA.process(cmd)
            if (cmd[1] == KipatApduProtocol.INS_READ_CHUNK && resp.size > 10) resp.also { it[3] = (it[3] + 1).toByte() } else resp
        })
        assertEquals(PeerFailure.INTEGRITY, (r as PeerExchangeResult.Failed).reason)
    }

    @Test
    fun presenterClaimingHugeProfileIsRefusedBeforeDownload() {
        val r = exchange(transceive = { cmd ->
            when (cmd[1]) {
                KipatApduProtocol.INS_SELECT -> KipatApduProtocol.ok(byteArrayOf(1))
                KipatApduProtocol.INS_HELLO -> KipatApduProtocol.ok(byteArrayOf(1) + ByteArray(16) + byteArrayOf(3))
                KipatApduProtocol.INS_GET_META -> KipatApduProtocol.ok(byteArrayOf(1, 0x40, 0x01) + ByteArray(32) + byteArrayOf(1))
                else -> error("must not read")
            }
        })
        assertEquals(PeerFailure.TOO_LARGE, (r as PeerExchangeResult.Failed).reason)
    }

    @Test
    fun malformedPeerProfileIsInvalidNotACrash() {
        val junk = "{\"v\":1,\"name\":".toByteArray()
        val hash = KipatApduProtocol.sha256(junk)
        val r = exchange(transceive = { cmd ->
            when (cmd[1]) {
                KipatApduProtocol.INS_SELECT -> KipatApduProtocol.ok(byteArrayOf(1))
                KipatApduProtocol.INS_HELLO -> KipatApduProtocol.ok(byteArrayOf(1) + ByteArray(16) + byteArrayOf(3))
                KipatApduProtocol.INS_GET_META -> KipatApduProtocol.ok(byteArrayOf(1, 0, junk.size.toByte()) + hash + byteArrayOf(1))
                KipatApduProtocol.INS_READ_CHUNK -> KipatApduProtocol.ok(junk)
                else -> Sw.OK.bytes
            }
        })
        assertEquals(PeerFailure.INVALID_PROFILE, (r as PeerExchangeResult.Failed).reason)
    }

    @Test
    fun unsupportedPresenterVersion() {
        val r = exchange(transceive = { cmd ->
            when (cmd[1]) {
                KipatApduProtocol.INS_SELECT -> KipatApduProtocol.ok(byteArrayOf(2))
                KipatApduProtocol.INS_HELLO -> KipatApduProtocol.ok(byteArrayOf(2) + ByteArray(16) + byteArrayOf(3))
                else -> error("stop")
            }
        })
        assertEquals(PeerFailure.UNSUPPORTED_VERSION, (r as PeerExchangeResult.Failed).reason)
    }

    @Test
    fun repeatedExchangeIsOneDiscovery() {
        storeA.arm(phoneA)
        val first = exchange() as PeerExchangeResult.Success
        val dedupe = PeerExchangeDeduper(windowMs = 10_000)
        assertTrue(dedupe.firstTime(first.dedupeKey, now))
        assertFalse(dedupe.firstTime(first.dedupeKey, now + 500))
        assertTrue(dedupe.firstTime(first.dedupeKey, now + 20_000))
    }

    @Test
    fun receivedProfileBecomesANearbyUnverifiedRevealAndDoesNotConnect() {
        storeA.arm(phoneA)
        val r = exchange() as PeerExchangeResult.Success
        val payload = r.profile.toRevealPayload(RevealSource.PEER_NFC, testProfile = false)
        assertEquals("Vash Puno", payload.displayName)
        assertEquals(AuthenticityState.UNVERIFIED, payload.authenticity)
        assertTrue(payload.isNearbyPeer)
        assertFalse(payload.testProfile)
        assertTrue(payload.canConnect)
        // Exchanging is discovery only: no connection exists until the person taps Connect.
        assertFalse(KipatRepository.isConnected(payload.cardId))
    }
}
