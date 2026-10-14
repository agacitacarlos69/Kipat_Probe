package com.kipat.probe

import android.content.Intent
import android.nfc.NdefMessage
import android.nfc.NfcAdapter
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import com.kipat.probe.data.NfcProfileReveal
import com.kipat.probe.data.NfcProfileReveal.toRevealPayload
import com.kipat.probe.model.AuthenticityState
import com.kipat.probe.nfc.NfcIntentParser
import com.kipat.probe.nfc.ParsedNfcIntent
import com.kipat.probe.nfc.profile.KipatProfileMessage
import com.kipat.probe.nfc.profile.Ndef
import com.kipat.probe.nfc.profile.NdefRecordData
import com.kipat.probe.nfc.profile.NfcProfileFormat
import com.kipat.probe.nfc.profile.NfcProfileMode
import com.kipat.probe.nfc.profile.NfcProfileParse
import com.kipat.probe.nfc.profile.NfcProfilePayload
import com.kipat.probe.nfc.profile.NfcProfileType
import com.kipat.probe.nfc.toMessage
import com.kipat.probe.reveal.RevealPayloadCodec
import com.kipat.probe.reveal.RevealSource
import com.kipat.probe.reveal.RevealTemplateId
import com.kipat.probe.reveal.RevealTheme
import com.kipat.probe.reveal.TapDebouncer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Tag → typed parse → RevealPayload → the existing Reveal, without a backend. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w411dp-h891dp-notnight-xxhdpi")
class NfcProfileRevealTest {

    @get:Rule
    val compose = createEmptyComposeRule()

    private val context get() = ApplicationProvider.getApplicationContext<android.content.Context>()
    private val pkg = "com.kipat.probe"
    private fun profileRecord(p: NfcProfilePayload = NfcProfilePayload.SAMPLE) = KipatProfileMessage.records(p, NfcProfileMode.FULL, pkg, includeAar = false).single()

    @Test
    fun embeddedProfileTakesPrecedenceOverCardUri() {
        val uri = Ndef.uri("https://kipat.app/c/KPT-DEMO-0001")
        listOf(listOf(uri, profileRecord()), listOf(profileRecord(), uri, Ndef.aar(pkg))).forEach { records ->
            val r = NfcIntentParser.parseTap(records, "https://kipat.app/c/KPT-DEMO-0001")
            assertTrue(r is ParsedNfcIntent.Profile && r.result is NfcProfileParse.Valid)
        }
        assertTrue(NfcIntentParser.parseTap(listOf(uri), null) is ParsedNfcIntent.Card)
        assertTrue(NfcIntentParser.parseTap(emptyList(), "kipatprobe://tap/TEST123") is ParsedNfcIntent.Card)
        val text = NdefRecordData(Ndef.TNF_WELL_KNOWN, "T".toByteArray(), payload = "\u0002enhello".toByteArray())
        assertTrue(NfcIntentParser.parseTap(listOf(text), null) is ParsedNfcIntent.Unknown)
        assertTrue(NfcIntentParser.parseTap(listOf(Ndef.uri("https://evil.example/c/x")), null) is ParsedNfcIntent.Unknown)
        // Invalid profiles are still profiles (they explain themselves), not silently ignored.
        val bad = Ndef.mime(NfcProfileFormat.MIME_TYPE, "{broken".toByteArray())
        assertTrue((NfcIntentParser.parseTap(listOf(bad, uri), null) as ParsedNfcIntent.Profile).result is NfcProfileParse.Invalid)
    }

    @Test
    fun sampleMapsToUnverifiedTestReveal() {
        val p = NfcProfilePayload.SAMPLE.toRevealPayload(RevealSource.NFC_TAG)
        assertEquals("Vash Puno", p.displayName)
        assertEquals("BS Computer Science", p.subtitle)
        assertEquals("Developer", p.role)
        assertEquals("Polytechnic University of the Philippines", p.organization)
        assertEquals(RevealTemplateId.PRECISION_ORBIT, p.template)
        assertEquals(RevealTheme.NIGHT, p.theme)
        assertEquals(AuthenticityState.UNVERIFIED, p.authenticity)
        assertTrue(p.testProfile)
        assertFalse(p.isDemo)
        assertFalse(p.isOrganization)
        assertEquals("test-vash-puno", p.cardId)
        assertEquals(RevealSource.NFC_TAG, p.source)
    }

    @Test
    fun mappingEdgeCases() {
        val minimal = NfcProfilePayload(displayName = "Vash Puno").toRevealPayload(RevealSource.NFC_TAG)
        assertEquals(RevealTemplateId.DEFAULT, minimal.template)
        assertNull(minimal.subtitle)
        assertEquals(minimal.cardId, NfcProfilePayload(displayName = "Vash Puno").toRevealPayload(RevealSource.NFC_TAG).cardId)
        assertTrue(minimal.cardId!!.startsWith("tp-"))

        val roleOnly = NfcProfilePayload(displayName = "A", role = "Developer").toRevealPayload(RevealSource.NFC_TAG)
        assertEquals("Developer", roleOnly.subtitle)
        assertNull(roleOnly.role)

        val org = NfcProfilePayload(displayName = "Kipat Labs", type = NfcProfileType.ORGANIZATION).toRevealPayload(RevealSource.NFC_TAG)
        assertTrue(org.isOrganization)

        val avatar = NfcProfilePayload(displayName = "A", avatarUrl = "http://example.com/a.png").toRevealPayload(RevealSource.NFC_TAG)
        assertNull("only https avatars are kept", avatar.avatarUri)

        val invalid = NfcProfileReveal.payloadFor(NfcProfileParse.Invalid("malformed JSON", 7), RevealSource.NFC_TAG)
        assertEquals("Kipat profile detected but invalid", invalid.displayName)
        assertEquals(AuthenticityState.UNVERIFIED, invalid.authenticity)
        val unsupported = NfcProfileReveal.payloadFor(NfcProfileParse.UnsupportedVersion(2, 20), RevealSource.NFC_TAG)
        assertEquals("Unsupported Kipat NFC profile version", unsupported.displayName)
    }

    @Test
    fun mappedPayloadSurvivesTheRevealCodec() {
        val p = NfcProfilePayload.SAMPLE.toRevealPayload(RevealSource.NFC_TAG)
        val decoded = RevealPayloadCodec.decode(RevealPayloadCodec.encode(p)::get).payload
        assertEquals(p.displayName, decoded.displayName)
        assertEquals(p.organization, decoded.organization)
        assertEquals("Developer", decoded.role)
        assertTrue(decoded.testProfile)
        assertEquals(AuthenticityState.UNVERIFIED, decoded.authenticity)
    }

    @Test
    fun repeatedTapOfTheSameProfileIsOneTap() {
        val id = NfcProfilePayload.SAMPLE.toRevealPayload(RevealSource.NFC_TAG).cardId
        val d = TapDebouncer()
        d.accept(id, 1_000)
        assertTrue(d.isDuplicate(id, RevealSource.NFC_TAG, 1_800))
        assertFalse(d.isDuplicate(id, RevealSource.NFC_TAG, 2_600))
    }

    @Test
    fun profileMimeTagResolvesToRevealActivity() {
        val intent = Intent(NfcAdapter.ACTION_NDEF_DISCOVERED).setType(NfcProfileFormat.MIME_TYPE)
        val matches = context.packageManager.queryIntentActivities(intent, 0).map { it.activityInfo.name }
        assertEquals(listOf(TapExperienceActivity::class.java.name), matches)
    }

    private fun tap(records: List<NdefRecordData>) = Intent(NfcAdapter.ACTION_NDEF_DISCOVERED)
        .setType(NfcProfileFormat.MIME_TYPE)
        .setClass(context, TapExperienceActivity::class.java)
        .putExtra(NfcAdapter.EXTRA_NDEF_MESSAGES, arrayOf<NdefMessage>(records.toMessage()))

    @Test
    fun physicalTapShowsTestProfileNotVerified() {
        val records = KipatProfileMessage.records(NfcProfilePayload.SAMPLE, NfcProfileMode.FULL, pkg)
        compose.mainClock.autoAdvance = false
        ActivityScenario.launch<TapExperienceActivity>(tap(records)).use {
            compose.mainClock.advanceTimeBy(3_000)
            compose.onNodeWithText("Vash Puno").assertIsDisplayed()
            compose.onNodeWithText("BS Computer Science").assertIsDisplayed()
            compose.onNodeWithText("Developer").assertIsDisplayed()
            compose.onNodeWithText("Polytechnic University of the Philippines").assertIsDisplayed()
            // The trust row is one accessibility node: "Authenticity: Unverified card. … Test profile".
            compose.onNodeWithContentDescription("Unverified card", substring = true).assertIsDisplayed()
            compose.onNodeWithContentDescription("Test profile", substring = true).assertIsDisplayed()
            screenshot("nfc-profile-tap-reveal")
            assertEquals(0, compose.onAllNodesWithContentDescription("Verified card", substring = true).fetchSemanticsNodes().size)
            assertEquals(0, compose.onAllNodesWithText("Verified", substring = true).fetchSemanticsNodes().size)
        }
    }

    @Test
    fun malformedProfileTapExplainsItselfWithoutCrashing() {
        val records = listOf(Ndef.mime(NfcProfileFormat.MIME_TYPE, "{\"v\":1,\"name\":".toByteArray()))
        compose.mainClock.autoAdvance = false
        ActivityScenario.launch<TapExperienceActivity>(tap(records)).use {
            compose.mainClock.advanceTimeBy(3_000)
            compose.onNodeWithText("Kipat profile detected but invalid").assertIsDisplayed()
        }
    }

    private fun screenshot(name: String) {
        if (System.getProperty("kipat.screenshots") != "true") return
        val dir = java.io.File(System.getProperty("user.dir"), "build/outputs/kipat-screenshots").apply { mkdirs() }
        val bmp = compose.onRoot().captureToImage().asAndroidBitmap()
        java.io.File(dir, "$name.png").outputStream().use { bmp.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
    }
}
