package com.kipat.probe

import com.kipat.probe.data.PublicKipatProfileSnapshot
import android.nfc.NdefMessage
import com.kipat.probe.nfc.peer.IncomingKipatExchangeStore
import com.kipat.probe.nfc.peer.KipatApduProcessor
import com.kipat.probe.nfc.peer.KipatApduProtocol
import com.kipat.probe.nfc.peer.KipatApduProtocol.toHex
import com.kipat.probe.nfc.peer.KipatHceRouter
import com.kipat.probe.nfc.peer.KipatPeerBootstrap
import com.kipat.probe.nfc.peer.KipatPeerCapabilities
import com.kipat.probe.nfc.peer.KipatPeerExchange
import com.kipat.probe.nfc.peer.KipatTapAvailability
import com.kipat.probe.nfc.peer.KipatType4Tag
import com.kipat.probe.nfc.peer.PeerDeliveryListener
import com.kipat.probe.nfc.peer.PeerExchangeResult
import com.kipat.probe.nfc.peer.PeerSessionStore
import com.kipat.probe.nfc.peer.PresentState
import com.kipat.probe.nfc.peer.Sw
import com.kipat.probe.nfc.peer.TapRole
import com.kipat.probe.nfc.peer.UploadOutcome
import com.kipat.probe.nfc.profile.NfcProfilePayload
import com.kipat.probe.nfc.toData
import com.kipat.probe.nfc.toMessage
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Ambient Kipat Tap: NFC Forum Type 4 bootstrap, encounter sessions, exactly-once delivery and the
 * PROTOCOL SIMULATION of an Android-like NDEF read upgrading to the Kipat AID on the same link.
 * Simulation only — not evidence that two phones do this (docs/nfc/ambient-peer-feasibility.md).
 */
@RunWith(RobolectricTestRunner::class)
class KipatAmbientTapTest {

    private var now = 100_000L
    private val pkg = "com.kipat.probe"
    private val phoneA = NfcProfilePayload.SAMPLE
    private val phoneB = NfcProfilePayload(id = "KPT-DEMO-0103", displayName = "José Dela Cruz", headline = "BS Architecture", organization = "University of Santo Tomas")
    private var eligible = true
    private val delivered = mutableListOf<NfcProfilePayload>()
    private val storeA = PeerSessionStore(clock = { now }, ambientProfile = { if (eligible) PublicKipatProfileSnapshot.of(phoneA) else null }).also {
        it.ambientListener = PeerDeliveryListener { peer, _, _, _ -> delivered += peer }
    }
    private val type4 = KipatType4Tag(storeA, pkg)
    private val routerA = KipatHceRouter(type4, KipatApduProcessor(storeA))

    private fun hex(s: String) = KipatApduProtocol.hex(s)
    private fun send(apdu: String) = KipatApduProtocol.parseResponse(routerA.process(hex(apdu)))!!

    // ------------------------------------------------------------------ Type 4 Tag state machine

    @Test
    fun ndefApplicationSelectAndWrongAid() {
        assertEquals(Sw.OK.code, send("00A4040007D276000085010100").sw)
        // Mapping 1.0 is Android's fallback SELECT (no Le), answered with a 1.0 CC (tested below).
        assertEquals("v1 mapping AID is offered", Sw.OK.code, send("00A4040007D2760000850100").sw)
        assertEquals(Sw.NOT_FOUND.code, send("00A4040007A0000000041010").sw)
    }

    @Test
    fun capabilityContainerIsExact() {
        send("00A4040007D276000085010100")
        assertEquals(Sw.OK.code, send("00A4000C02E103").sw)
        val cc = send("00B000000F")
        assertTrue(cc.isOk)
        val ndefFileSize = 2 + KipatPeerBootstrap.message(ByteArray(16), pkg).size
        assertEquals(
            "000F2000F600FF0406E104" + "%04X".format(ndefFileSize) + "00FF",
            cc.data.toHex(),
        )
    }

    @Test
    fun ndefFileReadsNlenThenMessageInPieces() {
        send("00A4040007D276000085010100")
        assertEquals(Sw.OK.code, send("00A4000C02E104").sw)
        val nlen = send("00B0000002")
        val n = ((nlen.data[0].toInt() and 0xFF) shl 8) or (nlen.data[1].toInt() and 0xFF)
        // Read in 10-byte pieces (partial reads at offsets).
        val msg = java.io.ByteArrayOutputStream()
        var off = 2
        while (off < 2 + n) {
            val r = send("00B0" + "%04X".format(off) + "0A")
            assertTrue(r.isOk)
            msg.write(r.data)
            off += r.data.size
        }
        val records = NdefMessage(msg.toByteArray()).records.map { it.toData() }
        assertEquals(2, records.size)
        assertEquals(KipatPeerBootstrap.MIME_TYPE, records[0].typeString)
        assertEquals("android.com:pkg", records[1].typeString)
        assertEquals(pkg, String(records[1].payload, Charsets.US_ASCII))
    }

    @Test
    fun type4ErrorsAndReadOnly() {
        assertEquals("file select before application", Sw.NOT_FOUND.code, send("00A4000C02E103").sw)
        send("00A4040007D276000085010100")
        assertEquals("read with no file selected", 0x6986, send("00B0000002").sw)
        assertEquals("wrong file id", Sw.NOT_FOUND.code, send("00A4000C02E105").sw)
        send("00A4000C02E103")
        assertEquals("offset past the CC", Sw.WRONG_P1P2.code, send("00B000100F").sw)
        assertEquals("malformed READ BINARY", Sw.WRONG_LENGTH.code, send("00B00000").sw)
        assertEquals("UPDATE BINARY refused", 0x6982, send("00D6000001FF").sw)
        assertEquals("unsupported INS", Sw.INS_NOT_SUPPORTED.code, send("00CA000000").sw)
        assertEquals("proprietary class not on the NDEF app", Sw.CLA_NOT_SUPPORTED.code, send("80B0000002").sw)
    }

    @Test
    fun notEligibleExposesNoTag() {
        eligible = false
        assertEquals(Sw.NOT_FOUND.code, send("00A4040007D276000085010100").sw)
        // The Kipat AID still answers SELECT, but nothing is shared.
        assertEquals(Sw.OK.code, send("00A4040007F04B495041540100").sw)
        assertEquals(Sw.CONDITIONS_NOT_SATISFIED.code, KipatApduProtocol.parseResponse(routerA.process(KipatApduProtocol.hello(ByteArray(16))))!!.sw)
    }

    // ------------------------------------------------------------------ bootstrap NDEF

    @Test
    fun bootstrapIsTinyMimeFirstWithAarAndMatchesAndroidEncoder() {
        val session = ByteArray(16) { it.toByte() }
        val records = KipatPeerBootstrap.records(session, pkg)
        val ours = KipatPeerBootstrap.message(session, pkg)
        val android = records.toMessage().toByteArray()
        assertArrayEquals("our encoder == android.nfc.NdefMessage", android, ours)
        assertEquals("""{"v":1,"t":"iso-dep","p":1,"s":"0001020304050607"}""", String(records[0].payload, Charsets.US_ASCII))
        assertEquals("tiny, no profile: MIME record + AAR", 112, ours.size)
        val parsed = KipatPeerBootstrap.parse(records[0].payload) as KipatPeerBootstrap.Result.Valid
        assertEquals("0001020304050607", parsed.bootstrap.nonceHex)
    }

    @Test
    fun maliciousOrMalformedBootstraps() {
        fun p(s: String) = KipatPeerBootstrap.parse(s.toByteArray())
        assertTrue(p("""{"v":2,"t":"iso-dep","p":1}""") is KipatPeerBootstrap.Result.Unsupported)
        assertTrue(p("""{"v":1,"t":"iso-dep","p":9}""") is KipatPeerBootstrap.Result.Unsupported)
        assertTrue(p("""{"v":1,"t":"https","p":1}""") is KipatPeerBootstrap.Result.Invalid)
        assertTrue(p("""{"v":1,"t":"iso-dep","p":1,"s":"<script>"}""") is KipatPeerBootstrap.Result.Invalid)
        assertTrue(p("""{"v":1,"t":"iso-dep",""") is KipatPeerBootstrap.Result.Invalid)
        assertTrue(p("[1]") is KipatPeerBootstrap.Result.Invalid)
        assertTrue(KipatPeerBootstrap.parse(ByteArray(500) { 'a'.code.toByte() }) is KipatPeerBootstrap.Result.Invalid)
        assertTrue(p("""{"v":1,"t":"iso-dep","p":1}""") is KipatPeerBootstrap.Result.Valid)
    }

    // ------------------------------------------------------------------ ambient simulation

    /** What Android's NDEF check does against a Type 4 tag, step by step. */
    private fun androidNdefRead(transceive: (ByteArray) -> ByteArray): NdefMessage? {
        fun ok(apdu: String) = KipatApduProtocol.parseResponse(transceive(hex(apdu)))!!.takeIf { it.isOk }
        ok("00A4040007D276000085010100") ?: return null
        ok("00A4000C02E103") ?: return null
        val cc = ok("00B000000F") ?: return null
        val fileId = cc.data.copyOfRange(9, 11).toHex()
        ok("00A4000C02$fileId") ?: return null
        val nlen = ok("00B0000002") ?: return null
        val n = ((nlen.data[0].toInt() and 0xFF) shl 8) or (nlen.data[1].toInt() and 0xFF)
        val body = ok("00B00002" + "%02X".format(n)) ?: return null
        return NdefMessage(body.data)
    }

    @Test
    fun ambientBootstrapUpgradesToMutualExchangeOnTheSameLink() {
        // INITIATOR (phone B) reads A's tag the way Android does, then dispatch hands it the same link.
        val link: (ByteArray) -> ByteArray = { routerA.process(it) }
        val ndef = androidNdefRead(link)!!
        val first = ndef.records[0].toData()
        assertTrue(KipatPeerBootstrap.isBootstrap(first))
        val boot = (KipatPeerBootstrap.parse(first.payload) as KipatPeerBootstrap.Result.Valid).bootstrap

        var seq = 0
        val result = KipatPeerExchange(
            transceive = link, chunkSize = 200, ownProfile = phoneB, ownProfileId = phoneB.id,
            newSessionId = { ByteArray(16) { (it + ++seq).toByte() } }, clock = { now },
        ).run() as PeerExchangeResult.Success

        assertEquals("B received A", phoneA, result.profile)
        assertEquals(UploadOutcome.SENT, result.upload)
        assertEquals("A received B through the ambient listener", listOf(phoneB), delivered)
        assertEquals("bootstrap nonce is the HELLO session", boot.nonceHex, result.presenterSession.take(16))
        assertEquals("ambient encounters never drive the manual Present UI", PresentState.Idle, storeA.state.value)
        assertNull("COMPLETE closed the encounter", storeA.activeSession())
    }

    @Test
    fun customAidWithoutBootstrapStillWorks() {
        var seq = 0
        val r = KipatPeerExchange(
            transceive = { routerA.process(it) }, chunkSize = 64, ownProfile = phoneB, ownProfileId = phoneB.id,
            newSessionId = { ByteArray(16) { (it + ++seq).toByte() } }, clock = { now },
        ).run()
        assertEquals(phoneA, (r as PeerExchangeResult.Success).profile)
        assertEquals(listOf(phoneB), delivered)
    }

    @Test
    fun repeatedBootstrapReadsInOneEncounterKeepTheSameSession() {
        val n1 = androidNdefRead { routerA.process(it) }!!.records[0].payload.decodeToString()
        routerA.onDeactivated(linkLost = true)
        now += 3_000
        val n2 = androidNdefRead { routerA.process(it) }!!.records[0].payload.decodeToString()
        assertEquals(n1, n2)
        now += PeerSessionStore.ENCOUNTER_WINDOW_MS
        val n3 = androidNdefRead { routerA.process(it) }!!.records[0].payload.decodeToString()
        assertFalse("a new encounter after the window", n1 == n3)
    }

    @Test
    fun encounterOpeningIsRateLimited() {
        repeat(PeerSessionStore.MAX_ENCOUNTERS_PER_MINUTE) {
            assertNotNull(storeA.beginEncounter())
            now += PeerSessionStore.ENCOUNTER_WINDOW_MS + 1 // each one expires before the next
        }
        // 20 encounters inside one minute? Not possible with 20 s windows — force them closer.
        val busy = PeerSessionStore(clock = { now }, ambientProfile = { PublicKipatProfileSnapshot.of(phoneA) })
        var opened = 0
        repeat(30) {
            if (busy.beginEncounter() != null) opened++
            busy.onExchangeComplete(busy.activeSession() ?: return@repeat, ByteArray(0), false)
        }
        assertEquals(PeerSessionStore.MAX_ENCOUNTERS_PER_MINUTE, opened)
    }

    @Test
    fun manualSessionTakesPrecedenceOverAmbient() {
        val manual = (storeA.arm(phoneA) as PeerSessionStore.ArmResult.Armed).session
        assertTrue(storeA.beginEncounter() === manual)
        assertFalse(manual.ambient)
    }

    @Test
    fun maliciousReaderIsBounded() {
        storeA.beginEncounter()
        routerA.process(KipatApduProtocol.select())
        routerA.process(KipatApduProtocol.hello(ByteArray(16)))
        var last = 0
        repeat(KipatApduProcessor.MAX_COMMANDS_PER_LINK + 5) {
            last = KipatApduProtocol.parseResponse(routerA.process(KipatApduProtocol.readChunk(0, 10)))!!.sw
        }
        assertEquals(Sw.CONDITIONS_NOT_SATISFIED.code, last)
    }

    // ------------------------------------------------------------------ delivery + availability

    @Test
    fun deliveryIsOncePerPeerAcrossRolesAndWindow() {
        val store = IncomingKipatExchangeStore(clock = { now })
        val d1 = store.offer(phoneB, TapRole.RESPONDER)
        assertNotNull(d1)
        assertNull("roles flipped in the same touch", store.offer(phoneB, TapRole.INITIATOR))
        assertNotNull("someone else is a new delivery", store.offer(phoneA, TapRole.INITIATOR))
        now += IncomingKipatExchangeStore.DEDUPE_WINDOW_MS + 1
        assertNotNull("a deliberate second tap later", store.offer(phoneB, TapRole.INITIATOR))
        assertFalse(store.wasShown(d1!!.exchangeId))
        store.markShown(d1.exchangeId)
        assertTrue(store.wasShown(d1.exchangeId))
    }

    @Test
    fun availabilityStates() {
        val all = KipatPeerCapabilities(hasNfc = true, nfcEnabled = true, hasHce = true)
        assertEquals(KipatTapAvailability.READY, KipatTapAvailability.of(true, true, all, locked = false))
        assertEquals(KipatTapAvailability.DISABLED_BY_USER, KipatTapAvailability.of(false, true, all, false))
        assertEquals(KipatTapAvailability.NO_PROFILE, KipatTapAvailability.of(true, false, all, false))
        assertEquals(KipatTapAvailability.NFC_DISABLED, KipatTapAvailability.of(true, true, all.copy(nfcEnabled = false), false))
        assertEquals(KipatTapAvailability.HCE_UNSUPPORTED, KipatTapAvailability.of(true, true, all.copy(hasHce = false), false))
        assertEquals(KipatTapAvailability.UNAVAILABLE, KipatTapAvailability.of(true, true, KipatPeerCapabilities(false, false, false), false))
        assertEquals(KipatTapAvailability.DEVICE_LOCKED, KipatTapAvailability.of(true, true, all, true))
        assertEquals("NFC is off", KipatTapAvailability.NFC_DISABLED.sentence)
    }
}
