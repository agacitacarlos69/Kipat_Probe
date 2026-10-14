package com.kipat.probe

import android.content.ComponentName
import android.content.pm.PackageManager
import android.nfc.cardemulation.HostApduService
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performScrollTo
import androidx.test.core.app.ApplicationProvider
import com.kipat.probe.data.SampleData
import com.kipat.probe.model.ProfileField
import com.kipat.probe.model.ProfileTemplateId
import com.kipat.probe.nfc.peer.KipatApduProcessor
import com.kipat.probe.nfc.peer.KipatApduProtocol
import com.kipat.probe.nfc.peer.KipatHceService
import com.kipat.probe.nfc.peer.KipatPeerCapabilities
import com.kipat.probe.nfc.peer.KipatPeerProfile
import com.kipat.probe.nfc.peer.KipatPeerSessions
import com.kipat.probe.nfc.peer.PresentState
import com.kipat.probe.nfc.profile.NfcProfilePayload
import com.kipat.probe.reveal.RevealPayloadCodec
import com.kipat.probe.reveal.RevealSource
import com.kipat.probe.ui.screens.share.NearbyShareScreen
import com.kipat.probe.ui.theme.KipatTheme
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.xmlpull.v1.XmlPullParser

/** Phone-to-phone NFC: registration, the public-profile boundary, capability states and the share UI (off-device). */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w411dp-h891dp-notnight-xxhdpi")
class NearbyShareTest {

    @get:Rule
    val compose = createComposeRule()

    private val context get() = ApplicationProvider.getApplicationContext<android.content.Context>()

    @After
    fun tearDown() = KipatPeerSessions.presenter.cancel()

    @Test
    fun hceServiceIsRegisteredForTheKipatAidAsCategoryOther() {
        val info = context.packageManager.getServiceInfo(ComponentName(context, KipatHceService::class.java), PackageManager.GET_META_DATA)
        assertEquals("android.permission.BIND_NFC_SERVICE", info.permission)
        assertTrue(info.exported)
        val res = info.metaData.getInt(HostApduService.SERVICE_META_DATA)
        val xml = context.resources.getXml(res)
        val aids = mutableListOf<String>()
        var category: String? = null
        var unlock: String? = null
        while (xml.next() != XmlPullParser.END_DOCUMENT) {
            if (xml.eventType != XmlPullParser.START_TAG) continue
            val ns = "http://schemas.android.com/apk/res/android"
            when (xml.name) {
                "host-apdu-service" -> unlock = xml.getAttributeValue(ns, "requireDeviceUnlock")
                "aid-group" -> category = xml.getAttributeValue(ns, "category")
                "aid-filter" -> aids += xml.getAttributeValue(ns, "name")
            }
        }
        assertEquals(listOf(KipatApduProtocol.AID_HEX), aids)
        assertEquals("other", category)
        assertEquals("true", unlock)
    }

    @Test
    fun sharedProfileUsesOnlyPublishedFields() {
        val identity = SampleData.identity // Student Minimal: role "Founder" and company "Kipat" are not published
        assertEquals(ProfileTemplateId.STUDENT_MINIMAL, identity.profileTemplate)
        val p = KipatPeerProfile.fromIdentity(identity)
        assertEquals("Vash Puno", p.displayName)
        assertEquals("BS Computer Science", p.headline)
        assertEquals("Polytechnic University of the Philippines", p.organization)
        assertEquals("https://github.com/vashpuno", p.github)
        assertNull("unpublished role is not shared", p.role)
        val json = String(KipatPeerProfile.toBytes(p), Charsets.UTF_8)
        assertFalse(json, json.contains("Founder") || json.contains("\"Kipat\""))
        assertEquals(identity.cardId, p.id)
        // Changing the template changes what is shared, exactly as for the Reveal.
        val pro = KipatPeerProfile.fromIdentity(identity.copy(profileTemplate = ProfileTemplateId.PROFESSIONAL_MINIMAL))
        assertEquals("Founder", pro.headline)
        assertEquals("Kipat", pro.organization)
        val creator = KipatPeerProfile.fromIdentity(identity.copy(profileTemplate = ProfileTemplateId.CREATOR, fields = identity.fields + (ProfileField.WEBSITE to "kipat.app")))
        assertNull("Creator publishes no organisation", creator.organization)
        assertEquals("https://kipat.app", creator.website)
    }

    @Test
    fun capabilityMessages() {
        assertEquals("This phone doesn't have NFC, so it can't share by touch.", KipatPeerCapabilities(false, false, false).notice)
        assertEquals("Turn on NFC to share by touch.", KipatPeerCapabilities(true, false, true).notice)
        val noHce = KipatPeerCapabilities(hasNfc = true, nfcEnabled = true, hasHce = false)
        assertTrue(noHce.receiveReady)
        assertFalse(noHce.canPresent)
        assertEquals("This phone can receive a Kipat but can't present one over NFC.", noHce.notice)
        assertNull(KipatPeerCapabilities(true, true, true).notice)
    }

    @Test
    fun withoutNfcBothRolesAreOffAndExplained() {
        compose.setContent { KipatTheme { NearbyShareScreen(onBack = {}) } }
        compose.onNodeWithText("Touch phones to share").assertIsDisplayed()
        compose.onNodeWithText("This phone doesn't have NFC, so it can't share by touch.").assertIsDisplayed()
        compose.onNodeWithText("Present my Kipat").assertIsNotEnabled()
        compose.onNodeWithText("Receive a Kipat").assertIsNotEnabled()
        compose.onNodeWithText("Send my profile back").assertIsDisplayed()
        screenshot("nfc-peer-choose-role")
        assertNoProtocolJargon()
    }

    @Test
    fun presentingShowsWhatIsSharedAndTheCountdown() {
        KipatPeerSessions.presenter.arm(KipatPeerProfile.fromIdentity(SampleData.identity))
        compose.setContent { KipatTheme { NearbyShareScreen(onBack = {}) } }
        compose.onNodeWithText("Ready to share").assertIsDisplayed()
        compose.onNodeWithText("Hold the back of this phone near another Kipat phone.").assertIsDisplayed()
        compose.onNodeWithText("Vash Puno").assertIsDisplayed()
        screenshot("nfc-peer-ready-to-share")
        compose.onNodeWithText("Sharing ends in").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Cancel").assertIsDisplayed()
        assertNoProtocolJargon()
    }

    @Test
    fun presenterOpensTheReceivedKipatOnceAndDoesNotConnect() {
        val store = KipatPeerSessions.presenter
        store.arm(KipatPeerProfile.fromIdentity(SampleData.identity))
        compose.setContent { KipatTheme { NearbyShareScreen(onBack = {}) } }
        // Phone B uploads its profile through the real processor.
        val peer = NfcProfilePayload(id = "KPT-PEER-1", displayName = "José Dela Cruz", headline = "BS Architecture")
        val bytes = KipatPeerProfile.toBytes(peer)
        val p = KipatApduProcessor(store)
        p.process(KipatApduProtocol.select())
        p.process(KipatApduProtocol.hello(ByteArray(16) { 7 }))
        p.process(KipatApduProtocol.beginUpload(bytes.size, KipatApduProtocol.sha256(bytes)))
        p.process(KipatApduProtocol.writeChunk(0, bytes))
        p.process(KipatApduProtocol.commitUpload())
        p.process(KipatApduProtocol.complete())
        compose.waitForIdle()

        compose.onNodeWithText("José received").assertIsDisplayed()
        val app = shadowOf(context as android.app.Application)
        val started = app.nextStartedActivity
        assertEquals(TapExperienceActivity::class.java.name, started.component?.className)
        val payload = RevealPayloadCodec.decode { started.getStringExtra(it) }.payload
        assertEquals("José Dela Cruz", payload.displayName)
        assertEquals(RevealSource.PEER_NFC, payload.source)
        assertFalse(payload.testProfile)
        assertNull("exactly one Reveal", app.nextStartedActivity)
        assertFalse("touching is not connecting", com.kipat.probe.data.KipatRepository.isConnected("KPT-PEER-1"))
        assertTrue(store.state.value is PresentState.Success)
    }

    private fun screenshot(name: String) {
        if (System.getProperty("kipat.screenshots") != "true") return
        val dir = java.io.File(System.getProperty("user.dir"), "build/outputs/kipat-screenshots").apply { mkdirs() }
        val bmp = compose.onRoot().captureToImage().asAndroidBitmap()
        java.io.File(dir, "$name.png").outputStream().use { bmp.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
    }

    /** Consumer screens never show protocol words. */
    private fun assertNoProtocolJargon() {
        val words = mutableListOf<String>()
        fun walk(n: SemanticsNode) {
            n.config.getOrNull(SemanticsProperties.Text)?.forEach { words += it.text }
            n.children.forEach(::walk)
        }
        walk(compose.onRoot(useUnmergedTree = true).fetchSemanticsNode())
        val text = words.joinToString(" ")
        listOf("APDU", "AID", "ISO-DEP", "IsoDep", "HCE", "Host Card", "9000", "SELECT").forEach {
            assertFalse("'$it' in consumer UI: $text", text.contains(it))
        }
    }
}
