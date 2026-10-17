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

    /** The initiator's full ambient Kipat Tap, as the real code logs it. */
    private fun initiatorHappyPath() {
        at(0, "ndef_dispatched")
        at(5, "ambient_initiator_created", "since_create_ms=5")
        at(40, "hce_aid_selected", "ok", "KipatPeerReader")
        at(60, "peer_hello", "v=1 presenter=aa reader=bb x=e-0a0b0c", "KipatPeerProtocol")
        at(180, "profile_download_complete", "bytes=820", "KipatPeerReader")
        at(190, "peer_upload_started", "bytes=790", "KipatPeerReader")
        at(200, "own_upload_begin_ok", "bytes=790", "KipatPeerReader")
        at(250, "own_upload_chunks_ok", "bytes=790", "KipatPeerReader")
        at(260, "peer_upload_completed", "bytes=790", "KipatPeerReader")
        at(265, "finalize_ok", "", "KipatPeerReader")
        at(266, "mutual_complete", "reason=finalize", "KipatPeerReader")
        at(270, "peer_profile_received", "down=820 up=790", "KipatAmbientNfc")
        at(275, "reveal_requested")
        at(330, "reveal_displayed")
    }

    @Test
    fun initiatorHappyPathPassesEveryStageWithMeasuredTimes() {
        initiatorHappyPath()
        val a = tracker.last()!!
        assertEquals(TapRoleSeen.INITIATOR, a.role)
        PilotStage.entries.forEach { assertEquals(it.name, StageResult.PASS, a.result(it)) }
        assertEquals(180L, a.elapsed(PilotStage.PEER_DOWNLOAD))
        assertEquals(200L, a.elapsed(PilotStage.OWN_BEGIN))
        assertEquals(250L, a.elapsed(PilotStage.OWN_CHUNKS))
        assertEquals(260L, a.elapsed(PilotStage.OWN_COMMIT))
        assertEquals(265L, a.elapsed(PilotStage.FINALIZE))
        assertEquals(330L, a.durationMs)
        assertNull(a.failureCode)
        assertTrue(a.profileReceived && a.profileSent && a.mutual)
        assertEquals("e-0a0b0c", a.correlation)
    }

    /** The responder's full ambient Kipat Tap, every Type 4 step included. */
    private fun responderHappyPath() {
        at(0, "hce_service_created", "cold=true since_process_ms=420 create_ms=35", "KipatHce")
        at(1, "hce_first_apdu", "ms=2", "KipatHce")
        at(2, "ndef_aid_selected", "v=2 ndef=112 session=x", "KipatType4")
        at(4, "cc_file_selected", "", "KipatType4")
        at(6, "cc_file_read", "bytes=15", "KipatType4")
        at(8, "ndef_file_selected", "", "KipatType4")
        at(10, "ndef_nlen_read", "bytes=110", "KipatType4")
        at(12, "ndef_payload_read", "offset=2 bytes=110", "KipatType4")
        at(13, "ndef_bootstrap_served", "bytes=110", "KipatType4")
        at(60, "hce_custom_aid_selected", "session=x ambient=true", "KipatHce")
        at(70, "peer_hello", "v=1 x=e-0a0b0c", "KipatPeerProtocol")
        at(90, "peer_profile_chunk_read", "offset=0 bytes=240 of=820", "KipatPeerProtocol")
        at(120, "peer_profile_chunk_read", "offset=240 bytes=240 of=820", "KipatPeerProtocol")
        at(150, "peer_profile_chunk_read", "offset=720 bytes=100 of=820", "KipatPeerProtocol")
        at(160, "peer_upload_started", "bytes=790", "KipatPeerProtocol")
        at(240, "peer_upload_completed", "bytes=790", "KipatPeerProtocol")
        at(245, "finalize_received", "uploaded=true", "KipatAmbientNfc")
        at(246, "mutual_complete", "reason=finalize", "KipatAmbientNfc")
        at(250, "responder_reveal_requested", "exchange=3", "KipatTapLaunch")
        at(400, "background_launch_allowed", "exchange=3", "KipatTapLaunch")
    }

    @Test
    fun responderHappyPathRecordsEveryType4StepAndBothProfiles() {
        responderHappyPath()
        assertEquals(1, tracker.attempts().size)
        val a = tracker.last()!!
        assertEquals(TapRoleSeen.RESPONDER, a.role)
        PilotStage.entries.forEach { assertEquals(it.name, StageResult.PASS, a.result(it)) }
        // Measured from the NDEF SELECT (2 ms after the service started), the attempt's first event.
        assertEquals(4L, a.elapsed(PilotStage.CC_FILE))
        assertEquals(8L, a.elapsed(PilotStage.NDEF_FILE))
        assertEquals(11L, a.elapsed(PilotStage.BOOTSTRAP))
        assertEquals("dispatch is inferred from the Kipat AID SELECT", 58L, a.elapsed(PilotStage.DISPATCHED))
        assertEquals(88L, a.elapsed(PilotStage.OWN_BEGIN))
        assertEquals(148L, a.elapsed(PilotStage.OWN_CHUNKS))
        assertEquals("they validated ours before uploading theirs", 158L, a.elapsed(PilotStage.OWN_COMMIT))
        assertEquals(238L, a.elapsed(PilotStage.PEER_DOWNLOAD))
        assertTrue(a.mutual)
        assertEquals("intermediate chunks are not buffered", 0, tracker.events().count { it.detail.contains("offset=240") })
    }

    @Test
    fun responderThatServedTheBootstrapButNeverSawTheKipatAidCannotKnowAboutDispatch() {
        at(0, "ndef_aid_selected", "v=2 ndef=112", "KipatType4")
        at(5, "cc_file_read", "bytes=15", "KipatType4")
        at(9, "ndef_bootstrap_served", "bytes=110", "KipatType4")
        val a = tracker.last()!!
        assertEquals(StageResult.PASS, a.result(PilotStage.BOOTSTRAP))
        assertEquals(StageResult.UNKNOWN, a.result(PilotStage.DISPATCHED))
        assertEquals(StageResult.NOT_REACHED, a.result(PilotStage.KIPAT_AID))
        assertFalse(a.mutual)
    }

    @Test
    fun kipatTapRefusingTheNdefSelectIsANamedFailure() {
        at(0, "type4_refused", "reason=no_public_profile", "KipatType4")
        assertEquals("KIPAT_TAP_NO_PUBLIC_PROFILE", tracker.last()!!.failureCode)
        assertEquals(StageResult.FAIL, tracker.last()!!.result(PilotStage.NDEF_AID))
        mono += 60_000
        tracker.onEvent("type4_refused", "reason=kipat_tap_disabled", "KipatType4")
        assertEquals("KIPAT_TAP_DISABLED", tracker.last()!!.failureCode)
        mono += 60_000
        tracker.onEvent("type4_refused", "reason=device_locked", "KipatType4")
        assertEquals("KIPAT_TAP_DEVICE_LOCKED", tracker.last()!!.failureCode)
    }

    @Test
    fun linkLostDuringUploadFailsThatStageAndLeavesLaterOnesNotReached() {
        at(0, "ndef_dispatched")
        at(40, "hce_aid_selected", "ok", "KipatPeerReader")
        at(60, "peer_hello", "v=1", "KipatPeerProtocol")
        at(180, "profile_download_complete", "bytes=820", "KipatPeerReader")
        at(200, "own_upload_begin_ok", "bytes=790", "KipatPeerReader")
        at(230, "peer_link_lost", "during_upload", "KipatPeerReader")
        val a = tracker.last()!!
        assertEquals(StageResult.PASS, a.result(PilotStage.PEER_DOWNLOAD))
        assertEquals(StageResult.PASS, a.result(PilotStage.OWN_BEGIN))
        assertEquals(StageResult.FAIL, a.result(PilotStage.OWN_CHUNKS))
        assertEquals("OWN_CHUNKS_LINK_LOST", a.failureCode)
        listOf(PilotStage.OWN_COMMIT, PilotStage.FINALIZE, PilotStage.MUTUAL, PilotStage.REVEAL_REQUESTED, PilotStage.REVEAL_DISPLAYED)
            .forEach { assertEquals(it.name, StageResult.NOT_REACHED, a.result(it)) }
        assertFalse(a.mutual)
    }

    @Test
    fun finalizeLostAfterACommittedUploadIsStillMutual() {
        at(0, "ndef_dispatched")
        at(180, "profile_download_complete", "bytes=820", "KipatPeerReader")
        at(260, "peer_upload_completed", "bytes=790", "KipatPeerReader")
        at(262, "finalize_lost", "", "KipatPeerReader")
        at(263, "mutual_complete", "reason=commit_acked", "KipatPeerReader")
        at(270, "reveal_requested")
        val a = tracker.last()!!
        assertEquals(StageResult.FAIL, a.result(PilotStage.FINALIZE))
        assertTrue(a.mutual)
        assertEquals(StageResult.PASS, a.result(PilotStage.REVEAL_REQUESTED))
        assertEquals("the attempt is not closed by the finalize failure", null, a.finishedMonoMs)
    }

    @Test
    fun responderLinkEndingAfterCommitIsNotALinkLossFailure() {
        at(0, "hce_custom_aid_selected", "ambient=true", "KipatHce")
        at(240, "peer_upload_completed", "bytes=790", "KipatPeerProtocol")
        at(250, "peer_link_lost", "committed=true", "KipatHce")
        at(251, "finalize_missing", "", "KipatAmbientNfc")
        at(252, "mutual_complete", "reason=commit_then_link_end", "KipatAmbientNfc")
        val a = tracker.last()!!
        assertTrue(a.mutual)
        assertEquals("FINALIZE_LINK_LOST", a.failureCode)
        assertEquals(StageResult.PASS, a.result(PilotStage.PEER_DOWNLOAD))
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
        at(0, "ndef_aid_selected")
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

    @Test
    fun savedHistoryLoadedLateGoesFirstAndIdsStayUnique() {
        val saved = PilotTracker(monoClock = { mono }, wallClock = { mono })
        saved.onEvent("ndef_dispatched"); mono += 20_000; saved.onEvent("ndef_dispatched")
        at(40_000, "ndef_aid_selected", "v=2", "KipatType4") // arrives before the file was read
        tracker.prependSaved(saved.events(), saved.attempts())
        assertEquals(listOf(1, 2, 3), tracker.attempts().map { it.id })
        assertEquals(TapRoleSeen.RESPONDER, tracker.last()!!.role)
        assertEquals("ndef_aid_selected", tracker.events().last().name)
    }

    // ------------------------------------------------------------------ failure codes

    @Test
    fun failureCodesNameTheStageAndReason() {
        assertEquals("PEER_DOWNLOAD_HASH_MISMATCH", FailureCodes.of(PilotStage.PEER_DOWNLOAD, "hash_mismatch"))
        assertEquals("OWN_CHUNKS_LINK_LOST", FailureCodes.of(PilotStage.OWN_CHUNKS, "link_lost"))
        assertEquals("HELLO_UNKNOWN", FailureCodes.of(PilotStage.HELLO, null))
        assertEquals(PilotStage.PEER_DOWNLOAD, FailureCodes.stageForReason("hash_mismatch"))
        assertEquals(PilotStage.PEER_DOWNLOAD, FailureCodes.stageForReason("upload_incomplete"))
        assertEquals(PilotStage.HELLO, FailureCodes.stageForReason("unsupported_version"))
        assertEquals(PilotStage.BOOTSTRAP, FailureCodes.stageForReason("bad_bootstrap"))
        assertNull(FailureCodes.stageForReason("PROTOCOL"))

        at(0, "ndef_aid_selected")
        at(50, "hce_custom_aid_selected")
        at(60, "peer_hello")
        at(200, "peer_exchange_failed", "reason=hash_mismatch", "KipatPeerProtocol")
        assertEquals("PEER_DOWNLOAD_HASH_MISMATCH", tracker.last()!!.failureCode)
        assertEquals(StageResult.FAIL, tracker.last()!!.result(PilotStage.PEER_DOWNLOAD))

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
        assertEquals("OWN_BEGIN_SAME_PROFILE_ID", a.failureCode)
        assertEquals(StageResult.FAIL, a.result(PilotStage.OWN_BEGIN))

        mono += 60_000
        tracker.onEvent("ndef_aid_selected")
        tracker.onEvent("responder_reveal_skipped", "reason=same_profile_id", "KipatTapLaunch")
        assertEquals("REVEAL_REQUESTED_SAME_PROFILE_ID", tracker.last()!!.failureCode)

        listOf("begin_rejected" to "OWN_BEGIN_REJECTED", "write_rejected" to "OWN_CHUNKS_REJECTED", "commit_rejected" to "OWN_COMMIT_REJECTED").forEach { (reason, expected) ->
            mono += 60_000
            tracker.onEvent("ndef_dispatched")
            tracker.onEvent("profile_download_complete", "bytes=820", "KipatPeerReader")
            tracker.onEvent("peer_upload_failed", "reason=$reason", "KipatPeerReader")
            assertEquals(expected, tracker.last()!!.failureCode)
        }
        assertEquals("ids in hello are never kept", "v=1 x=e-a81f92", tracker.events().first { it.name == "peer_hello" }.detail.let { d -> d.split(' ').filter { it.startsWith("v=") || it.startsWith("x=") }.joinToString(" ") })
        assertFalse(tracker.events().any { it.detail.contains("presenter=") })
    }

    // ------------------------------------------------------------------ decision gate: controlled vs ambient

    @Test
    fun controlledAttemptsAreTaggedAndNeverCountAsAmbientEvidence() {
        at(0, "controlled_test_started", "role=presenter", "KipatPeerNfc")
        at(5, "hce_service_created", "cold=false since_process_ms=9000", "KipatHce")
        at(10, "hce_custom_aid_selected", "ambient=false", "KipatHce")
        at(20, "peer_hello", "v=1 x=e-111111", "KipatPeerProtocol")
        at(30, "controlled_test_stopped", "", "KipatPeerNfc")
        val e = tracker.ambientEvidence()
        assertFalse("the controlled run's HCE start is not ambient evidence", e.hceServiceCreated)
        assertFalse(e.customAidSelected)
        assertTrue(tracker.lastControlled(TapRoleSeen.RESPONDER)!!.controlled)
        assertNull(tracker.lastAmbient())
        assertTrue(tracker.events().filter { it.name == "hce_service_created" }.all { it.controlled })

        // Later, an ambient touch on the same phone.
        at(60_000, "hce_service_created", "cold=true since_process_ms=640", "KipatHce")
        at(60_002, "ndef_aid_selected", "v=2 ndef=112", "KipatType4")
        val after = tracker.ambientEvidence()
        assertTrue(after.hceServiceCreated && after.ndefSelectReceived)
        assertFalse(after.bootstrapServed)
        assertFalse(tracker.lastAmbient()!!.controlled)
        assertEquals("the controlled attempt is still there", 2, tracker.attempts().size)
    }

    @Test
    fun noAmbientEventsMeansKipatNeverReceivedTheSelect() {
        val e = tracker.ambientEvidence()
        assertFalse(e.hceServiceCreated || e.firstApdu || e.ndefSelectReceived || e.bootstrapServed || e.kipatDispatchedHere)
    }

    @Test
    fun anAmbientAttemptNeverContinuesAControlledOne() {
        at(0, "controlled_test_started", "role=reader", "KipatPeerNfc")
        at(10, "hce_aid_selected", "ok", "KipatPeerReader")
        at(20, "controlled_test_stopped", "", "KipatPeerNfc")
        at(30, "ndef_dispatched")
        assertEquals(2, tracker.attempts().size)
        assertTrue(tracker.attempts().first().controlled)
        assertFalse(tracker.attempts().last().controlled)
    }
}
