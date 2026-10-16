package com.kipat.probe

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.nfc.NdefMessage
import android.nfc.NfcAdapter
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import com.kipat.probe.cards.CardLinkPlan
import com.kipat.probe.cards.CardResolution
import com.kipat.probe.cards.KipatCardId
import com.kipat.probe.cards.KipatCards
import com.kipat.probe.cards.LinkDecision
import com.kipat.probe.cards.LinkedCardMessage
import com.kipat.probe.cards.LinkedCardReveal
import com.kipat.probe.cards.LinkedCardStatus
import com.kipat.probe.cards.LinkedKipatCard
import com.kipat.probe.data.KipatIdentityStore
import com.kipat.probe.data.KipatRepository
import com.kipat.probe.data.NfcProfileReveal.toRevealPayload
import com.kipat.probe.data.SampleData
import com.kipat.probe.data.toRevealPayload
import com.kipat.probe.model.AuthenticityState
import com.kipat.probe.model.Identity
import com.kipat.probe.model.ProfileField
import com.kipat.probe.nfc.peer.KipatTapLauncher
import com.kipat.probe.nfc.peer.KipatTapStore
import com.kipat.probe.nfc.profile.Ndef
import com.kipat.probe.nfc.profile.NdefRecordData
import com.kipat.probe.nfc.profile.TagSnapshot
import com.kipat.probe.nfc.toMessage
import com.kipat.probe.reveal.RevealPayload
import com.kipat.probe.reveal.RevealSource
import com.kipat.probe.reveal.RevealTemplateId
import com.kipat.probe.reveal.RevealTheme
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.security.SecureRandom

/**
 * One identity, many transports (docs/nfc-card-identity-sync.md). Every "change" test taps the
 * SAME linked card before and after the edit and checks the card's bytes never change: the Reveal
 * follows the identity, not the card. LOCAL PROTOTYPE parity only (this phone's registry and identity).
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w411dp-h891dp-notnight-xxhdpi")
class LinkedCardIdentityTest {

    @get:Rule
    val compose = createEmptyComposeRule()

    private val context get() = ApplicationProvider.getApplicationContext<Context>()
    private val identity: Identity get() = KipatRepository.identity.value

    @Before
    fun reset() {
        listOf("kipat_identity.json", "kipat_cards.json").forEach { File(context.filesDir, it).delete() }
        KipatIdentityStore.resetForTest()
        KipatCards.resetForTest()
        KipatRepository.restoreIdentity(SampleData.identity, 0)
        KipatIdentityStore.init(context, persistCurrent = true)
        KipatCards.init(context)
    }

    @After
    fun tearDown() {
        KipatIdentityStore.resetForTest()
        KipatCards.resetForTest()
        KipatRepository.restoreIdentity(SampleData.identity, 0)
    }

    private fun link(status: LinkedCardStatus = LinkedCardStatus.ACTIVE, owner: String = identity.id): LinkedKipatCard =
        LinkedKipatCard(KipatCardId.generate(), owner, "Kipat card", status, createdAt = 1L, lastSyncedAt = 1L).also(KipatCards::add)

    private fun cardBytes(card: LinkedKipatCard): ByteArray = LinkedCardMessage.records(card.cardId, context.packageName).toMessage().toByteArray()

    private fun resolve(cardId: String): CardResolution = runBlocking { KipatCards.resolver(context).resolve(cardId) }

    private fun tap(card: LinkedKipatCard): RevealPayload = LinkedCardReveal.payloadFor(resolve(card.cardId))

    private fun edit(change: (Identity) -> Identity) = KipatRepository.publishIdentity(change(identity))

    // ------------------------------------------------------------------ §33–37 mandatory change tests

    @Test
    fun revealChangeAppliesToTheSameCardWithoutRewrite() {
        edit { it.copy(revealTemplate = RevealTemplateId.CONTACT_RIPPLE) }
        val card = link()
        val before = cardBytes(card)
        assertEquals(RevealTemplateId.CONTACT_RIPPLE, tap(card).template)

        edit { it.copy(revealTemplate = RevealTemplateId.PRECISION_ORBIT) }

        assertEquals(RevealTemplateId.PRECISION_ORBIT, tap(card).template)
        assertArrayEquals("no rewrite: the card holds the same bytes", before, cardBytes(card))
    }

    @Test
    fun accentChangeAppliesToTheSameCardWithoutRewrite() {
        edit { it.copy(revealTheme = RevealTheme.NIGHT) }
        val card = link()
        val before = cardBytes(card)
        assertEquals(RevealTheme.NIGHT, tap(card).theme)
        val other = RevealTheme.entries.first { it != RevealTheme.NIGHT }

        edit { it.copy(revealTheme = other) }

        assertEquals(other, tap(card).theme)
        assertArrayEquals(before, cardBytes(card))
    }

    @Test
    fun profileChangeAppliesToTheSameCardWithoutRewrite() {
        val card = link()
        val before = cardBytes(card)
        val r0 = (resolve(card.cardId) as CardResolution.Resolved).revision

        edit { it.copy(fields = it.fields + (ProfileField.DISPLAY_NAME to "Vash P. Puno") + (ProfileField.PROGRAM to "BS Information Technology")) }

        val after = resolve(card.cardId) as CardResolution.Resolved
        assertEquals("Vash P. Puno", LinkedCardReveal.payloadFor(after).displayName)
        assertEquals("BS Information Technology", LinkedCardReveal.payloadFor(after).subtitle)
        assertEquals("revision increments on change", r0 + 1, after.revision)
        assertArrayEquals(before, cardBytes(card))
    }

    @Test
    fun publishedFieldsChangeAppliesToTheSameCardWithoutRewrite() {
        val card = link()
        val org = identity.organization
        assertEquals(org, tap(card).organization)

        // Unpublishing (clearing) a field removes it from the card's Reveal on the next tap.
        edit { it.copy(fields = it.fields - ProfileField.UNIVERSITY) }

        assertNull(tap(card).organization)
        val snapshot = (resolve(card.cardId) as CardResolution.Resolved).snapshot
        assertFalse("only published fields are served", String(snapshot.bytes).contains(org))
    }

    @Test
    fun phoneTapAndCardTapShowTheSameReveal() {
        val card = link()
        val phone = KipatTapLauncher.payloadFor(KipatTapStore.snapshot(context)!!.profile)
        val viaCard = tap(card)
        assertEquals(RevealSource.PEER_NFC, phone.source)
        assertEquals(RevealSource.NFC_TAG, viaCard.source)
        // Identical apart from how it arrived.
        assertEquals(phone.copy(source = RevealSource.NFC_TAG, primaryCta = viaCard.primaryCta, secondaryCta = viaCard.secondaryCta), viaCard)
        assertEquals(AuthenticityState.UNVERIFIED, viaCard.authenticity)
        assertEquals("both resolve the same snapshot revision", KipatTapStore.snapshot(context)!!.revision, (resolve(card.cardId) as CardResolution.Resolved).revision)
        // And the in-app preview reads the same identity.
        val preview = identity.toRevealPayload(RevealSource.PREVIEW, isDemo = false, authenticity = AuthenticityState.UNVERIFIED)
        assertEquals(preview.displayName, viaCard.displayName)
        assertEquals(preview.template, viaCard.template)
        assertEquals(preview.theme, viaCard.theme)
    }

    @Test
    fun hceSnapshotRefreshesImmediatelyAfterAnEdit() {
        val before = KipatTapStore.snapshot(context)!!
        edit { it.copy(revealTemplate = RevealTemplateId.APERTURE) }
        val after = KipatTapStore.snapshot(context)!!
        assertEquals(before.revision + 1, after.revision)
        assertEquals(RevealTemplateId.APERTURE, after.profile.reveal)
        assertFalse(before.sha256.contentEquals(after.sha256))
    }

    @Test
    fun multipleCardsShareOneIdentity() {
        val a = link()
        val b = link()
        assertNotEquals(a.cardId, b.cardId)
        edit { it.copy(revealTemplate = RevealTemplateId.MINIMAL_FADE) }
        assertEquals(RevealTemplateId.MINIMAL_FADE, tap(a).template)
        assertEquals(tap(a), tap(b).copy(cardId = tap(a).cardId))
        assertEquals(2, KipatCards.forIdentity(identity.id).size)
    }

    // ------------------------------------------------------------------ status, unknown, malformed

    @Test
    fun disabledCardShowsNoProfileAndCanBeTurnedBackOn() {
        val card = link()
        assertTrue(KipatCards.setStatus(card.cardId, LinkedCardStatus.DISABLED))
        assertTrue(resolve(card.cardId) is CardResolution.Disabled)
        val shown = tap(card)
        assertEquals("Kipat card unavailable", shown.displayName)
        assertFalse(shown.canConnect)
        assertFalse(shown.displayName.contains(identity.displayName))
        KipatCards.setStatus(card.cardId, LinkedCardStatus.ACTIVE)
        assertTrue(resolve(card.cardId) is CardResolution.Resolved)
    }

    @Test
    fun revokedCardStaysRevoked() {
        val card = link()
        KipatCards.setStatus(card.cardId, LinkedCardStatus.REVOKED)
        assertFalse("removed is final", KipatCards.setStatus(card.cardId, LinkedCardStatus.ACTIVE))
        assertEquals(AuthenticityState.REVOKED, tap(card).authenticity)
    }

    @Test
    fun unknownAndMalformedCardsNeverShowAProfile() {
        assertTrue(resolve(KipatCardId.generate()) is CardResolution.Unknown)
        assertTrue(resolve("kc_short") is CardResolution.Malformed)
        assertTrue(resolve("kc_ILLEGALCHARS!!!!") is CardResolution.Malformed)
        // A card linked to an identity that isn't on this phone (future multiple identities).
        val foreign = link(owner = "someone-else")
        assertTrue(resolve(foreign.cardId) is CardResolution.ProfileUnavailable)
    }

    @Test
    fun cardIdsAreOpaqueRandomAndValidated() {
        val ids = (1..500).map { KipatCardId.generate(SecureRandom()) }.toSet()
        assertEquals(500, ids.size)
        ids.forEach { assertTrue(it, KipatCardId.isValid(it)) }
        assertFalse(KipatCardId.isValid("kc_" + "a".repeat(15)))
        assertFalse(KipatCardId.isValid("KPT-DEMO-0001"))
        assertTrue(KipatCardId.masked("kc_0123456789abb871").endsWith("B871"))
    }

    @Test
    fun registryAndIdentitySurviveAColdProcess() {
        val card = link()
        edit { it.copy(revealTemplate = RevealTemplateId.SPATIAL_CARD) }
        KipatCards.resetForTest()
        KipatIdentityStore.resetForTest()
        KipatRepository.restoreIdentity(SampleData.identity, 0)
        // As in TapExperienceActivity started by a card tap in a fresh process.
        KipatIdentityStore.init(context)
        assertEquals(RevealTemplateId.SPATIAL_CARD, tap(card).template)
        assertEquals(1, KipatCards.cards.value.size)
    }

    @Test
    fun unknownStoredRevealFallsBackToTheDefault() {
        val json = KipatIdentityStore.encode(identity, 4).replace("\"${identity.revealTemplate.stableId}\"", "\"holo_vortex\"")
        val (decoded, revision) = KipatIdentityStore.decode(json)!!
        assertEquals(RevealTemplateId.DEFAULT, decoded.revealTemplate)
        assertEquals(4, revision)
        assertEquals("contact_ripple", RevealTemplateId.CONTACT_RIPPLE.stableId)
        RevealTemplateId.entries.forEach { assertEquals(it, RevealTemplateId.fromStableId(it.stableId)) }
    }

    // ------------------------------------------------------------------ card contents and linking

    @Test
    fun linkedCardHoldsOnlyTheRouteAndFitsAnNtag213() {
        val id = "kc_0123456789abcdef"
        val records = LinkedCardMessage.records(id, context.packageName)
        assertEquals("https://kipat.app/c/kc_0123456789abcdef", records[0].uriOrNull())
        val text = records.joinToString("") { String(it.payload) }
        assertFalse("no name on the card", text.contains(identity.displayName))
        assertFalse("no template on the card", text.contains("t=") || text.contains(identity.revealTemplate.stableId))
        val size = LinkedCardMessage.size(id, context.packageName)
        assertEquals(size, NdefMessage(records.toMessage().records).byteArrayLength)
        assertEquals(69, size)
        assertTrue("fits NTAG213 (137 bytes usable)", size <= 137)
        assertEquals(id, LinkedCardMessage.cardIdIn(records))
    }

    private fun tag(records: List<NdefRecordData>, writable: Boolean = true, max: Int = 137) =
        TagSnapshot("04:AA", listOf("Ndef"), "org.nfcforum.ndef.type2", max, writable, false, records)

    @Test
    fun linkingRefusesSomeoneElsesCard() {
        val mine = link()
        val other = link(owner = "someone-else")
        fun decide(t: TagSnapshot, pending: String? = null, confirmed: Boolean = false) =
            CardLinkPlan.decide(t, 69, identity.id, KipatCards::find, pending, confirmed)

        assertEquals(LinkDecision.LinkedElsewhere, decide(tag(LinkedCardMessage.records(other.cardId, "x"))))
        assertEquals("linked on another phone", LinkDecision.LinkedElsewhere, decide(tag(LinkedCardMessage.records(KipatCardId.generate(), "x"))))
        assertEquals("ownership before writability", LinkDecision.LinkedElsewhere, decide(tag(LinkedCardMessage.records(other.cardId, "x"), writable = false)))
        assertTrue(decide(tag(LinkedCardMessage.records(mine.cardId, "x"))) is LinkDecision.AlreadyLinked)
        val pending = KipatCardId.generate()
        assertEquals(LinkDecision.ResumePending(pending), decide(tag(LinkedCardMessage.records(pending, "x")), pending))
        assertEquals(LinkDecision.Proceed, decide(tag(emptyList())))
        assertTrue(decide(tag(listOf(Ndef.uri("https://example.com")))) is LinkDecision.ConfirmReplace)
        assertEquals(LinkDecision.ReadOnly, decide(tag(emptyList(), writable = false)))
        assertEquals(LinkDecision.TooLarge(69, 48), decide(tag(emptyList(), max = 48)))
        KipatCards.setStatus(mine.cardId, LinkedCardStatus.REVOKED)
        assertTrue("a removed card of mine can be linked again", decide(tag(LinkedCardMessage.records(mine.cardId, "x"))) is LinkDecision.ConfirmReplace)
    }

    @Test
    fun readBackMustMatchExactly() {
        val records = LinkedCardMessage.records("kc_0123456789abcdef", "com.kipat.probe")
        assertTrue(LinkedCardMessage.matches(records, LinkedCardMessage.records("kc_0123456789abcdef", "com.kipat.probe")))
        assertFalse(LinkedCardMessage.matches(records, LinkedCardMessage.records("kc_0123456789abcdeg", "com.kipat.probe")))
        assertFalse(LinkedCardMessage.matches(records, records.take(1)))
        assertFalse(LinkedCardMessage.matches(records, null))
    }

    // ------------------------------------------------------------------ the real tap path

    private fun cardIntent(card: LinkedKipatCard) = Intent(NfcAdapter.ACTION_NDEF_DISCOVERED)
        .setData(Uri.parse(KipatCardId.uri(card.cardId)))
        .setClass(context, TapExperienceActivity::class.java)
        .putExtra(NfcAdapter.EXTRA_NDEF_MESSAGES, arrayOf<NdefMessage>(LinkedCardMessage.records(card.cardId, context.packageName).toMessage()))

    @Test
    fun tappingTheLinkedCardOpensTheCurrentIdentityAndRecordsLastSeen() {
        val card = link()
        edit { it.copy(fields = it.fields + (ProfileField.DISPLAY_NAME to "Vash Card Test")) }
        compose.mainClock.autoAdvance = false
        ActivityScenario.launch<TapExperienceActivity>(cardIntent(card)).use {
            compose.mainClock.advanceTimeBy(3_000)
            compose.onNodeWithText("Vash Card Test").assertIsDisplayed()
        }
        assertTrue(KipatCards.find(card.cardId)!!.lastSeenAt != null)
    }

    @Test
    fun tappingADisabledCardShowsItIsUnavailable() {
        val card = link()
        KipatCards.setStatus(card.cardId, LinkedCardStatus.DISABLED)
        compose.mainClock.autoAdvance = false
        ActivityScenario.launch<TapExperienceActivity>(cardIntent(card)).use {
            compose.mainClock.advanceTimeBy(3_000)
            compose.onNodeWithText("Kipat card unavailable").assertIsDisplayed()
            assertEquals(0, compose.onAllNodesWithText(identity.displayName).fetchSemanticsNodes().size)
        }
        assertNull("a turned-off card doesn't count as seen", KipatCards.find(card.cardId)!!.lastSeenAt)
    }

    @Test
    fun selfContainedTestCardKeepsItsOwnSnapshot() {
        // A self-contained card carries its profile, so it does NOT follow identity edits (by design).
        val written = KipatTapStore.snapshot(context)!!.profile
        edit { it.copy(revealTemplate = RevealTemplateId.LINE_ASSEMBLY) }
        val shown = written.toRevealPayload(RevealSource.NFC_TAG)
        assertNotEquals(RevealTemplateId.LINE_ASSEMBLY, shown.template)
        assertTrue(shown.testProfile)
    }
}
