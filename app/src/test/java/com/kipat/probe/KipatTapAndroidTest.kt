package com.kipat.probe

import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.nfc.NdefMessage
import android.nfc.NfcAdapter
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import com.kipat.probe.data.KipatRepository
import com.kipat.probe.data.SampleData
import com.kipat.probe.nfc.peer.KipatApduProcessor
import com.kipat.probe.nfc.peer.KipatApduProtocol
import com.kipat.probe.nfc.peer.KipatHceRouter
import com.kipat.probe.nfc.peer.KipatPeerBootstrap
import com.kipat.probe.nfc.peer.KipatPeerProfile
import com.kipat.probe.nfc.peer.KipatPeerSessions
import com.kipat.probe.nfc.peer.KipatTapLauncher
import com.kipat.probe.nfc.peer.KipatTapStore
import com.kipat.probe.nfc.peer.KipatType4Tag
import com.kipat.probe.nfc.peer.PeerSessionStore
import com.kipat.probe.nfc.peer.Sw
import com.kipat.probe.nfc.profile.NfcProfilePayload
import com.kipat.probe.nfc.toMessage
import com.kipat.probe.reveal.RevealPayloadCodec
import com.kipat.probe.reveal.RevealSource
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/** Kipat Tap on the Android side, off-device: snapshot, eligibility, manifest, initiator and responder routing. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w411dp-h891dp-notnight-xxhdpi")
class KipatTapAndroidTest {

    @get:Rule
    val compose = createEmptyComposeRule()

    private val context get() = ApplicationProvider.getApplicationContext<android.content.Context>()
    private val own get() = KipatPeerProfile.fromIdentity(SampleData.identity)
    private val app get() = shadowOf(context as android.app.Application)

    @Before
    fun reset() {
        context.getSharedPreferences("kipat_tap", 0).edit().clear().commit()
        File(context.filesDir, "kipat_tap_profile.json").delete()
        KipatTapStore.clearCache()
        KipatPeerSessions.attach(context)
    }

    @After
    fun tearDown() = KipatPeerSessions.presenter.cancel()

    // ------------------------------------------------------------------ snapshot + eligibility

    @Test
    fun publicSnapshotSurvivesAColdProcessAndIsPublicOnly() {
        KipatTapStore.publish(context, own)
        KipatTapStore.clearCache() // as if the HCE service started in a fresh process
        val snap = KipatTapStore.snapshot(context)!!
        assertEquals(own, snap.profile)
        assertArrayEquals(KipatApduProtocol.sha256(snap.bytes), snap.sha256)
        val json = File(context.filesDir, "kipat_tap_profile.json").readText()
        assertFalse("unpublished role and company never stored", json.contains("Founder") || json.contains("\"Kipat\""))
        // Editing the profile refreshes the snapshot.
        KipatTapStore.publish(context, own.copy(headline = "BS Information Technology"))
        KipatTapStore.clearCache()
        assertEquals("BS Information Technology", KipatTapStore.snapshot(context)!!.profile.headline)
    }

    @Test
    fun ambientEligibilityFollowsTheSettingAndSnapshot() {
        assertNull("off by default until the person enables it", KipatTapStore.eligibleProfile(context))
        KipatTapStore.setEnabled(context, true)
        assertNull("enabled but nothing published", KipatTapStore.eligibleProfile(context))
        KipatTapStore.publish(context, own)
        assertNotNull(KipatTapStore.eligibleProfile(context))
        KipatTapStore.setEnabled(context, false)
        assertNull(KipatTapStore.eligibleProfile(context))
        assertTrue(KipatTapStore.wasPrompted(context))
    }

    @Test
    fun hceExposesTheBootstrapOnlyWhenKipatTapIsOn() {
        val store = PeerSessionStore(clock = { System.nanoTime() / 1_000_000 }, ambientProfile = { KipatTapStore.eligibleProfile(context) })
        val router = KipatHceRouter(KipatType4Tag(store, context.packageName), KipatApduProcessor(store))
        val selectNdef = KipatApduProtocol.hex("00A4040007D276000085010100")
        KipatTapStore.publish(context, own)
        assertEquals(Sw.NOT_FOUND.code, KipatApduProtocol.parseResponse(router.process(selectNdef))!!.sw)
        KipatTapStore.setEnabled(context, true)
        assertEquals(Sw.OK.code, KipatApduProtocol.parseResponse(router.process(selectNdef))!!.sw)
    }

    // ------------------------------------------------------------------ manifest (Android 17 rule)

    @Test
    fun nfcDispatchUsesTheApiGatedAliases() {
        val pm = context.packageManager
        val legacy = pm.getActivityInfo(ComponentName(context, "com.kipat.probe.NfcTapEntry"), PackageManager.MATCH_DISABLED_COMPONENTS)
        val protected = pm.getActivityInfo(ComponentName(context, "com.kipat.probe.NfcTapEntryProtected"), PackageManager.MATCH_DISABLED_COMPONENTS)
        assertEquals(TapExperienceActivity::class.java.name, legacy.targetActivity)
        assertEquals(TapExperienceActivity::class.java.name, protected.targetActivity)
        assertEquals("android.permission.DISPATCH_NFC_MESSAGE", protected.permission)
        assertNull(legacy.permission)
        // Robolectric runs API 36: the unprotected alias is the enabled one.
        assertTrue(legacy.enabled)
        assertFalse(protected.enabled)
        val peer = Intent(NfcAdapter.ACTION_NDEF_DISCOVERED).setType(KipatPeerBootstrap.MIME_TYPE)
        assertEquals(listOf("com.kipat.probe.NfcTapEntry"), pm.queryIntentActivities(peer, 0).map { it.activityInfo.name })
    }

    // ------------------------------------------------------------------ initiator routing

    private fun bootstrapIntent() = Intent(NfcAdapter.ACTION_NDEF_DISCOVERED)
        .setType(KipatPeerBootstrap.MIME_TYPE)
        .setClass(context, TapExperienceActivity::class.java)
        .putExtra(NfcAdapter.EXTRA_NDEF_MESSAGES, arrayOf<NdefMessage>(KipatPeerBootstrap.records(ByteArray(16), context.packageName).toMessage()))

    @Test
    fun bootstrapWithKipatTapOffExplainsAndNeverExchanges() {
        compose.mainClock.autoAdvance = false
        ActivityScenario.launch<TapExperienceActivity>(bootstrapIntent()).use {
            compose.mainClock.advanceTimeBy(500)
            compose.onNodeWithText("Kipat Tap is off. Turn it on in Kipat to exchange profiles.").assertIsDisplayed()
            assertEquals("no Reveal surface", 0, compose.onAllNodesWithText("Connect").fetchSemanticsNodes().size)
        }
    }

    @Test
    fun bootstrapWithoutAUsableLinkFallsBackWithoutCrashing() {
        KipatTapStore.publish(context, own)
        KipatTapStore.setEnabled(context, true)
        compose.mainClock.autoAdvance = false
        ActivityScenario.launch<TapExperienceActivity>(bootstrapIntent()).use {
            compose.mainClock.advanceTimeBy(500)
            // No Tag in the intent → reader-mode reacquisition → this test device has no NFC.
            compose.onNodeWithText("This phone doesn't have NFC, so it can't receive a Kipat by touch.").assertIsDisplayed()
        }
    }

    // ------------------------------------------------------------------ responder delivery

    @Test
    fun responderOpensExactlyOneNearbyRevealAndRecordsADiscoveryOnly() {
        KipatTapStore.publish(context, own)
        val peer = NfcProfilePayload(id = "KPT-AMB-1", displayName = "Ana Cruz", headline = "BS Nursing")
        KipatTapLauncher.deliverAsResponder(context, peer, ByteArray(16), ByteArray(16) { 1 }, 120)
        val started = app.nextStartedActivity
        assertEquals(TapExperienceActivity::class.java.name, started.component?.className)
        assertTrue("its own task, above the previous app", started.flags and Intent.FLAG_ACTIVITY_NEW_TASK != 0)
        assertTrue(started.getLongExtra(KipatTapLauncher.EXTRA_EXCHANGE_ID, -1) > 0)
        val payload = RevealPayloadCodec.decode { started.getStringExtra(it) }.payload
        assertEquals("Ana Cruz", payload.displayName)
        assertEquals(RevealSource.PEER_NFC, payload.source)
        assertTrue(KipatRepository.recentTaps.value.any { it.cardId == "KPT-AMB-1" })
        assertFalse("touching is not connecting", KipatRepository.isConnected("KPT-AMB-1"))

        // The same peer again in the same touch (re-read, role flip): nothing new.
        KipatTapLauncher.deliverAsResponder(context, peer, ByteArray(16), ByteArray(16) { 2 }, 120)
        assertNull(app.nextStartedActivity)
        // Your own profile coming back never opens a Reveal.
        KipatTapLauncher.deliverAsResponder(context, own, ByteArray(16), ByteArray(16) { 3 }, 120)
        assertNull(app.nextStartedActivity)
    }

    // ------------------------------------------------------------------ consumer UX

    @Test
    fun shareScreenHasKipatTapAndNoRoleButtons() {
        val main = Intent(context, MainActivity::class.java).putExtra(MainActivity.EXTRA_DESTINATION, "share")
        ActivityScenario.launch<MainActivity>(main).use {
            compose.waitForIdle()
            compose.onNodeWithText("Kipat Tap").assertIsDisplayed()
            listOf("Present my Kipat", "Receive a Kipat", "Send my profile back", "Share with another Kipat").forEach {
                assertEquals("'$it' is not a consumer control", 0, compose.onAllNodesWithText(it).fetchSemanticsNodes().size)
            }
            listOf("APDU", "AID", "HCE", "ISO-DEP", "IsoDep", "Type 4").forEach {
                assertEquals("'$it' is protocol jargon", 0, compose.onAllNodesWithText(it, substring = true).fetchSemanticsNodes().size)
            }
        }
    }

    @Test
    fun encounterWindowIsNotTheOldSixtySecondSession() {
        assertTrue(PeerSessionStore.ENCOUNTER_WINDOW_MS < PeerSessionStore.PRESENT_WINDOW_MS)
    }
}
