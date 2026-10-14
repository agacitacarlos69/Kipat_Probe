package com.kipat.probe

import com.kipat.probe.nfc.peer.KipatApduProcessor
import com.kipat.probe.nfc.peer.KipatApduProtocol
import com.kipat.probe.nfc.peer.KipatPeerProfile
import com.kipat.probe.nfc.peer.PeerSessionStore
import com.kipat.probe.nfc.peer.PresentState
import com.kipat.probe.nfc.peer.Sw
import com.kipat.probe.nfc.profile.NfcProfilePayload
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** Presenter (HCE) side of the Kipat APDU protocol v1, command by command. Robolectric only for org.json. */
@RunWith(RobolectricTestRunner::class)
class KipatApduProcessorTest {

    private var now = 1_000L
    private val store = PeerSessionStore(clock = { now })
    private val processor = KipatApduProcessor(store)
    private val presenter = NfcProfilePayload.SAMPLE
    private val presenterBytes = KipatPeerProfile.toBytes(presenter)
    private val readerSession = ByteArray(16) { (it + 1).toByte() }

    private fun send(apdu: ByteArray) = KipatApduProtocol.parseResponse(processor.process(apdu))!!
    private fun sw(apdu: ByteArray) = send(apdu).sw
    private fun arm() = store.arm(presenter)
    private fun selectAndHello() {
        assertEquals(Sw.OK.code, sw(KipatApduProtocol.select()))
        assertEquals(Sw.OK.code, sw(KipatApduProtocol.hello(readerSession)))
    }

    // ------------------------------------------------------------------ framing

    @Test
    fun aidIsAValidAndroidAid() {
        val hex = KipatApduProtocol.AID_HEX
        assertTrue(Regex("^[0-9A-F]+$").matches(hex))
        assertEquals(0, hex.length % 2)
        assertTrue("5–16 bytes", KipatApduProtocol.AID.size in 5..16)
        assertEquals('F', hex[0]) // proprietary, unregistered
    }

    @Test
    fun selectApduBytesAreExact() {
        assertArrayEquals(
            KipatApduProtocol.hex("00A4040007F04B495041540100"),
            KipatApduProtocol.select(),
        )
        assertArrayEquals(KipatApduProtocol.hex("8022010AC8"), KipatApduProtocol.readChunk(0x010A, 200))
        assertArrayEquals(KipatApduProtocol.hex("80340000"), KipatApduProtocol.commitUpload())
    }

    @Test
    fun statusWordsParse() {
        val r = KipatApduProtocol.parseResponse(byteArrayOf(1, 2, 0x90.toByte(), 0x00))!!
        assertTrue(r.isOk)
        assertArrayEquals(byteArrayOf(1, 2), r.data)
        assertEquals(0x6985, KipatApduProtocol.parseResponse(Sw.CONDITIONS_NOT_SATISFIED.bytes)!!.sw)
        assertEquals(null, KipatApduProtocol.parseResponse(byteArrayOf(0x90.toByte())))
        assertEquals(Sw.INVALID_DATA, Sw.of(0x6A80))
    }

    @Test
    fun selectRightAndWrongAid() {
        val ok = send(KipatApduProtocol.select())
        assertTrue(ok.isOk)
        assertArrayEquals(byteArrayOf(1), ok.data)
        assertEquals(Sw.NOT_FOUND.code, sw(KipatApduProtocol.select(KipatApduProtocol.hex("F0010203040506"))))
        // After a wrong SELECT, Kipat commands are refused.
        assertEquals(Sw.NOT_FOUND.code, sw(KipatApduProtocol.getMeta()))
    }

    @Test
    fun commandsBeforeSelectAreRefused() {
        arm()
        assertEquals(Sw.NOT_FOUND.code, sw(KipatApduProtocol.hello(readerSession)))
    }

    @Test
    fun badLengthsAndUnknownInstructions() {
        arm()
        selectAndHello()
        assertEquals(Sw.WRONG_LENGTH.code, sw(byteArrayOf(0x00)))
        assertEquals(Sw.WRONG_LENGTH.code, sw(byteArrayOf(0x80.toByte(), 0x10, 0, 0, 5, 1, 2))) // Lc says 5, 2 sent
        assertEquals(Sw.INS_NOT_SUPPORTED.code, sw(byteArrayOf(0x80.toByte(), 0x55, 0, 0)))
        assertEquals(Sw.CLA_NOT_SUPPORTED.code, sw(byteArrayOf(0x00, 0x20, 0, 0)))
    }

    // ------------------------------------------------------------------ session gating

    @Test
    fun presentNotArmedRefusesHello() {
        sw(KipatApduProtocol.select())
        assertEquals(Sw.CONDITIONS_NOT_SATISFIED.code, sw(KipatApduProtocol.hello(readerSession)))
    }

    @Test
    fun expiredSessionRefusesEverything() {
        arm()
        selectAndHello()
        now += PeerSessionStore.PRESENT_WINDOW_MS
        assertEquals(Sw.CONDITIONS_NOT_SATISFIED.code, sw(KipatApduProtocol.getMeta()))
        assertEquals(PresentState.Expired, store.state.value)
    }

    @Test
    fun cancelledSessionRefuses() {
        arm()
        selectAndHello()
        store.cancel()
        assertEquals(Sw.CONDITIONS_NOT_SATISFIED.code, sw(KipatApduProtocol.readChunk(0, 10)))
    }

    @Test
    fun helloV1AndUnsupportedVersion() {
        arm()
        sw(KipatApduProtocol.select())
        assertEquals(Sw.INVALID_DATA.code, sw(KipatApduProtocol.hello(readerSession, version = 2)))
        val r = send(KipatApduProtocol.hello(readerSession))
        assertTrue(r.isOk)
        assertEquals(KipatApduProtocol.HELLO_RESPONSE_BYTES, r.data.size)
        assertEquals(1.toByte(), r.data[0])
        assertEquals(0x03, r.data.last().toInt()) // download + upload
        assertTrue(store.state.value is PresentState.PeerDetected)
    }

    @Test
    fun metaBeforeHelloIsRefused() {
        arm()
        sw(KipatApduProtocol.select())
        assertEquals(Sw.CONDITIONS_NOT_SATISFIED.code, sw(KipatApduProtocol.getMeta()))
    }

    // ------------------------------------------------------------------ download

    @Test
    fun metadataCarriesLengthHashAndType() {
        arm()
        selectAndHello()
        val r = send(KipatApduProtocol.getMeta())
        assertEquals(KipatApduProtocol.META_RESPONSE_BYTES, r.data.size)
        assertEquals(1.toByte(), r.data[0])
        assertEquals(presenterBytes.size, ((r.data[1].toInt() and 0xFF) shl 8) or (r.data[2].toInt() and 0xFF))
        assertArrayEquals(KipatApduProtocol.sha256(presenterBytes), r.data.copyOfRange(3, 35))
        assertEquals(1.toByte(), r.data[35]) // person
    }

    @Test
    fun readFirstMiddleAndFinalChunks() {
        arm()
        selectAndHello()
        val n = presenterBytes.size
        assertArrayEquals(presenterBytes.copyOfRange(0, 100), send(KipatApduProtocol.readChunk(0, 100)).data)
        assertArrayEquals(presenterBytes.copyOfRange(100, 200), send(KipatApduProtocol.readChunk(100, 100)).data)
        // The last read asks for more than remains and gets just the tail.
        assertArrayEquals(presenterBytes.copyOfRange(200, n), send(KipatApduProtocol.readChunk(200, 200)).data)
    }

    @Test
    fun readOutOfBoundsAndTooLong() {
        arm()
        selectAndHello()
        assertEquals(Sw.WRONG_P1P2.code, sw(KipatApduProtocol.readChunk(presenterBytes.size, 10)))
        assertEquals(Sw.WRONG_P1P2.code, sw(KipatApduProtocol.readChunk(0xFFFF, 10)))
        assertEquals(Sw.WRONG_LENGTH.code, sw(KipatApduProtocol.readChunk(0, KipatApduProtocol.MAX_CHUNK + 1)))
        assertEquals(Sw.WRONG_LENGTH.code, sw(byteArrayOf(0x80.toByte(), 0x22, 0, 0, 0)))
    }

    // ------------------------------------------------------------------ upload

    private val peer = NfcProfilePayload(id = "KPT-B", displayName = "José Dela Cruz", headline = "BS Architecture")
    private val peerBytes = KipatPeerProfile.toBytes(peer)

    private fun begin(bytes: ByteArray = peerBytes, hash: ByteArray = KipatApduProtocol.sha256(bytes), length: Int = bytes.size) =
        sw(KipatApduProtocol.beginUpload(length, hash))

    private fun writeAll(bytes: ByteArray = peerBytes, chunk: Int = 20) {
        var o = 0
        while (o < bytes.size) {
            val part = bytes.copyOfRange(o, minOf(bytes.size, o + chunk))
            assertEquals(Sw.OK.code, sw(KipatApduProtocol.writeChunk(o, part)))
            o += part.size
        }
    }

    @Test
    fun beginValidAndOversized() {
        arm()
        selectAndHello()
        assertEquals(Sw.WRONG_LENGTH.code, begin(length = KipatApduProtocol.MAX_PROFILE_BYTES + 1))
        assertEquals(Sw.WRONG_LENGTH.code, begin(length = 0))
        assertEquals(Sw.OK.code, begin())
        assertEquals(PresentState.ReceivingPeerProfile, store.state.value)
    }

    @Test
    fun writeBeforeBeginIsRefused() {
        arm()
        selectAndHello()
        assertEquals(Sw.CONDITIONS_NOT_SATISFIED.code, sw(KipatApduProtocol.writeChunk(0, byteArrayOf(1))))
    }

    @Test
    fun duplicateAndOutOfOrderChunksLandByOffset() {
        arm()
        selectAndHello()
        begin()
        val mid = peerBytes.size / 2
        // Second half first, then the first half, then a retry of the first half.
        assertEquals(Sw.OK.code, sw(KipatApduProtocol.writeChunk(mid, peerBytes.copyOfRange(mid, peerBytes.size))))
        assertEquals(Sw.OK.code, sw(KipatApduProtocol.writeChunk(0, peerBytes.copyOfRange(0, mid))))
        assertEquals(Sw.OK.code, sw(KipatApduProtocol.writeChunk(0, peerBytes.copyOfRange(0, mid))))
        assertEquals(Sw.OK.code, sw(KipatApduProtocol.commitUpload()))
        assertEquals(peer, (store.state.value as PresentState.Success).peer)
    }

    @Test
    fun offsetOverflowIsRejected() {
        arm()
        selectAndHello()
        begin()
        assertEquals(Sw.WRONG_P1P2.code, sw(KipatApduProtocol.writeChunk(peerBytes.size - 1, byteArrayOf(1, 2))))
        assertEquals(Sw.WRONG_P1P2.code, sw(KipatApduProtocol.writeChunk(0xFFFF, byteArrayOf(1))))
    }

    @Test
    fun commitIncompleteUpload() {
        arm()
        selectAndHello()
        begin()
        sw(KipatApduProtocol.writeChunk(0, peerBytes.copyOfRange(0, 10)))
        assertEquals(Sw.CONDITIONS_NOT_SATISFIED.code, sw(KipatApduProtocol.commitUpload()))
        assertTrue(store.state.value !is PresentState.Success)
    }

    @Test
    fun commitWrongHash() {
        arm()
        selectAndHello()
        begin(hash = ByteArray(32))
        writeAll()
        assertEquals(Sw.INVALID_DATA.code, sw(KipatApduProtocol.commitUpload()))
        assertTrue(store.state.value !is PresentState.Success)
    }

    @Test
    fun commitMalformedJson() {
        val junk = "{\"v\":1,\"name\":".toByteArray()
        arm()
        selectAndHello()
        begin(junk)
        writeAll(junk)
        assertEquals(Sw.INVALID_DATA.code, sw(KipatApduProtocol.commitUpload()))
        assertTrue(store.state.value !is PresentState.Success)
    }

    @Test
    fun commitValidProfileIsDeliveredOnce() {
        arm()
        selectAndHello()
        begin()
        writeAll()
        assertEquals(Sw.OK.code, sw(KipatApduProtocol.commitUpload()))
        val success = store.state.value as PresentState.Success
        assertEquals(peer, success.peer)
        assertTrue(store.claimReveal(success.exchangeId))
        assertTrue("one Reveal per exchange", !store.claimReveal(success.exchangeId))
        // COMPLETE ends the sharing window.
        assertEquals(Sw.OK.code, sw(KipatApduProtocol.complete()))
        assertEquals(null, store.activeSession())
        assertEquals(Sw.CONDITIONS_NOT_SATISFIED.code, sw(KipatApduProtocol.getMeta()))
    }

    @Test
    fun partialUploadClearedOnDeactivation() {
        arm()
        selectAndHello()
        begin()
        sw(KipatApduProtocol.writeChunk(0, peerBytes.copyOfRange(0, 10)))
        processor.onDeactivated(linkLost = true)
        // Back to Ready (window still open) and the half upload is gone: a new link must start over.
        assertTrue(store.state.value is PresentState.Ready)
        selectAndHello()
        assertEquals(Sw.CONDITIONS_NOT_SATISFIED.code, sw(KipatApduProtocol.writeChunk(10, peerBytes.copyOfRange(10, 20))))
        assertEquals(Sw.CONDITIONS_NOT_SATISFIED.code, sw(KipatApduProtocol.commitUpload()))
    }

    @Test
    fun committedProfileSurvivesLinkLoss() {
        arm()
        selectAndHello()
        begin()
        writeAll()
        sw(KipatApduProtocol.commitUpload())
        processor.onDeactivated(linkLost = true)
        assertEquals(peer, (store.state.value as PresentState.Success).peer)
    }

    @Test
    fun oneWayCompleteWithoutUpload() {
        arm()
        selectAndHello()
        sw(KipatApduProtocol.complete())
        val s = store.state.value as PresentState.Success
        assertEquals(null, s.peer)
    }

    @Test
    fun selfProfileIsFlagged() {
        arm()
        selectAndHello()
        val me = KipatPeerProfile.toBytes(presenter)
        begin(me)
        writeAll(me)
        sw(KipatApduProtocol.commitUpload())
        assertTrue((store.state.value as PresentState.Success).isSelf)
    }

    @Test
    fun oversizedOwnProfileIsNotArmed() {
        val huge = presenter.copy(bio = "é".repeat(500), organization = "東".repeat(200), headline = "東".repeat(160), role = "東".repeat(120),
            website = "https://example.com/" + "a".repeat(450), github = "https://github.com/" + "b".repeat(450),
            linkedin = "https://linkedin.com/in/" + "c".repeat(450), avatarUrl = "https://example.com/" + "d".repeat(450))
        val result = store.arm(huge)
        assertTrue(result is PeerSessionStore.ArmResult.TooLarge)
        assertEquals(null, store.activeSession())
        assertEquals(PresentState.Error("Profile too large for nearby NFC sharing."), store.state.value)
    }
}
