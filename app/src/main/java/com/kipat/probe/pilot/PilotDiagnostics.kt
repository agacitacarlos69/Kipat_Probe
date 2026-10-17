package com.kipat.probe.pilot

/**
 * Physical-pilot diagnostics for Kipat NFC: a small, bounded, content-free record of what each
 * Kipat Tap attempt reached, so a tester can export it from the phone (no adb, no laptop).
 *
 * Pure Kotlin (clock injected) so the stage machine, redaction and bounds are unit tested.
 * Fed from the existing structured NFC log (KipatPeerSessions.log) plus a few Reveal milestones.
 *
 * Never recorded: profile text, names, emails, tokens, device identifiers, location, contacts,
 * session ids. Event details pass through [PilotRedactor], which keeps only allow-listed numeric or
 * code values.
 */
enum class TapRoleSeen(val label: String) { NONE("None"), INITIATOR("Initiator"), RESPONDER("Responder") }

enum class StageResult { PASS, FAIL, NOT_REACHED, UNKNOWN }

/** The stages of one ambient Kipat Tap, in order. [code] is used in failure codes and exports. */
enum class PilotStage(val label: String, val code: String) {
    BOOTSTRAP("Bootstrap", "BOOTSTRAP"),
    TYPE4("Type-4", "TYPE4"),
    KIPAT_AID("Kipat AID", "AID"),
    HELLO("HELLO", "HELLO"),
    PEER_DOWNLOAD("Peer profile download", "PEER_DOWNLOAD"),
    OWN_UPLOAD("Own profile upload", "OWN_UPLOAD"),
    INTEGRITY("Integrity", "INTEGRITY"),
    REVEAL_REQUESTED("Reveal requested", "REVEAL_REQUESTED"),
    REVEAL_DISPLAYED("Reveal displayed", "REVEAL_DISPLAYED"),
}

data class StageState(val result: StageResult, val atMs: Long? = null)

data class PilotEvent(
    /** Wall clock (ms since epoch) for the export; [elapsedMs] is relative to the attempt start. */
    val wallMs: Long,
    val name: String,
    val result: String,
    val elapsedMs: Long?,
    /** Redacted detail (allow-listed keys only), e.g. "bytes=812 reason=hash_mismatch". */
    val detail: String,
)

data class TapAttempt(
    val id: Int,
    val role: TapRoleSeen,
    val startedWallMs: Long,
    val startedMonoMs: Long,
    val stages: Map<PilotStage, StageState>,
    val failureCode: String? = null,
    val finishedMonoMs: Long? = null,
    /** "e-xxxxxx": the same value on both phones for one exchange (from HELLO). */
    val correlation: String? = null,
) {
    fun result(stage: PilotStage): StageResult = stages[stage]?.result
        ?: if (stage == PilotStage.REVEAL_DISPLAYED && stages[PilotStage.REVEAL_REQUESTED]?.result == StageResult.PASS) StageResult.UNKNOWN
        else StageResult.NOT_REACHED

    /** Elapsed ms from the attempt start to this stage, measured; null if not reached. */
    fun elapsed(stage: PilotStage): Long? = stages[stage]?.atMs
    val durationMs: Long? get() = finishedMonoMs?.let { it - startedMonoMs } ?: stages.values.mapNotNull { it.atMs }.maxOrNull()
    val profileReceived: Boolean get() = stages[PilotStage.PEER_DOWNLOAD]?.result == StageResult.PASS
    val profileSent: Boolean get() = stages[PilotStage.OWN_UPLOAD]?.result == StageResult.PASS
}

/** Keeps only safe, allow-listed key=value pairs from an NFC log detail string. */
object PilotRedactor {
    private val ALLOWED = setOf(
        "bytes", "ms", "since_create_ms", "offset", "of", "maxTransceive", "chunk", "v",
        "down", "up", "uploaded", "reason", "kind", "encounters", "ambient", "committed",
        "fallback", "send_back", "role", "ndef", "partial_upload_cleared", "result", "source", "status",
        "x", "upload",
    )
    private val VALUE = Regex("^[A-Za-z0-9_.-]{1,32}$")

    fun redact(detail: String): String = detail.split(' ', '\t', '\n')
        .mapNotNull { token ->
            val eq = token.indexOf('=')
            if (eq <= 0) return@mapNotNull null
            val key = token.substring(0, eq)
            val value = token.substring(eq + 1)
            if (key in ALLOWED && VALUE.matches(value)) "$key=$value" else null
        }
        .joinToString(" ")

    fun value(detail: String, key: String): String? =
        redact(detail).split(' ').firstOrNull { it.startsWith("$key=") }?.substringAfter('=')
}

/** Short, stable failure codes: "<STAGE>_<REASON>", e.g. OWN_UPLOAD_LINK_LOST, INTEGRITY_HASH_MISMATCH. */
object FailureCodes {
    fun of(stage: PilotStage, reason: String?): String {
        val r = (reason ?: "unknown").uppercase().replace(Regex("[^A-Z0-9]+"), "_").trim('_').ifEmpty { "UNKNOWN" }
        return "${stage.code}_$r"
    }

    /** Which stage a failing reason belongs to, independent of where the attempt had got to. */
    fun stageForReason(reason: String?): PilotStage? = when (reason?.lowercase()) {
        "hash_mismatch", "upload_incomplete", "invalid_profile" -> PilotStage.INTEGRITY
        "unsupported_version" -> PilotStage.HELLO
        "bad_bootstrap" -> PilotStage.BOOTSTRAP
        "not_eligible", "not_armed" -> PilotStage.TYPE4
        "no_isodep" -> PilotStage.KIPAT_AID
        else -> null
    }
}

/**
 * The stage machine. Feed it the structured NFC events ([onEvent]); it groups them into attempts
 * and tracks each stage's PASS / FAIL / NOT_REACHED with measured elapsed times.
 */
class PilotTracker(
    private val monoClock: () -> Long,
    private val wallClock: () -> Long,
    private val maxEvents: Int = MAX_EVENTS,
    private val maxAttempts: Int = MAX_ATTEMPTS,
) {
    private val events = ArrayDeque<PilotEvent>()
    private val attempts = ArrayDeque<TapAttempt>()
    private var nextId = 1
    var version: Long = 0
        private set

    @Synchronized fun events(): List<PilotEvent> = events.toList()
    @Synchronized fun attempts(): List<TapAttempt> = attempts.toList()
    @Synchronized fun last(): TapAttempt? = attempts.lastOrNull()

    @Synchronized
    fun restore(savedEvents: List<PilotEvent>, savedAttempts: List<TapAttempt>) {
        events.clear(); events.addAll(savedEvents.takeLast(maxEvents))
        attempts.clear(); attempts.addAll(savedAttempts.takeLast(maxAttempts))
        nextId = (attempts.maxOfOrNull { it.id } ?: 0) + 1
        version++
    }

    @Synchronized
    fun clear() {
        events.clear(); attempts.clear(); version++
    }

    /** Raw NFC log event (tag, name, detail). Returns true when it changed the pilot state. */
    @Synchronized
    fun onEvent(name: String, detail: String = "", tag: String = ""): Boolean {
        val now = monoClock()
        val code = PilotRedactor.value(detail, "reason")
        when (name) {
            // ---- attempt starts
            // Android may re-dispatch or re-select within one touch: continue the open attempt.
            "ndef_dispatched", "ambient_bootstrap_detected" -> {
                ensure(TapRoleSeen.INITIATOR, now)
                pass(PilotStage.BOOTSTRAP, now); pass(PilotStage.TYPE4, now)
            }
            "type4_selected" -> { ensure(TapRoleSeen.RESPONDER, now); pass(PilotStage.TYPE4, now) }
            "bootstrap_read" -> { ensure(TapRoleSeen.RESPONDER, now); pass(PilotStage.BOOTSTRAP, now) }
            // ---- protocol
            "hce_aid_selected" -> { ensure(TapRoleSeen.INITIATOR, now); pass(PilotStage.KIPAT_AID, now) }
            "kipat_aid_selected" -> { ensure(TapRoleSeen.RESPONDER, now); pass(PilotStage.KIPAT_AID, now) }
            "peer_hello" -> {
                ensure(null, now); pass(PilotStage.HELLO, now)
                PilotRedactor.value(detail, "x")?.let { x -> current()?.let { replace(it.copy(correlation = x)) } }
            }
            // Both phones carry the same public profile id: Kipat treats the peer as "you" and stops.
            "own_profile_upload_skipped" -> { ensure(null, now); fail(PilotStage.OWN_UPLOAD, FailureCodes.of(PilotStage.OWN_UPLOAD, code ?: "same_profile_id"), now) }
            "responder_reveal_skipped" -> {
                ensure(null, now)
                if (code == "same_profile_id") fail(PilotStage.REVEAL_REQUESTED, FailureCodes.of(PilotStage.REVEAL_REQUESTED, code), now)
            }
            // The other phone refused part of our upload (BEGIN, WRITE or COMMIT).
            "peer_upload_failed" -> { ensure(null, now); fail(PilotStage.OWN_UPLOAD, FailureCodes.of(PilotStage.OWN_UPLOAD, code), now) }
            "profile_download_complete" -> { ensure(TapRoleSeen.INITIATOR, now); pass(PilotStage.PEER_DOWNLOAD, now) }
            "peer_profile_chunk_read" -> {
                // Responder: our profile fully read by the other phone = our profile delivered.
                val offset = PilotRedactor.value(detail, "offset")?.toLongOrNull()
                val bytes = PilotRedactor.value(detail, "bytes")?.toLongOrNull()
                val of = PilotRedactor.value(detail, "of")?.toLongOrNull()
                if (offset != null && bytes != null && of != null && offset + bytes >= of) {
                    ensure(TapRoleSeen.RESPONDER, now); pass(PilotStage.OWN_UPLOAD, now)
                } else return false   // intermediate chunks are not worth a buffer slot
            }
            "peer_upload_completed" -> {
                ensure(null, now)
                if (current()?.role == TapRoleSeen.INITIATOR || tag == "KipatPeerReader") {
                    pass(PilotStage.OWN_UPLOAD, now)
                } else {
                    // Responder: the initiator's profile arrived and passed size, hash and schema checks.
                    pass(PilotStage.PEER_DOWNLOAD, now); pass(PilotStage.INTEGRITY, now)
                }
            }
            "peer_profile_received", "exchange_integrity_passed" -> {
                ensure(null, now)
                if (current()?.role == TapRoleSeen.INITIATOR) pass(PilotStage.INTEGRITY, now)
            }
            // ---- Reveal
            "reveal_requested", "responder_reveal_requested" -> { ensure(null, now); pass(PilotStage.REVEAL_REQUESTED, now) }
            "reveal_activity_created", "reveal_displayed", "background_launch_allowed" -> { ensure(null, now); pass(PilotStage.REVEAL_DISPLAYED, now); finish(now) }
            "background_launch_blocked" -> { ensure(null, now); fail(PilotStage.REVEAL_DISPLAYED, FailureCodes.of(PilotStage.REVEAL_DISPLAYED, "background_launch_blocked"), now) }
            // ---- failures
            "peer_exchange_failed", "exchange_failed" -> {
                ensure(null, now)
                val stage = FailureCodes.stageForReason(code) ?: nextStage() ?: PilotStage.HELLO
                fail(stage, FailureCodes.of(stage, code), now)
            }
            "peer_link_lost", "link_lost" -> {
                // The ambient initiator falls back to reader mode after a link loss: not final.
                if (PilotRedactor.value(detail, "fallback") == null) {
                    ensure(null, now)
                    val stage = nextStage() ?: PilotStage.HELLO
                    fail(stage, FailureCodes.of(stage, "link_lost"), now)
                }
            }
            "duplicate_exchange_ignored", "peer_exchange_duplicate", "duplicate_ignored",
            "mutual_incomplete", "ambient_rate_limited", "type4_refused",
            "linked_card_resolved", "card_read", "card_write", "connection_stored",
            "isodep_connected", "ambient_initiator_created", "exchange_complete", "peer_exchange_success",
            "peer_upload_started", "own_profile_upload_started", "profile_download_started", "kipat_peer_detected" -> Unit
            else -> return false
        }
        record(name, resultLabel(name), now, detail)
        return true
    }

    private fun resultLabel(name: String) = when {
        name.contains("failed") || name.contains("blocked") || name.contains("lost") -> "FAIL"
        name.contains("skipped") -> "FAIL"
        name.contains("duplicate") || name.contains("incomplete") || name.contains("refused") || name.contains("rate_limited") -> "INFO"
        else -> "PASS"
    }

    private fun record(name: String, result: String, now: Long, detail: String) {
        val a = current()
        events.addLast(PilotEvent(wallClock(), name, result, a?.let { now - it.startedMonoMs }, PilotRedactor.redact(detail)))
        while (events.size > maxEvents) events.removeFirst()
        version++
    }

    private fun current(): TapAttempt? = attempts.lastOrNull()

    private fun start(role: TapRoleSeen, now: Long) {
        attempts.addLast(TapAttempt(nextId++, role, wallClock(), now, emptyMap()))
        while (attempts.size > maxAttempts) attempts.removeFirst()
    }

    /** Continue the current attempt if it is recent and unfinished; otherwise open a new one. */
    private fun ensure(role: TapRoleSeen?, now: Long) {
        val a = current()
        val stale = a == null || a.finishedMonoMs != null || now - a.startedMonoMs > ATTEMPT_WINDOW_MS ||
            (role != null && a.role != TapRoleSeen.NONE && a.role != role)
        if (stale) start(role ?: TapRoleSeen.NONE, now)
        else if (role != null && a!!.role == TapRoleSeen.NONE) replace(a.copy(role = role))
    }

    private fun replace(a: TapAttempt) {
        attempts.removeLast(); attempts.addLast(a)
    }

    private fun pass(stage: PilotStage, now: Long) {
        val a = current() ?: return
        if (a.stages[stage]?.result == StageResult.PASS) return
        replace(a.copy(stages = a.stages + (stage to StageState(StageResult.PASS, now - a.startedMonoMs))))
    }

    private fun fail(stage: PilotStage, code: String, now: Long) {
        val a = current() ?: return
        replace(a.copy(stages = a.stages + (stage to StageState(StageResult.FAIL, now - a.startedMonoMs)), failureCode = a.failureCode ?: code, finishedMonoMs = now))
    }

    private fun finish(now: Long) {
        val a = current() ?: return
        if (a.finishedMonoMs == null) replace(a.copy(finishedMonoMs = now))
    }

    /** The first stage (in protocol order) not yet passed: where a failure without a reason happened. */
    private fun nextStage(): PilotStage? {
        val a = current() ?: return null
        val order = PilotStage.entries.filter { it != PilotStage.REVEAL_DISPLAYED }
        return order.firstOrNull { a.stages[it]?.result != StageResult.PASS }
    }

    companion object {
        const val MAX_EVENTS = 200
        const val MAX_ATTEMPTS = 10
        /** Events further apart than this belong to separate touches. */
        const val ATTEMPT_WINDOW_MS = 15_000L
    }
}
