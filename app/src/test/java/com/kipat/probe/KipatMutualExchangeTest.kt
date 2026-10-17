package com.kipat.probe

import android.nfc.NdefMessage
import com.kipat.probe.data.KipatIdentityStore
import com.kipat.probe.data.KipatRepository
import com.kipat.probe.data.PublicKipatProfileSnapshot
import com.kipat.probe.data.RevealResolver
import com.kipat.probe.data.SampleData
import com.kipat.probe.nfc.NfcIntentParser
import com.kipat.probe.nfc.peer.IncomingKipatExchangeStore
import com.kipat.probe.nfc.peer.KipatApduProcessor
import com.kipat.probe.nfc.peer.KipatApduProtocol
import com.kipat.probe.nfc.peer.KipatApduProtocol.toHex
import com.kipat.probe.nfc.peer.KipatHceRouter
import com.kipat.probe.nfc.peer.KipatPeerExchange
import com.kipat.probe.nfc.peer.KipatPeerProfile
import com.kipat.probe.nfc.peer.KipatTapLauncher
import com.kipat.probe.nfc.peer.KipatType4Tag
import com.kipat.probe.nfc.peer.PeerDeliveryListener
import com.kipat.probe.nfc.peer.PeerExchangeResult
import com.kipat.probe.nfc.peer.PeerLog
import com.kipat.probe.nfc.peer.PeerSessionStore
import com.kipat.probe.nfc.peer.TapRole
import com.kipat.probe.nfc.peer.UploadOutcome
import com.kipat.probe.nfc.profile.NfcProfilePayload
import com.kipat.probe.pilot.PilotStage
import com.kipat.probe.pilot.PilotTracker
import com.kipat.probe.pilot.StageResult
import com.kipat.probe.reveal.RevealSource
import com.kipat.probe.reveal.RevealTemplateId
import com.kipat.probe.reveal.RevealTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.IOException

/**
 * P1-NFC-MUTUAL-EXCHANGE. PROTOCOL SIMULATION of one physical Kipat Tap between two phones, each
 * with its own responder stack (Type 4 bootstrap + Kipat AID), its own exactly-once delivery store
 * and its own pilot diagnostics. Phone 1 (Kyle) is the initiator, phone 2 (Randy) the responder.
 * Not evidence of radio behaviour: the physical retest is still required.
 */
@RunWith(RobolectricTestRunner::class)
class KipatMutualExchangeTest {

    private var now = 500_000L

    private val kyle = NfcProfilePayload(
        id = KipatIdentityStore.newProfileId(), displayName = "Kyle Jimenez", headline = "BS Computer Engineering",
        organization = "Mapua University", reveal = RevealTemplateId.PRECISION_ORBIT, accent = RevealTheme.NIGHT, // Cobalt
    )
    private val randy = NfcProfilePayload(
        id = KipatIdentityStore.newProfileId(), displayName = "Randy Orton", headline = "Product Designer",
        organization = "Kipat", reveal = RevealTemplateId.CONTACT_RIPPLE, accent = RevealTheme.EMBER, // Amber
    )

    /** One phone: its public profile, responder stack, delivery store, Reveals shown and pilot buffer. */
    private inner class Phone(val profile: NfcProfilePayload) {
        val pilot = PilotTracker(monoClock = { now }, wallClock = { now })
        val log = PeerLog { tag, name, detail -> pilot.onEvent(name, detail, tag) }
        val deliveries = IncomingKipatExchangeStore(clock = { now })
        val reveals = mutableListOf<NfcProfilePayload>()
        val store = PeerSessionStore(clock = { now }, log = log, ambientProfile = { PublicKipatProfileSnapshot.of(profile) }).also {
            // Same rules as KipatTapLauncher.deliverAsResponder: never "yourself", exactly once per person.
            it.ambientListener = PeerDeliveryListener { peer, _, _, _ ->
                if (KipatPeerProfile.isSamePerson(peer, profile.id)) {
                    log.event(PeerLog.TAG_LAUNCH, "responder_reveal_skipped", "reason=same_profile_id")
                } else if (deliveries.offer(peer, TapRole.RESPONDER) != null) {
                    reveals += peer
                    log.event(PeerLog.TAG_LAUNCH, "responder_reveal_requested", "exchange=1")
                }
            }
        }
        val processor = KipatApduProcessor(store, log)
        val router = KipatHceRouter(KipatType4Tag(store, "com.kipat.probe", log), processor)

        /** Initiator side, as KipatTapInitiator does it: a Reveal only for a finished mutual exchange. */
        fun initiate(other: Phone, chunk: Int = 200, link: (ByteArray) -> ByteArray = { other.router.process(it) }): PeerExchangeResult {
            log.event(PeerLog.TAG_AMBIENT, "ndef_dispatched", "")
            androidNdefRead(link)
            var seq = (profile.id ?: "").hashCode()
            val r = KipatPeerExchange(
                transceive = link, chunkSize = chunk, ownProfile = profile, ownProfileId = profile.id,
                newSessionId = { ByteArray(16) { (it + ++seq).toByte() } }, clock = { now }, log = log,
            ).run()
            if (r is PeerExchangeResult.Success && !r.isSelf && !r.reverseTripIncomplete &&
                deliveries.offer(r.profile, TapRole.INITIATOR) != null
            ) {
                reveals += r.profile
                log.event(PeerLog.TAG_AMBIENT, "peer_profile_received", "down=${r.downloadedBytes} up=${r.uploadedBytes}")
                log.event(PeerLog.TAG_LAUNCH, "reveal_requested", "")
            }
            return r
        }
    }

    /** What Android's NDEF check does against the other phone's Type 4 tag before dispatch. */
    private fun androidNdefRead(transceive: (ByteArray) -> ByteArray): NdefMessage? {
        fun ok(apdu: String) = KipatApduProtocol.parseResponse(transceive(KipatApduProtocol.hex(apdu)))!!.takeIf { it.isOk }
        ok("00A4040007D276000085010100") ?: return null
        ok("00A4000C02E103") ?: return null
        val cc = ok("00B000000F") ?: return null
        ok("00A4000C02" + cc.data.copyOfRange(9, 11).toHex()) ?: return null
        val nlen = ok("00B0000002") ?: return null
        val n = ((nlen.data[0].toInt() and 0xFF) shl 8) or (nlen.data[1].toInt() and 0xFF)
        return NdefMessage(ok("00B00002" + "%02X".format(n))!!.data)
    }

    // ------------------------------------------------------------------ the physical failure, reproduced

    @Test
    fun physicalFailureReproduced_bothPhonesCarriedTheSharedSampleId() {
        // Both pilot exports reported profile pseudonym p-8fd2af7c = KPT-DEMO-0001 on BOTH phones.
        val phone1 = Phone(kyle.copy(id = SampleData.DEMO_CARD_ID))
        val phone2 = Phone(randy.copy(id = SampleData.DEMO_CARD_ID))
        val r = phone1.initiate(phone2) as PeerExchangeResult.Success

        assertTrue("Kyle's phone thinks Randy is Kyle", r.isSelf)
        assertEquals("so it never sends Kyle back", UploadOutcome.SKIPPED_OWN_PROFILE, r.upload)
        assertTrue("Randy's phone never receives Kyle", phone2.reveals.isEmpty())
        assertTrue("and Kyle's phone shows 'your own profile', not Randy", phone1.reveals.isEmpty())
        assertEquals("OWN_UPLOAD_SAME_PROFILE_ID", phone1.pilot.last()!!.failureCode)
        assertTrue(KipatIdentityStore.needsOwnProfileId(SampleData.identity))
    }

    // ------------------------------------------------------------------ the fixed flow

    @Test
    fun oneEncounterGivesKyleRandyAndRandyKyle() {
        val phone1 = Phone(kyle)
        val phone2 = Phone(randy)
        val r = phone1.initiate(phone2) as PeerExchangeResult.Success

        assertEquals(UploadOutcome.SENT, r.upload)
        assertFalse(r.isSelf)
        assertEquals("phone 1 received Randy", listOf(randy), phone1.reveals)
        assertEquals("phone 2 received Kyle", listOf(kyle), phone2.reveals)

        // Each Reveal is built from the PEER profile, never the local one.
        val on1 = KipatTapLauncher.payloadFor(phone1.reveals.single())
        val on2 = KipatTapLauncher.payloadFor(phone2.reveals.single())
        assertEquals("Randy Orton", on1.displayName)
        assertEquals(RevealTemplateId.CONTACT_RIPPLE, on1.template)
        assertEquals(RevealTheme.EMBER, on1.theme) // Amber
        assertEquals("Kyle Jimenez", on2.displayName)
        assertEquals(RevealTemplateId.PRECISION_ORBIT, on2.template)
        assertEquals(RevealTheme.NIGHT, on2.theme) // Cobalt
        assertEquals(RevealSource.PEER_NFC, on1.source)

        // Pilot diagnostics: every stage on both phones, and the same exchange id on both.
        val a1 = phone1.pilot.last()!!
        val a2 = phone2.pilot.last()!!
        listOf(PilotStage.KIPAT_AID, PilotStage.HELLO, PilotStage.PEER_DOWNLOAD, PilotStage.OWN_UPLOAD, PilotStage.INTEGRITY, PilotStage.REVEAL_REQUESTED)
            .forEach { s ->
                assertEquals("phone 1 $s", StageResult.PASS, a1.result(s))
                assertEquals("phone 2 $s", StageResult.PASS, a2.result(s))
            }
        assertNull(a1.failureCode); assertNull(a2.failureCode)
        assertNotNull(a1.correlation)
        assertTrue(a1.correlation!!.matches(Regex("e-[0-9a-f]{6}")))
        assertEquals("both exports name the same exchange", a1.correlation, a2.correlation)
        assertEquals(r.correlation, a1.correlation)
    }

    @Test
    fun rolesReversedIsSymmetricToo() {
        val phone1 = Phone(kyle)
        val phone2 = Phone(randy)
        phone2.initiate(phone1)
        assertEquals(listOf(kyle), phone2.reveals)
        assertEquals(listOf(randy), phone1.reveals)
    }

    @Test
    fun largeProfilesChunkInBothDirections() {
        fun wide(c: Char, n: Int) = c.toString().repeat(n)
        fun big(name: String, id: String) = NfcProfilePayload(
            id = id, displayName = name + wide('名', 100), headline = wide('見', 160), organization = wide('組', 200),
            role = wide('役', 100), bio = wide('話', 500), website = "https://example.com/" + "a".repeat(300),
            linkedin = "https://linkedin.com/in/" + "b".repeat(300), github = "https://github.com/kipat", publicEmail = "kipat@example.com",
            reveal = RevealTemplateId.APERTURE, accent = RevealTheme.MONO,
        )
        val k = big("Kyle", kyle.id!!)
        val rd = big("Randy", randy.id!!)
        listOf(k, rd).forEach {
            val size = KipatPeerProfile.toBytes(it).size
            assertTrue("near but below the 4096-byte limit: $size", size in 3_500..KipatApduProtocol.MAX_PROFILE_BYTES)
        }
        val phone1 = Phone(k)
        val phone2 = Phone(rd)
        val r = phone1.initiate(phone2, chunk = 64) as PeerExchangeResult.Success
        assertEquals(UploadOutcome.SENT, r.upload)
        assertEquals(KipatPeerProfile.toBytes(rd).size, r.downloadedBytes)
        assertEquals(KipatPeerProfile.toBytes(k).size, r.uploadedBytes)
        assertEquals(listOf(rd), phone1.reveals)
        assertEquals(listOf(k), phone2.reveals)
    }

    @Test
    fun linkLostHalfwayThroughTheReverseUploadIsNotAnExchange() {
        val phone1 = Phone(kyle.copy(bio = "Kyle ".repeat(80).trim()))
        val phone2 = Phone(randy)
        val chunks = (KipatPeerProfile.toBytes(phone1.profile).size + 63) / 64
        var writes = 0
        val link: (ByteArray) -> ByteArray = { apdu ->
            if (apdu[1] == KipatApduProtocol.INS_WRITE_CHUNK && ++writes > chunks / 2) {
                phone2.router.onDeactivated(linkLost = true)
                throw IOException("Tag was lost.")
            }
            phone2.router.process(apdu)
        }
        val r = phone1.initiate(phone2, chunk = 64, link = link) as PeerExchangeResult.Success

        assertEquals("phone 1 has Randy internally", randy, r.profile)
        assertTrue(r.reverseTripIncomplete)
        assertTrue("but no one-sided Reveal on phone 1", phone1.reveals.isEmpty())
        assertTrue("and phone 2 never renders a partial Kyle", phone2.reveals.isEmpty())
        assertEquals("OWN_UPLOAD_LINK_LOST", phone1.pilot.last()!!.failureCode)
        assertEquals(StageResult.PASS, phone1.pilot.last()!!.result(PilotStage.PEER_DOWNLOAD))

        // The phones are still together (or touched again): the retry completes the mutual exchange.
        now += 1_000
        val retry = phone1.initiate(phone2) as PeerExchangeResult.Success
        assertEquals(UploadOutcome.SENT, retry.upload)
        assertEquals(listOf(randy), phone1.reveals)
        assertEquals(listOf(phone1.profile), phone2.reveals)
    }

    @Test
    fun deactivationRightAfterCommitKeepsTheCommittedProfile() {
        val phone1 = Phone(kyle)
        val phone2 = Phone(randy)
        val link: (ByteArray) -> ByteArray = { apdu ->
            if (apdu[1] == KipatApduProtocol.INS_COMPLETE) {
                // COMMIT succeeded; the RF field drops before COMPLETE.
                phone2.router.onDeactivated(linkLost = true)
                throw IOException("Tag was lost.")
            }
            phone2.router.process(apdu)
        }
        val r = phone1.initiate(phone2, link = link) as PeerExchangeResult.Success
        assertEquals("COMMIT was confirmed before the link dropped", UploadOutcome.SENT, r.upload)
        assertEquals("phone 2 still owns Kyle and its Reveal", listOf(kyle), phone2.reveals)
        assertEquals(listOf(randy), phone1.reveals)
    }

    @Test
    fun repeatedReadsInOneTouchGiveExactlyOneRevealPerPhone() {
        val phone1 = Phone(kyle)
        val phone2 = Phone(randy)
        phone1.initiate(phone2)
        now += 400
        phone1.initiate(phone2) // the controller re-polls, Android re-dispatches
        now += 400
        phone2.initiate(phone1) // or the roles flip within the same touch
        assertEquals(listOf(randy), phone1.reveals)
        assertEquals(listOf(kyle), phone2.reveals)
        now += IncomingKipatExchangeStore.DEDUPE_WINDOW_MS + 1
        phone1.initiate(phone2)
        assertEquals("a deliberate tap later is a new encounter", 2, phone1.reveals.size)
    }

    @Test
    fun aRevealIsARecentTapNeverAConnection() {
        val before = KipatRepository.connections.value.size
        KipatRepository.recordTap(KipatTapLauncher.payloadFor(randy))
        assertEquals(before, KipatRepository.connections.value.size)
        assertFalse(KipatRepository.isConnected(randy.id))
        assertEquals("Randy Orton", KipatRepository.recentTaps.value.first().displayName)
    }

    // ------------------------------------------------------------------ the identity fix

    @Test
    fun everyInstallGetsItsOwnValidProfileId() {
        val ids = (1..2_000).map { KipatIdentityStore.newProfileId() }.toSet()
        assertEquals(2_000, ids.size)
        ids.forEach { assertTrue(it, it.matches(Regex("^KPT-[0-9A-HJKMNP-TV-Z]{12}$"))) }
        assertNotEquals(SampleData.DEMO_CARD_ID, ids.first())
        assertTrue(KipatIdentityStore.needsOwnProfileId(SampleData.identity))
        assertTrue(KipatIdentityStore.needsOwnProfileId(SampleData.identity.copy(cardId = "")))
        assertFalse(KipatIdentityStore.needsOwnProfileId(SampleData.identity.copy(cardId = ids.first())))

        // The migrated id survives the identity file round trip and is what the phone shares.
        val migrated = SampleData.identity.copy(cardId = ids.first())
        val (back, rev) = KipatIdentityStore.decode(KipatIdentityStore.encode(migrated, 7))!!
        assertEquals(ids.first(), back.cardId)
        assertEquals(7, rev)
        assertEquals(ids.first(), KipatPeerProfile.fromIdentity(back).id)
    }

    @Test
    fun oldDemoIdTagsStillOpenYourOwnDemoProfile() {
        val own = SampleData.identity.copy(cardId = KipatIdentityStore.newProfileId())
        val target = NfcIntentParser.parse("https://kipat.app/c/${SampleData.DEMO_CARD_ID}")!!
        val p = RevealResolver.resolve(target, own, ownCardIsDemo = true, connections = emptyList())
        assertEquals(own.displayName, p.displayName)
    }
}
