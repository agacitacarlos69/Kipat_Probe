package com.kipat.probe

import com.kipat.probe.pilot.FailureCodes
import com.kipat.probe.pilot.PilotRedactor
import com.kipat.probe.pilot.PilotStage
import com.kipat.probe.pilot.PilotTracker
import com.kipat.probe.pilot.StageResult
import com.kipat.probe.pilot.TapRoleSeen
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The physical-pilot stage machine, redaction and bounds (pure JVM). */
class PilotDiagnosticsTest {

    private var mono = 1_000L
    private val tracker = PilotTracker(monoClock = { mono }, wallClock = { 1_760_000_000_000L + mono })

    private fun at(ms: Long, name: String, detail: String = "", tag: String = "") {
        mono = 1_000L + ms
        tracker.onEvent(name, detail, tag)
    }

    // ------------------------------------------------------------------ redaction

    @Test
    fun redactionKeepsOnlyAllowListedCodesAndNumbers() {
        val raw = "session=ab12cd34 presenter=deadbeef reader=0011 bytes=812 reason=hash_mismatch name=Ana email=ana@x.co exchange=7 ms=140"
        assertEquals("bytes=812 reason=hash_mismatch ms=140", PilotRedactor.redact(raw))
        assertEquals("", PilotRedactor.redact("Ana Reyes Product Designer"))
        assertEquals("reason dropped when the value isn't a plain code", "", PilotRedactor.redact("reason=<script>alert(1)</script>"))
        assertEquals("", PilotRedactor.redact("reason=" + "x".repeat(40)))
        assertEquals("hash_mismatch", PilotRedactor.value(raw, "reason"))
        assertNull(PilotRedactor.value(raw, "session"))
    }

    @Test
    fun eventsInTheBufferAreAlreadyRedacted() {
        at(0, "type4_selected", "session=abcdef12 ndef=112")
        val e = tracker.events().single()
        assertEquals("ndef=112", e.detail)
        assertFalse(e.detail.contains("abcdef12"))
    }

    // ------------------------------------------------------------------ bounds

    @Test
    fun eventBufferAndAttemptsAreBounded() {
        val small = PilotTracker(monoClock = { mono }, wallClock = { mono }, maxEvents = 50, maxAttempts = 3)
        repeat(500) { i ->
            mono += 20_000 // each touch is a separate attempt
            small.onEvent("type4_selected", "ndef=112")
            small.onEvent("bootstrap_read", "bytes=110")
            if (i == 499) assertEquals(3, small.attempts().size)
        }
        assertEquals(50, small.events().size)
        assertEquals(3, small.attempts().size)
        assertEquals("ids keep increasing", 500, small.attempts().last().id)
    }

    @Test
    fun unrelatedEventsAreIgnored() {
        assertFalse(tracker.onEvent("peer_profile_meta", "bytes=800"))
        assertFalse(tracker.onEvent("some_android_activity"))
        assertTrue(tracker.events().isEmpty())
    }

    // ------------------------------------------------------------------ stage machine: both roles

    @Test
    fun initiatorHappyPathPassesEveryStageWithMeasuredTimes() {
        at(0, "ndef_dispatched")
        at(5, "ambient_initiator_created", "since_create_ms=5")
        at(40, "hce_aid_selected", "ok", "KipatPeerReader")
        at(60, "peer_hello", "v=1 presenter=aa reader=bb", "KipatPeerProtocol")
        at(180, "profile_download_complete", "bytes=820", "KipatPeerReader")
        at(260, "peer_upload_completed", "bytes=790", "KipatPeerReader")
        at(270, "peer_profile_received", "down=820 up=790", "KipatAmbientNfc")
        at(275, "reveal_requested")
        at(330, "reveal_displayed")
        val a = tracker.last()!!
        assertEquals(TapRoleSeen.INITIATOR, a.role)
        PilotStage.entries.forEach { assertEquals(it.name, StageResult.PASS, a.result(it)) }
        assertEquals(180L, a.elapsed(PilotStage.PEER_DOWNLOAD))
        assertEquals(260L, a.elapsed(PilotStage.OWN_UPLOAD))
        assertEquals(330L, a.durationMs)
        assertNull(a.failureCode)
        assertTrue(a.profileReceived && a.profileSent)
    }

    @Test
    fun responderHappyPathMapsServedAndReceivedProfiles() {
        at(0, "type4_selected", "session=x ndef=112", "KipatType4")
        at(10, "type4_selected", "session=x ndef=112", "KipatType4") // Android re-selects: same attempt
        at(20, "bootstrap_read", "bytes=110", "KipatType4")
        at(60, "kipat_aid_selected", "session=x ambient=true", "KipatHce")
        at(70, "peer_hello", "v=1", "KipatPeerProtocol")
        at(90, "peer_profile_chunk_read", "offset=0 bytes=240 of=820", "KipatPeerProtocol")
        assertEquals("intermediate chunk is not a delivery", StageResult.NOT_REACHED, tracker.last()!!.result(PilotStage.OWN_UPLOAD))
        at(150, "peer_profile_chunk_read", "offset=720 bytes=100 of=820", "KipatPeerProtocol")
        at(240, "peer_upload_completed", "bytes=790", "KipatPeerProtocol")
        at(250, "responder_reveal_requested", "exchange=3", "KipatTapLaunch")
        at(400, "background_launch_allowed", "exchange=3", "KipatTapLaunch")
        assertEquals(1, tracker.attempts().size)
        val a = tracker.last()!!
        assertEquals(TapRoleSeen.RESPONDER, a.role)
        PilotStage.entries.forEach { assertEquals(it.name, StageResult.PASS, a.result(it)) }
        assertEquals(150L, a.elapsed(PilotStage.OWN_UPLOAD))
        assertEquals(240L, a.elapsed(PilotStage.PEER_DOWNLOAD))
    }

    @Test
    fun linkLostDuringUploadFailsThatStageAndLeavesLaterOnesNotReached() {
        at(0, "ndef_dispatched")
        at(40, "hce_aid_selected", "ok", "KipatPeerReader")
        at(60, "peer_hello", "v=1", "KipatPeerProtocol")
        at(180, "profile_download_complete", "bytes=820", "KipatPeerReader")
        at(200, "peer_upload_started", "bytes=790", "KipatPeerReader")
        at(230, "peer_link_lost", "during_upload", "KipatPeerReader")
        val a = tracker.last()!!
        assertEquals(StageResult.PASS, a.result(PilotStage.PEER_DOWNLOAD))
        assertEquals(StageResult.FAIL, a.result(PilotStage.OWN_UPLOAD))
        assertEquals("OWN_UPLOAD_LINK_LOST", a.failureCode)
        assertEquals(StageResult.NOT_REACHED, a.result(PilotStage.INTEGRITY))
        assertEquals(StageResult.NOT_REACHED, a.result(PilotStage.REVEAL_REQUESTED))
        assertEquals(StageResult.NOT_REACHED, a.result(PilotStage.REVEAL_DISPLAYED))
    }

    @Test
    fun ambientLinkLossWithReaderFallbackIsNotAFailure() {
        at(0, "ndef_dispatched")
        at(30, "link_lost", "fallback=reader_mode", "KipatAmbientNfc")
        assertNull(tracker.last()!!.failureCode)
        assertTrue(tracker.last()!!.stages.values.none { it.result == StageResult.FAIL })
    }

    @Test
    fun revealRequestedButNeverSeenIsUnknownNotPass() {
        at(0, "type4_selected")
        at(250, "responder_reveal_requested")
        assertEquals(StageResult.UNKNOWN, tracker.last()!!.result(PilotStage.REVEAL_DISPLAYED))
        at(260, "background_launch_blocked", "exchange=1")
        assertEquals(StageResult.FAIL, tracker.last()!!.result(PilotStage.REVEAL_DISPLAYED))
        assertEquals("REVEAL_DISPLAYED_BACKGROUND_LAUNCH_BLOCKED", tracker.last()!!.failureCode)
    }

    @Test
    fun aNewTouchAfterTheWindowOrAFinishedAttemptStartsANewAttempt() {
        at(0, "ndef_dispatched")
        at(300, "reveal_displayed")
        at(1_000, "ndef_dispatched") // finished -> new
        assertEquals(2, tracker.attempts().size)
        at(1_000 + PilotTracker.ATTEMPT_WINDOW_MS + 1, "ndef_dispatched") // stale -> new
        assertEquals(3, tracker.attempts().size)
    }

    // ------------------------------------------------------------------ failure codes

    @Test
    fun failureCodesNameTheStageAndReason() {
        assertEquals("INTEGRITY_HASH_MISMATCH", FailureCodes.of(PilotStage.INTEGRITY, "hash_mismatch"))
        assertEquals("OWN_UPLOAD_LINK_LOST", FailureCodes.of(PilotStage.OWN_UPLOAD, "link_lost"))
        assertEquals("HELLO_UNKNOWN", FailureCodes.of(PilotStage.HELLO, null))
        assertEquals(PilotStage.INTEGRITY, FailureCodes.stageForReason("hash_mismatch"))
        assertEquals(PilotStage.INTEGRITY, FailureCodes.stageForReason("upload_incomplete"))
        assertEquals(PilotStage.HELLO, FailureCodes.stageForReason("unsupported_version"))
        assertEquals(PilotStage.BOOTSTRAP, FailureCodes.stageForReason("bad_bootstrap"))
        assertNull(FailureCodes.stageForReason("PROTOCOL"))

        at(0, "type4_selected")
        at(50, "kipat_aid_selected")
        at(60, "peer_hello")
        at(200, "peer_exchange_failed", "reason=hash_mismatch", "KipatPeerProtocol")
        assertEquals("INTEGRITY_HASH_MISMATCH", tracker.last()!!.failureCode)
        assertEquals(StageResult.FAIL, tracker.last()!!.result(PilotStage.INTEGRITY))

        mono += 60_000
        tracker.onEvent("ndef_dispatched")
        tracker.onEvent("peer_exchange_failed", "reason=PROTOCOL IOException", "KipatPeerNfc")
        assertEquals("unknown reason fails the next unreached stage", "AID_PROTOCOL", tracker.last()!!.failureCode)
    }

    // ------------------------------------------------------------------ P1-NFC-MUTUAL-EXCHANGE codes

    @Test
    fun sameProfileIdAndRejectedUploadsAreNamedFailures() {
        at(0, "ndef_dispatched")
        at(40, "hce_aid_selected", "ok", "KipatPeerReader")
        at(60, "peer_hello", "v=1 presenter=aa reader=bb caps=1 x=e-a81f92", "KipatPeerProtocol")
        at(180, "profile_download_complete", "bytes=820", "KipatPeerReader")
        at(181, "own_profile_upload_skipped", "reason=same_profile_id", "KipatPeerReader")
        val a = tracker.last()!!
        assertEquals("e-a81f92", a.correlation)
        assertEquals("OWN_UPLOAD_SAME_PROFILE_ID", a.failureCode)
        assertEquals(StageResult.FAIL, a.result(PilotStage.OWN_UPLOAD))

        mono += 60_000
        tracker.onEvent("type4_selected")
        tracker.onEvent("responder_reveal_skipped", "reason=same_profile_id", "KipatTapLaunch")
        assertEquals("REVEAL_REQUESTED_SAME_PROFILE_ID", tracker.last()!!.failureCode)

        mono += 60_000
        tracker.onEvent("ndef_dispatched")
        tracker.onEvent("profile_download_complete", "bytes=820", "KipatPeerReader")
        tracker.onEvent("peer_upload_failed", "reason=commit_rejected", "KipatPeerReader")
        assertEquals("OWN_UPLOAD_COMMIT_REJECTED", tracker.last()!!.failureCode)
        assertEquals("ids in hello are never kept", "v=1 x=e-a81f92", tracker.events().first { it.name == "peer_hello" }.detail.let { d -> d.split(' ').filter { it.startsWith("v=") || it.startsWith("x=") }.joinToString(" ") })
        assertFalse(tracker.events().any { it.detail.contains("presenter=") })
    }
}
