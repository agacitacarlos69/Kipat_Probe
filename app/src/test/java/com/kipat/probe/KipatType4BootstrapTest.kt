package com.kipat.probe

import android.nfc.NdefMessage
import android.nfc.NdefRecord
import com.kipat.probe.data.PublicKipatProfileSnapshot
import com.kipat.probe.nfc.NfcIntentParser
import com.kipat.probe.nfc.ParsedNfcIntent
import com.kipat.probe.nfc.peer.KipatApduProcessor
import com.kipat.probe.nfc.peer.KipatApduProtocol
import com.kipat.probe.nfc.peer.KipatApduProtocol.toHex
import com.kipat.probe.nfc.peer.KipatHceRouter
import com.kipat.probe.nfc.peer.KipatPeerBootstrap
import com.kipat.probe.nfc.peer.KipatPeerExchange
import com.kipat.probe.nfc.peer.KipatType4Tag
import com.kipat.probe.nfc.peer.PeerDeliveryListener
import com.kipat.probe.nfc.peer.PeerExchangeResult
import com.kipat.probe.nfc.peer.PeerLog
import com.kipat.probe.nfc.peer.PeerSessionStore
import com.kipat.probe.nfc.peer.Sw
import com.kipat.probe.nfc.peer.UploadOutcome
import com.kipat.probe.nfc.profile.Ndef
import com.kipat.probe.nfc.profile.NfcProfilePayload
import com.kipat.probe.nfc.toData
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.IOException

/**
 * P1-NFC-AMBIENT-EMPTY-TAG. The responder's Type 4 tag driven by the EXACT command APDUs Android's
 * NDEF check sends (packages/modules/Nfc libnfc-nci/src/nfc/tags/rw_t4t.cc: rw_t4t_select_application,
 * rw_t4t_select_file, rw_t4t_read_file, rw_t4t_validate_cc_file, rw_t4t_sm_detect_ndef), and the
 * FINALIZE barrier that follows the bootstrap. Simulation: not evidence of routing on a real phone.
 */
@RunWith(RobolectricTestRunner::class)
class KipatType4BootstrapTest {

    private var now = 10_000L
    private val pkg = "com.kipat.probe"
    private val randy = NfcProfilePayload(id = "KPT-RANDY0000001", displayName = "Randy Orton", headline = "Product Designer")
    private val kyle = NfcProfilePayload(id = "KPT-KYLE00000001", displayName = "Kyle Jimenez", headline = "BS Computer Engineering")
    private var eligible: PublicKipatProfileSnapshot? = PublicKipatProfileSnapshot.of(randy)
    private var refusal = "kipat_tap_disabled"
    private val events = mutableListOf<String>()
    private val log = PeerLog { _, name, detail -> events += if (detail.isEmpty()) name else "$name $detail" }
    private val revealed = mutableListOf<NfcProfilePayload>()
    private val posted = mutableListOf<() -> Unit>()
    private val store = PeerSessionStore(
        clock = { now }, log = log, ambientProfile = { eligible }, ambientRefusal = { refusal },
        post = { posted += it }, // run after the APDU response, like the HCE service's main-thread post
    ).also { it.ambientListener = PeerDeliveryListener { peer, _, _, _ -> revealed += peer } }
    private val router = KipatHceRouter(KipatType4Tag(store, pkg, log), KipatApduProcessor(store, log))

    private fun apdu(hex: String) = KipatApduProtocol.parseResponse(router.process(KipatApduProtocol.hex(hex)))!!
    private fun names() = events.map { it.substringBefore(' ') }

    // ------------------------------------------------------------------ Android's NDEF check, byte for byte

    /** rw_t4t mapping 2.0: SELECT app (P2=00, Le=00), SELECT CC/NDEF (P1=00 P2=0C), READ BINARY with Le. */
    private fun androidDetectAndReadV2(): ByteArray {
        assertEquals(Sw.OK.code, apdu("00A4040007D276000085010100").sw)
        assertEquals(Sw.OK.code, apdu("00A4000C02E103").sw)
        val cc = apdu("00B000000F")
        assertEquals(Sw.OK.code, cc.sw)
        validateCcLikeAndroid(cc.data, selectedMajor = 2)
        assertEquals(Sw.OK.code, apdu("00A4000C02" + cc.data.copyOfRange(9, 11).toHex()).sw)
        val nlenR = apdu("00B0000002")
        assertEquals(2, nlenR.data.size)
        val nlen = ((nlenR.data[0].toInt() and 0xFF) shl 8) or (nlenR.data[1].toInt() and 0xFF)
        val maxLe = ((cc.data[3].toInt() and 0xFF) shl 8) or (cc.data[4].toInt() and 0xFF)
        // Android reads the message from offset 2 in pieces of at most MLe.
        val out = java.io.ByteArrayOutputStream()
        var offset = 2
        while (offset < 2 + nlen) {
            val want = minOf(maxLe, 2 + nlen - offset)
            val r = apdu("00B0" + "%04X".format(offset) + "%02X".format(want))
            assertEquals(Sw.OK.code, r.sw)
            assertEquals("exactly what was asked for", want, r.data.size)
            out.write(r.data)
            offset += r.data.size
        }
        return out.toByteArray()
    }

    /** rw_t4t_validate_cc_file, condition by condition. */
    private fun validateCcLikeAndroid(cc: ByteArray, selectedMajor: Int) {
        fun u16(i: Int) = ((cc[i].toInt() and 0xFF) shl 8) or (cc[i + 1].toInt() and 0xFF)
        assertEquals(15, cc.size)
        assertTrue("CCLEN >= 15", u16(0) >= 0x0F)
        assertTrue("CC mapping version not newer than the selected application", (cc[2].toInt() shr 4) <= selectedMajor)
        assertTrue("MaxLe >= 0x000F", u16(3) >= 0x000F)
        assertTrue("MaxLc >= 0x0001", u16(5) >= 0x0001)
        assertEquals("NDEF File Control TLV type", 0x04, cc[7].toInt())
        assertEquals("NDEF File Control TLV length", 0x06, cc[8].toInt())
        assertFalse("file id not reserved", u16(9) in setOf(0xE103, 0xE102, 0x0000, 0x3F00, 0x3FFF, 0xFFFF))
        assertTrue("max file size 0x0005..0x7FFF", u16(11) in 0x0005..0x7FFF)
        assertEquals("read access open", 0x00, cc[13].toInt())
        assertTrue("write access 00 or FF", cc[14].toInt() and 0xFF in setOf(0x00, 0xFF))
    }

    @Test
    fun androidNdefCheckReadsTheKipatBootstrapByteForByte() {
        val bytes = androidDetectAndReadV2()
        // Android's own parser, as NfcService builds EXTRA_NDEF_MESSAGES.
        val msg = NdefMessage(bytes)
        assertEquals(NdefRecord.TNF_MIME_MEDIA, msg.records[0].tnf)
        assertEquals(KipatPeerBootstrap.MIME_TYPE, String(msg.records[0].type, Charsets.US_ASCII))
        assertEquals("the MIME record is first: NfcDispatcher matches on it", KipatPeerBootstrap.MIME_TYPE, msg.records[0].toMimeType())
        assertEquals(NdefRecord.TNF_EXTERNAL_TYPE, msg.records[1].tnf)
        assertEquals("android.com:pkg", String(msg.records[1].type, Charsets.US_ASCII))
        assertEquals(pkg, String(msg.records[1].payload, Charsets.US_ASCII))
        assertTrue(KipatPeerBootstrap.parse(msg.records[0].payload) is KipatPeerBootstrap.Result.Valid)
        assertFalse("no profile in the bootstrap", String(bytes).contains("Randy"))
        // What the initiator's NFC intent parser does with that message.
        val tap = NfcIntentParser.parseTap(msg.records.map { it.toData() }, null)
        assertTrue(tap is ParsedNfcIntent.PeerBootstrap)
        // Diagnostics: every Type 4 step, in order, once.
        assertEquals(
            listOf("ndef_aid_selected", "cc_file_selected", "cc_file_read", "ndef_file_selected", "ndef_nlen_read", "ndef_payload_read", "ndef_bootstrap_served"),
            names().filter { it.startsWith("ndef_") || it.startsWith("cc_") },
        )
    }

    @Test
    fun androidsMapping10FallbackIsAnsweredWithA10CapabilityContainer() {
        // rw_t4t retries with the 1.0 AID (no Le) when 2.0 fails, then selects files with P2=00.
        assertEquals(Sw.OK.code, apdu("00A4040007D2760000850100").sw)
        assertEquals(Sw.OK.code, apdu("00A4000002E103").sw)
        val cc = apdu("00B000000F")
        assertEquals(0x10, cc.data[2].toInt())
        validateCcLikeAndroid(cc.data, selectedMajor = 1)
        assertEquals(Sw.OK.code, apdu("00A4000002E104").sw)
        val nlen = apdu("00B0000002").data
        val n = ((nlen[0].toInt() and 0xFF) shl 8) or (nlen[1].toInt() and 0xFF)
        val msg = NdefMessage(apdu("00B00002" + "%02X".format(n)).data)
        assertEquals(KipatPeerBootstrap.MIME_TYPE, msg.records[0].toMimeType())
        assertTrue(events.first { it.startsWith("ndef_aid_selected") }.contains("v=1"))
    }

    @Test
    fun readBinaryHandlesEveryOffsetAndLengthAndroidCanSend() {
        apdu("00A4040007D276000085010100"); apdu("00A4000C02E104")
        val whole = KipatApduProtocol.hex(apdu("00B0000002").data.toHex()) +
            apdu("00B0000200").data // Le=00 means "up to 256": capped at MLe, here the whole message
        val n = whole.size - 2
        assertEquals("NLEN matches the message", n, ((whole[0].toInt() and 0xFF) shl 8) or (whole[1].toInt() and 0xFF))
        assertArrayEquals("a read starting inside NLEN", whole.copyOfRange(1, 4), apdu("00B0000103").data)
        assertArrayEquals("a read inside the payload", whole.copyOfRange(10, 20), apdu("00B0000A0A").data)
        assertArrayEquals("asking for more than remains returns the rest", whole.copyOfRange(n - 3, n + 2), apdu("00B0" + "%04X".format(n - 3) + "40").data)
        assertEquals("offset at the end", Sw.WRONG_P1P2.code, apdu("00B0" + "%04X".format(n + 2) + "01").sw)
        assertEquals("offset past the end", Sw.WRONG_P1P2.code, apdu("00B07F0001").sw)
        assertEquals("READ BINARY with a body is malformed", Sw.WRONG_LENGTH.code, apdu("00B000000100").sw)
    }

    @Test
    fun malformedOrUnexpectedType4CommandsNeverCrash() {
        assertEquals("wrong NDEF AID", Sw.NOT_FOUND.code, apdu("00A4040007D276000085010200").sw)
        assertEquals("file before application", Sw.NOT_FOUND.code, apdu("00A4000C02E104").sw)
        apdu("00A4040007D276000085010100")
        assertEquals("bad file id", Sw.NOT_FOUND.code, apdu("00A4000C02E105").sw)
        assertEquals("read with no file", 0x6986, apdu("00B0000002").sw)
        assertEquals("truncated SELECT", Sw.WRONG_LENGTH.code, apdu("00A4000C02E1").sw)
        assertEquals("unsupported P1", Sw.WRONG_P1P2.code, apdu("00A4080C02E103").sw)
        assertEquals("write refused: read-only tag", 0x6982, apdu("00D6000001FF").sw)
        listOf("", "00", "00A4", "FFFFFFFF").forEach { router.process(KipatApduProtocol.hex(it)) }
    }

    @Test
    fun aRefusedNdefSelectSaysWhyInsteadOfAnEmptyTag() {
        eligible = null
        listOf("kipat_tap_disabled", "no_public_profile", "device_locked").forEach { reason ->
            refusal = reason
            assertEquals(Sw.NOT_FOUND.code, apdu("00A4040007D276000085010100").sw)
            assertEquals("type4_refused reason=$reason", events.last())
        }
        assertFalse("never a zero-length NDEF file", names().contains("ndef_aid_selected"))
    }

    @Test
    fun malformedBootstrapsAreNotKipatDispatches() {
        fun parse(records: List<com.kipat.probe.nfc.profile.NdefRecordData>) = NfcIntentParser.parseTap(records, null)
        val good = KipatPeerBootstrap.records(ByteArray(16), pkg)
        assertTrue("with AAR", parse(good) is ParsedNfcIntent.PeerBootstrap)
        assertTrue("without AAR the MIME record alone is enough", parse(good.take(1)) is ParsedNfcIntent.PeerBootstrap)
        assertFalse("other MIME", parse(listOf(Ndef.mime("application/vnd.other", "{}".toByteArray()))) is ParsedNfcIntent.PeerBootstrap)
        val v2 = (parse(listOf(Ndef.mime(KipatPeerBootstrap.MIME_TYPE, """{"v":2,"t":"iso-dep","p":1}""".toByteArray()))) as ParsedNfcIntent.PeerBootstrap).result
        assertTrue("newer bootstrap is Unsupported, never guessed", v2 is KipatPeerBootstrap.Result.Unsupported)
        val junk = (parse(listOf(Ndef.mime(KipatPeerBootstrap.MIME_TYPE, "{oops".toByteArray()))) as ParsedNfcIntent.PeerBootstrap).result
        assertTrue(junk is KipatPeerBootstrap.Result.Invalid)
        // A truncated message never reaches Kipat: Android's own parser rejects it.
        val bytes = KipatPeerBootstrap.message(ByteArray(16), pkg)
        assertTrue(runCatching { NdefMessage(bytes.copyOf(bytes.size - 5)) }.isFailure)
    }

    // ------------------------------------------------------------------ bootstrap → Kipat AID → FINALIZE

    private fun exchange(link: (ByteArray) -> ByteArray = { router.process(it) }): PeerExchangeResult {
        var seq = 0
        return KipatPeerExchange(
            transceive = link, chunkSize = 120, ownProfile = kyle, ownProfileId = kyle.id,
            newSessionId = { ByteArray(16) { (it + ++seq).toByte() } }, clock = { now }, log = log,
        ).run()
    }

    @Test
    fun theResponderRevealsOnlyAtFinalizeAndAfterItsResponse() {
        androidDetectAndReadV2()
        var revealedBeforeFinalize: Int? = null
        var postedBeforeFinalizeAnswered: Int? = null
        val r = exchange { apdu ->
            if (apdu[1] == KipatApduProtocol.INS_COMPLETE) revealedBeforeFinalize = revealed.size + posted.size
            router.process(apdu).also { if (apdu[1] == KipatApduProtocol.INS_COMPLETE) postedBeforeFinalizeAnswered = revealed.size }
        } as PeerExchangeResult.Success
        assertEquals(UploadOutcome.SENT, r.upload)
        assertTrue("FINALIZE answered 9000", r.finalized)
        assertEquals("nothing delivered before FINALIZE, though COMMIT had passed", 0, revealedBeforeFinalize)
        assertEquals("the Reveal launch waits for FINALIZE's response", 0, postedBeforeFinalizeAnswered)
        assertEquals(1, posted.size)
        posted.single().invoke()
        assertEquals(listOf(kyle), revealed)
        assertTrue(names().containsAll(listOf("finalize_received", "mutual_complete", "finalize_ok")))
        assertEquals(2, names().count { it == "mutual_complete" }) // one per side
    }

    @Test
    fun aCommittedProfileSurvivesDeactivationWithoutFinalize() {
        val r = exchange { apdu ->
            if (apdu[1] == KipatApduProtocol.INS_COMPLETE) {
                router.onDeactivated(linkLost = true)
                throw IOException("Tag was lost.")
            }
            router.process(apdu)
        } as PeerExchangeResult.Success
        assertEquals("COMMIT was answered, so the initiator still reveals", UploadOutcome.SENT, r.upload)
        assertFalse(r.finalized)
        posted.forEach { it() }
        assertEquals(listOf(kyle), revealed)
        assertTrue(names().containsAll(listOf("finalize_missing", "finalize_lost")))
    }

    @Test
    fun finalizeThenDeactivationDeliversExactlyOnce() {
        exchange()
        router.onDeactivated(linkLost = false)
        router.onDeactivated(linkLost = true)
        posted.forEach { it() }
        assertEquals(listOf(kyle), revealed)
        assertEquals("one mutual_complete per side, none from the later deactivations", 2, names().count { it == "mutual_complete" })
        assertFalse(names().contains("finalize_missing"))
    }

    @Test
    fun aPartialUploadIsNeverDeliveredByDeactivation() {
        var writes = 0
        exchange { apdu ->
            if (apdu[1] == KipatApduProtocol.INS_WRITE_CHUNK && ++writes == 1) { // after BEGIN, before any chunk lands
                router.onDeactivated(linkLost = true)
                throw IOException("Tag was lost.")
            }
            router.process(apdu)
        }
        posted.forEach { it() }
        assertTrue(revealed.isEmpty())
        assertTrue(events.any { it.startsWith("peer_link_lost partial_upload_cleared=true") })
    }
}
