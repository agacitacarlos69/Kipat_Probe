package com.kipat.probe

import androidx.test.core.app.ApplicationProvider
import com.kipat.probe.pilot.BuildMetadata
import com.kipat.probe.pilot.CardTestRecord
import com.kipat.probe.pilot.DeviceSnapshot
import com.kipat.probe.pilot.PeerSeen
import com.kipat.probe.pilot.PilotCheck
import com.kipat.probe.pilot.PilotReport
import com.kipat.probe.pilot.PilotState
import com.kipat.probe.pilot.PilotStore
import com.kipat.probe.pilot.PilotTracker
import com.kipat.probe.pilot.ProfileSnapshot
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.ByteArrayInputStream
import java.util.zip.ZipInputStream

/** Build metadata and the exported diagnostic (Robolectric for org.json and BuildConfig). */
@RunWith(RobolectricTestRunner::class)
class PilotReportTest {

    private var mono = 1_000L
    private val tracker = PilotTracker({ mono }, { 1_760_700_000_000L + mono })

    private fun state(): PilotState {
        listOf("ndef_dispatched" to "", "hce_aid_selected" to "ok", "peer_hello" to "v=1", "profile_download_complete" to "bytes=820",
            "peer_upload_started" to "bytes=790", "peer_link_lost" to "during_upload").forEach { (n, d) ->
            mono += 40; tracker.onEvent(n, d, "KipatPeerReader")
        }
        return PilotState(
            build = BuildMetadata("0.2.1-pilot.14", 114, 14, "105711e", "2026-10-17T10:00:00Z", "pilot"),
            device = DeviceSnapshot("Xiaomi", "2201117TG", "15", 35, hasNfc = true, nfcEnabled = true, hasHce = true, kipatTap = "READY"),
            profile = ProfileSnapshot("KPT-VASH-0001", "Vash Puno", "Precision Orbit", "Cobalt"),
            last = tracker.last(),
            attempts = tracker.attempts(),
            events = tracker.events(),
            peer = PeerSeen("Ana Reyes", PilotReport.pseudonym("KPT-ANA-0001"), "Contact Ripple", "Night", 1_760_700_000_000L),
            connectionStored = false,
            card = CardTestRecord("write", "NfcA, MifareUltralight, Ndef", "org.nfcforum.ndef.type2", 496, true, 412, "WRITE_VERIFIED", 1_760_700_000_000L),
            checklist = PilotCheck.entries.associateWith { it == PilotCheck.CORRECT_PERSON },
        )
    }

    @Test
    fun buildMetadataIsBakedIntoTheApk() {
        val b = PilotStore.build
        assertEquals(BuildConfig.VERSION_NAME, b.versionName)
        assertEquals("debug", b.buildType)
        assertTrue("a commit sha or 'unknown'", b.gitSha.matches(Regex("[0-9a-f]{7,12}|unknown")))
        assertTrue(b.buildTime.isNotBlank())
        assertEquals("Kipat Pilot 0.2.1-pilot.14", BuildMetadata("0.2.1-pilot.14", 114, 14, "105711e9", "t", "pilot").title)
        assertEquals("105711e", BuildMetadata("0.2.1-pilot.14", 114, 14, "105711e9abc", "t", "pilot").shortSha)
        assertEquals("Kipat 0.2.0-shell", BuildMetadata("0.2.0-shell", 2, 0, "x", "t", "debug").title)
    }

    @Test
    fun summaryHasStagesTimingsAndNoPersonalData() {
        val s = state()
        val json = PilotReport.summaryJson(s, 1_760_700_100_000L)
        assertEquals("105711e", json.getJSONObject("build").getString("gitSha"))
        assertEquals("Xiaomi", json.getJSONObject("device").getString("manufacturer"))
        assertTrue(json.getJSONObject("nfc").getBoolean("hce"))
        val last = json.getJSONObject("lastAttempt")
        assertEquals("initiator", last.getString("role"))
        assertEquals("OWN_UPLOAD_LINK_LOST", last.getString("failureCode"))
        assertEquals("PASS", last.getJSONObject("stages").getJSONObject("peer_download").getString("result"))
        assertEquals("FAIL", last.getJSONObject("stages").getJSONObject("own_upload").getString("result"))
        assertEquals("NOT_REACHED", last.getJSONObject("stages").getJSONObject("reveal_requested").getString("result"))
        assertTrue(json.getJSONObject("humanChecklist").getBoolean("correct_person"))
        val text = json.toString() + PilotReport.eventsJson(s.events).toString() + PilotReport.deviceText(s)
        listOf("Vash", "Ana", "KPT-VASH-0001", "KPT-ANA-0001").forEach { assertFalse("leaks $it", text.contains(it)) }
        assertTrue(text.contains(PilotReport.pseudonym("KPT-VASH-0001")))
    }

    @Test
    fun zipHoldsTheFourFilesAndIsSmall() {
        val bytes = PilotReport.zip(state(), 1_760_700_100_000L)
        val names = mutableListOf<String>()
        ZipInputStream(ByteArrayInputStream(bytes)).use { z ->
            while (true) {
                val e = z.nextEntry ?: break
                names += e.name
                val content = z.readBytes().toString(Charsets.UTF_8)
                if (e.name == "summary.json") JSONObject(content)
                if (e.name == "events.json") assertTrue(JSONArray(content).length() > 0)
            }
        }
        assertEquals(listOf("summary.json", "events.json", "device.txt", "README.txt"), names)
        assertTrue("small enough to paste or attach", bytes.size < 64_000)
        assertTrue(PilotReport.fileName(state().device, 1_760_700_100_000L).matches(Regex("kipat-pilot-xiaomi-\\d{8}-\\d{6}\\.zip")))
    }

    @Test
    fun copyTextIsPasteableAndNamesTheFailure() {
        val text = PilotReport.copyText(state())
        assertTrue(text.startsWith("KIPAT PILOT"))
        assertTrue(text.contains("Build: 105711e"))
        assertTrue(text.contains("Kipat AID PASS"))
        assertTrue(text.contains("Own profile upload FAIL"))
        assertTrue(text.contains("Reveal requested NOT REACHED"))
        assertTrue(text.contains("Failure: OWN_UPLOAD_LINK_LOST"))
        assertTrue(text.contains("Checklist: 1/8"))
        assertFalse(text.contains("Ana"))
    }

    @Test
    fun storeSnapshotRoundTripsThroughAppPrivateJson() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        PilotStore.attach(context)
        PilotStore.reset(context)
        PilotStore.record("type4_selected", "ndef=112", "KipatType4")
        PilotStore.record("bootstrap_read", "bytes=110", "KipatType4")
        PilotStore.record("peer_hello", "v=1 x=e-0a1b2c", "KipatPeerProtocol")
        val saved = PilotStore.encode()
        PilotStore.tracker.clear()
        PilotStore.decode(saved)
        assertEquals(3, PilotStore.tracker.events().size)
        assertEquals(1, PilotStore.tracker.attempts().size)
        assertEquals("e-0a1b2c", PilotStore.tracker.last()!!.correlation)
        val summary = PilotReport.summaryJson(PilotStore.state(context), 0)
        assertEquals("e-0a1b2c", summary.getJSONObject("lastAttempt").getString("exchange"))
        assertTrue(summary.getJSONObject("build").has("installedAt"))
        PilotStore.reset(context)
    }
}
